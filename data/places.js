// GPS 只负责识别室外景点；展厅内请手动选文物，不伪称室内精准定位。
// Amap 坐标是 GCJ-02；浏览器 Geolocation 以 WGS84 处理，算法见 geo.js。
export const places = [
  {
    id: 'aiwan-pavilion',
    name: '爱晚亭',
    area: '岳麓山',
    kind: 'outdoor',
    gcj: { lat: 28.180437, lng: 112.937564 },
    radiusM: 120,
    intro: '爱晚亭位于岳麓山清风峡。它由岳麓书院山长罗典于清乾隆五十七年（1792年）初建，初名红叶亭，又名爱枫亭。抗日战争期间亭子被毁，1952年重建。现在匾额上的“爱晚亭”三字，是毛泽东应湖南大学校长李达邀请题写的。',
    sources: [
      { title: '湖南省文化和旅游厅：爱晚亭', url: 'https://whhlyt.hunan.gov.cn/ggwh/201408/t20140804_5430585.html' },
      { title: '高德地图：爱晚亭（坐标）', url: 'https://www.amap.com/place/B02DB06929' }
    ],
    artifacts: []
  },
  {
    id: 'orange-island-statue',
    name: '橘子洲·青年毛泽东艺术雕塑',
    area: '橘子洲',
    kind: 'outdoor',
    // 开放地图的雕塑中心点，只用于到点提示，不能作为精确导航点。
    wgs: { lat: 28.17108, lng: 112.95479 },
    radiusM: 180,
    approximate: true,
    intro: '这里是橘子洲头的青年毛泽东艺术雕塑。作品取1925年前后的青年形象。橘子洲还可看《沁园春·长沙》诗词碑和问天台；这首词与毛泽东重游橘子洲的经历相连。',
    sources: [
      { title: '湖南省人民政府：橘子洲风景区', url: 'https://www.hunan.gov.cn/hnszf/jxxx/hslv/c101474/202108/t20210827_20403826.html' },
      { title: '湖南省人民政府台湾事务办公室：橘子洲', url: 'https://www.hnstb.gov.cn/plus/view-548-1.html' },
      { title: 'OpenStreetMap 地点数据：雕塑坐标', url: 'https://mapcarta.com/W1165042478' }
    ],
    artifacts: []
  },
  {
    id: 'mawangdui-exhibition',
    name: '长沙马王堆汉墓陈列',
    area: '湖南博物院',
    kind: 'indoor',
    intro: '这座常设陈列以马王堆汉墓出土文物呈现西汉生活、科技文献和丧葬观念。展陈中有T形帛画、简帛文献及辛追墓坑的复原展示。馆内请在看到具体展品时手动点选讲解，手机定位无法判断你站在哪个展柜前。',
    sources: [
      { title: '湖南省文化和旅游厅：长沙马王堆汉墓陈列', url: 'https://whhlyt.hunan.gov.cn/whhlyt/news/mtjj/201712/t20171215_5421815.html' },
      { title: '湖南省人民政府：长沙马王堆汉墓陈列', url: 'https://www.hunan.gov.cn/topic/ashunan/112/118/content_8229.html' }
    ],
    artifacts: [
      {
        id: 't-banner',
        name: 'T形帛画',
        intro: '陈列以T形帛画营造序厅氛围，把汉代关于生命与死后世界的观念带进展览。这是理解整个马王堆陈列的入口；具体图像的含义存在研究解释，不能只凭一张照片认定某个细节。'
      },
      {
        id: 'manuscripts',
        name: '马王堆简帛',
        intro: '“简帛典藏”单元展示出土简牍与帛书，涵盖当时的知识与思想文献，也让人看到轪侯家族的阅读和知识生活。'
      },
      {
        id: 'xinzhui-tomb',
        name: '辛追墓坑复原',
        intro: '陈列以一比一比例复原辛追墓坑，并以影像呈现棺椁的陈放方式。它帮助观众理解汉代葬制与当时对死后世界的想象。'
      }
    ]
  }
];

export function findPlace(id) {
  return places.find(place => place.id === id);
}
