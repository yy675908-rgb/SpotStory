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
        final String id, name, area, intro, story, source, lookFor;
        final double lat, lng;
        final float radius;
        final boolean gcj;
        final List<Artifact> artifacts;
        Spot(String id, String name, String area, String intro, String story, String source,
             double lat, double lng, float radius, boolean gcj, Artifact... artifacts) {
            this(id, name, area, intro, story, source, lat, lng, radius, gcj, "", artifacts);
        }
        Spot(String id, String name, String area, String intro, String story, String source,
             double lat, double lng, float radius, boolean gcj, String lookFor, Artifact... artifacts) {
            this.id = id; this.name = name; this.area = area; this.intro = intro; this.story = story; this.source = source;
            this.lookFor = lookFor;
            this.lat = lat; this.lng = lng; this.radius = radius; this.gcj = gcj;
            this.artifacts = Arrays.asList(artifacts);
        }
    }

    static final List<Spot> ALL = Collections.unmodifiableList(Arrays.asList(
        new Spot("aiwan", "爱晚亭", "岳麓山",
            "爱晚亭位于岳麓山清风峡。岳麓书院山长罗典于清乾隆五十七年，也就是1792年初建，初名红叶亭。抗日战争期间亭子被毁，1952年重建。匾额上的爱晚亭三字，是毛泽东应湖南大学校长李达邀请题写的。",
            "先看看亭顶的绿琉璃瓦，再看四角向上挑起的屋檐。这里最初并不叫爱晚亭。1792年，岳麓书院山长罗典建亭，取名红叶亭，也称爱枫亭。后来毕沅借杜牧《山行》中写晚秋枫林的诗意，改为爱晚亭。一个名字，把眼前山色和唐诗连在了一起。\n\n这座亭又见证了另一段长沙历史。1913年至1918年，青年毛泽东在长沙求学时，曾与蔡和森等人来这里聚会、讨论时局。眼前所见并非清代原亭：它在抗日战争期间被毁，于1952年重建。今天匾额上的三个字，是毛泽东应湖南大学校长李达之邀题写的。\n\n走近一点，还能看到花岗岩立柱和亭内的彩绘藻井。它的价值不只是景色好看：同一处山亭，串起了书院文化、古诗中的秋色，以及近代青年人的思想活动。",
            "https://whhlyt.hunan.gov.cn/ggwh/201408/t20140804_5430585.html",
            28.180437, 112.937564, 120, true,
            "站在亭外先看四角上翘的绿色琉璃瓦；入亭后抬头找荷花纹彩绘藻井，再看匾额的‘爱晚亭’三字。"),
        new Spot("juzizhou", "青年毛泽东艺术雕塑", "橘子洲",
            "这座雕塑位于橘子洲头，以1925年前后的青年毛泽东形象为基础。附近还可以看问天台和沁园春长沙诗词碑。",
            "先别急着只看雕塑。转过身去，看看湘江怎样把橘子洲与两岸分开。橘子洲是一条由江水和泥沙形成的长洲；洲头的青年毛泽东艺术雕塑，让一段求学与思考的历史有了可见的形象。\n\n1911年至1923年在长沙求学和生活期间，毛泽东曾与蔡和森等朋友登洲、畅游湘江，讨论国家前途。1925年，他重游橘子洲，写下《沁园春·长沙》。词中眼前的秋景与对青年时代的回忆连在一起，也提出了谁来主宰国家命运的问题。\n\n往附近走，还能看到诗词碑，上面刻的是他1961年手书的这首词。站在雕塑前，值得想的或许是：同一条江、同一片山洲，怎样成为一代青年讨论未来的现场。这里的故事也因此超出了一个拍照地标。",
            "https://www.hunan.gov.cn/hnszf/jxxx/hslv/c101474/202108/t20210827_20403826.html",
            28.17108, 112.95479, 180, false,
            "在洲头看雕塑，随后找附近的《沁园春·长沙》诗词碑；碑上是1961年的手书。"),
        new Spot("wuyi", "五一广场", "五一商圈 · 城市中心",
            "这里不仅是逛街的起点。五一广场一带也是长沙古城长期的中心，2019年被确定为长沙城市原点的选址。",
            "站在五一广场，先辨认黄兴路与五一大道的交会。今天周围是商场和地铁，旧城的道路却仍在脚下延伸。长沙市曾把这一带作为城市原点的选址：它处在城市核心，附近还能接到太平街、贾谊故居等历史街巷。\n\n从这里向西去太平街，可以从现代商圈走进保留街巷格局的老街；向南是黄兴路步行街、坡子街和火宫殿。五一广场值得停一停，因为它把一座城市不同年代的生活叠在了一起。",
            "https://whhlyt.hunan.gov.cn/news/mtjj/201912/t20191227_11018568.html",
            28.196500, 112.977340, 125, true,
            "先找黄兴路与五一大道的交会，再沿街口标识选太平街或黄兴路方向；这里不是室内地铁站定位点。"),
        new Spot("taiping", "太平老街", "五一商圈 · 老街",
            "太平街保留了长沙古城的街巷格局。沿麻石路走，留意两侧的小青瓦、封火墙与老式门窗。",
            "走进太平街，脚下的麻石路比商店招牌更值得看。两侧的小青瓦、坡屋顶、封火墙和木门窗，留下的是长沙旧街巷的尺度。这里的历史并不只属于某一座房子：贾谊故居、长怀井、明吉藩王府西牌楼旧址和近代遗迹分布在街区里。\n\n不要急着把整条街走完。先看两侧屋顶与墙线，再去找贾谊故居祠前巷侧的长怀井。热闹的商业街背后，还有西汉人物、唐代诗人的记忆。",
            "https://whhlyt.hunan.gov.cn/whhlyt/news/sxxw/201909/t20190910_5475088.html",
            28.193573, 112.972071, 100, true,
            "沿麻石路找两侧封火墙和小青瓦；要找长怀井，请走到贾谊故居祠前的巷侧，不要把沿街装饰当古迹。"),
        new Spot("jiayi", "贾谊故居与长怀井", "太平街 · 外观讲解",
            "贾谊在西汉曾任长沙王太傅。故居祠前巷侧的长怀井，是这段记忆中可以寻找的实物线索；入内是否开放请看现场公告。",
            "到了太平街，不妨先找祠前巷侧的井，而不是只看故居大门。长怀井传为贾谊所凿，井口收窄、腹部较宽，形状像壶。唐代杜甫写到贾傅井仍在，后人由此称它长怀井。\n\n贾谊从朝廷来到长沙，留下的不是一段简单的失意故事。他的政论和文学使后人不断回到这条小巷凭吊。故居建筑历经重建，不能把今天所见的每一面墙都说成西汉原物。井与街巷的位置关系，反而帮助你把两千多年的记忆落到眼前。入内是否开放请看现场公告。",
            "https://whhlyt.hunan.gov.cn/whhlyt/news/sxxw/202312/t20231212_32476475.html",
            28.192634, 112.972067, 80, true,
            "到祠前的太傅里小巷一侧找井口较窄的两眼井；不要误认成故居院内的装饰。入内以现场公告为准。"),
        new Spot("huogong", "火宫殿 · 坡子街", "五一商圈 · 民俗与湘菜",
            "火宫殿曾是祭祀火神的庙宇，后来形成看戏、听书与饮食交织的庙市。这里可以了解长沙饮食文化，也有湘菜可选择。",
            "现在看火宫殿，很容易把它只当餐馆。它原是祭祀火神的乾元宫，始建于清乾隆年间。晚清时期，庙会吸引看戏、听书、观艺和吃小吃的人；民国时摊担、棚伞聚成热闹市面。\n\n站在坡子街上，可以想象这座场所怎样从庙宇扩展为市井公共空间。要吃饭可在这里看湘菜菜单，也可沿坡子街挑别的正餐；餐饮口味、排队和营业情况请以当天为准。",
            "https://whhlyt.hunan.gov.cn/whhlyt/news/sxxw/201909/t20190910_5475088.html",
            28.190555, 112.973763, 85, true,
            "到坡子街127号先看火宫殿的入口与院落；再留意它作为庙市的戏台空间。不要把餐馆装修都当成清代遗存。"),
        new Spot("huangxing", "黄兴南路步行街", "五一商圈 · 夜逛",
            "黄兴南路步行街连接五一商圈与南门口一带，适合逛街看夜景；这条路较长，地图点位在南门口附近。",
            "沿黄兴南路步行街往南走，城市的热闹会从五一商圈延伸到南门口。它更适合慢慢逛，而不是把某一处当唯一目标。沿街可随时转往坡子街或湘江边。\n\n如果想找湘菜正餐、演出或夜景，先用下方的附近搜索看当天仍营业的地点。步行街上的店铺变化快，讲解不会把一家店的旧口碑当成现在的保证。",
            "https://whhlyt.hunan.gov.cn/whhlyt/news/mtjj/202111/t20211101_20954579.html",
            28.184013, 112.975908, 100, true,
            "这条街很长，当前定位点在南门口一带；从五一广场出发请先看黄兴路沿线的路牌，打开步行地图选择具体入口。"),
        new Spot("dufu", "杜甫江阁", "湘江东岸 · 夜景",
            "杜甫江阁是为纪念杜甫而修建的仿唐建筑，临湘江而立。它不是杜甫居住至今的唐代原阁。",
            "来到湘江东岸，先看江阁的飞檐和斗拱，再看它与江对岸橘子洲、岳麓山的关系。杜甫晚年留在湖湘，长沙修建此阁以纪念他。今天这座阁是后世的纪念性建筑，不是唐代原物。\n\n设计上，它的东西两面都以正面朝向观者：从江对岸看、从城里走来，都能看到完整立面。晚上可在江边看亮灯的楼阁与江景；要登阁则以现场开放和票务为准。",
            "https://whhlyt.hunan.gov.cn/whhlyt/news/sxxw/201909/t20190910_5475088.html",
            28.184442, 112.968624, 110, true,
            "在湘江东岸先找主阁与两侧长廊，再抬头看飞檐斗拱；转向江面可望橘子洲和岳麓山。"),
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
        Spot mentioned = mentioned(question);
        if (mentioned != null && mentioned != spot) return answer(mentioned, question);
        if (spot.id.equals("aiwan")) {
            if (question.contains("为什么叫") || question.contains("名字") || question.contains("典故") || question.contains("谁取名"))
                return "它起初叫红叶亭，也叫爱枫亭。后来毕沅借杜牧《山行》的晚秋枫林诗意，改名爱晚亭。";
            if (question.contains("谁建") || question.contains("谁修") || question.contains("哪年") || question.contains("什么时候建"))
                return "岳麓书院山长罗典在1792年初建，1952年重建。";
            if (question.contains("题字") || question.contains("匾额"))
                return "现在匾额上的字是毛泽东应湖南大学校长李达邀请题写的。";
            if (question.contains("历史") || question.contains("价值") || question.contains("毛泽东") || question.contains("蔡和森"))
                return "1913年至1918年，毛泽东在长沙求学时曾与蔡和森等人在这里讨论时局；这座亭也联系着书院文化与唐诗意象。";
        }
        if (spot.id.equals("juzizhou") && (question.contains("诗") || question.contains("典故") || question.contains("历史") || question.contains("价值")))
            return "1925年毛泽东重游橘子洲，写下《沁园春·长沙》。附近的诗词碑刻有他1961年手书的这首词。";
        if (spot.id.equals("juzizhou") && (question.contains("哪年") || question.contains("什么形象") || question.contains("谁")))
            return "雕塑以1925年前后的青年毛泽东形象为基础。";
        if (spot.id.equals("mawangdui") && (question.contains("帛") || question.contains("墓坑")))
            return question.contains("墓坑") ? spot.artifacts.get(2).intro : spot.artifacts.get(0).intro;
        if (spot.id.equals("mawangdui") && (question.contains("价值") || question.contains("历史") || question.contains("简") || question.contains("医学")))
            return "马王堆出土的漆器、丝织品和简帛，把西汉初年的生活技艺、知识与思想留在具体文物里。展览还以墓葬结构呈现当时的生命观。";
        if (question.contains("在哪") || question.contains("找") || question.contains("哪里看") || question.contains("看什么")) {
            if (mentioned == null && !question.contains("这里") && !question.contains("这儿") &&
                !question.contains("这处") && !question.contains("现场") && !question.contains("眼前"))
                return "请先选中想看的景点，或说出它的名称。";
            return spot.lookFor.isEmpty() ? "这里没有核实到更细的位置，请看现场标识。" : spot.lookFor;
        }
        if (question.contains("历史") || question.contains("典故") || question.contains("故事") || question.contains("价值") || question.contains("为什么"))
            return spot.story;
        return "这个问题暂时没有核实过的答案。你可以听完整故事，或点开资料来源继续看。";
    }

    private static Spot mentioned(String question) {
        for (Spot spot : ALL) {
            String[] aliases;
            switch (spot.id) {
                case "juzizhou": aliases = new String[]{"橘子洲", "青年毛泽东艺术雕塑"}; break;
                case "taiping": aliases = new String[]{"太平街", "太平老街"}; break;
                case "jiayi": aliases = new String[]{"长怀井", "贾谊故居"}; break;
                case "huogong": aliases = new String[]{"火宫殿", "坡子街"}; break;
                case "huangxing": aliases = new String[]{"黄兴路", "黄兴南路"}; break;
                default: aliases = new String[]{spot.name.split(" · ")[0]};
            }
            for (String name : aliases) if (question.contains(name)) return spot;
        }
        return null;
    }

    private Spots() {}
}
