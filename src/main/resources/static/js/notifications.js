const stack=document.createElement('div');stack.className='toast-stack';document.body.append(stack);
function toast(title,text,error=false,flash=false){
  const card=document.createElement('section');card.className='rvt-toast'+(error?' toast-error':'');
  if(!flash){card.setAttribute('role',error?'alert':'status');}
  const heading=document.createElement('strong');heading.textContent=title;
  const message=document.createElement('p');message.textContent=text;
  const close=document.createElement('button');close.type='button';close.textContent='×';close.setAttribute('aria-label','Đóng thông báo');close.addEventListener('click',()=>card.remove());
  card.append(heading,message,close);stack.append(card);
  while(stack.children.length>3)stack.firstElementChild.remove();
  if(!error){let timer=setTimeout(()=>card.remove(),10000);card.addEventListener('mouseenter',()=>clearTimeout(timer));card.addEventListener('focusin',()=>clearTimeout(timer));}
}
document.querySelectorAll('[data-popup]').forEach(el=>toast(el.dataset.popup==='error'?'Chưa thực hiện được':'Đã cập nhật',el.textContent,el.dataset.popup==='error',true));
const toggle=document.getElementById('notification-toggle'),panel=document.getElementById('notification-panel'),items=document.getElementById('notification-items'),count=document.getElementById('notification-count');
if(toggle){
  const readAll=document.createElement('button');readAll.type='button';readAll.className='notification-read-all';readAll.textContent='Đánh dấu tất cả đã đọc';readAll.disabled=true;panel.querySelector('h3').after(readAll);
  readAll.addEventListener('click',async()=>{
    readAll.disabled=true;
    try{
      const csrf=document.querySelector('meta[name="csrf-token"]')?.content,header=document.querySelector('meta[name="csrf-header"]')?.content;
      if(!csrf||!header)throw new Error('SESSION');
      const response=await fetch('/tai-khoan/thong-bao/doc-tat-ca',{method:'POST',headers:{[header]:csrf}});
      if(!response.ok||response.redirected)throw new Error('SESSION');
      await refresh();
    }catch{readAll.disabled=false;toast('Chưa cập nhật được','Hãy mở lại trang để kiểm tra phiên đăng nhập, rồi thử lại.',true);}
  });
  function hide(){panel.hidden=true;toggle.setAttribute('aria-expanded','false');}
  toggle.addEventListener('click',()=>{panel.hidden=!panel.hidden;toggle.setAttribute('aria-expanded',String(!panel.hidden));});
  document.addEventListener('click',e=>{if(!e.target.closest('.notification-hub'))hide();});
  document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!panel.hidden){hide();toggle.focus();}});
  let initialized=false,lastId=0,inFlight=false;
  async function refresh(){
    if(document.hidden||inFlight)return;inFlight=true;
    try{
      const response=await fetch('/tai-khoan/thong-bao',{headers:{Accept:'application/json'},cache:'no-store'});
      if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))return;
      const data=await response.json();count.textContent=String(data.unread);count.hidden=!data.unread;
      readAll.disabled=!data.unread;
      const latest=data.items[0]?.ThongBaoID||0;
      if(initialized)data.items.filter(n=>n.ThongBaoID>lastId&&!n.DaDoc).reverse().forEach(n=>toast(n.TieuDe,n.NoiDung));
      lastId=Math.max(lastId,latest);initialized=true;
      // Do not replace focused links while the user is navigating the inbox.
      if(!panel.contains(document.activeElement)){
        items.replaceChildren();
        if(!data.items.length){const empty=document.createElement('p');empty.className='muted';empty.textContent='Chưa có thông báo. Các thay đổi đơn vé sẽ xuất hiện tại đây.';items.append(empty);}
        data.items.forEach(n=>{
          const link=document.createElement('a');link.className='notification-item'+(n.DaDoc?'':' unread');link.href=(/^\/tai-khoan\/ho-tro\/\d+$/.test(n.LienKet||'')?n.LienKet:'/tai-khoan/don/'+encodeURIComponent(n.MaDon))+'.html';
          const title=document.createElement('strong');title.textContent=n.TieuDe;const text=document.createElement('p');text.textContent=n.NoiDung;link.append(title,text);
          link.addEventListener('click',()=>{
            const csrf=document.querySelector('meta[name="csrf-token"]')?.content,header=document.querySelector('meta[name="csrf-header"]')?.content;
            if(csrf&&header)fetch('/tai-khoan/thong-bao/'+n.ThongBaoID+'/doc',{method:'POST',headers:{[header]:csrf},keepalive:true}).catch(()=>{});
          });items.append(link);
        });
      }
    }catch{if(!initialized)items.textContent='Chưa tải được thông báo. Bạn vẫn có thể xem đơn trong tài khoản.';}
    finally{inFlight=false;}
  }
  refresh();setInterval(refresh,20000);document.addEventListener('visibilitychange',refresh);
}
