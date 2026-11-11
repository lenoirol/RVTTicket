import test from 'node:test';
import assert from 'node:assert/strict';
import {calculateTotal, toggleSelection,ageGroup,passengerFare,conflictedSeats} from '../../main/resources/static/js/booking-state.mjs';
test('uses configured allowance and price rather than fixed baggage rules', () => {
  assert.deepEqual(calculateTotal(320000,[{extra:0,luggage:26},{extra:100000,luggage:20}],20,12000),
    {extras:100000,luggage:72000,grand:812000});
});
test('free baggage never reduces the price and empty orders cost zero', () => {
  assert.equal(calculateTotal(100000,[{luggage:0}],25,15000).grand,100000);
  assert.equal(calculateTotal(100000,[],25,15000).grand,0);
});
test('selection limit, deselection and stable order of remaining seats', () => {
  const selected=new Map();
  for(let i=1;i<=8;i++) assert.equal(toggleSelection(selected,'A'+i,0),'added');
  assert.equal(toggleSelection(selected,'A9',0),'limit');
  assert.equal(toggleSelection(selected,'A3',0),'removed');
  assert.equal(toggleSelection(selected,'A9',100000),'added');
  assert.deepEqual([...selected.keys()],['A1','A2','A4','A5','A6','A7','A8','A9']);
});
test('age uses birthday on travel date with exact child/senior boundaries',()=>{
  assert.equal(ageGroup('2014-10-04','2026-10-03'),'TRE_EM');assert.equal(ageGroup('2014-10-03','2026-10-03'),'NGUOI_LON');assert.equal(ageGroup('1966-10-03','2026-10-03'),'CAO_TUOI');assert.equal(ageGroup('1966-10-04','2026-10-03'),'NGUOI_LON');assert.equal(ageGroup('2027-01-01','2026-10-03'),null);assert.equal(ageGroup('2020-02-30','2026-10-03'),null);
});
test('discounted passenger fares, full VIP and baggage share one total',()=>{
  assert.equal(passengerFare('TRE_EM',315000,225000,265000),225000);assert.equal(passengerFare('CAO_TUOI',315000,225000,265000),265000);assert.equal(passengerFare('TRE_EM',315000,225000,265000,true),315000);assert.equal(passengerFare('TRE_EM',315000,null,null),252000);
  assert.equal(calculateTotal(315000,[{fare:225000,extra:0,luggage:26},{fare:315000,extra:185000,luggage:0}],25,15000).grand,740000);
});
test('seat refresh reports conflicts without discarding selection or passenger data',()=>{
  const selected=new Map([['A01',0],['A02',0],['A03',50000]]);
  assert.deepEqual(conflictedSeats(selected,[{seat:'A01',unavailable:false},{seat:'A02',unavailable:true}]),['A02','A03']);
  assert.deepEqual([...selected.keys()],['A01','A02','A03']);
  assert.deepEqual(conflictedSeats(selected,[{seat:'A01',unavailable:false},{seat:'A02',unavailable:false},{seat:'A03',unavailable:false}]),[]);
});
