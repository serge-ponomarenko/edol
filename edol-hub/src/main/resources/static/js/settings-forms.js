// Protect edits when navigating between settings pages or leaving the browser tab.
document.querySelectorAll('form[data-settings-form]').forEach(form => {
    let dirty = false;
    form.addEventListener('input', () => { dirty = true; });
    form.addEventListener('change', () => { dirty = true; });
    form.addEventListener('submit', () => { dirty = false; });
    window.addEventListener('beforeunload', event => {
        if (dirty) {
            event.preventDefault();
        }
    });
});
