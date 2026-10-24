import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import assert from 'node:assert/strict';
import {test} from 'node:test';

const source = readFileSync(new URL('../../main/resources/static/js/printer-commands.js', import.meta.url), 'utf8');
function client(fetch, secure = true) {
    const feedback = {hidden: true, textContent: ''};
    const context = {fetch, setTimeout, clearTimeout, AbortController, document: {
        getElementById: () => feedback,
        querySelector: selector => secure ? {content: selector.includes('_csrf_header') ? 'X-CSRF-TOKEN' : 'synthetic-token'} : null
    }};
    runInNewContext(source, context);
    return {send: context.sendPrinterCommand, feedback};
}
test('POST carries CSRF and JSON to the actual printer-scoped command route', async () => {
    let request;
    const ui = client(async (url, options) => { request = {url, options}; return {ok: true}; });
    assert.equal(await ui.send('printer-1', 'pause'), true);
    assert.equal(request.url, '/api/printers/printer-1/commands/pause');
    assert.equal(request.options.method, 'POST');
    assert.equal(request.options.headers['X-CSRF-TOKEN'], 'synthetic-token');
    assert.match(ui.feedback.textContent, /Awaiting printer telemetry/);
    await ui.send('printer-1', 'print-speed', {level: 2});
    assert.equal(request.options.body, '{"level":2}');
});
test('Home submits without CSRF metadata', async () => {
    const ui = client(async (_, options) => { assert.equal(Object.keys(options.headers).length, 0); return {ok: true}; }, false);
    assert.equal(await ui.send('printer-1', 'resume'), true);
});
test('authorization and upstream failures produce visible safe feedback', async () => {
    for (const status of [401, 403, 500, 504]) {
        const ui = client(async () => ({ok: false, status}));
        assert.equal(await ui.send('printer-1', 'pause'), false);
        assert.equal(ui.feedback.hidden, false);
        assert.match(ui.feedback.textContent, status === 504 ? /unknown/ : /rejected/);
    }
});
test('network/session redirect failure is uncertain and never retried automatically', async () => {
    let calls = 0;
    const ui = client(async () => { ++calls; throw new Error('private upstream detail'); });
    assert.equal(await ui.send('printer-1', 'stop'), false);
    assert.equal(calls, 1);
    assert.match(ui.feedback.textContent, /outcome is unknown/);
    assert.doesNotMatch(ui.feedback.textContent, /private/);
});
test('pending commands are bounded per printer', async () => {
    let finish;
    const ui = client(() => new Promise(resolve => { finish = resolve; }));
    const first = ui.send('printer-1', 'pause');
    assert.equal(await ui.send('printer-1', 'resume'), false);
    finish({ok: true});
    assert.equal(await first, true);
});
