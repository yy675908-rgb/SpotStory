const aliases = {
  'aiwan-pavilion': ['爱晚亭'],
  'orange-island-statue': ['橘子洲', '青年毛泽东艺术雕塑'],
  'wuyi-square': ['五一广场'],
  'taiping-street': ['太平街', '太平老街'],
  'jiayi-well': ['贾谊故居', '长怀井'],
  huogongdian: ['火宫殿', '坡子街'],
  'huangxing-street': ['黄兴路', '黄兴南路'],
  'dufu-pavilion': ['杜甫江阁']
};

export function answerOffline(question, current, places) {
  const q = question.trim().replace(/\s+/g, '');
  const mentioned = places.find(place => (aliases[place.id] ?? [place.name]).some(name => q.includes(name)));
  if (/吃|餐馆|饭店|美食|夜景|演出|花鼓戏/.test(q)) {
    if (/吃|餐馆|饭店|美食/.test(q)) return { action: 'food', text: '已展开附近餐厅；点定位图标在美团查找。' };
    const keyword = /演出|花鼓戏/.test(q) ? '演出' : '夜景';
    return { action: 'search', keyword, text: `已打开高德搜索“${keyword}”，请核对当天营业或演出信息。` };
  }
  if (/附近|周围|前面有什么/.test(q)) return { action: 'nearby', text: '正在根据你的位置查已收录的讲解点；距离仅供参考。' };
  if (/怎么走|我要去|带我去|去哪里|路线|导航/.test(q)) {
    const marker = Math.max(q.lastIndexOf('去'), q.lastIndexOf('到'));
    const targetText = marker >= 0 ? q.slice(marker + 1) : q;
    const destination = places.find(place => place.kind === 'outdoor' &&
      (aliases[place.id] ?? [place.name]).some(name => targetText.includes(name)));
    if (destination) return { action: 'route', destination, text: `已打开去“${destination.name}”的步行路线。` };
    if (/怎么走|去这里|到这里/.test(q) && current.kind === 'outdoor')
      return { action: 'route', destination: current, text: `已打开去“${current.name}”的步行路线。` };
    return { text: '请说出目的地名称，例如“我要去杜甫江阁”。' };
  }
  if (/在哪|哪里|怎么找|看什么|什么方位|有什么细节/.test(q)) {
    const target = mentioned ?? (/这里|这儿|这处|当前|眼前|现场|这个/.test(q) ? current : null);
    return { text: target ? target.lookFor || '这处没有核实到更细的位置，请看现场标识。' : '请先选中想看的景点，或说出它的名称。' };
  }
  if (/历史|典故|故事|价值|为什么|介绍/.test(q)) {
    const target = mentioned ?? current;
    return { text: target.story ?? target.intro };
  }
  return { text: '这道题暂无核实过的答案。可听完整故事，或打开下方资料来源。' };
}
