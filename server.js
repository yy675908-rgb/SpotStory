import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { places, findPlace } from './data/places.js';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), 'public');
const files = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/app.js', ['app.js', 'text/javascript; charset=utf-8']],
  ['/geo.js', ['../lib/geo.js', 'text/javascript; charset=utf-8']],
  ['/guide.js', ['../lib/guide.js', 'text/javascript; charset=utf-8']],
  ['/places.js', ['../data/places.js', 'text/javascript; charset=utf-8']],
  ['/style.css', ['style.css', 'text/css; charset=utf-8']],
  ['/manifest.webmanifest', ['manifest.webmanifest', 'application/manifest+json']],
  ['/sw.js', ['sw.js', 'text/javascript; charset=utf-8']],
  ['/icon.svg', ['icon.svg', 'image/svg+xml']]
]);

function json(res, status, data) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  res.end(JSON.stringify(data));
}

async function body(req) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 6 * 1024 * 1024) {
      const error = new Error('请求内容过大'); error.status = 413; throw error;
    }
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { const error = new Error('JSON 格式错误'); error.status = 400; throw error; }
}

export function createServer(config = process.env) {
  return http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost');
      if (req.method === 'GET' && url.pathname === '/api/places') {
        return json(res, 200, { places, aiEnabled: Boolean(config.AI_API_BASE_URL && config.AI_API_KEY && config.AI_MODEL) });
      }
      if (req.method === 'POST' && url.pathname === '/api/ask') {
        const input = await body(req);
        const place = findPlace(input.placeId);
        if (!place || typeof input.question !== 'string' || !input.question.trim() || input.question.length > 600) {
          return json(res, 400, { error: '请选景点并输入 1—600 字的问题' });
        }
        const artifact = place.artifacts.find(item => item.id === input.artifactId);
        const image = input.image;
        if (image !== undefined && (typeof image !== 'string' || !/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(image) || image.length > 5.5 * 1024 * 1024)) {
          return json(res, 400, { error: '照片仅支持小于 4 MB 的 JPEG、PNG 或 WebP' });
        }
        if (!config.AI_API_BASE_URL || !config.AI_API_KEY || !config.AI_MODEL) {
          return json(res, 503, { error: '尚未配置 AI 问答。仍可听已核实的景点讲解。' });
        }
        let endpoint;
        try {
          const base = new URL(config.AI_API_BASE_URL.endsWith('/') ? config.AI_API_BASE_URL : config.AI_API_BASE_URL + '/');
          if (base.protocol !== 'https:' && base.hostname !== 'localhost' && base.hostname !== '127.0.0.1') throw new Error();
          endpoint = new URL('chat/completions', base);
        } catch { return json(res, 500, { error: 'AI 接口地址配置有误' }); }
        const context = JSON.stringify({ name: place.name, intro: place.intro, artifact: artifact ?? null, sources: place.sources });
        const content = image ? [{ type: 'text', text: input.question }, { type: 'image_url', image_url: { url: image } }] : input.question;
        const upstream = await fetch(endpoint, {
          method: 'POST',
          headers: { Authorization: `Bearer ${config.AI_API_KEY}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({ model: config.AI_MODEL, temperature: 0.2, messages: [
            { role: 'system', content: '你是中文景点解说员。只根据提供的资料回答具体史实；资料不足时明确说不知道。照片只能提供观察线索，不能单凭相似外观断言展品身份。简洁口语化，避免杜撰日期、位置、故事。不要把来源 URL 当成已阅读的全文。' },
            { role: 'system', content: `已核实的资料：${context}` },
            { role: 'user', content }
          ] }),
          signal: AbortSignal.timeout(30000)
        });
        if (!upstream.ok) return json(res, 502, { error: `AI 服务未能回答（状态 ${upstream.status}）` });
        const result = await upstream.json();
        const answer = result.choices?.[0]?.message?.content;
        if (typeof answer !== 'string' || !answer.trim()) return json(res, 502, { error: 'AI 服务未返回文字回答' });
        return json(res, 200, { answer: answer.slice(0, 3000), sources: place.sources });
      }
      if (req.method === 'GET' && files.has(url.pathname)) {
        const [filename, type] = files.get(url.pathname);
        const buffer = await fs.readFile(path.join(root, filename));
        res.writeHead(200, { 'Content-Type': type, 'X-Content-Type-Options': 'nosniff', 'Cache-Control': url.pathname === '/sw.js' ? 'no-cache' : 'public, max-age=3600' });
        return res.end(buffer);
      }
      return json(res, 404, { error: '页面不存在' });
    } catch (error) {
      if (error.name === 'TimeoutError') return json(res, 504, { error: 'AI 接口超时，请重试' });
      if (error.status) return json(res, error.status, { error: error.message });
      console.error(error);
      return json(res, 500, { error: '暂时无法处理，请稍后重试' });
    }
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 3000);
  createServer().listen(port, () => console.log(`Tour Guide running at http://localhost:${port}`));
}
