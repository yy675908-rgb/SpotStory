const pi = Math.PI;
const a = 6378245.0;
const ee = 0.006693421622965943;

function transformLat(x, y) {
  let v = -100 + 2 * x + 3 * y + .2 * y * y + .1 * x * y + .2 * Math.sqrt(Math.abs(x));
  v += (20 * Math.sin(6 * x * pi) + 20 * Math.sin(2 * x * pi)) * 2 / 3;
  v += (20 * Math.sin(y * pi) + 40 * Math.sin(y / 3 * pi)) * 2 / 3;
  return v + (160 * Math.sin(y / 12 * pi) + 320 * Math.sin(y * pi / 30)) * 2 / 3;
}
function transformLng(x, y) {
  let v = 300 + x + 2 * y + .1 * x * x + .1 * x * y + .1 * Math.sqrt(Math.abs(x));
  v += (20 * Math.sin(6 * x * pi) + 20 * Math.sin(2 * x * pi)) * 2 / 3;
  v += (20 * Math.sin(x * pi) + 40 * Math.sin(x / 3 * pi)) * 2 / 3;
  return v + (150 * Math.sin(x / 12 * pi) + 300 * Math.sin(x / 30 * pi)) * 2 / 3;
}
function outsideChina(lat, lng) {
  return lng < 72.004 || lng > 137.8347 || lat < .8293 || lat > 55.8271;
}
export function wgsToGcj({ lat, lng }) {
  if (outsideChina(lat, lng)) return { lat, lng };
  let dLat = transformLat(lng - 105, lat - 35);
  let dLng = transformLng(lng - 105, lat - 35);
  const radLat = lat / 180 * pi;
  let magic = 1 - ee * Math.sin(radLat) ** 2;
  const sqrtMagic = Math.sqrt(magic);
  dLat = dLat * 180 / ((a * (1 - ee)) / (magic * sqrtMagic) * pi);
  dLng = dLng * 180 / (a / sqrtMagic * Math.cos(radLat) * pi);
  return { lat: lat + dLat, lng: lng + dLng };
}
export function distanceM(p1, p2) {
  const r = 6371000;
  const dLat = (p2.lat - p1.lat) * pi / 180;
  const dLng = (p2.lng - p1.lng) * pi / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(p1.lat * pi / 180) * Math.cos(p2.lat * pi / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
}
export function nearestPlace(position, places, maxAccuracyM = 80) {
  if (!Number.isFinite(position.lat) || !Number.isFinite(position.lng) ||
      !Number.isFinite(position.accuracy) || position.accuracy > maxAccuracyM) return null;
  const gcj = wgsToGcj(position);
  const matches = places.filter(p => p.kind === 'outdoor').map(p => ({
    place: p,
    meters: distanceM(p.gcj ? gcj : position, p.gcj ?? p.wgs)
  })).filter(match => match.meters <= match.place.radiusM);
  return matches.sort((a, b) => a.meters - b.meters)[0] ?? null;
}
