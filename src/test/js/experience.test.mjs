import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {normalizePlace,presetLayout,validateLayout,validityMessage,isValidationField} from '../../main/resources/static/js/ui-state.mjs';
test('catalogue covers all 34 current provinces and nonduplicated destinations',()=>{
  const rows=fs.readFileSync(new URL('../../main/resources/data/vietnam-places.tsv',import.meta.url),'utf8').split(/\r?\n/).filter(row=>row&&!row.startsWith('#')).map(row=>row.split('|'));
  assert.equal(rows.length,34);assert.equal(new Set(rows.map(r=>r[0])).size,34);
  const places=rows.flatMap(r=>r[3].split(';'));assert.equal(places.length,185);assert.equal(new Set(places).size,185);
  for(const row of rows)assert.ok(row[3].split(';').length>=3);
});
test('Vietnamese search works without accents and matches former names',()=>{
  assert.equal(normalizePlace('Đà Lạt'),'da lat');assert.equal(normalizePlace('TP. Hồ Chí Minh'),'tp ho chi minh');assert.equal(normalizePlace('Cảng Hà Tiên'),'cang ha tien');
});
test('layouts preserve empty aisle positions and all seats are unique',()=>{
  for(const [model,count,type]of [['BUS_SEAT',28,'XE_KHACH'],['BUS_SLEEPER',36,'XE_KHACH'],['BOAT',40,'TAU_THUYEN']]){
    const seats=presetLayout(model);assert.equal(seats.length,count);assert.equal(validateLayout(seats,type),'');
    assert.equal(new Set(seats.map(s=>s.floor+':'+s.row+':'+s.column)).size,count);
    assert.ok(seats.every(s=>s.column!==3||model==='BUS_SLEEPER'));
  }
  assert.equal(presetLayout('BOAT').filter(s=>s.kind==='VIP').length,8);
});
test('invalid, repeated and incompatible vehicle seats are rejected',()=>{
  assert.ok(validateLayout([],'XE_KHACH'));
  const seats=presetLayout('BUS_SEAT');assert.ok(validateLayout(seats.concat(seats[0]),'XE_KHACH'));
  assert.ok(validateLayout([{...seats[0],column:7}],'XE_KHACH'));
  assert.ok(validateLayout(presetLayout('BOAT'),'XE_KHACH'));
});
test('inline errors have Vietnamese explanations',()=>{
  assert.equal(validityMessage({validity:{valueMissing:true}}),'Vui lòng điền thông tin này.');
  assert.equal(validityMessage({validity:{rangeOverflow:true},max:'30'}),'Giá trị tối đa là 30.');
});
test('submit buttons cannot erase the first field feedback',()=>{
  for(const tagName of ['INPUT','SELECT','TEXTAREA'])assert.equal(isValidationField({tagName,type:'text',willValidate:true}),true);
  assert.equal(isValidationField({tagName:'BUTTON',type:'submit',willValidate:true}),false);
  for(const type of ['submit','reset','button','image','hidden'])assert.equal(isValidationField({tagName:'INPUT',type,willValidate:true}),false);
  assert.equal(isValidationField({tagName:'INPUT',type:'text',willValidate:false}),false);
});
test('trip and vehicle input patterns compile in modern browser Unicode mode',()=>{
  for(const name of ['trip-form','vehicle-form']){
    const html=fs.readFileSync(new URL(`../../main/resources/templates/${name}.html`,import.meta.url),'utf8');
    const pattern=html.match(/pattern="([^"]+)"/)[1];
    const regex=new RegExp('^(?:'+pattern+')$','v');
    assert.equal(regex.test('RVT-XE-001'),true);assert.equal(regex.test('lower case'),false);
  }
});
