// Exercise the actual template's pure rendering functions without a browser/network.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const template = fs.readFileSync(path.join(__dirname, '../../edol-hub/src/main/resources/templates/dashboard/printers/list.html'), 'utf8');
const functions = ['hasTelemetry', 'isPrinting', 'isOffline', 'needsAttention',
    'statusLabel', 'statusDescription', 'statusClass', 'renderMetrics', 'renderPrinterCard',
    'renderFilament', 'renderActions', 'renderManagementActions', 'escapeHtml', 'safeColor', 'formatMinutes'];
const context = vm.createContext({});
for (const name of functions) {
    const match = template.match(new RegExp(`    function ${name}\\([^]*?\\n    }`));
    assert.ok(match, `Template function ${name} exists`);
    vm.runInContext(match[0], context);
}
const printer = state => ({enabled: true, availableInCore: true, state});

test('a connected transport with no telemetry is not Ready and has no invented zero temperatures', () => {
    const state = {online: true, gcodeState: null, nozzleTemp: 0, bedTemp: 0};
    assert.equal(context.statusLabel(printer(state)), 'Waiting for telemetry');
    assert.equal(context.statusDescription(printer(state)), 'Connected; waiting for printer telemetry');
    assert.match(context.renderMetrics(state), /Live data unavailable/);
    assert.doesNotMatch(context.renderMetrics(state), /0&deg;/);
    assert.doesNotMatch(context.statusClass(printer(state)), /green/);
});

test('an offline printer never claims Ready to print', () => {
    const state = {online: false, gcodeState: null};
    assert.equal(context.statusLabel(printer(state)), 'Offline');
    assert.equal(context.statusDescription(printer(state)), 'Printer is offline');
    assert.match(context.renderMetrics(state), /Live data unavailable/);
});

test('real idle telemetry permits Ready and preserves a genuinely reported zero', () => {
    const state = {online: true, gcodeState: 'IDLE', nozzleTemp: 24, bedTemp: 23, nozzleTargetTemp: 0, bedTargetTemp: 0};
    assert.equal(context.statusLabel(printer(state)), 'Ready');
    assert.match(context.renderMetrics(state), /24&deg;/);
    assert.match(context.renderMetrics(state), /0&deg;/);
});

test('both logical printers show live printing progress without model metadata', () => {
    for (const id of ['direct-logical-printer', 'agent-logical-printer']) {
        const state = {online: true, printing: true, gcodeState: 'RUNNING', progress: 58,
            currentTask: 'Куб', nozzleTemp: 230, bedTemp: 60};
        const entry = {...printer(state), id, name: id};
        assert.equal(context.statusLabel(entry), 'Printing');
        assert.match(context.renderPrinterCard(entry), /width:58%/);
        assert.match(context.renderPrinterCard(entry), /Куб/);
        entry.state.progress = 65;
        assert.match(context.renderPrinterCard(entry), /width:65%/);
        entry.state.printing = false;
        entry.state.gcodeState = 'FINISH';
        assert.equal(context.statusLabel(entry), 'Ready');
    }
});
