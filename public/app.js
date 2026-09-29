import { nearestPlace, distanceM, wgsToGcj } from '/geo.js';
import { answerOffline } from '/guide.js';
import { places as bundledPlaces } from '/places.js';

const $ = id => document.getElementById(id);
const state = { places: [], current: null, artifact: null, watchId: null, triggered: new Set(), favorites: new Set(), onlyFavorites: false, aiEnabled: false };
try { state.favorites = new Set(JSON.parse(localStorage.getItem('tour-guide-favorites') || '[]')); } catch { /* 无法读取时仍可浏览 */ }

function speak(text) {
  if (!('speechSynthesis' in window)) { $('location-status').textContent = '当前浏览器不支持朗读，仍可阅读文字'; return; }
  speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = 'zh-CN'; utterance.rate = Number($('speech-rate').value);
  const chosen = speechSynthesis.getVoices().find(v => v.voiceURI === $('speech-voice').value);
  if (chosen) utterance.voice = chosen;
  speechSynthesis.speak(utterance);
}
function renderVoices() {
  if (!('speechSynthesis' in window)) return;
  const previous = $('speech-voice').value || localStorage.getItem('tour-guide-voice') || '';
  $('speech-voice').replaceChildren(new Option('系统默认', ''));
  for (const voice of speechSynthesis.getVoices().filter(v => v.lang.toLowerCase().startsWith('zh'))) {
    $('speech-voice').add(new Option(`${voice.name} · ${voice.lang}${voice.localService ? ' · 本机' : ' · 需联网'}`, voice.voiceURI));
  }
  if ([...$('speech-voice').options].some(option => option.value === previous)) $('speech-voice').value = previous;
}
function renderPlaces() {
  $('places').replaceChildren();
  for (const place of state.places.filter(p => !state.onlyFavorites || state.favorites.has(p.id))) {
    const card = document.createElement('button'); card.className = `place-card${state.current?.id === place.id ? ' selected' : ''}`;
    const area = document.createElement('small'); area.textContent = place.area + (place.kind === 'indoor' ? ' · 馆内手动选择' : ' · 室外可到点提示');
    const name = document.createElement('strong'); name.textContent = place.name;
    const arrow = document.createElement('span'); arrow.className = 'arrow'; arrow.textContent = '查看讲解 ↗';
    card.append(area, name, arrow); card.onclick = () => selectPlace(place);
    $('places').append(card);
  }
  if (!$('places').childElementCount) $('places').textContent = '还没有收藏的景点。';
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
  state.current = place; state.artifact = null;
  $('detail').classList.remove('hidden'); $('detail-area').textContent = place.area;
  $('detail-name').textContent = place.name; $('detail-intro').textContent = place.intro;
  $('detail-story').textContent = place.story ?? place.intro;
  $('detail-look').textContent = place.lookFor || '现场细节尚未核实，请看标识。';
  $('route').disabled = place.kind !== 'outdoor';
  $('route').textContent = place.kind === 'outdoor' ? '↗ 去这里 · 步行路线' : '馆内请按现场导览';
  $('nearby-list').replaceChildren();
  $('favorite').textContent = state.favorites.has(place.id) ? '★' : '☆';
  $('favorite').setAttribute('aria-label', state.favorites.has(place.id) ? '取消收藏' : '收藏当前景点');
  $('answer').classList.add('hidden'); $('question').value = ''; $('photo').value = ''; $('photo-name').textContent = '';
  showSources(place); renderArtifacts(); renderPlaces();
  $('detail').scrollIntoView({ behavior: 'smooth', block: 'start' });
  if (auto) speak(place.story ?? place.intro);
}
function routeTo(place) {
  if (!place || place.kind !== 'outdoor') return;
  const point = place.gcj ?? wgsToGcj(place.wgs);
  const query = new URLSearchParams({ from: '', to: `${point.lng},${point.lat},${place.name}`, mode: 'walk', callnative: '1', src: 'yantu' });
  window.open(`https://uri.amap.com/navigation?${query}`, '_blank', 'noopener');
}
function mapSearch(keyword) {
  const query = new URLSearchParams({ keyword, city: '长沙', view: 'list', callnative: '1', src: 'yantu' });
  const point = state.current?.gcj ?? (state.current?.wgs && wgsToGcj(state.current.wgs));
  if (point) query.set('center', `${point.lng},${point.lat}`);
  window.open(`https://uri.amap.com/search?${query}`, '_blank', 'noopener');
}
function showNearby() {
  const box = $('nearby-list'); box.textContent = '正在确认附近位置…';
  if (!navigator.geolocation) { box.textContent = '当前浏览器不支持定位。'; return; }
  navigator.geolocation.getCurrentPosition(position => {
    if (position.coords.accuracy > 100) { box.textContent = `定位误差约 ${Math.round(position.coords.accuracy)} 米，暂不判断附近。`; return; }
    const wgs = { lat: position.coords.latitude, lng: position.coords.longitude };
    const gcj = wgsToGcj(wgs);
    const matches = state.places.filter(p => p.kind === 'outdoor').map(place => ({
      place, meters: distanceM(place.gcj ? gcj : wgs, place.gcj ?? place.wgs)
    })).filter(item => item.meters <= 2000).sort((a, b) => a.meters - b.meters).slice(0, 4);
    box.replaceChildren();
    if (!matches.length) { box.textContent = '附近两公里内暂无收录的长沙讲解点。'; return; }
    for (const item of matches) {
      const button = document.createElement('button'); button.className = 'artifact';
      button.textContent = `${item.place.name} · 直线约 ${Math.round(item.meters)} 米`;
      button.onclick = () => selectPlace(item.place); box.append(button);
    }
  }, () => { box.textContent = '定位不可用，请先允许定位或手动选景点。'; }, { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 });
}
function startLocation() {
  if (state.watchId !== null) { navigator.geolocation.clearWatch(state.watchId); state.watchId = null; $('locate').textContent = '开启到点讲解'; $('location-status').textContent = '已暂停定位'; return; }
  if (!navigator.geolocation) { $('location-status').textContent = '当前浏览器不支持定位，请手动选景点'; return; }
  $('location-status').textContent = '正在获取位置…';
  state.watchId = navigator.geolocation.watchPosition(position => {
    const { latitude: lat, longitude: lng, accuracy } = position.coords;
    if (accuracy > 80) { $('location-status').textContent = `定位误差约 ${Math.round(accuracy)} 米，请手动选点`; return; }
    const match = nearestPlace({ lat, lng, accuracy }, state.places);
    $('location-status').textContent = match ? `附近：${match.place.name}（约 ${Math.round(match.meters)} 米）` : '已定位，附近暂无收录的室外讲解点';
    if (match && !state.triggered.has(match.place.id)) { state.triggered.add(match.place.id); selectPlace(match.place, true); }
  }, error => { $('location-status').textContent = error.code === 1 ? '未获得定位权限，请手动选景点' : '定位失败，请手动选景点'; }, { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 });
  $('locate').textContent = '暂停到点讲解';
}

$('locate').onclick = startLocation;
$('listen').onclick = () => speak(state.artifact?.intro ?? state.current.story ?? state.current.intro);
$('brief').onclick = () => speak(state.artifact?.intro ?? state.current.intro);
$('stop').onclick = () => { if ('speechSynthesis' in window) speechSynthesis.cancel(); };
$('route').onclick = () => routeTo(state.current);
$('nearby').onclick = showNearby;
$('food-search').onclick = () => mapSearch('湘菜 正餐');
$('fun-search').onclick = () => mapSearch('夜景');
$('show-search').onclick = () => mapSearch('演出');
$('speech-rate').value = localStorage.getItem('tour-guide-rate') || '0.96';
$('speech-rate').onchange = () => localStorage.setItem('tour-guide-rate', $('speech-rate').value);
$('speech-voice').onchange = () => localStorage.setItem('tour-guide-voice', $('speech-voice').value);
renderVoices();
if ('speechSynthesis' in window) speechSynthesis.onvoiceschanged = renderVoices;
$('favorite').onclick = () => { const id = state.current.id; state.favorites.has(id) ? state.favorites.delete(id) : state.favorites.add(id); localStorage.setItem('tour-guide-favorites', JSON.stringify([...state.favorites])); selectPlace(state.current); };
$('show-favorites').onclick = () => { state.onlyFavorites = !state.onlyFavorites; $('show-favorites').setAttribute('aria-pressed', String(state.onlyFavorites)); $('show-favorites').textContent = state.onlyFavorites ? '查看全部' : '☆ 只看收藏'; renderPlaces(); };
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
    if (reply.action === 'nearby') showNearby();
    if (reply.action === 'route') routeTo(reply.destination);
    if (reply.action === 'search') mapSearch(reply.keyword);
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
