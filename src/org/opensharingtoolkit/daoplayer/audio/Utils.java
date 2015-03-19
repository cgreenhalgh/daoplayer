package org.opensharingtoolkit.daoplayer.audio;

import android.util.Log;

public class Utils {
	public static double distance(double lat1, double lon1, double lat2, double lon2) {
		double R = 6371000.0; // m
		double r1 = lat1*Math.PI/180.0;
		double r2 = lat2*Math.PI/180.0;
		double dr = (lat2-lat1)*Math.PI/180.0;
		double dl = (lon2-lon1)*Math.PI/180.0;
		double a = Math.sin(dr/2) * Math.sin(dr/2) + Math.cos(r1) * Math.cos(r2) * Math.sin(dl/2) * Math.sin(dl/2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
		//Log.d(TAG,"distance "+lat1+","+lon1+" - "+lat2+","+lon2+", R*c="+R*c);
		return R * c;
	}
	// http://wiki.openstreetmap.org/wiki/Mercator#Java_Implementation
    final private static double R_MAJOR = 6378137.0;
    final private static double R_MINOR = 6356752.3142;
    final private static double RATIO = R_MINOR / R_MAJOR;
    final private static double ECCENT = Math.sqrt(1.0 - (RATIO * RATIO));
    final private static double COM = 0.5 * ECCENT;
    
    public static double[] merc(double x, double y) {
        return new double[] {mercX(x), mercY(y)};
    }
 
    public static double  mercX(double lon) {
        return R_MAJOR * Math.toRadians(lon);
    }
 
    public static double mercY(double lat) {
        if (lat > 89.5) {
            lat = 89.5;
        }
        if (lat < -89.5) {
            lat = -89.5;
        }
        double temp = R_MINOR / R_MAJOR;
        double es = 1.0 - (temp * temp);
        double eccent = Math.sqrt(es);
        double phi = Math.toRadians(lat);
        double sinphi = Math.sin(phi);
        double con = eccent * sinphi;
        double com = 0.5 * eccent;
        con = Math.pow(((1.0-con)/(1.0+con)), com);
        double ts = Math.tan(0.5 * ((Math.PI*0.5) - phi))/con;
        double y = 0 - R_MAJOR * Math.log(ts);
        return y;
    }
    // cludgy way to check how big a metre is in merc x,y at a lat,lng
    public static double mercMetre(double lat, double lon) {
        double angle = 360*1/(2*Math.PI*R_MAJOR);
        if (lat > 89.5 || lat < -89.5)
            return Double.NaN;
        if (lat < 0)
            angle = -angle;
        // straight-line approx. on surface of elipsoid 
        double dsx = R_MAJOR*(Math.sin(Math.toRadians(lat+angle))-Math.sin(Math.toRadians(lat)));
        double dsy = R_MINOR*(Math.cos(Math.toRadians(lat+angle))-Math.cos(Math.toRadians(lat)));
        double d = Math.sqrt(dsx*dsx+dsy*dsy);
        double dmy = mercY(lat+angle)-mercY(lat);
        if (dmy<0)
            dmy = -dmy;
        Log.d("utils", "at "+lat+","+lon+" angle "+angle+" is delta "+dsx+","+dsy+" ("+d+"m), while mercator dY="+dmy);
        return dmy/d;
    }
    public static double mercLon (double x) {
        return Math.toDegrees(x) / R_MAJOR;
    }
 
    public static double mercLat (double y) {
        double ts = Math.exp ( -y / R_MAJOR);
        double phi = Math.PI/2 - 2 * Math.atan(ts);
        double dphi = 1.0;
        int i;
        for (i = 0; Math.abs(dphi) > 0.000000001 && i < 15; i++) {
        	double con = ECCENT * Math.sin (phi);
        	dphi = Math.PI/2 - 2 * Math.atan (ts * Math.pow((1.0 - con) / (1.0 + con), COM)) - phi;
        	phi += dphi;
        }
        return Math.toDegrees(phi);
    }
}
