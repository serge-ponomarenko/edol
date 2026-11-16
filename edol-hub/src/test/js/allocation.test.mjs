import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import assert from 'node:assert/strict';
import {test} from 'node:test';

const template = readFileSync(new URL('../../main/resources/templates/dashboard/print-jobs/allocation.html', import.meta.url), 'utf8');
const source = template.match(/<script th:inline="javascript">([\s\S]*?)<\/script>/)[1];

function page(response = {ok: true, json: async () => true}, secure = true) {
    const requests = [];
    const alerts = [];
    const elements = new Map();
    const values = {
        spoolMutationMode: 'replace', spoolMutationFilamentId: '3', selectedSpoolId: '7',
        spoolMutationGrams: '25.4', replaceSourceFilamentId: '3', targetFilamentId: '4'
    };
    let reloads = 0;
    let hides = 0;
    const context = {
        Headers, console, window: {location: {pathname: '/printers/printer-1/print-jobs/allocation/42'}},
        alert: message => alerts.push(message), confirm: () => true,
        document: {
            getElementById(id) {
                if (!elements.has(id)) elements.set(id, {value: values[id], max: '25.4', addEventListener() {}});
                return elements.get(id);
            },
            querySelector: selector => secure ? {content: selector.includes('_csrf_header') ? 'X-CSRF-TOKEN' : 'synthetic-token'} : null,
            addEventListener() {}
        },
        fetch: async (url, options) => {
            // Leave the initial page load pending; mutations reload through the stub below.
            if (options.method !== 'POST') return new Promise(() => {});
            requests.push({url, options});
            if (response instanceof Error) throw response;
            return response;
        },
        reloaded: () => ++reloads,
        hidden: () => ++hides
    };
    context.location = context.window.location;
    runInNewContext(source + `
        loadAllocation = async () => reloaded();
        spoolMutationModal = {hide: hidden};
        replaceFilamentModal = {hide: hidden};
    `, context);
    return {context, requests, alerts, elements, reloads: () => reloads, hides: () => hides};
}

const actions = {
    rerun: ui => ui.context.rerunAllocation(3),
    'replace-spool': ui => ui.context.submitSpoolMutation(),
    'add-spool': ui => {
        ui.context.document.getElementById('spoolMutationMode').value = 'add';
        return ui.context.submitSpoolMutation();
    },
    'replace-filament': ui => ui.context.submitFilamentReplace(),
    finalize: ui => ui.context.finalizeReconciliation()
};

test('allocation template renders optional server CSRF metadata', () => {
    assert.match(template, /name="_csrf" th:if="\$\{_csrf != null\}" th:content="\$\{_csrf.token\}"/);
    assert.match(template, /name="_csrf_header" th:if="\$\{_csrf != null\}" th:content="\$\{_csrf.headerName\}"/);
});

for (const [endpoint, action] of Object.entries(actions)) {
    test(`${endpoint} sends CSRF and succeeds in secure mode`, async () => {
        const ui = page();
        await action(ui);
        assert.equal(ui.requests.length, 1);
        const {url, options} = ui.requests[0];
        assert.match(url, new RegExp(`/allocations/${endpoint}\\?jobId=42`));
        assert.equal(options.headers.get('X-CSRF-TOKEN'), 'synthetic-token');
        if (endpoint === 'finalize') {
            assert.match(ui.context.window.location.href, /\/print-jobs$/);
        } else {
            assert.equal(ui.reloads(), 1);
            assert.equal(ui.alerts.length, 0);
        }
    });

    test(`${endpoint} reports rejection without presenting success or retrying`, async () => {
        for (const response of [
            {ok: false, status: 403}, {ok: false, status: 500},
            {ok: true, redirected: true}, new Error('synthetic network failure')
        ]) {
            const ui = page(response);
            await action(ui);
            assert.equal(ui.requests.length, 1);
            assert.equal(ui.reloads(), 0);
            assert.equal(ui.hides(), 0);
            assert.equal(ui.alerts.length, 1);
            assert.match(ui.alerts[0], /^Failed to /);
            assert.equal(ui.context.window.location.href, undefined);
            if (endpoint === 'finalize') assert.equal(ui.elements.get('finalizeBtn').disabled, false);
        }
    });
}

test('Home allocation works without CSRF metadata', async () => {
    const ui = page(undefined, false);
    await actions.rerun(ui);
    assert.equal(ui.requests[0].options.headers.has('X-CSRF-TOKEN'), false);
    assert.equal(ui.reloads(), 1);
});

test('unresolved finalization restores the button for another attempt', async () => {
    const ui = page({ok: true, json: async () => false});
    await actions.finalize(ui);
    assert.equal(ui.elements.get('finalizeBtn').disabled, false);
    assert.match(ui.alerts[0], /still unresolved/);
    assert.equal(ui.context.window.location.href, undefined);
});
