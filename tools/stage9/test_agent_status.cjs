const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const template = fs.readFileSync(path.join(__dirname, '../../edol-hub/src/main/resources/templates/dashboard/printers/agent.html'), 'utf8');
const script = template.match(/<script>([^]*?)<\/script>/)[1];

function setup(response) {
    const nodes = {
        'refresh-agent-status': {dataset: {statusUrl: '/printers/synthetic/agent/status'}, addEventListener() {}},
        'agent-status': {}, 'agent-status-message': {},
        'agent-pairing-code': {hidden: false, replaceChildren() { this.cleared = true; }}
    };
    const calls = [];
    const context = vm.createContext({
        document: {getElementById: id => nodes[id], hidden: false},
        setInterval() {}, setTimeout() {},
        fetch: async (url, options) => {
            calls.push({url, options});
            return {ok: true, headers: {get: () => 'application/json'}, json: async () => response};
        }
    });
    vm.runInContext(script, context);
    return {context, nodes, calls};
}

test('read-only polling reports ACTIVE and hides the one-time code without redisplaying it', async () => {
    const {context, nodes, calls} = setup({state: 'ACTIVE', mqttState: 'READY'});
    await context.refreshAgentStatus();
    assert.equal(nodes['agent-status'].textContent, 'ACTIVE');
    assert.match(nodes['agent-status-message'].textContent, /enrolled successfully/);
    assert.ok(nodes['agent-pairing-code'].cleared && nodes['agent-pairing-code'].hidden);
    assert.equal(calls[0].options.cache, 'no-store');
    assert.equal(calls[0].options.method, undefined);
    assert.match(calls[0].url, /\/status$/);
});

test('network/login failure is visible and permits a manual retry', async () => {
    const {context, nodes} = setup({state: 'ACTIVE', mqttState: 'READY'});
    context.fetch = async () => { throw new Error('Synthetic network failure'); };
    await context.refreshAgentStatus();
    assert.match(nodes['agent-status-message'].textContent, /Unable to refresh/);
    assert.equal(nodes['refresh-agent-status'].disabled, false);
    assert.equal(nodes['agent-pairing-code'].cleared, undefined);
});

test('an expired pairing code is removed from the page', () => {
    const {context, nodes} = setup({});
    context.applyAgentStatus({state: 'PAIRING', expiresAt: '2000-01-01T00:00:00Z'});
    assert.equal(nodes['agent-pairing-code'].hidden, true);
});
