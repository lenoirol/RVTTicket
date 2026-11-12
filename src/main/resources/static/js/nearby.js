// Nút "Vị trí của bạn": lấy vị trí bằng trình duyệt, tính khoảng cách tới các bến ngay trên máy, không gửi vị trí lên máy chủ.
(()=>{
  const km=(a,b,c,d)=>{const r=6371,t=x=>x*Math.PI/180,dl=t(c-a),dg=t(d-b),h=Math.sin(dl/2)**2+Math.cos(t(a))*Math.cos(t(c))*Math.sin(dg/2)**2;return 2*r*Math.asin(Math.min(1,Math.sqrt(h)));};
  const fmt=v=>v<10?v.toFixed(1).replace('.',','):Math.round(v).toLocaleString('vi-VN');
  const reasons={1:'Bạn chưa cho phép truy cập vị trí. Hãy bật quyền vị trí cho trang này trong cài đặt trình duyệt, hoặc chọn điểm đi bằng tay.',2:'Không xác định được vị trí (thiết bị chưa bật định vị hoặc mất mạng).',3:'Lấy vị trí quá lâu. Hãy thử lại.'};
  function locate(onPosition,onError){
    if(!('geolocation' in navigator)){onError('Trình duyệt này không hỗ trợ định vị.');return;}
    if(!window.isSecureContext){onError('Trình duyệt chỉ cho phép định vị trên trang HTTPS.');return;}
    navigator.geolocation.getCurrentPosition(p=>onPosition(p.coords.latitude,p.coords.longitude),e=>onError(reasons[e.code]||'Không lấy được vị trí.'),{enableHighAccuracy:false,timeout:12000,maximumAge:300000});
  }
  let stations=null;
  const load=async()=>stations||(stations=await fetch('/api/ben-xe',{headers:{Accept:'application/json'}}).then(r=>{if(!r.ok)throw new Error();return r.json();}));

  // 1. Ô Điểm đi trong form tìm chuyến (trang chủ, trang tìm chuyến).
  document.querySelectorAll('.search-form').forEach(form=>{
    const select=form.querySelector('select[name=from]');if(!select)return;
    const row=form.querySelector('.round-trip-row');if(!row)return;
    const button=document.createElement('button');button.type='button';button.className='locate-button';button.innerHTML='<span aria-hidden="true">📍</span> Vị trí của bạn';
    const panel=document.createElement('div');panel.className='locate-panel';panel.hidden=true;panel.setAttribute('role','dialog');panel.setAttribute('aria-label','Bến xe gần bạn');
    const wrap=document.createElement('span');wrap.className='locate-wrap';wrap.append(button,panel);row.prepend(wrap);
    const close=()=>{panel.hidden=true;button.setAttribute('aria-expanded','false');};
    const show=html=>{panel.innerHTML=html;panel.hidden=false;button.setAttribute('aria-expanded','true');};
    document.addEventListener('pointerdown',e=>{if(!panel.hidden&&!panel.contains(e.target)&&e.target!==button&&!button.contains(e.target))close();});
    document.addEventListener('keydown',e=>{if(e.key==='Escape')close();});
    button.addEventListener('click',()=>{
      if(!panel.hidden){close();return;}
      show('<p class="locate-status">Đang lấy vị trí của bạn…</p>');
      locate(async(lat,lng)=>{
        try{
          const list=(await load()).map(s=>({...s,d:km(lat,lng,s.lat,s.lng)})).sort((a,b)=>a.d-b.d).slice(0,5);
          panel.innerHTML='<p class="locate-title">Bến xe gần bạn nhất</p>';
          list.forEach(s=>{
            const item=document.createElement('div');item.className='locate-item';
            const info=document.createElement('div');
            const name=document.createElement('strong');name.textContent=s.ten;
            const dist=document.createElement('span');dist.className='locate-distance';dist.textContent=fmt(s.d)+' km';
            const addr=document.createElement('small');addr.textContent=s.diaChi;
            info.append(name,' ',dist,document.createElement('br'),addr);
            const pick=document.createElement('button');pick.type='button';pick.className='button small';pick.textContent='Đi từ đây';
            pick.addEventListener('click',()=>{
              const option=[...select.options].find(o=>o.value===s.thanhPho);
              if(!option){addr.textContent='Chưa có '+s.thanhPho+' trong danh mục điểm đi.';return;}
              select.value=option.value;select.dispatchEvent(new Event('change',{bubbles:true}));close();
              const hint=form.querySelector('.locate-chosen')||Object.assign(document.createElement('small'),{className:'locate-chosen'});
              hint.textContent='Đi từ '+s.ten+' ('+fmt(s.d)+' km). Có mặt tại bến trước giờ khởi hành 30 phút.';wrap.after(hint);
            });
            const map=document.createElement('a');map.href=s.banDo;map.target='_blank';map.rel='noopener noreferrer';map.className='text-link';map.textContent='Bản đồ ↗';
            const actions=document.createElement('div');actions.className='locate-actions';actions.append(pick,map);
            item.append(info,actions);panel.append(item);
          });
          const foot=document.createElement('p');foot.className='locate-foot';foot.innerHTML='Khoảng cách đường chim bay. <a class="text-link" href="/ben-xe.html">Xem tất cả bến xe</a>';panel.append(foot);
        }catch{show('<p class="locate-status">Chưa tải được danh sách bến. Hãy thử lại.</p>');}
      },message=>{const p=document.createElement('p');p.className='locate-status';p.textContent=message;panel.replaceChildren(p);panel.hidden=false;});
    });
  });

  // 2. Trang /ben-xe: lọc theo chữ và sắp xếp theo khoảng cách.
  const list=document.querySelector('[data-station-list]');
  if(list){
    const cards=[...list.querySelectorAll('.station-card')],filter=document.querySelector('[data-station-filter]'),status=document.querySelector('[data-locate-status]');
    const norm=s=>s.normalize('NFD').replace(/\p{M}/gu,'').replace(/đ/g,'d').replace(/Đ/g,'D').toLowerCase();
    filter?.addEventListener('input',()=>{const q=norm(filter.value.trim());cards.forEach(c=>{c.hidden=q&&!norm(c.dataset.search).includes(q);});
      list.querySelectorAll('.station-city').forEach(h=>{let n=h.nextElementSibling,any=false;while(n&&n.classList.contains('station-card')){if(!n.hidden)any=true;n=n.nextElementSibling;}h.hidden=!any;});});
    document.querySelector('[data-locate-stations]')?.addEventListener('click',()=>{
      status.textContent='Đang lấy vị trí của bạn…';
      locate((lat,lng)=>{
        cards.forEach(c=>{const d=km(lat,lng,+c.dataset.lat,+c.dataset.lng);c.dataset.d=d;const el=c.querySelector('[data-distance]');el.textContent='Cách bạn khoảng '+fmt(d)+' km';el.hidden=false;});
        list.querySelectorAll('.station-city').forEach(h=>h.remove());
        cards.sort((a,b)=>a.dataset.d-b.dataset.d).forEach(c=>list.append(c));
        status.textContent='Đã sắp xếp theo khoảng cách từ vị trí của bạn (đường chim bay).';
      },message=>{status.textContent=message;});
    });
  }
})();
