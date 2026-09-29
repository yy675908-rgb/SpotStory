import test from 'node:test';
import assert from 'node:assert/strict';
import { distanceM, nearestPlace, wgsToGcj } from '../lib/geo.js';
import { places } from '../data/places.js';

test('同点距离为零，远处不触发', () => {
  assert.equal(distanceM({ lat: 28, lng: 112 }, { lat: 28, lng: 112 }), 0);
  assert.equal(nearestPlace({ lat: 39.9, lng: 116.4, accuracy: 10 }, places), null);
});
test('拒绝低精度 GPS，且不能将馆内展品当室外定位点', () => {
  assert.equal(nearestPlace({ lat: 28.17108, lng: 112.95479, accuracy: 300 }, places), null);
  assert.equal(nearestPlace({ lat: NaN, lng: 112.95479, accuracy: 5 }, places), null);
  assert.equal(places.find(p => p.kind === 'indoor').gcj, undefined);
});
test('GCJ-02 地点可与 WGS84 浏览器定位在同一坐标系比较', () => {
  const spot = places.find(p => p.id === 'aiwan-pavilion');
  // 反向近似搜索一个点，检查落在几十米而不是数百米外。
  const wgs = { lat: 28.18345, lng: 112.93248 };
  assert.ok(distanceM(wgsToGcj(wgs), spot.gcj) < 100);
  assert.equal(nearestPlace({ ...wgs, accuracy: 10 }, places)?.place.id, spot.id);
});
test('雕塑地点的 WGS84 坐标可识别', () => {
  assert.equal(nearestPlace({ lat: 28.17108, lng: 112.95479, accuracy: 10 }, places)?.place.id, 'orange-island-statue');
});
