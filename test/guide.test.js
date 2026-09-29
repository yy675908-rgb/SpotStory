import test from 'node:test';
import assert from 'node:assert/strict';
import { answerOffline } from '../lib/guide.js';
import { places } from '../data/places.js';

const current = places.find(p => p.id === 'wuyi-square');
test('线下提问可打开指定地点路线，普通问题不编造事实', () => {
  assert.equal(answerOffline('我要去杜甫江阁', current, places).destination.id, 'dufu-pavilion');
  assert.equal(answerOffline('长怀井在哪', current, places).action, undefined);
  assert.match(answerOffline('某石碑上有什么划痕', current, places).text, /暂无核实/);
});
test('附近和现场细节在无 AI 时仍可用', () => {
  assert.equal(answerOffline('附近有什么', current, places).action, 'nearby');
  assert.equal(answerOffline('附近有啥好吃的', current, places).keyword, '湘菜 正餐');
  assert.equal(answerOffline('到现场看什么', current, places).text, current.lookFor);
});
