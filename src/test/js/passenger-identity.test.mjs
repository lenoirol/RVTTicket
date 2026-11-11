import test from 'node:test';
import assert from 'node:assert/strict';
import {documentRule} from '../../main/resources/static/js/passenger-identity.mjs';
test('citizen card rule preserves a leading zero and requires twelve digits',()=>{
  const rule=documentRule('CCCD'),pattern=new RegExp(`^(?:${rule.pattern})$`,'u');
  assert.equal(rule.maxLength,12);assert.equal(rule.inputMode,'numeric');assert.ok(pattern.test('012345678901'));
  for(const value of ['01234567890','0123456789012','A12345678901','012 45678901'])assert.equal(pattern.test(value),false);
});
test('changing document type does not impose citizen card rules on passport or birth certificate',()=>{
  assert.ok(new RegExp(`^(?:${documentRule('HO_CHIEU').pattern})$`,'u').test('B1234567'));
  assert.ok(new RegExp(`^(?:${documentRule('KHAI_SINH').pattern})$`,'u').test('123/2020'));
});
