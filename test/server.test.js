import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from '../server.js';

test('无接口密钥时景点仍可浏览，问答明确提示配置', async () => {
  const server = createServer({}); await new Promise(resolve => server.listen(0, resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const places = await (await fetch(`${base}/api/places`)).json();
    assert.equal(places.aiEnabled, false); assert.ok(places.places.length >= 3);
    const answer = await fetch(`${base}/api/ask`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ placeId: 'aiwan-pavilion', question: '谁修建的？' }) });
    assert.equal(answer.status, 503); assert.match((await answer.json()).error, /尚未配置/);
    const invalid = await fetch(`${base}/api/ask`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ placeId: 'missing', question: '你好' }) });
    assert.equal(invalid.status, 400);
    const image = await fetch(`${base}/api/ask`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ placeId: 'aiwan-pavilion', question: '这是什么', image: 'data:text/html;base64,PGgxPg==' }) });
    assert.equal(image.status, 400);
  } finally { server.close(); }
});
