package com.spotstory.guide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Restaurants {
    static final class Restaurant {
        final String name, kind, poi;
        final double lat, lng; // 高德 GCJ-02
        Restaurant(String name, String kind, String poi, double lat, double lng) {
            this.name = name; this.kind = kind; this.poi = poi;
            this.lat = lat; this.lng = lng;
        }
    }

    static final List<Restaurant> ALL = Arrays.asList(
        new Restaurant("火宫殿（坡子街总店）", "长沙小吃 · 可选不辣", "B02DB02DH4", 28.190555, 112.973763),
        new Restaurant("公交新村粉店（坡子街店）", "长沙米粉 · 请说不放辣", "B0HAA545PP", 28.190523, 112.970916),
        new Restaurant("宴长沙（五一广场店）", "湘菜 · 有辣菜，点单确认", "B0G10UEAPG", 28.193257, 112.984519),
        new Restaurant("COMMUNE幻师（解放西路店）", "西餐 / 披萨 · 点单确认", "B0FFMDA0WK", 28.191426, 112.973049),
        new Restaurant("正粤粥铺（工大店）", "粥 / 简餐 · 点单确认", "B02DB05LM8", 28.165456, 112.937158),
        new Restaurant("老欧洲咖啡·西餐（后湖旗舰店）", "西餐 · 点单确认", "B0G2MX2L6E", 28.157971, 112.945002)
    );

    static List<Restaurant> near(double lat, double lng) {
        List<Restaurant> list = new ArrayList<>();
        for (Restaurant item : ALL) if (Geo.distance(lat, lng, item.lat, item.lng) <= 3500) list.add(item);
        list.sort((a, b) -> Double.compare(Geo.distance(lat, lng, a.lat, a.lng), Geo.distance(lat, lng, b.lat, b.lng)));
        return list;
    }
    private Restaurants() { }
}
