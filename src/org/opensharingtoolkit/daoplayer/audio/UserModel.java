/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;

import org.ejml.data.DenseMatrix64F;
import org.opensharingtoolkit.daoplayer.audio.Context.Route;
import org.opensharingtoolkit.daoplayer.audio.Context.Waypoint;

import android.util.Log;

/** Model of user, e.g. activity classification.
 * 
 * @author pszcmg
 *
 */
public class UserModel {
	static class Location {
		private double lat;
		private double lng;
		private double accuracy;
		private long time;
		private double x, y;
		private long elapsedtime;
		
		private double distance1;
		//private double distanceN;
		private long elapsed1;
		//private long elapsedN;
		/**
		 * @param lat
		 * @param lng
		 * @param accuracy
		 * @param time
		 * @param elapsedtime 
		 */
		public Location(double lat, double lng, double accuracy, long time, long elapsedtime) {
			super();
			this.lat = lat;
			this.lng = lng;
			this.accuracy = accuracy;
			this.time = time;
			this.elapsedtime = elapsedtime;
		}
		
	}
	private static final int MAX_LOCATION_HISTORY_SIZE = 100;
	private static final long MAX_GPS_INTERVAL = 2000;
	private static final double MAX_STATIONARY_SPEED = 0.3; // m/s
	private static final double MIN_WALKING_SPEED = 0.9; // m/s
	private static final double DEFAULT_WALKING_SPEED = 1.5; // m/s
	/** work in progress. Not sure if this is useful. Depends a lot on the uncertainty
	 * in the kalman model, but also on the gps accuracy. With acceleration 2m/s in model
	 * I see 2.7 as very good (with <4m accuracy), often 3.7 (with 10m accuracy),
	 * 4.3 (w 15m), 5 (w 25m) (w 0.3m drift).
	 * Even w 10m accuracy (3.7-4 speed acc) i often see 0.3m/s+ drift, up 1.1m/s
	 */
	private static final double MAX_SPEED_INACCURACY = 6; 
	private static final String TAG = "usermodel";
	private static final int MIN_UPDATE_INTERVAL = 900;
	/* 50 m ?! */
	private static final double MAX_REQUIRED_ACCURACY = 50;
	
	public static enum Activity { NOGPS, STATIONARY, WALKING, UNCERTAIN };

	//private Location mLastLocation;
	private Vector<Location> mLocations = new Vector<Location>();
	private Context mContext;
	// cache...
	private Map<String,String> mLocalWaypoints; 
	private Context.Waypoint mLastWaypoint;
	private long mLastWaypointCheckTime, mLastWaypointNearTime, mLastWaypointNotNearTime;
	HashMap<String,WaypointInfo> mWaypointInfos = new HashMap<String,WaypointInfo>();
	private Activity mEstimatedActivity = Activity.NOGPS;
	private double mEstimatedWalkingSpeed = DEFAULT_WALKING_SPEED;
	private double mEstimatedCurrentSpeed = 0;
	private long mLastElapsedtime;
	private KalmanFilter mKalmanFilter = new KalmanFilter();
	private double mEstimatedX, mEstimatedY;
	// :-)
	private double mEstimatedAccuracy = 100000000;
	private double mEstimatedSpeedAccuracy = 100;
	private double mEstimatedLat, mEstimatedLng;
	private double mEstimatedXSpeed, mEstimatedYSpeed;
	private int mUpdateNoLocationCount;
	private Map<String, RouteInfo> mRouteInfos = new HashMap<String,RouteInfo>();
	
	public void setLocation(double lat, double lng, double accuracy, long time, long elapsedtime) {
		mKalmanFilter.predict();

		Location loc = new Location(lat, lng, accuracy, time, elapsedtime);
		if (mContext!=null) {
			loc.x = mContext.lng2x(lng);
			loc.y = mContext.lat2y(lat);
			mKalmanFilter.update(loc.x, loc.y, accuracy);
		}
		else
			Log.e(TAG,"cannot map location to x,y - no user context");		
		
		if (mLocations.size()>0) {
			Location lastLoc = mLocations.get(0);
			loc.distance1 = Utils.distance(lat, lng, lastLoc.lat, lastLoc.lng);
			loc.elapsed1 = elapsedtime-lastLoc.elapsedtime;
		}
		mLocations.add(0, loc);
		if (mLocations.size()>MAX_LOCATION_HISTORY_SIZE)
			mLocations.remove(mLocations.size()-1);

		mUpdateNoLocationCount = 0;
		updateEstimates(time, elapsedtime);
	}
	private void updateEstimates(long time, long elapsedtime) {
		mLastElapsedtime = elapsedtime;
		Log.d(TAG,"updateEstimates("+time+","+elapsedtime+")");
		
		DenseMatrix64F cov = mKalmanFilter.getCovariance();
		DenseMatrix64F state = mKalmanFilter.getState();
		Log.d(TAG,"Kalman: "+state.get(0)+","+state.get(1)+" v="+state.get(2)+","+state.get(3)+", cov="+cov.get(0,0)+","+cov.get(1,1)+","+cov.get(2,2)+","+cov.get(3,3));
		
		// current speed
		mEstimatedXSpeed = state.get(KalmanFilter.STATE_VX);
		mEstimatedYSpeed = state.get(KalmanFilter.STATE_VY);
		mEstimatedCurrentSpeed = Math.sqrt(mEstimatedXSpeed*mEstimatedXSpeed+mEstimatedYSpeed*mEstimatedYSpeed);
		/*
		// TODO better :-) e.g. longer history
		if (mLocations.size()>0) {
			// last known?!
			Location lastLoc = mLocations.get(0);
			if (lastLoc.elapsed1==0 || lastLoc.elapsed1 > MAX_GPS_INTERVAL)
				mEstimatedCurrentSpeed = 0;
			else 
				mEstimatedCurrentSpeed = lastLoc.distance1/lastLoc.elapsed1/1000;		
		}
		*/
		// TODO walking speed
		// activity
		mEstimatedSpeedAccuracy = Math.sqrt(cov.get(2,2)+cov.get(3,3));

		mEstimatedAccuracy = Math.sqrt(cov.get(0,0)+cov.get(1,1));
		mEstimatedX = state.get(0);
		mEstimatedY = state.get(1);
		mEstimatedLat = mContext.y2lat(mEstimatedY);
		mEstimatedLng = mContext.x2lng(mEstimatedX);
		
		if (mContext==null) {
			Log.w(TAG,"updateEstimates with no context");
			mEstimatedActivity = Activity.NOGPS;
		}
		else if (mEstimatedAccuracy > mContext.getRequiredAccuracy() || mEstimatedAccuracy>MAX_REQUIRED_ACCURACY)
			mEstimatedActivity = Activity.NOGPS;
		else if (mEstimatedSpeedAccuracy > MAX_SPEED_INACCURACY)
			mEstimatedActivity = Activity.UNCERTAIN;
		else if (mEstimatedCurrentSpeed<=MAX_STATIONARY_SPEED)
			mEstimatedActivity = Activity.STATIONARY;
		else if (mEstimatedCurrentSpeed<=MIN_WALKING_SPEED)
			mEstimatedActivity = Activity.UNCERTAIN;
		else
			mEstimatedActivity = Activity.WALKING;

		updateWaypointInfos();
		updateRouteInfos();
	}
	/* note elapsedtime should be comparable with gps elapsedTimeNanos mapped to ms */
	public void updateNoLocation(long time, long elapsedtime) {
		if (elapsedtime<mLastElapsedtime+MIN_UPDATE_INTERVAL) {
			Log.d(TAG,"Ignore updateNoLocation("+time+","+elapsedtime+"); last elapsedtime="+mLastElapsedtime);
			return;
		}
		for (long t=mLastElapsedtime+1000; t<elapsedtime; t+=1000) {
			mKalmanFilter.predict();
			mUpdateNoLocationCount++;
		}

		updateEstimates(time, elapsedtime);		
	}
	
	public Activity getActivity() {
		return mEstimatedActivity;
	}
	
	public double getWalkingSpeed() {
		return mEstimatedWalkingSpeed;
	}
	
	public double getCurrentSpeed() {
		return mEstimatedCurrentSpeed;
	}
	public double getX() {
		return mEstimatedX;
	}
	public double getY() {
		return mEstimatedY;
	}
	public double getLat() {
		return mEstimatedLat;
	}
	public double getLng() {
		return mEstimatedLng;
	}
	public double getAccuracy() {
		return mEstimatedAccuracy;
	}
	public double getCurrentSpeedAccuracy() {
		return mEstimatedSpeedAccuracy;
	}
	public synchronized void setContext(Context context) {
		Log.d(TAG,"user setContext");
		mContext = context;
		mLastWaypoint = null;
		mLocalWaypoints = null;
		mWaypointInfos = new HashMap<String,WaypointInfo>();
		Map<String,Waypoint> waypoints = mContext.getWaypoints();
		for (Map.Entry<String, Waypoint> entry : waypoints.entrySet()) {
			mWaypointInfos.put(entry.getKey(), new WaypointInfo(entry.getValue()));				
		}
		Vector<Route> routes = mContext.getRoutes();
		for (Route route : routes) {
			if (route.getName()!=null) {
				if (route.getFromWaypoint()!=null && route.getToWaypoint()!=null)
					mRouteInfos.put(route.getName(), new RouteInfo(route));				
				else
					Log.w(TAG,"Ignore route "+route.getName()+" with missing from/to");
			}
		}
	}

	public synchronized void toJavascript(StringBuilder sb, Map<String,String> localWaypoints, Map<String,String> localRoutes) {
		mLocalWaypoints = localWaypoints;
		Activity activity = getActivity();
		double currentSpeed = getCurrentSpeed();
		double currentSpeedAccuracy = getCurrentSpeedAccuracy();
		double walkingSpeed = getWalkingSpeed();
		// TODO position
		sb.append("var position=");
		if (mContext!=null && mEstimatedAccuracy < mContext.getRequiredAccuracy() && mEstimatedAccuracy < MAX_REQUIRED_ACCURACY) {
			sb.append("{x:");
			sb.append(mEstimatedX);
			sb.append(",y:");
			sb.append(mEstimatedY);
			sb.append(",xspeed:");
			sb.append(mEstimatedXSpeed);
			sb.append(",yspeed:");
			sb.append(mEstimatedYSpeed);
			sb.append(",accuracy:");
			sb.append(mEstimatedAccuracy);
			sb.append(",age:");
			sb.append(mUpdateNoLocationCount);
			sb.append(",lat:");
			sb.append(mEstimatedLat);
			sb.append(",lng:");
			sb.append(mEstimatedLng);
			sb.append("}");
		}
		else
			sb.append("null");
		sb.append(";");
		sb.append("var activity='");
		sb.append(activity.name());
		sb.append("';");
		sb.append("var currentSpeed=");
		sb.append(currentSpeed);
		sb.append(";");
		sb.append("var currentSpeedAccuracy=");
		sb.append(currentSpeedAccuracy);
		sb.append(";");
		sb.append("var walkingSpeed=");
		sb.append(walkingSpeed);
		sb.append(";");
		String lastWaypointName = mLastWaypoint!=null ? mLastWaypoint.getName() : null;
		// update last waypoint 
		if (mLastWaypoint!=null) {
			long now = System.currentTimeMillis();
			long elapsed = now-mLastWaypointCheckTime;
			WaypointInfo wi = mWaypointInfos.get(mLastWaypoint.getName());
			if (wi==null) 
				Log.e(TAG,"could not find last waypoint info "+mLastWaypoint.getName());
			else {
				if (wi.near)
					mLastWaypointNearTime += elapsed;
				else
					mLastWaypointNotNearTime += elapsed;
			}
		}
		// which waypoints? local plus last, or all if no local
		Map<String,WaypointInfo> waypointInfos = null;
		if (mContext!=null && localWaypoints!=null) {
			boolean lastWaypointIsLocal = false;
			waypointInfos = new HashMap<String,WaypointInfo>();
			for (Map.Entry<String,String> entry: localWaypoints.entrySet()) {
				if (mLastWaypoint!=null && mLastWaypoint.getName().equals(entry.getValue())) {
					lastWaypointIsLocal = true;
					lastWaypointName = entry.getKey();
				}
				WaypointInfo wi = mWaypointInfos.get(entry.getValue());
				if (wi!=null)
					waypointInfos.put(entry.getKey(), wi);
				else {
					// route as waypoint?
					Context.Route route = mContext.getRoute(entry.getValue());
					if (route!=null) {
						if (route.getFromWaypoint()!=null && route.getToWaypoint()!=null) {							
							Log.i(TAG,"Create waypoint from route "+route.getName());
							double nearDistance = Utils.distance(route.getFromWaypoint().getLat(), route.getFromWaypoint().getLng(), route.getToWaypoint().getLat(), route.getFromWaypoint().getLng())/2+route.getNearDistance();
							// Note: average lat/lng as centre of line is not correct, but should be OK for a few miles
							Waypoint waypoint = new Waypoint(route.getName(), (route.getFromWaypoint().getLat()+route.getToWaypoint().getLat())/2, (route.getFromWaypoint().getLng()+route.getToWaypoint().getLng())/2, nearDistance, new HashSet<String>(), false);
							wi = new WaypointInfo(waypoint);
							mWaypointInfos.put(route.getName(), wi);
							updateWaypointInfo(wi);
							waypointInfos.put(entry.getKey(), wi);
						}
						else
							Log.w(TAG,"Cannot make waypoint from route "+entry.getValue()+" - missing from or to (local name "+entry.getKey()+")");
							
					}
					else 
						Log.w(TAG,"Cannot find waypoint or route "+entry.getValue()+" (local name "+entry.getKey()+")");
				}
			}
			if (mLastWaypoint!=null && !lastWaypointIsLocal) {
				// extra!
				WaypointInfo wi = mWaypointInfos.get(mLastWaypoint.getName());
				if (wi!=null)
					waypointInfos.put(lastWaypointName, wi);
				else
					Log.w(TAG,"Cannot find last waypoint "+mLastWaypoint.getName());
			}
		} else if (mContext!=null) {
			// all...
			waypointInfos = mWaypointInfos;
		}
		// waypoints
		if (waypointInfos==null) {
			sb.append("var waypoints={}\n");
		} else {
			sb.append("var waypoints={\n");
			WaypointInfo nearest = null;
			String nearestName = null;
			boolean first = true;
			for(Map.Entry<String, WaypointInfo> entry: waypointInfos.entrySet()) {
				if (first)
					first = false;
				else
					sb.append(",\n");
				sb.append("'");
				sb.append(entry.getKey());
				sb.append("':{ name: '");
				WaypointInfo wi = entry.getValue();
				sb.append(wi.waypoint.getName());
				sb.append("', lat: ");
				sb.append(wi.waypoint.getLat());
				sb.append(", lng: ");
				sb.append(wi.waypoint.getLng());
				sb.append(", x: ");
				sb.append(wi.waypoint.getX());
				sb.append(", y: ");
				sb.append(wi.waypoint.getY());
				sb.append(", near: ");
				if (wi.valid) {
					sb.append(wi.near ? "true" : "false");
					sb.append(", distance: ");
					sb.append(wi.distance);
					sb.append(", relativeBearing: ");
					sb.append(wi.relativeBearing);
					sb.append(", timeAtCurrentSpeed: ");
					sb.append(wi.timeAtCurrentSpeed);
					sb.append(", timeAtWalkingSpeed: ");
					sb.append(wi.timeAtWalkingSpeed);
				}
				else
					sb.append("false");
				if (wi.waypoint==mLastWaypoint) {
					sb.append(", nearTime: ");
					sb.append(mLastWaypointNearTime/1000);
					sb.append(", notNearTime: ");
					sb.append(mLastWaypointNotNearTime/1000);
				}
				sb.append(" }");
				
				if (wi.waypoint!=mLastWaypoint && (nearest==null || wi.distance<nearest.distance)) {
					nearestName = entry.getKey();
					nearest = wi;
				}
			}
			sb.append("\n};\n");
		
			// lastWaypoint
			if (lastWaypointName!=null) {
				sb.append("var lastWaypoint='");
				sb.append(lastWaypointName);
				sb.append("';\n");
			}
			// nextWaypoint
			// TODO not just nearest?!
			if (nearestName!=null) {
				sb.append("var nextWaypoint='");
				sb.append(nearestName);
				sb.append("';\n");
			}
		}
		// which routes? local , or all if no local
		Map<String,RouteInfo> routeInfos = null;
		if (mContext!=null && localRoutes!=null) {
			routeInfos = new HashMap<String,RouteInfo>();
			for (Map.Entry<String,String> entry: localRoutes.entrySet()) {
				RouteInfo ri = mRouteInfos.get(entry.getValue());
				if (ri!=null)
					routeInfos.put(entry.getKey(), ri);
				else 
					Log.w(TAG,"Cannot find route "+entry.getValue()+" (local name "+entry.getKey()+")");
			}
		} else if (mContext!=null) {
			// all...
			routeInfos = mRouteInfos;
		}
		// routes
		if (routeInfos==null) {
			sb.append("var routes={}\n");
		} else {
			sb.append("var routes={\n");
			boolean first = true;
			for(Map.Entry<String, RouteInfo> entry: routeInfos.entrySet()) {
				if (first)
					first = false;
				else
					sb.append(",\n");
				sb.append("'");
				sb.append(entry.getKey());
				sb.append("':{ name: '");
				RouteInfo ri = entry.getValue();
				sb.append(ri.route.getName());
				sb.append("', near: ");
				if (ri.valid) {
					sb.append(ri.near ? "true" : "false");
					sb.append(", distanceAlong: ");
					sb.append(ri.distanceAlong);
					sb.append(", distanceFrom: ");
					sb.append(ri.distanceFrom);
					sb.append(", length: ");
					sb.append(ri.length);
					sb.append(", nearest: ");
					sb.append(ri.nearest);
				}
				else
					sb.append("false");
				sb.append(" }");
			}
			sb.append("\n};\n");
		}
	}
	private void updateWaypointInfos() {
		// NOTE: sync with updateWaypointInfo
		if (mContext==null)
			return;
		if (mEstimatedAccuracy > mContext.getRequiredAccuracy() || mEstimatedAccuracy>MAX_REQUIRED_ACCURACY) {
			for(Map.Entry<String, WaypointInfo> entry: mWaypointInfos.entrySet()) {
				WaypointInfo wi = entry.getValue();
				wi.valid = false;
				wi.near = false;
			}
		} else {
			double currentSpeed = getCurrentSpeed();
			double walkingSpeed = getWalkingSpeed();
			for(Map.Entry<String, WaypointInfo> entry: mWaypointInfos.entrySet()) {
				WaypointInfo wi = entry.getValue();
				// TODO route and distance along route!
				double dx = wi.waypoint.getX()-mEstimatedX;
				double dy = wi.waypoint.getY()-mEstimatedY;
				wi.distance = Math.sqrt(dx*dx+dy*dy);
				wi.relativeBearing = Math.atan2(-mEstimatedYSpeed*dx+mEstimatedXSpeed*dy, mEstimatedXSpeed*dx+mEstimatedYSpeed*dy)*180/Math.PI;
				wi.near = wi.distance < wi.waypoint.getNearDistance();
				if (currentSpeed>=0)
					wi.timeAtCurrentSpeed = wi.distance / currentSpeed;
				if (walkingSpeed>=0)
					wi.timeAtWalkingSpeed = wi.distance / walkingSpeed;
				wi.valid = true;
			}		
		}
	}
	private void updateWaypointInfo(WaypointInfo wi) {
		// NOTE: sync with updateWaypointInfos
		if (mContext==null)
			return;
		if (mEstimatedAccuracy > mContext.getRequiredAccuracy() || mEstimatedAccuracy>MAX_REQUIRED_ACCURACY) {
			wi.valid = false;
			wi.near = false;
		} else {
			double currentSpeed = getCurrentSpeed();
			double walkingSpeed = getWalkingSpeed();
			// TODO route and distance along route!
			double dx = wi.waypoint.getX()-mEstimatedX;
			double dy = wi.waypoint.getY()-mEstimatedY;
			wi.distance = Math.sqrt(dx*dx+dy*dy);
			wi.relativeBearing = Math.atan2(-mEstimatedYSpeed*dx+mEstimatedXSpeed*dy, mEstimatedXSpeed*dx+mEstimatedYSpeed*dy)*180/Math.PI;
			wi.near = wi.distance < wi.waypoint.getNearDistance();
			if (currentSpeed>=0)
				wi.timeAtCurrentSpeed = wi.distance / currentSpeed;
			if (walkingSpeed>=0)
				wi.timeAtWalkingSpeed = wi.distance / walkingSpeed;
			wi.valid = true;
		}
	}
	private void updateRouteInfos() {
		if (mContext==null)
			return;
		if (mEstimatedAccuracy > mContext.getRequiredAccuracy() || mEstimatedAccuracy>MAX_REQUIRED_ACCURACY) {
			for(Map.Entry<String, RouteInfo> entry: mRouteInfos.entrySet()) {
				RouteInfo ri = entry.getValue();
				ri.valid = false;
				ri.near = false;
			}
		} else {
			RouteInfo nearest = null;
			for(Map.Entry<String, RouteInfo> entry: mRouteInfos.entrySet()) {
				RouteInfo ri = entry.getValue();
				double dx = mEstimatedX-ri.route.getFromWaypoint().getX();
				double dy = mEstimatedY-ri.route.getFromWaypoint().getY();
				double rx = ri.route.getToWaypoint().getX()-ri.route.getFromWaypoint().getX();
				double ry = ri.route.getToWaypoint().getY()-ri.route.getFromWaypoint().getY();
				if (ri.length<=0)
					ri.length = Math.sqrt(rx*rx+ry*ry);
				if (ri.length>0) {
					double dot = dx*rx/ri.length+dy*ry/ri.length;
					if (dot<0)
						ri.distanceAlong = 0;
					else if (dot>ri.length) 
						ri.distanceAlong = ri.length;
					else
						ri.distanceAlong = dot;
					double onx = ri.distanceAlong*rx/ri.length;
					double ony = ri.distanceAlong*ry/ri.length;
					ri.distanceFrom = Math.sqrt((dx-onx)*(dx-onx)+(dy-ony)*(dy-ony));
				} else {
					// zero length
					ri.distanceFrom = Math.sqrt(dx*dx+dy*dy);
					ri.distanceAlong = 0;
				}
					
				ri.near = ri.distanceFrom < ri.route.getNearDistance();
				ri.valid = true;
				ri.nearest = false;
				if (ri.near && (nearest==null || ri.distanceFrom<nearest.distanceFrom))
					nearest = ri;
			}		
			if (nearest!=null)
				nearest.nearest = true;
		}
	}
	static class WaypointInfo {
		private Context.Waypoint waypoint;
		private boolean near = false;
		private boolean valid = false;
		private double distance;
		private double relativeBearing;
		//private Vector<String> route;
		private double timeAtCurrentSpeed;
		private double timeAtWalkingSpeed;
		/**
		 * @param mWaypoint
		 */
		public WaypointInfo(Waypoint waypoint) {
			super();
			this.waypoint = waypoint;
		}
	}
	static class RouteInfo {
		public RouteInfo(Route route) {
			this.route = route;
		}
		private Context.Route route;
		private boolean near = false;
		private boolean valid = false;
		private boolean nearest = false;
		private double distanceFrom;
		private double distanceAlong;
		private double length;
	}
	public synchronized void setLastWaypoint(String name) {
		// WARNING: called on an internal javascript thread - be careful!
		mLastWaypointCheckTime = System.currentTimeMillis();
		mLastWaypointNearTime = mLastWaypointNotNearTime = 0;
		if (mLocalWaypoints!=null && mLocalWaypoints.containsKey(name)) {
			name = mLocalWaypoints.get(name);
		}
		if (mContext!=null) {
			mLastWaypoint = mContext.getWaypoint(name);
			if (mLastWaypoint==null)
				Log.e(TAG,"setLastWaypoint: could not find waypoint "+name);
		}
		else
			Log.w(TAG,"setLastWaypoint: ignored "+name+" - no context");
	}
}
