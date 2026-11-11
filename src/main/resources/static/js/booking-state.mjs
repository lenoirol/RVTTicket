export const money = value => new Intl.NumberFormat('vi-VN').format(value) + ' ₫';
export function calculateTotal(base, passengers, allowance, rate) {
  const extras = passengers.reduce((sum, p) => sum + Number(p.extra || 0), 0);
  const luggage = passengers.reduce((sum, p) => sum + Math.max(0, Number(p.luggage || 0) - allowance) * rate, 0);
  const fares = passengers.reduce((sum, p) => sum + Number(p.fare ?? base), 0);
  return {extras, luggage, grand: fares + extras + luggage};
}
export function ageGroup(birth, travel) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(birth || '') || !/^\d{4}-\d{2}-\d{2}$/.test(travel || '') || birth > travel) return null;
  const [y,m,d] = birth.split('-').map(Number), [ty,tm,td] = travel.split('-').map(Number);
  if (new Date(Date.UTC(y,m-1,d)).toISOString().slice(0,10) !== birth) return null;
  const age = ty-y-((tm<m || (tm===m && td<d))?1:0);
  if (age>120) return null;
  return age<12?'TRE_EM':age>=60?'CAO_TUOI':'NGUOI_LON';
}
export function passengerFare(group, adult, child, senior, vip=false) {
  if (vip || group==='NGUOI_LON' || !group) return adult;
  const fallback = Math.max(1000, Math.round(adult*.8/1000)*1000);
  return group==='TRE_EM' ? (child ?? fallback) : (senior ?? fallback);
}
export function toggleSelection(selected, seat, extra, limit = 8) {
  if (selected.has(seat)) { selected.delete(seat); return 'removed'; }
  if (selected.size >= limit) return 'limit';
  selected.set(seat, extra); return 'added';
}
export function conflictedSeats(selected,availability){
  const states=new Map(availability.map(s=>[s.seat,s.unavailable]));
  return [...selected.keys()].filter(seat=>states.get(seat)!==false);
}
