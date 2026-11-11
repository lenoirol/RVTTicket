const button=document.getElementById('operator-menu');
const overlay=document.getElementById('operator-overlay');
const navigation=document.getElementById('operator-navigation');
const mobile=matchMedia('(max-width:760px)');
function access(open){if(navigation){navigation.inert=mobile.matches&&!open;navigation.setAttribute('aria-hidden',String(mobile.matches&&!open));}}
function menu(open){document.body.classList.toggle('admin-nav-open',open);button?.setAttribute('aria-expanded',String(open));if(overlay)overlay.hidden=!open;access(open);if(open)navigation?.querySelector('a')?.focus();else button?.focus();}
button?.addEventListener('click',()=>menu(button.getAttribute('aria-expanded')!=='true'));
overlay?.addEventListener('click',()=>menu(false));
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&document.body.classList.contains('admin-nav-open'))menu(false);});
mobile.addEventListener('change',()=>{document.body.classList.remove('admin-nav-open');button?.setAttribute('aria-expanded','false');if(overlay)overlay.hidden=true;access(false);});
access(false);
document.querySelectorAll('.operator-sidebar nav a').forEach(link=>{if(link.classList.contains('active'))link.setAttribute('aria-current','page');});
