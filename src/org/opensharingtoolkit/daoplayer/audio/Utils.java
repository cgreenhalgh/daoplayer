package org.opensharingtoolkit.daoplayer.audio;

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
}
