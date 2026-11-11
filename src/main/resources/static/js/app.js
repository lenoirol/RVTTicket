import {money, calculateTotal, toggleSelection, ageGroup, passengerFare, conflictedSeats} from './booking-state.mjs';
import {initPlaces, initValidation, applyServerErrors, initFleet, initTripVehicle, initScanner} from './experience.js';
import {documentRule} from './passenger-identity.mjs';

initPlaces();
initValidation();

function identityRule(type,field,label,hint){
  const rule=documentRule(type);field.pattern=rule.pattern;field.maxLength=rule.maxLength;field.inputMode=rule.inputMode;field.placeholder=rule.placeholder;
  if(label)label.textContent=rule.label;if(hint)hint.textContent=rule.hint;
}
document.querySelectorAll('[data-identity-type]').forEach(select=>{
  const form=select.closest('form'),field=form.querySelector('[data-identity-document]');
  const update=()=>identityRule(select.value,field,form.querySelector('[data-document-label]'),form.querySelector('[data-document-hint]'));
  select.addEventListener('change',update);update();
});

const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
function pulse(element) {
  if (reducedMotion.matches) return;
  element.classList.remove('value-updated');
  void element.offsetWidth;
  element.classList.add('value-updated');
}
function busy(form, label = 'Đang xử lý…') {
  form.setAttribute('aria-busy', 'true');
  form.querySelectorAll('button[type="submit"], button:not([type])').forEach(button => {
    button.disabled = true; button.dataset.originalText = button.textContent; button.textContent = label;
  });
}
function confirmation({title, text, lines = [], confirm = 'Xác nhận', danger = false}, onConfirm) {
  const dialog = document.createElement('dialog'); dialog.className = 'confirm-dialog';
  const titleEl = document.createElement('h2'); titleEl.id = 'confirmation-title'; titleEl.textContent = title;
  const description = document.createElement('p'); description.id = 'confirmation-description'; description.textContent = text;
  dialog.setAttribute('aria-labelledby', titleEl.id); dialog.setAttribute('aria-describedby', description.id);
  dialog.append(titleEl, description);
  if (lines.length) {
    const list = document.createElement('ul'); list.className = 'review-list';
    lines.forEach(line => { const item = document.createElement('li'); item.textContent = line; list.append(item); });
    dialog.append(list);
  }
  const actions = document.createElement('div'); actions.className = 'dialog-actions';
  const back = document.createElement('button'); back.type = 'button'; back.className = 'button outline'; back.textContent = 'Quay lại'; back.autofocus = true;
  const ok = document.createElement('button'); ok.type = 'button'; ok.className = 'button' + (danger ? ' danger' : ' orange'); ok.textContent = confirm;
  actions.append(back, ok); dialog.append(actions); document.body.append(dialog);
  back.addEventListener('click', () => dialog.close());
  ok.addEventListener('click', () => { dialog.close(); onConfirm(); });
  dialog.addEventListener('close', () => dialog.remove(), {once: true});
  dialog.showModal();
}
document.querySelectorAll('.cancel-form, [data-confirm]').forEach(form => {
  form.addEventListener('submit', event => {
    if (form.dataset.confirmed === 'true') { busy(form); return; }
    event.preventDefault();
    confirmation({
      title: form.classList.contains('cancel-form') ? 'Hủy toàn bộ đơn vé?' : 'Xác nhận thao tác',
      text: form.dataset.confirm || 'Tất cả chỗ trong đơn sẽ được giải phóng. Giao dịch thử nghiệm đã thanh toán sẽ được hoàn theo chính sách của đơn.',
      confirm: form.classList.contains('cancel-form') ? 'Hủy đơn vé' : 'Xác nhận',
      danger: form.classList.contains('cancel-form')
    }, () => { form.dataset.confirmed = 'true'; form.requestSubmit(); });
  });
});
document.querySelectorAll('[data-busy]').forEach(form => form.addEventListener('submit', () => busy(form)));

const booking = document.getElementById('booking-form');
if (booking) {
  const selected = new Map(), values = new Map();
  let open=true,conflicts=[];
  const container = document.getElementById('passengers'), feedback = document.getElementById('seat-feedback');
  const boat = booking.dataset.type === 'TAU_THUYEN';
  const base = Number(booking.dataset.price), allowance = Number(booking.dataset.allowance), rate = Number(booking.dataset.rate);
  function passengers() {
    return [...container.querySelectorAll('.passenger-fields')].map(row => ({
      seat: row.dataset.seat, name: row.querySelector('[data-name]').value,
      luggage: row.querySelector('[data-luggage]').value, document: row.querySelector('[data-document]')?.value || '',
      birthDate: row.querySelector('[data-birth]')?.value || '', documentType: row.querySelector('[data-document-type]')?.value || 'CCCD', nationality: row.querySelector('[data-nationality]')?.value || 'Việt Nam',
      extra: selected.get(row.dataset.seat), fare: boat ? passengerFare(ageGroup(row.querySelector('[data-birth]')?.value,booking.dataset.travelDate),base,Number(booking.dataset.childPrice),Number(booking.dataset.seniorPrice),row.dataset.vip==='true') : base
    }));
  }
  function total() {
    container.querySelectorAll('.passenger-fields').forEach(row => {
      const label = row.querySelector('[data-age-label]');
      if (label) { const group=ageGroup(row.querySelector('[data-birth]').value,booking.dataset.travelDate); label.textContent = (group==='TRE_EM'?'Trẻ em · Ghế riêng':group==='CAO_TUOI'?'Người cao tuổi':group==='NGUOI_LON'?'Người lớn':'Nhập ngày sinh để tính giá') + (row.dataset.vip==='true'?' · VIP không giảm theo tuổi':''); }
    });
    const amounts = calculateTotal(base, passengers(), allowance, rate);
    document.getElementById('selected-seats').textContent = [...selected.keys()].join(', ') || '—';
    document.getElementById('extra-total').textContent = money(amounts.extras);
    document.getElementById('fare-total').textContent = money(amounts.grand-amounts.extras-amounts.luggage);
    document.getElementById('luggage-total').textContent = money(amounts.luggage);
    const grand = document.getElementById('grand-total');
    if (grand.textContent !== money(amounts.grand)) { grand.textContent = money(amounts.grand); pulse(grand); }
    const mobile=document.getElementById('mobile-grand-total');if(mobile)mobile.textContent=money(amounts.grand);
    // Nút giữ chỗ chỉ bật khi đã chọn chỗ và đã tích đồng ý chính sách.
    const agree = document.getElementById('book-agree');
    document.getElementById('book-submit').disabled = selected.size === 0 || !open || conflicts.length>0 || (agree && !agree.checked);
    return amounts;
  }
  document.getElementById('book-agree')?.addEventListener('change', total);
  function input(labelText, fieldName, attributes, value) {
    const label = document.createElement('label'); label.textContent = labelText;
    const field = document.createElement('input'); field.dataset.field = fieldName;
    Object.entries(attributes).forEach(([key, val]) => field.setAttribute(key, val));
    field.value = value ?? ''; label.append(field); return label;
  }
  function render() {
    // Retain existing nodes and input focus instead of rebuilding all passengers.
    container.querySelectorAll('.passenger-fields').forEach(row => {
      if (!selected.has(row.dataset.seat)) row.remove();
    });
    container.querySelector('.empty-passengers')?.remove();
    [...selected.keys()].forEach((seat, index) => {
      let row = [...container.querySelectorAll('.passenger-fields')].find(el => el.dataset.seat === seat);
      if (!row) {
        const old = values.get(seat) || {name: '', luggage: 0, document: ''};
        row = document.createElement('div'); row.className = 'passenger-fields passenger-enter'; row.dataset.seat = seat;
        row.dataset.vip = String([...document.querySelectorAll('.seat[data-seat]')].find(b=>b.dataset.seat===seat)?.dataset.kind === 'VIP');
        row.append(document.createElement('h3'));
        if(window.passengerProfile){
          const selfLabel=document.createElement('label');selfLabel.className='passenger-autofill';
          const self=document.createElement('input');self.type='checkbox';self.dataset.passengerSelf='';selfLabel.append(self,document.createTextNode('Tôi là hành khách · Điền từ hồ sơ'));row.append(selfLabel);
          self.addEventListener('change',()=>{
            if(!self.checked)return;
            for(const [key,value] of Object.entries(window.passengerProfile)){
              const field=row.querySelector(`[data-field="${key}"]`);if(field){field.value=value??'';field.dispatchEvent(new Event('input',{bubbles:true}));}
            }
            const kind=row.querySelector('[data-document-type]');if(kind)kind.dispatchEvent(new Event('change',{bubbles:true}));
            self.checked=true;
            feedback.textContent='Đã điền thông tin đã lưu. Kiểm tra lại ngày sinh và giấy tờ trước khi giữ chỗ.';total();
          });
          row.addEventListener('input',event=>{if(event.target.dataset.field&&['name','birthDate','document','documentType','nationality'].includes(event.target.dataset.field))self.checked=false;});
        }
        const hidden = document.createElement('input'); hidden.type = 'hidden'; hidden.dataset.field = 'seat'; hidden.value = seat; row.append(hidden);
        const fields = document.createElement('div'); fields.className = 'fields';
        fields.append(input('Họ tên hành khách', 'name', {required: '', maxlength: '100', 'data-name': '', placeholder: 'Nhập đúng họ tên'}, old.name));
        fields.append(input('Hành lý (kg)', 'luggage', {type: 'number', required: '', min: '0', max: booking.dataset.maximum, step: '0.5', 'data-luggage': ''}, old.luggage));
        if (boat) {
          fields.append(input('Ngày sinh (tính tuổi theo ngày đi)', 'birthDate', {type:'date',required:'',max:booking.dataset.today,'data-birth':''}, old.birthDate));
          const label=document.createElement('label'); label.textContent='Loại giấy tờ';
          const select=document.createElement('select'); select.dataset.field='documentType'; select.dataset.documentType='';
          [['CCCD','Căn cước / CCCD'],['HO_CHIEU','Hộ chiếu'],['KHAI_SINH','Giấy khai sinh (trẻ dưới 12 tuổi)']].forEach(([value,text])=>{const option=document.createElement('option');option.value=value;option.textContent=text;select.append(option);});
          select.value=old.documentType||'CCCD'; label.append(select);fields.append(label);
          const documentLabel=input('Số căn cước / CCCD', 'document', {required: '', pattern: '[0-9]{12}', maxlength: '12',inputmode:'numeric', 'data-document': '',autocomplete:'off', placeholder: 'Nhập đúng 12 chữ số'}, old.document);
          fields.append(documentLabel);const documentInput=documentLabel.querySelector('input');
          const updateDocument=()=>{identityRule(select.value,documentInput);documentLabel.firstChild.textContent=documentRule(select.value).label;};
          select.addEventListener('change',updateDocument);updateDocument();
          fields.append(input('Quốc tịch', 'nationality', {required:'',maxlength:'50','data-nationality':''}, old.nationality||'Việt Nam'));
          const ageLabel=document.createElement('p');ageLabel.className='pill soft';ageLabel.dataset.ageLabel='';row.append(ageLabel);
        }
        row.append(fields); container.append(row);
        const remove=document.createElement('button');remove.type='button';remove.className='passenger-remove';remove.textContent='Bỏ chỗ này';
        remove.addEventListener('click',()=>document.querySelector(`.seat[data-seat="${CSS.escape(seat)}"]`)?.click());row.append(remove);
      }
      row.querySelector('h3').textContent = `Hành khách ${index + 1} · Chỗ ${seat}`;
      row.querySelectorAll('[data-field]').forEach(field => { field.name = `passengers[${index}].${field.dataset.field}`; });
    });
    if (!selected.size) {
      const p = document.createElement('p'); p.className = 'muted empty-passengers'; p.textContent = 'Hãy chọn ít nhất một chỗ ở sơ đồ phía trên.'; container.append(p);
    }
    total();
  }
  container.replaceChildren();
  document.querySelectorAll('.seat[data-seat]').forEach(button => {
    button.setAttribute('aria-pressed', 'false');
    button.addEventListener('click', () => {
      if(button.dataset.unavailable==='true'&&!selected.has(button.dataset.seat))return;
      passengers().forEach(p => values.set(p.seat, p));
      const result = toggleSelection(selected, button.dataset.seat, Number(button.dataset.extra));
      if (result === 'limit') { feedback.textContent = 'Mỗi đơn tối đa 8 hành khách. Bỏ một chỗ để chọn chỗ khác.'; return; }
      button.classList.toggle('selected', result === 'added'); button.setAttribute('aria-pressed', String(result === 'added'));
      if(result==='removed'){button.classList.remove('conflicted');button.disabled=button.dataset.unavailable==='true';conflicts=conflicts.filter(s=>s!==button.dataset.seat);}
      feedback.textContent = `${result === 'added' ? 'Đã chọn' : 'Đã bỏ'} chỗ ${button.dataset.seat}. Hiện có ${selected.size} chỗ trong đơn.`;
      render();
    });
  });
  container.addEventListener('input', total);
  container.addEventListener('change', total);
  (window.savedPassengers || []).forEach(passenger => {
    const button = [...document.querySelectorAll('.seat[data-seat]')].find(b => b.dataset.seat === passenger.seat);
    if (button && !selected.has(passenger.seat)) {
      values.set(passenger.seat, passenger); selected.set(passenger.seat, Number(button.dataset.extra));
      button.classList.add('selected'); button.setAttribute('aria-pressed', 'true');
      if(button.disabled){button.disabled=false;button.dataset.unavailable='true';button.classList.add('conflicted');conflicts.push(passenger.seat);}
    }
  });
  render();
  applyServerErrors(container);
  const seatStatus=document.getElementById('seat-refresh-status');
  let refreshing=false;
  async function refreshSeats(){
    if(document.hidden||refreshing||booking.dataset.submitting==='true')return;
    refreshing=true;
    try{
      const response=await fetch(booking.dataset.availabilityUrl,{headers:{Accept:'application/json'},cache:'no-store'});
      if(!response.ok||response.redirected)throw new Error('UNAVAILABLE');
      const data=await response.json();if(!Array.isArray(data.seats))throw new Error('INVALID');
      open=data.open===true;conflicts=conflictedSeats(selected,data.seats);
      const states=new Map(data.seats.map(s=>[s.seat,s.unavailable]));
      document.querySelectorAll('.seat[data-seat]').forEach(button=>{
        const unavailable=states.get(button.dataset.seat)!==false;button.dataset.unavailable=String(unavailable);
        button.disabled=unavailable&&!selected.has(button.dataset.seat);button.classList.toggle('conflicted',conflicts.includes(button.dataset.seat));
      });
      seatStatus.classList.toggle('error',!open||conflicts.length>0);
      seatStatus.textContent=!open?'Chuyến đã ngừng nhận đặt. Thông tin bạn nhập vẫn giữ ở đây; hãy tìm chuyến khác.':conflicts.length?'Chỗ '+conflicts.join(', ')+' vừa có người đặt hoặc ngừng dùng. Bỏ chỗ này rồi chọn chỗ khác; các thông tin khác vẫn được giữ.':'Đã cập nhật tình trạng chỗ · '+new Intl.DateTimeFormat('vi-VN',{hour:'2-digit',minute:'2-digit',timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
      total();
    }catch{seatStatus.textContent='Chưa cập nhật được chỗ. Kiểm tra kết nối; hệ thống sẽ kiểm tra lại khi bạn giữ chỗ.';}
    finally{refreshing=false;}
  }
  refreshSeats();let seatTimer=setInterval(refreshSeats,20000);
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)refreshSeats();});
  window.addEventListener('pagehide',()=>{clearInterval(seatTimer);seatTimer=null;});
  window.addEventListener('pageshow',event=>{
    if(event.persisted){
      delete booking.dataset.confirmed;
      if(!seatTimer)seatTimer=setInterval(refreshSeats,20000);
      refreshSeats();
    }
  });
  booking.addEventListener('submit', event => {
    if(!open||conflicts.length){event.preventDefault();seatStatus.focus();return;}
    if (booking.dataset.confirmed === 'true') { busy(booking, 'Đang giữ chỗ…'); return; }
    event.preventDefault();
    confirmation({
      title: 'Kiểm tra trước khi giữ chỗ',
      text: 'Đối chiếu hành trình, người đặt, họ tên từng khách và hành lý. Sau khi giữ chỗ, bạn có 15 phút để thanh toán.',
      lines: [document.querySelector('.page-title').textContent,document.querySelector('.booking-summary>p').textContent,'Người đặt: '+booking.elements.name.value+' · '+booking.elements.phone.value].concat(passengers().map(p => `${p.seat} · ${p.name} · ${p.luggage || 0} kg${boat ? ' · ' + p.birthDate+' · '+p.document : ''}`),'Tổng dự kiến: ' + money(total().grand)),
      confirm: 'Đúng thông tin — giữ chỗ'
    }, () => { booking.dataset.confirmed = 'true'; booking.requestSubmit(); });
  });
}
const countdown = document.getElementById('countdown');
if (countdown) {
  const deadline = new Date(countdown.dataset.deadline + '+07:00').getTime();
  let timer;
  function tick() {
    const seconds = Math.max(0, Math.floor((deadline - Date.now()) / 1000));
    countdown.textContent = seconds ? `Còn ${Math.floor(seconds / 60)} phút ${String(seconds % 60).padStart(2, '0')} giây để thanh toán.` : 'Đã hết thời gian giữ chỗ. Tải lại để cập nhật trạng thái đơn.';
    if (!seconds) {
      document.querySelectorAll('form[action$="/thanh-toan"] button').forEach(b=>b.setAttribute('disabled', ''));
      if (timer) clearInterval(timer);
    }
  }
  tick(); timer = setInterval(tick, 1000);
}
document.querySelectorAll('[data-copy]').forEach(button => button.addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(button.dataset.copy); button.textContent = 'Đã sao chép'; }
  catch { button.textContent = 'Hãy chọn và sao chép mã đơn'; }
}));
if ('IntersectionObserver' in window && !reducedMotion.matches) {
  const observer = new IntersectionObserver(entries => entries.forEach(entry => {
    if (entry.isIntersecting) { entry.target.classList.add('is-visible'); observer.unobserve(entry.target); }
  }), {threshold: 0.1});
  document.querySelectorAll('[data-reveal]').forEach(element => {
    if (element.getBoundingClientRect().top > innerHeight) { element.classList.add('reveal-ready'); observer.observe(element); }
  });
}
window.addEventListener('pageshow', event => {
  if (!event.persisted) return;
  document.querySelectorAll('[aria-busy="true"]').forEach(form => {
    form.removeAttribute('aria-busy'); delete form.dataset.confirmed;
    form.querySelectorAll('[data-original-text]').forEach(button => { button.disabled = false; button.textContent = button.dataset.originalText; });
  });
});
initFleet(confirmation);
initTripVehicle();
initScanner();
