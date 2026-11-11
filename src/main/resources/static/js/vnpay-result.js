(() => {
  const card = document.querySelector('[data-vnpay-state]');
  if (card?.dataset.vnpayState !== 'PENDING') return;
  let attempts = 0;
  const refresh = async () => {
    if (++attempts > 20) return;
    if (!document.hidden) {
      try {
        const response = await fetch(window.location.href, {
          credentials: 'same-origin', cache: 'no-store',
          signal: AbortSignal.timeout(8000)
        });
        if (response.ok && !response.redirected) {
          const page = new DOMParser().parseFromString(await response.text(), 'text/html');
          const state = page.querySelector('[data-vnpay-state]')?.dataset.vnpayState;
          if (state && state !== 'PENDING') { window.location.reload(); return; }
        }
      } catch { /* Manual refresh remains available when offline. */ }
    }
    window.setTimeout(refresh, 15000);
  };
  window.setTimeout(refresh, 15000);
})();
