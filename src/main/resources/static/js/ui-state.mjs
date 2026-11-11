export function normalizePlace(value) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}
export function presetLayout(model) {
  const sleeper=model==='BUS_SLEEPER',boat=model==='BOAT',seats=[];
  const floors=sleeper?2:1,rows=boat?10:sleeper?6:7,columns=sleeper?[1,3,5]:[1,2,4,5];
  for(let floor=1;floor<=floors;floor++)for(let row=1;row<=rows;row++)columns.forEach((column,index)=>{
    const vip=boat&&row<=2;
    seats.push({seat:(floor===1?'A':'B')+String((row-1)*columns.length+index+1).padStart(2,'0'),
      kind:boat?(vip?'VIP':'PHO_THONG'):(sleeper?'GIUONG_NAM':'GHE_NGOI'),extra:vip?100000:0,floor,row,column});
  });
  return seats;
}
export function validateLayout(seats,type) {
  if(!Array.isArray(seats)||seats.length<1||seats.length>300)return 'Sơ đồ cần có 1–300 chỗ.';
  const codes=new Set(),positions=new Set(),kinds=type==='TAU_THUYEN'?['PHO_THONG','VIP']:['GHE_NGOI','GIUONG_NAM'];
  for(const s of seats){
    if(!s||!/^[A-Z0-9-]{1,20}$/.test(s.seat)||!kinds.includes(s.kind)||!Number.isFinite(Number(s.extra))||Number(s.extra)<0||Number(s.extra)>1000000)return 'Mã chỗ, loại chỗ hoặc phụ thu chưa hợp lệ.';
    if(!Number.isInteger(s.floor)||!Number.isInteger(s.row)||!Number.isInteger(s.column)||s.floor<1||s.floor>3||s.row<1||s.row>30||s.column<1||s.column>6)return 'Vị trí phải nằm trong giới hạn 3 tầng, 30 hàng, 6 cột.';
    const position=`${s.floor}:${s.row}:${s.column}`;
    if(codes.has(s.seat)||positions.has(position))return 'Mã chỗ và vị trí không được trùng.';
    codes.add(s.seat);positions.add(position);
  }
  return '';
}
export function isValidationField(field) {
  return Boolean(field?.willValidate && ['INPUT','SELECT','TEXTAREA'].includes(field.tagName)
    && !['hidden','submit','button','reset','image'].includes(field.type));
}
export function validityMessage(field) {
  const v=field.validity;
  if(v.valueMissing)return 'Vui lòng điền thông tin này.';
  if(v.typeMismatch)return field.type==='email'?'Nhập địa chỉ email hợp lệ.':'Thông tin chưa đúng định dạng.';
  if(v.patternMismatch)return field.pattern==='[0-9]{12}'?'Số căn cước / CCCD phải gồm đúng 12 chữ số.':field.type==='tel'?'Số điện thoại phải có 10–15 chữ số.':'Thông tin chưa đúng định dạng yêu cầu.';
  if(v.rangeUnderflow)return `Giá trị tối thiểu là ${field.min}.`;
  if(v.rangeOverflow)return `Giá trị tối đa là ${field.max}.`;
  if(v.stepMismatch)return `Giá trị cần theo bước ${field.step}.`;
  if(v.tooShort)return `Cần ít nhất ${field.minLength} ký tự.`;
  if(v.tooLong)return `Tối đa ${field.maxLength} ký tự.`;
  if(v.badInput)return 'Nhập giá trị hợp lệ.';
  if(v.customError)return field.validationMessage;
  return '';
}
