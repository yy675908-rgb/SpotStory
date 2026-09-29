package com.spotstory.guide;

final class Geo {
    private static final double A = 6378245.0, EE = 0.006693421622965943;
    static double[] wgsToGcj(double lat, double lng) {
        if (lng < 72.004 || lng > 137.8347 || lat < .8293 || lat > 55.8271) return new double[]{lat, lng};
        double x = lng - 105, y = lat - 35;
        double dLat = -100 + 2*x + 3*y + .2*y*y + .1*x*y + .2*Math.sqrt(Math.abs(x));
        dLat += (20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;
        dLat += (20*Math.sin(y*Math.PI)+40*Math.sin(y/3*Math.PI))*2/3;
        dLat += (160*Math.sin(y/12*Math.PI)+320*Math.sin(y*Math.PI/30))*2/3;
        double dLng = 300 + x + 2*y + .1*x*x + .1*x*y + .1*Math.sqrt(Math.abs(x));
        dLng += (20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;
        dLng += (20*Math.sin(x*Math.PI)+40*Math.sin(x/3*Math.PI))*2/3;
        dLng += (150*Math.sin(x/12*Math.PI)+300*Math.sin(x/30*Math.PI))*2/3;
        double rad = lat / 180 * Math.PI, magic = 1 - EE*Math.pow(Math.sin(rad),2), sqrt = Math.sqrt(magic);
        dLat = dLat*180 / ((A*(1-EE))/(magic*sqrt)*Math.PI);
        dLng = dLng*180 / (A/sqrt*Math.cos(rad)*Math.PI);
        return new double[]{lat+dLat, lng+dLng};
    }
    static double distance(double lat1, double lng1, double lat2, double lng2) {
        double rad = Math.PI/180, a = Math.pow(Math.sin((lat2-lat1)*rad/2),2) +
            Math.cos(lat1*rad)*Math.cos(lat2*rad)*Math.pow(Math.sin((lng2-lng1)*rad/2),2);
        return 2*6371000*Math.asin(Math.min(1, Math.sqrt(a)));
    }
    private Geo() {}
}
