import { nearestPlace, distanceM, wgsToGcj } from '/geo.js';
import { answerOffline } from '/guide.js';
import { places as bundledPlaces } from '/places.js';
import { restaurants } from '/restaurants.js';

const $ = id => document.getElementById(id);
const state = { places: [], current: null, detailOpen: false, artifact: null, watchId: null, position: null, triggered: new Set(), aiEnabled: false };

let playback = { owner: null, utterance: null, paused: false };
function updatePlayback() {
  for (const button of document.querySelectorAll('[data-play-id]')) {
    const playing = playback.owner === `play:${button.dataset.playId}` && !playback.paused;
    button.textContent = playing ? 'Ⅱ' : '▶';
    button.setAttribute('aria-label', `${playing ? '暂停' : '播放'}${button.dataset.playName}完整故事`);
  }
}
function stopPlayback() {
  if ('speechSynthesis' in window) speechSynthesis.cancel();
  playback = { owner: null, utterance: null, paused: false }; updatePlayback();
}
function speak(text, owner = null) {
  if (!('speechSynthesis' in window)) { $('location-status').textContent = '当前浏览器不支持朗读，仍可阅读文字'; return; }
  stopPlayback();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = 'zh-CN'; utterance.rate = Number(localStorage.getItem('tour-guide-rate') || '1.0');
  playback = { owner, utterance, paused: false }; updatePlayback();
  utterance.onend = utterance.onerror = () => { if (playback.utterance === utterance) stopPlayback(); };
  speechSynthesis.speak(utterance);
}
function togglePlayback(owner, text) {
  if (playback.owner === owner && playback.utterance) {
    if (playback.paused) speechSynthesis.resume(); else speechSynthesis.pause();
    playback.paused = !playback.paused; updatePlayback();
  } else speak(text, owner);
}
function renderPlaces() {
  const detail = $('detail');
  if (detail.parentElement !== $('places').parentElement) $('places').after(detail);
  $('places').replaceChildren();
  for (const place of state.places) {
    const wrapper = document.createElement('article'); wrapper.className = 'place-accordion';
    const top = document.createElement('div'); top.className = 'place-top';
    const card = document.createElement('button'); card.className = `place-card${state.current?.id === place.id && state.detailOpen ? ' selected' : ''}`;
    const area = document.createElement('small'); area.textContent = place.area + (place.kind === 'indoor' ? ' · 馆内手动选择' : ' · 室外可到点提示');
    const name = document.createElement('strong'); name.textContent = place.name;
    const arrow = document.createElement('span'); arrow.className = 'arrow'; arrow.textContent = state.current?.id === place.id && state.detailOpen ? '⌃ 收起' : '⌄ 展开';
    card.append(name, area, arrow); card.onclick = () => state.current?.id === place.id && state.detailOpen ? collapsePlace() : selectPlace(place);
    top.append(card);
    const play = document.createElement('button'); play.className = 'play-short'; play.dataset.playId = place.id;
    play.dataset.playName = place.name; play.type = 'button';
    play.onclick = () => {
      if (state.current?.id !== place.id) selectPlace(place);
      togglePlayback(`play:${place.id}`, state.current.story ?? state.current.intro);
    }; top.append(play);
    if (place.kind === 'outdoor') {
      const route = document.createElement('button'); route.className = 'route-short'; route.textContent = '高德 ↗';
      route.setAttribute('aria-label', `高德步行去${place.name}`); route.onclick = () => routeTo(place); top.append(route);
    }
    wrapper.append(top);
    if (state.current?.id === place.id && state.detailOpen) wrapper.append(detail);
    $('places').append(wrapper);
  }
  if (!$('places').childElementCount) $('places').textContent = '还没有收藏的景点。';
  updatePlayback();
}
function collapsePlace() {
  $('detail').classList.add('hidden'); state.detailOpen = false; renderPlaces();
}
function showSources(place) {
  $('sources').replaceChildren();
  for (const source of place.sources) {
    const li = document.createElement('li'); const link = document.createElement('a');
    link.href = source.url; link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = source.title;
    li.append(link); $('sources').append(li);
  }
}
function renderArtifacts() {
  const section = $('artifact-section'); section.classList.toggle('hidden', !state.current.artifacts.length);
  $('artifacts').replaceChildren();
  for (const artifact of state.current.artifacts) {
    const button = document.createElement('button'); button.className = `artifact${state.artifact?.id === artifact.id ? ' selected' : ''}`;
    button.textContent = artifact.name; button.onclick = () => {
      state.artifact = state.artifact?.id === artifact.id ? null : artifact;
      $('detail-intro').textContent = state.artifact?.intro ?? state.current.intro;
      $('detail-story').textContent = state.artifact?.intro ?? state.current.story ?? state.current.intro;
      $('detail-look').textContent = state.artifact ? '看展柜旁的正式说明牌；馆内位置请按现场导览寻找。' : state.current.lookFor || '现场细节尚未核实，请看标识。';
      $('answer').classList.add('hidden'); renderArtifacts();
    }; $('artifacts').append(button);
  }
}
function selectPlace(place, auto = false) {
  if (state.current?.id === place.id && !state.detailOpen) {
    state.detailOpen = true; $('detail').classList.remove('hidden'); renderPlaces();
    $('detail').scrollIntoView({ behavior: 'smooth', block: 'start' }); return;
  }
  stopPlayback();
  state.current = place; state.detailOpen = true; state.artifact = null;
  $('detail').classList.remove('hidden'); $('detail-area').textContent = place.area;
  $('detail-name').textContent = place.name; $('detail-intro').textContent = place.intro;
  $('detail-story').textContent = place.story ?? place.intro;
  $('detail-look').textContent = place.lookFor || '现场细节尚未核实，请看标识。';
  $('answer').classList.add('hidden'); $('question').value = ''; $('photo').value = ''; $('photo-name').textContent = '';
  showSources(place); renderArtifacts(); renderPlaces();
  $('sources-panel').open = false;
  $('detail').scrollIntoView({ behavior: 'smooth', block: 'start' });
  if (auto) speak(place.story ?? place.intro, `play:${place.id}`);
}
function openMeituan(keyword) {
  const url = `imeituan://www.meituan.com/search?q=${encodeURIComponent(keyword)}`;
  window.location.href = url;
  const timer = setTimeout(() => { if (!document.hidden) window.open('https://i.meituan.com/', '_blank', 'noopener'); }, 1600);
  document.addEventListener('visibilitychange', () => { if (document.hidden) clearTimeout(timer); }, { once: true });
}
function explorePanel(message) {
  const box = $('explore-panel'); box.classList.remove('hidden'); box.replaceChildren();
  if (message) box.textContent = message;
  return box;
}
function currentPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) { reject(new Error('当前浏览器不支持定位。')); return; }
    navigator.geolocation.getCurrentPosition(position => {
      if (position.coords.accuracy > 150 || Math.abs(Date.now() - position.timestamp) > 20000) {
        reject(new Error('定位精度不足，请到开阔处重试。')); return;
      }
      resolve(wgsToGcj({ lat: position.coords.latitude, lng: position.coords.longitude }));
    }, () => reject(new Error('无法获取当前位置，请授权定位后重试。')),
    { enableHighAccuracy: true, maximumAge: 0, timeout: 15000 });
  });
}
function renderExploreRows(items, point, food) {
  const box = explorePanel();
  const close = document.createElement('button'); close.className = 'outline'; close.textContent = '⌃ 收起';
  close.onclick = () => box.classList.add('hidden'); box.append(close);
  const note = document.createElement('p'); note.className = 'hint'; note.textContent = '按当前位置排序 · 步行距离为估算'; box.append(note);
  if (!items.length) {
    const empty = document.createElement('p'); empty.textContent = `当前位置附近暂未收录${food ? '餐厅' : '讲解点'}。`; box.append(empty);
  }
  for (const item of items) {
    const row = document.createElement('div'); row.className = 'food-row';
    const info = document.createElement('div'); const title = document.createElement('strong'); title.textContent = item.name;
    const detail = document.createElement('small'); detail.textContent = `${food ? item.kind : item.area} · 预计步行约 ${Math.round(item.meters * 1.3 / 50) * 50} 米`;
    info.append(title, detail);
    const action = document.createElement('button'); action.type = 'button'; action.textContent = food ? '⌖' : '高德 ↗';
    action.setAttribute('aria-label', food ? `美团查找${item.name}` : `高德步行去${item.name}`);
    action.onclick = () => food ? openMeituan(item.name) : routeTo(item); row.append(info, action); box.append(row);
  }
  const more = document.createElement('button'); more.className = 'outline';
  more.textContent = food ? '美团查看更多餐厅 ↗' : '高德找更多玩法 ↗';
  more.onclick = () => food ? openMeituan('不辣 美食') : mapSearch('景点', point); box.append(more);
  if (food) { const hint = document.createElement('p'); hint.className = 'hint'; hint.textContent = '距离按直线估算；营业和口味请在美团核对，点单说明不吃辣。'; box.append(hint); }
}
async function explore(action) {
  explorePanel('正在获取当前位置…');
  try {
    const point = await currentPosition();
    if (action === 'night' || action === 'show') {
      $('explore-panel').classList.add('hidden'); mapSearch(action === 'night' ? '夜景' : '演出', point); return;
    }
    if (action === 'food') {
      const items = restaurants.map(item => ({ ...item, meters: distanceM(point, item.gcj) }))
        .filter(item => item.meters <= 3500).sort((a, b) => a.meters - b.meters);
      renderExploreRows(items, point, true); return;
    }
    const items = state.places.filter(item => item.kind === 'outdoor').map(item => ({ ...item, meters: distanceM(point, item.gcj ?? wgsToGcj(item.wgs)) }))
      .filter(item => item.meters <= 3500).sort((a, b) => a.meters - b.meters);
    renderExploreRows(items, point, false);
  } catch (error) { explorePanel(error.message); }
}
function routeTo(place) {
  if (!place || place.kind !== 'outdoor') return;
  const point = place.gcj ?? wgsToGcj(place.wgs);
  if (/Android/i.test(navigator.userAgent)) {
    const appQuery = new URLSearchParams({ sourceApplication: '沿途', dlat: String(point.lat), dlon: String(point.lng), dname: place.name, dev: '0', t: '2' });
    window.location.href = `amapuri://route/plan/?${appQuery}`;
    return;
  }
  const query = new URLSearchParams({ from: '', to: `${point.lng},${point.lat},${place.name}`, mode: 'walk', callnative: '1', src: 'yantu' });
  window.open(`https://uri.amap.com/navigation?${query}`, '_blank', 'noopener');
}
function mapSearch(keyword, point) {
  if (/Android/i.test(navigator.userAgent)) {
    const params = new URLSearchParams({ sourceApplication: '沿途', keywords: keyword, lat: String(point.lat), lon: String(point.lng), dev: '0' });
    window.location.href = `androidamap://arroundpoi?${params}`; return;
  }
  const query = new URLSearchParams({ keyword, center: `${point.lng},${point.lat}`, view: 'list', callnative: '1', src: 'yantu' });
  window.open(`https://uri.amap.com/search?${query}`, '_blank', 'noopener');
}
function startLocation() {
  if (state.watchId !== null) { navigator.geolocation.clearWatch(state.watchId); state.watchId = null; $('locate').textContent = '开启到点讲解'; $('location-status').textContent = '已暂停定位'; return; }
  if (!navigator.geolocation) { $('location-status').textContent = '当前浏览器不支持定位，请手动选景点'; return; }
  $('location-status').textContent = '正在获取位置…';
  state.watchId = navigator.geolocation.watchPosition(position => {
    const { latitude: lat, longitude: lng, accuracy } = position.coords;
    if (accuracy <= 100) { state.position = wgsToGcj({ lat, lng }); }
    if (accuracy > 80) { $('location-status').textContent = `定位误差约 ${Math.round(accuracy)} 米，请手动选点`; return; }
    const match = nearestPlace({ lat, lng, accuracy }, state.places);
    $('location-status').textContent = match ? `附近：${match.place.name}（约 ${Math.round(match.meters)} 米）` : '已定位，附近暂无收录的室外讲解点';
    if (match && !state.triggered.has(match.place.id)) { state.triggered.add(match.place.id); selectPlace(match.place, true); }
  }, error => { $('location-status').textContent = error.code === 1 ? '未获得定位权限，请手动选景点' : '定位失败，请手动选景点'; }, { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 });
  $('locate').textContent = '暂停到点讲解';
}

$('locate').onclick = startLocation;
 $('collapse-detail').onclick = collapsePlace;
for (const option of document.querySelectorAll('[data-explore]')) option.onclick = () => explore(option.dataset.explore);
function updateRates() {
  const saved = localStorage.getItem('tour-guide-rate') || '1.0';
  for (const option of $('speech-rate').querySelectorAll('button')) option.setAttribute('aria-pressed', String(option.dataset.rate === saved));
}
for (const option of $('speech-rate').querySelectorAll('button')) option.onclick = () => {
  localStorage.setItem('tour-guide-rate', option.dataset.rate); updateRates();
  if (playback.utterance && !playback.paused) speak(playback.utterance.text, playback.owner);
};
updateRates();
$('photo').onchange = () => { const file = $('photo').files[0]; $('photo-name').textContent = file ? `已选：${file.name}（仅在提问时发送）` : ''; };

const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
$('voice').onclick = () => {
  if (!Recognition) { $('answer').textContent = '当前浏览器不支持语音识别，可直接输入问题。'; $('answer').classList.remove('hidden'); return; }
  const recognition = new Recognition(); recognition.lang = 'zh-CN'; recognition.interimResults = false;
  $('voice').textContent = '正在听…'; recognition.onresult = event => { $('question').value = event.results[0][0].transcript; };
  recognition.onerror = () => { $('answer').textContent = '没有听清，请再试或直接输入。'; $('answer').classList.remove('hidden'); };
  recognition.onend = () => { $('voice').textContent = '🎙 说话提问'; };
  recognition.start();
};

function imageData(file) {
  return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(file); });
}
$('ask-form').onsubmit = async event => {
  event.preventDefault(); if (!state.current) return;
  const question = $('question').value.trim();
  if (!state.aiEnabled) {
    const reply = answerOffline(question, state.current, state.places);
    $('answer').textContent = reply.text; $('answer').classList.remove('hidden');
    if (reply.action === 'nearby' || reply.action === 'fun') explore('fun');
    if (reply.action === 'food') explore('food');
    if (reply.action === 'route') routeTo(reply.destination);
    if (reply.action === 'search') explore(reply.keyword === '夜景' ? 'night' : 'show');
    return;
  }
  const file = $('photo').files[0];
  if (file && (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 4 * 1024 * 1024)) { $('answer').textContent = '照片需为小于 4 MB 的 JPEG、PNG 或 WebP。'; $('answer').classList.remove('hidden'); return; }
  const placeId = state.current.id, artifactId = state.artifact?.id;
  $('ask').disabled = true; $('answer').textContent = '正在查资料回答…'; $('answer').classList.remove('hidden');
  try {
    const response = await fetch('/api/ask', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ placeId, artifactId, question, image: file ? await imageData(file) : undefined }) });
    const data = await response.json();
    if (state.current?.id === placeId) $('answer').textContent = response.ok ? data.answer : data.error;
  } catch { $('answer').textContent = '网络暂不可用。已核实的文字讲解仍可阅读。'; }
  finally { $('ask').disabled = false; }
};

state.places = bundledPlaces;
try {
  const response = await fetch('/api/places');
  if (response.ok) { const data = await response.json(); state.places = data.places; state.aiEnabled = data.aiEnabled; }
} catch { /* 静态站点使用随应用附带的核实讲解 */ }
renderPlaces();
if (!state.aiEnabled) {
  $('question').placeholder = '例如：这里有什么典故？我要去杜甫江阁';
  $('photo-button').classList.add('hidden');
  $('ask-title').textContent = '问沿途';
  $('ask-hint').textContent = '可问已收录的故事、现场线索和目的地。其他问题会如实说不知道。';
}
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
