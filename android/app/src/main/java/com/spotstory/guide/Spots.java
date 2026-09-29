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
        final String id, name, area, intro, story, source;
        final double lat, lng;
        final float radius;
        final boolean gcj;
        final List<Artifact> artifacts;
        Spot(String id, String name, String area, String intro, String story, String source,
             double lat, double lng, float radius, boolean gcj, Artifact... artifacts) {
            this.id = id; this.name = name; this.area = area; this.intro = intro; this.story = story; this.source = source;
            this.lat = lat; this.lng = lng; this.radius = radius; this.gcj = gcj;
            this.artifacts = Arrays.asList(artifacts);
        }
    }

    static final List<Spot> ALL = Collections.unmodifiableList(Arrays.asList(
        new Spot("aiwan", "爱晚亭", "岳麓山",
            "爱晚亭位于岳麓山清风峡。岳麓书院山长罗典于清乾隆五十七年，也就是1792年初建，初名红叶亭。抗日战争期间亭子被毁，1952年重建。匾额上的爱晚亭三字，是毛泽东应湖南大学校长李达邀请题写的。",
            "先看看亭顶的绿琉璃瓦，再看四角向上挑起的屋檐。这里最初并不叫爱晚亭。1792年，岳麓书院山长罗典建亭，取名红叶亭，也称爱枫亭。后来毕沅借杜牧《山行》中写晚秋枫林的诗意，改为爱晚亭。一个名字，把眼前山色和唐诗连在了一起。\n\n这座亭又见证了另一段长沙历史。1913年至1918年，青年毛泽东在长沙求学时，曾与蔡和森等人来这里聚会、讨论时局。眼前所见并非清代原亭：它在抗日战争期间被毁，于1952年重建。今天匾额上的三个字，是毛泽东应湖南大学校长李达之邀题写的。\n\n走近一点，还能看到花岗岩立柱和亭内的彩绘藻井。它的价值不只是景色好看：同一处山亭，串起了书院文化、古诗中的秋色，以及近代青年人的思想活动。",
            "https://whhlyt.hunan.gov.cn/ggwh/201408/t20140804_5430585.html",
            28.180437, 112.937564, 120, true),
        new Spot("juzizhou", "青年毛泽东艺术雕塑", "橘子洲",
            "这座雕塑位于橘子洲头，以1925年前后的青年毛泽东形象为基础。附近还可以看问天台和沁园春长沙诗词碑。",
            "先别急着只看雕塑。转过身去，看看湘江怎样把橘子洲与两岸分开。橘子洲是一条由江水和泥沙形成的长洲；洲头的青年毛泽东艺术雕塑，让一段求学与思考的历史有了可见的形象。\n\n1911年至1923年在长沙求学和生活期间，毛泽东曾与蔡和森等朋友登洲、畅游湘江，讨论国家前途。1925年，他重游橘子洲，写下《沁园春·长沙》。词中眼前的秋景与对青年时代的回忆连在一起，也提出了谁来主宰国家命运的问题。\n\n往附近走，还能看到诗词碑，上面刻的是他1961年手书的这首词。站在雕塑前，值得想的或许是：同一条江、同一片山洲，怎样成为一代青年讨论未来的现场。这里的故事也因此超出了一个拍照地标。",
            "https://www.hunan.gov.cn/hnszf/jxxx/hslv/c101474/202108/t20210827_20403826.html",
            28.17108, 112.95479, 180, false),
        new Spot("mawangdui", "长沙马王堆汉墓陈列", "湖南博物院 · 馆内手动选择",
            "马王堆汉墓陈列展示西汉生活、简帛文献和丧葬观念。看到具体展品时可以在下方选择；手机定位无法分辨你站在哪个展柜前。",
            "马王堆汉墓陈列讲的是西汉初年轪侯利苍一家的生活，也讲他们对死亡之后世界的想象。你可以把参观当成走进两个空间：一边是他们生前怎样吃饭、穿衣、阅读；另一边是墓葬怎样安放这些生活用品和观念。\n\n看漆器与丝织品时，留意器物的制作和使用方式；走到简帛展区，注意天文、地理、医学与思想文献并列出现。这些文献让我们看到的不只是贵族财物，还有两千多年前的人怎样认识身体和世界。\n\nT形帛画、层层棺椁与墓坑展示则把问题转向死后：当时人如何理解天、地与生命的延续。博物院以墓主一家为线索组织展览，文物的历史价值也在这里：它们把抽象的汉代，变成有饮食、书写、手艺和信念的具体生活。看到哪件展品，再手动点下方单独听；手机定位不能判断你站在哪个展柜前。",
            "https://whhlyt.hunan.gov.cn/whhlyt/news/mtjj/201712/t20171215_5421815.html",
            0, 0, 0, false,
            new Artifact("T形帛画", "先看画面如何分层，再想它为什么被放在墓葬里。陈列用T形帛画开启关于生命与死后世界的叙事；图像的具体含义仍有不同学术解释，不宜把某一种说法当作唯一答案。"),
            new Artifact("马王堆简帛", "这些简牍与帛书涉及天文地理、医学养生和历史哲学。它们珍贵，不只因为年代久远，还因为文字留下了当时人思考身体与世界的直接线索。"),
            new Artifact("辛追墓坑复原", "抬头看墓坑的纵深，再看棺椁层层安放的关系。展陈依考古资料按原墓坑大小和形制复原，让人理解汉代的墓葬结构与葬制，而不只是看一件孤立的文物。"))
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
