/* Commands use the server-rendered CSRF token and report acceptance separately from telemetry. */
const pendingPrinterCommands = new Set();

async function sendPrinterCommand(printerId, command, body) {
    if (pendingPrinterCommands.has(printerId)) return false;
    pendingPrinterCommands.add(printerId);
    const feedback = document.getElementById('printer-command-feedback');
    const report = message => {
        if (feedback) {
            feedback.textContent = message;
            feedback.hidden = false;
        }
    };
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
        const headers = {};
        const csrf = document.querySelector('meta[name="_csrf"]');
        const header = document.querySelector('meta[name="_csrf_header"]');
        if (csrf?.content && header?.content) headers[header.content] = csrf.content;
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const response = await fetch(`/api/printers/${encodeURIComponent(printerId)}/commands/${encodeURIComponent(command)}`, {
            method: 'POST', headers, credentials: 'same-origin', redirect: 'error',
            body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal
        });
        if (!response.ok) {
            if (response.status === 504) {
                report('Command outcome is unknown. Check printer state before retrying.');
                return false;
            }
            report(response.status === 401 || response.status === 403
                ? 'Command rejected: reload the page and check your session and printer access.'
                : `Command rejected (HTTP ${response.status}). Check printer connectivity before retrying.`);
            return false;
        }
        report('Command accepted. Awaiting printer telemetry.');
        return true;
    } catch {
        report('Command outcome is unknown. Check printer state and your session before retrying.');
        return false;
    } finally {
        clearTimeout(timeout);
        pendingPrinterCommands.delete(printerId);
    }
}
