// Small progressive enhancements; no passenger or credential data is persisted in the browser.
document.querySelectorAll('[data-swap-route]').forEach(button=>{
  const form=button.closest('form'),from=form.querySelector('[name=from]'),to=form.querySelector('[name=to]');
  if(!from||!to)return;
  button.hidden=false;
  button.addEventListener('click',()=>{
    const old=from.value;from.value=to.value;to.value=old;
    [from,to].forEach(select=>select.dispatchEvent(new Event('change',{bubbles:true})));
  });
});
const password=document.querySelector('[name=password][autocomplete=new-password]');
const confirmation=document.querySelector('[name=confirmation]');
if(password&&confirmation){
  const validate=()=>confirmation.setCustomValidity(confirmation.value&&confirmation.value!==password.value?'Hai lần nhập mật khẩu chưa khớp.':'');
  password.addEventListener('input',validate);confirmation.addEventListener('input',validate);
}
// Show password controls are deliberately local to the input; never save the value.
document.querySelectorAll('input[type=password]').forEach((input,index)=>{
  const label=input.closest('label');
  const labelText=label?[...label.childNodes].filter(node=>node.nodeType===Node.TEXT_NODE).map(node=>node.textContent).join('').trim():'';
  if(labelText)input.setAttribute('aria-label',labelText);
  if(!input.id)input.id='rvt-password-'+index;
  const button=document.createElement('button');button.type='button';button.className='copy-code';button.textContent='Hiện mật khẩu';button.setAttribute('aria-pressed','false');
  button.setAttribute('aria-controls',input.id);
  button.addEventListener('click',()=>{const visible=input.type==='password';input.type=visible?'text':'password';button.textContent=visible?'Ẩn mật khẩu':'Hiện mật khẩu';button.setAttribute('aria-pressed',String(visible));});input.after(button);
});

// Khứ hồi: hiện/ẩn ô "Ngày về" và giữ ngày về >= ngày đi.
document.querySelectorAll('[data-round-trip]').forEach(box=>{
  const form=box.closest('form'),label=form.querySelector('[data-return-date]'),input=label.querySelector('input'),date=form.querySelector('[name=date]');
  const sync=()=>{label.hidden=!box.checked;input.required=box.checked;if(!box.checked)input.value='';else if(!input.value&&date.value)input.value=date.value;};
  const limit=()=>{if(date.value){input.min=date.value;if(input.value&&input.value<date.value)input.value=date.value;}};
  box.addEventListener('change',()=>{sync();limit();});date.addEventListener('change',limit);sync();limit();
});
