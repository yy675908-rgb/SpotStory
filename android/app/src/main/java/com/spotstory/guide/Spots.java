package com.spotstory.guide;

import android.location.Location;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class Spots {
    static final class Artifact {
        final String name, intro;
        Artifact(String name, String intro) { this.name = name; this.intro = intro; }
    }
    static final class Spot {
        final String id, name, area, intro, source;
        final double lat, lng;
        final float radius;
        final boolean gcj;
        final List<Artifact> artifacts;
        Spot(String id, String name, String area, String intro, String source,
             double lat, double lng, float radius, boolean gcj, Artifact... artifacts) {
            this.id = id; this.name = name; this.area = area; this.intro = intro; this.source = source;
            this.lat = lat; this.lng = lng; this.radius = radius; this.gcj = gcj;
            this.artifacts = Arrays.asList(artifacts);
        }
    }

    static final List<Spot> ALL = Collections.unmodifiableList(Arrays.asList(
        new Spot("aiwan", "爱晚亭", "岳麓山",
            "爱晚亭位于岳麓山清风峡。岳麓书院山长罗典于清乾隆五十七年，也就是1792年初建，初名红叶亭。抗日战争期间亭子被毁，1952年重建。匾额上的爱晚亭三字，是毛泽东应湖南大学校长李达邀请题写的。",
            "https://whhlyt.hunan.gov.cn/ggwh/201408/t20140804_5430585.html",
            28.180437, 112.937564, 120, true),
        new Spot("juzizhou", "青年毛泽东艺术雕塑", "橘子洲",
            "这座雕塑位于橘子洲头，以1925年前后的青年毛泽东形象为基础。附近还可以看问天台和沁园春长沙诗词碑。",
            "https://www.hunan.gov.cn/hnszf/jxxx/hslv/c101474/202108/t20210827_20403826.html",
            28.17108, 112.95479, 180, false),
        new Spot("mawangdui", "长沙马王堆汉墓陈列", "湖南博物院 · 馆内手动选择",
            "马王堆汉墓陈列展示西汉生活、简帛文献和丧葬观念。看到具体展品时可以在下方选择；手机定位无法分辨你站在哪个展柜前。",
            "https://whhlyt.hunan.gov.cn/whhlyt/news/mtjj/201712/t20171215_5421815.html",
            0, 0, 0, false,
            new Artifact("T形帛画", "陈列以T形帛画营造序厅氛围，展示汉代关于生命与死后世界的观念。具体图像细节有不同研究解释。"),
            new Artifact("马王堆简帛", "简帛典藏单元呈现出土简牍与帛书，涉及当时的科技成就和思想文献。"),
            new Artifact("辛追墓坑复原", "陈列以一比一比例复原辛追墓坑，帮助观众理解汉代棺椁的陈放方式与葬制。"))
    ));

    static Spot nearest(Location location) {
        if (!location.hasAccuracy() || location.getAccuracy() > 80) return null;
        double[] gcj = Geo.wgsToGcj(location.getLatitude(), location.getLongitude());
        Spot closest = null;
        double best = Double.MAX_VALUE;
        for (Spot spot : ALL) {
            if (spot.radius <= 0) continue;
            double lat = spot.gcj ? gcj[0] : location.getLatitude();
            double lng = spot.gcj ? gcj[1] : location.getLongitude();
            double meters = Geo.distance(lat, lng, spot.lat, spot.lng);
            if (meters <= spot.radius && meters < best) { closest = spot; best = meters; }
        }
        return closest;
    }

    static String answer(Spot spot, String question) {
        if (spot.id.equals("aiwan")) {
            if (question.contains("谁建") || question.contains("谁修") || question.contains("哪年") || question.contains("什么时候建"))
                return "岳麓书院山长罗典在1792年初建，1952年重建。";
            if (question.contains("题字") || question.contains("匾额"))
                return "现在匾额上的字是毛泽东应湖南大学校长李达邀请题写的。";
        }
        if (spot.id.equals("juzizhou") && (question.contains("哪年") || question.contains("什么形象") || question.contains("谁")))
            return "雕塑以1925年前后的青年毛泽东形象为基础。";
        if (spot.id.equals("mawangdui") && (question.contains("帛") || question.contains("墓坑")))
            return question.contains("墓坑") ? spot.artifacts.get(2).intro : spot.artifacts.get(0).intro;
        return "这处目前只有已核对的简短讲解，资料不足以回答这个问题。";
    }

    private Spots() {}
}
