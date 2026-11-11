import {normalizePlace, validityMessage, presetLayout, validateLayout, isValidationField} from './ui-state.mjs';

export function fieldFeedback(field,message){
  if(field?.hidden&&field.dataset.placeSelect!==undefined)field=field.parentElement.querySelector('[data-place-input]')||field;
  if(!field||!['INPUT','SELECT','TEXTAREA'].includes(field.tagName)||['hidden','submit','button','reset','image'].includes(field.type))return;
  // Feedback belongs to this control, never to another field nested inside its parent.
  let hint=field._rvtFeedback;
  if(!hint){
    hint=document.createElement('small');hint.className='field-error';
    const base='field-error-'+(field.id||field.name||'input').replace(/[^a-zA-Z0-9_-]/g,'-');
    let id=base,suffix=1;while(document.getElementById(id))id=base+'-'+suffix++;
    hint.id=id;field.after(hint);field._rvtFeedback=hint;
  }
  hint.textContent=message;hint.hidden=!message;field.setAttribute('aria-invalid',String(Boolean(message)));
  const descriptions=new Set((field.getAttribute('aria-describedby')||'').split(' ').filter(Boolean));descriptions.add(hint.id);
  field.setAttribute('aria-describedby',[...descriptions].join(' '));
}
export function applyServerErrors(root=document){
  root.querySelectorAll('input[name],select[name],textarea[name]').forEach(field=>{
    if(window.fieldErrors?.[field.name])fieldFeedback(field,window.fieldErrors[field.name]);
  });
}
export function initValidation(){
  document.querySelectorAll('form').forEach(form=>{
    form.noValidate=true;
    form.addEventListener('submit',event=>{
      if(!form.checkValidity()){
        event.preventDefault();event.stopImmediatePropagation();
        const fields=[...form.elements].filter(isValidationField);
        fields.forEach(field=>fieldFeedback(field,validityMessage(field)));
        fields.find(f=>!f.validity.valid)?.focus();
      }
    },true);
    form.addEventListener('focusout',event=>{if(isValidationField(event.target))fieldFeedback(event.target,validityMessage(event.target));});
    form.addEventListener('input',event=>{if(isValidationField(event.target)&&event.target.getAttribute('aria-invalid')==='true')fieldFeedback(event.target,validityMessage(event.target));});
  });
  applyServerErrors();
  document.querySelectorAll('[data-unsaved]').forEach(form=>{
    form.addEventListener('input',()=>{form.dataset.dirty='true';});
    form.addEventListener('change',()=>{form.dataset.dirty='true';});
    form.addEventListener('submit',event=>queueMicrotask(()=>{if(!event.defaultPrevented)form.dataset.submitting='true';}));
  });
  window.addEventListener('beforeunload',event=>{
    if(document.querySelector('[data-unsaved][data-dirty="true"]:not([data-submitting="true"])')){event.preventDefault();event.returnValue='';}
  });
  window.addEventListener('pageshow',()=>document.querySelectorAll('[data-unsaved]').forEach(form=>delete form.dataset.submitting));
}
export function initPlaces(){
  document.querySelectorAll('[data-place-select]').forEach(select=>{
    const options=[...new Map([...select.options].filter(o=>o.value).map(o=>[o.value,{value:o.value,label:o.textContent,aliases:o.dataset.aliases||''}])).values()];
    const wrapper=document.createElement('span');wrapper.className='place-picker';select.before(wrapper);wrapper.append(select);
    const input=document.createElement('input');input.type='text';input.autocomplete='off';input.placeholder='Tìm tỉnh/thành, địa điểm…';input.required=select.required;select.required=false;
    input.id=select.id+'-search';input.dataset.placeInput='';input.setAttribute('role','combobox');input.setAttribute('aria-autocomplete','list');input.setAttribute('aria-expanded','false');
    const label=select.closest('label');
    const labelText=label?[...label.childNodes].filter(node=>node.nodeType===Node.TEXT_NODE).map(node=>node.textContent).join('').trim():'';
    if(label){label.htmlFor=input.id;}if(labelText)input.setAttribute('aria-label',labelText);
    input.disabled=select.disabled;
    const list=document.createElement('span');list.className='place-options';list.id=select.id+'-options';list.setAttribute('role','listbox');list.hidden=true;
    input.setAttribute('aria-controls',list.id);wrapper.append(input,list);select.hidden=true;
    const valueLabel=()=>options.find(o=>o.value===select.value)?.label||'';
    input.value=valueLabel();let index=-1,shown=[];
    select.addEventListener('change',()=>{input.value=valueLabel();input.setCustomValidity('');fieldFeedback(input,'');close();});
    function close(){list.hidden=true;input.setAttribute('aria-expanded','false');input.removeAttribute('aria-activedescendant');index=-1;}
    function choose(option){
      select.value=option.value;input.value=option.label;input.setCustomValidity('');fieldFeedback(input,'');close();select.dispatchEvent(new Event('change',{bubbles:true}));
    }
    function highlight(){
      const buttons=[...list.querySelectorAll('[role=option]')];buttons.forEach((button,i)=>button.setAttribute('aria-selected',String(i===index)));
      if(buttons[index]){input.setAttribute('aria-activedescendant',buttons[index].id);buttons[index].scrollIntoView({block:'nearest'});}
    }
    function open(query=''){
      list.replaceChildren();const needle=normalizePlace(query);
      const matches=options.filter(o=>normalizePlace(o.label+' '+o.aliases).includes(needle));
      shown=(needle?matches:[...matches.filter(o=>o.value.startsWith('province:')),...matches.filter(o=>!o.value.startsWith('province:'))]).slice(0,80);
      if(!select.disabled&&!input.required&&!needle)shown=[{value:'',label:'Tất cả địa điểm'},...shown];
      shown.forEach((option,i)=>{
        const button=document.createElement('span');button.id=list.id+'-'+i;button.className='place-option';button.setAttribute('role','option');button.setAttribute('aria-selected','false');button.textContent=option.label;
        button.addEventListener('pointerdown',event=>{event.preventDefault();choose(option);});list.append(button);
      });
      const hint=document.createElement('span');hint.className='place-hint';hint.textContent=shown.length?'Nhập thêm để thu hẹp gợi ý · ↑ ↓ để chọn · Enter xác nhận':'Không có trong danh mục. Hãy chọn tên khác.';list.append(hint);
      list.hidden=false;input.setAttribute('aria-expanded','true');index=-1;
    }
    input.addEventListener('focus',()=>open(input.value===valueLabel()?'':input.value));
    input.addEventListener('input',()=>{
      select.value='';input.setCustomValidity(input.value?'Hãy chọn một địa điểm từ danh sách gợi ý.':'');open(input.value);
    });
    input.addEventListener('keydown',event=>{
      if(event.key==='Escape'){close();return;}
      if(event.key==='ArrowDown'||event.key==='ArrowUp'){
        event.preventDefault();if(list.hidden)open('');
        index=shown.length?(index<0?(event.key==='ArrowDown'?0:shown.length-1):(index+(event.key==='ArrowDown'?1:-1)+shown.length)%shown.length):-1;highlight();
      }else if(event.key==='Enter'&&!list.hidden){
        if(index>=0){event.preventDefault();choose(shown[index]);}
        else if(shown.length===1){event.preventDefault();choose(shown[0]);}
        else if(input.validity.valid)close();
        else{event.preventDefault();index=shown.length?0:-1;highlight();}
      }
    });
    input.addEventListener('blur',()=>{close();fieldFeedback(input,validityMessage(input));});
  });
}
export function initFleet(confirmation){
  const editor=document.getElementById('layout-editor');if(!editor)return;
  const json=document.getElementById('layout-json'),canvas=document.getElementById('layout-canvas'),type=document.getElementById('vehicle-type'),feedback=document.getElementById('layout-feedback');
  const dimensions=['floors','rows','columns'].map(key=>document.getElementById('layout-'+key));
  let seats=[];try{seats=JSON.parse(json.value);}catch{feedback.textContent='Dữ liệu JSON chưa hợp lệ.';}
  function fit(){if(seats.length){dimensions[0].value=Math.max(...seats.map(s=>s.floor));dimensions[1].value=Math.max(...seats.map(s=>s.row));dimensions[2].value=Math.max(...seats.map(s=>s.column));}}
  function sync(mark=true){json.value=JSON.stringify(seats);if(mark)json.dispatchEvent(new Event('input',{bubbles:true}));feedback.textContent=validateLayout(seats,type.value)||`Đã khai báo ${seats.length} chỗ. Các ô trống không được bán vé.`;}
  function editCell(floor,row,column){
    const existing=seats.find(s=>s.floor===floor&&s.row===row&&s.column===column),dialog=document.createElement('dialog');dialog.className='confirm-dialog';
    const title=document.createElement('h2');title.id='seat-editor-title';title.textContent=`Tầng/khoang ${floor} · hàng ${row} · cột ${column}`;dialog.setAttribute('aria-labelledby',title.id);dialog.append(title);
    const form=document.createElement('form');form.className='stack';form.method='dialog';
    const fields=[];
    for(const [labelText,fieldType,value] of [['Mã chỗ','text',existing?.seat||`${String.fromCharCode(64+floor)}${row.toString().padStart(2,'0')}-${column}`],['Phụ thu (₫)','number',existing?.extra||0]]){
      const label=document.createElement('label');label.textContent=labelText;const field=document.createElement('input');field.type=fieldType;field.value=value;field.required=true;
      if(fieldType==='text'){field.pattern='[A-Z0-9\\-]{1,20}';field.maxLength=20;}else{field.min='0';field.max='1000000';field.step='1000';}
      label.append(field);form.append(label);fields.push(field);
    }
    const kindLabel=document.createElement('label');kindLabel.textContent='Loại chỗ';const kind=document.createElement('select');
    const kinds=type.value==='TAU_THUYEN'?[['PHO_THONG','Phổ thông'],['VIP','VIP']]:[['GHE_NGOI','Ghế ngồi'],['GIUONG_NAM','Giường nằm']];
    kinds.forEach(([value,label])=>{const option=document.createElement('option');option.value=value;option.textContent=label;kind.append(option);});kind.value=existing?.kind||kinds[0][0];kindLabel.append(kind);form.append(kindLabel);
    const message=document.createElement('p');message.className='field-error';message.setAttribute('role','alert');form.append(message);
    const actions=document.createElement('div');actions.className='dialog-actions';
    for(const [text,action]of [['Quay lại','back'],['Để trống / lối đi','empty'],['Lưu chỗ','save']]){
      const button=document.createElement('button');button.type=action==='save'?'submit':'button';button.className='button'+(action==='save'?'':' outline');button.textContent=text;
      if(action==='back'){button.autofocus=true;button.addEventListener('click',()=>dialog.close());}
      if(action==='empty')button.addEventListener('click',()=>{seats=seats.filter(s=>s!==existing);sync();render();dialog.close();});
      actions.append(button);
    }
    form.append(actions);form.addEventListener('submit',event=>{
      event.preventDefault();const updated={seat:fields[0].value.trim(),kind:kind.value,extra:Number(fields[1].value),floor,row,column};
      const next=seats.filter(s=>s!==existing).concat(updated),error=validateLayout(next,type.value);if(error){message.textContent=error;return;}
      seats=next;sync();render();dialog.close();
    });
    dialog.append(form);document.body.append(dialog);dialog.addEventListener('close',()=>dialog.remove(),{once:true});dialog.showModal();
  }
  function render(){
    canvas.replaceChildren();const [floors,rows,columns]=dimensions.map(input=>Number(input.value));
    if(!Number.isInteger(floors)||floors<1||floors>3||!Number.isInteger(rows)||rows<1||rows>30||!Number.isInteger(columns)||columns<1||columns>6){feedback.textContent='Kiểm tra số tầng, hàng và cột.';return;}
    for(let floor=1;floor<=floors;floor++){
      const deck=document.createElement('section');deck.className='editor-deck';const heading=document.createElement('h3');heading.textContent=`Tầng / khoang ${floor}`;deck.append(heading);
      const grid=document.createElement('div');grid.className='editor-grid';grid.style.gridTemplateColumns=`repeat(${columns},minmax(40px,1fr))`;
      for(let row=1;row<=rows;row++)for(let column=1;column<=columns;column++){
        const seat=seats.find(s=>s.floor===floor&&s.row===row&&s.column===column),button=document.createElement('button');button.type='button';button.className='editor-cell '+(seat?(seat.kind==='VIP'?'vip':seat.kind==='GIUONG_NAM'?'sleeper':'filled'):'empty');
        button.textContent=seat?.seat||'＋';button.setAttribute('aria-label',`Hàng ${row}, cột ${column}: ${seat?seat.seat+' · '+seat.kind:'ô trống / lối đi'}`);
        button.addEventListener('click',()=>editCell(floor,row,column));grid.append(button);
      }
      deck.append(grid);canvas.append(deck);
    }
  }
  document.getElementById('layout-resize').addEventListener('click',()=>{
    const [f,r,c]=dimensions.map(input=>Number(input.value));if(dimensions.some(input=>!input.checkValidity())){feedback.textContent='Kích thước chưa hợp lệ.';return;}
    const next=seats.filter(s=>s.floor<=f&&s.row<=r&&s.column<=c);
    if(next.length<seats.length)confirmation({title:'Thu nhỏ sơ đồ?',text:'Các chỗ nằm ngoài kích thước mới sẽ được bỏ khỏi mẫu phương tiện.',confirm:'Áp dụng kích thước'},()=>{seats=next;sync();render();});else render();
  });
  document.querySelectorAll('[data-layout-preset]').forEach(button=>button.addEventListener('click',()=>{
    const load=()=>{type.value=button.dataset.layoutPreset==='BOAT'?'TAU_THUYEN':'XE_KHACH';seats=presetLayout(button.dataset.layoutPreset);fit();sync();render();};
    if(seats.length)confirmation({title:'Dùng sơ đồ mẫu?',text:'Mẫu sẽ thay thế sơ đồ đang chỉnh. Các chuyến cũ không thay đổi.',confirm:'Dùng mẫu'},load);else load();
  }));
  document.getElementById('layout-import').addEventListener('click',()=>{try{const parsed=JSON.parse(json.value),error=validateLayout(parsed,type.value);if(error){feedback.textContent=error;return;}seats=parsed;fit();render();sync();}catch{feedback.textContent='JSON chưa đúng định dạng.';}});
  type.addEventListener('change',()=>{feedback.textContent=validateLayout(seats,type.value)||'Loại phương tiện đã thay đổi. Kiểm tra lại sơ đồ trước khi lưu.';});
  fit();render();if(seats.length)sync(false);else feedback.textContent='Chọn mẫu hoặc nhấn từng ô để khai báo vị trí chỗ.';
}
export function initTripVehicle(){
  const select=document.getElementById('trip-vehicle');if(!select)return;
  const operator=document.querySelector('#trip-form [name=operator]'),summary=document.getElementById('trip-vehicle-summary');
  function refresh(){
    const option=select.selectedOptions[0];
    if(option?.value){operator.value=option.dataset.operator;operator.readOnly=true;summary.textContent=option.textContent+' · Sơ đồ và sức chứa lấy từ phương tiện. Không thể phân công trùng lịch.';}
    else{operator.readOnly=false;summary.textContent='Chọn phương tiện để dùng sơ đồ đã lưu.';}
  }
  select.addEventListener('change',refresh);refresh();
}
export function initScanner(){
  const start=document.getElementById('qr-start');if(!start)return;
  const stop=document.getElementById('qr-stop'),video=document.getElementById('qr-video'),feedback=document.getElementById('qr-feedback');
  let stream,timer,working=false,version=0;
  const end=()=>{version++;working=false;clearTimeout(timer);stream?.getTracks().forEach(track=>track.stop());stream=null;video.srcObject=null;video.hidden=true;start.disabled=false;stop.disabled=true;};
  stop.addEventListener('click',end);window.addEventListener('pagehide',end);
  start.addEventListener('click',async()=>{
    const attempt=++version;
    if(!('BarcodeDetector' in window)||!navigator.mediaDevices?.getUserMedia){feedback.textContent='Trình duyệt chưa hỗ trợ camera đọc QR ở địa chỉ này. Dùng ảnh QR hoặc nhập mã bên dưới.';return;}
    start.disabled=true;feedback.textContent='Đang xin quyền camera…';
    try{
      const supported=await BarcodeDetector.getSupportedFormats();if(!supported.includes('qr_code'))throw new Error('QR_UNSUPPORTED');
      const detector=new BarcodeDetector({formats:['qr_code']});
      const opened=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'}},audio:false});
      if(attempt!==version){opened.getTracks().forEach(track=>track.stop());return;}
      stream=opened;video.srcObject=stream;video.hidden=false;stop.disabled=false;await video.play();working=true;feedback.textContent='Đưa mã QR vé vào camera.';
      async function scan(){
        if(!working||attempt!==version)return;
        try{
          const results=await detector.detect(video);if(!working||attempt!==version)return;
          const value=results.find(result=>/^RVT-TICKET:[A-Fa-f0-9]{64}$/.test(result.rawValue))?.rawValue;
          if(value){end();location.assign('/admin/kiem-ve.html?token='+encodeURIComponent(value));return;}
        }catch{/* Frame not ready; retry without logging image or ticket content. */}
        if(working&&attempt===version)timer=setTimeout(scan,350);
      }
      scan();
    }catch{if(attempt!==version)return;end();feedback.textContent='Chưa mở được camera hoặc trình duyệt không hỗ trợ QR. Kiểm tra quyền camera, hoặc chọn ảnh QR.';}
  });
}
