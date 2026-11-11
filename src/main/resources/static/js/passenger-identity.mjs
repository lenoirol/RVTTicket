export function documentRule(type) {
  if (type === 'CCCD') return {pattern:'[0-9]{12}', maxLength:12, inputMode:'numeric', placeholder:'Nhập đúng 12 chữ số', label:'Số căn cước / CCCD', hint:'Căn cước / CCCD Việt Nam gồm đúng 12 chữ số.'};
  if (type === 'KHAI_SINH') return {pattern:'[\\p{L}\\p{N}.\\/\\-]{1,30}', maxLength:30, inputMode:'text', placeholder:'Số trên giấy khai sinh', label:'Số giấy khai sinh', hint:'Nhập số giấy khai sinh, tối đa 30 ký tự.'};
  return {pattern:'[A-Za-z0-9]{6,30}', maxLength:30, inputMode:'text', placeholder:'Số hộ chiếu, không có khoảng trắng', label:'Số hộ chiếu', hint:'Hộ chiếu: 6–30 ký tự chữ hoặc số.'};
}
