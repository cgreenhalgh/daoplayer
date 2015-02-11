/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

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
		
		private double distance1;
		//private double distanceN;
		private long elapsed1;
		//private long elapsedN;
		/**
		 * @param lat
		 * @param lng
		 * @param accuracy
		 * @param time
		 */
		public Location(double lat, double lng, double accuracy, long time) {
			super();
			this.lat = lat;
			this.lng = lng;
			this.accuracy = accuracy;
			this.time = time;
		}
		
	}
	private static final int MAX_LOCATION_HISTORY_SIZE = 100;
	private static final long MAX_GPS_INTERVAL = 2000;
	private static final double MAX_STATIONARY_SPEED = 0.5; // m/s
	private static final double DEFAULT_WALKING_SPEED = 1.5; // m/s
	private static final String TAG = "usermodel";
	
	public static enum Activity { NOGPS, STATIONARY, WALKING };

	//private Location mLastLocation;
	private Vector<Location> mLocations = new Vector<Location>();
	private Context mContext;
	// cache...
	private Map<String,String> mLocalWaypoints; 
	private Context.Waypoint mLastWaypoint;
	private long mLastWaypointCheckTime, mLastWaypointNearTime, mLastWaypointNotNearTime;
	HashMap<String,WaypointInfo> mWaypointInfos = new HashMap<String,WaypointInfo>();
	
	public void setLocation(double lat, double lng, double accuracy, long time) {
		Location loc = new Location(lat, lng, accuracy, time);
		// TODO log?
		if (mLocations.size()>0) {
			Location lastLoc = mLocations.get(0);
			loc.distance1 = Utils.distance(lat, lng, lastLoc.lat, lastLoc.lng);
			loc.elapsed1 = time-lastLoc.time;
		}
		mLocations.add(0, loc);
		if (mLocations.size()>MAX_LOCATION_HISTORY_SIZE)
			mLocations.remove(mLocations.size()-1);
		updateWaypointInfos();
	}

	public Activity getActivity() {
		// TODO better :-) e.g. longer history
		if (mLocations.size()==0)
			return Activity.NOGPS;
		long now = System.currentTimeMillis();
		Location lastLoc = mLocations.get(0);
		long elapsed = now-lastLoc.time;
		if (elapsed > MAX_GPS_INTERVAL || lastLoc.elapsed1==0 || lastLoc.elapsed1 > MAX_GPS_INTERVAL)
			return Activity.NOGPS;
		if (lastLoc.distance1 <= MAX_STATIONARY_SPEED)
			return Activity.STATIONARY;
		return Activity.WALKING;
	}
	
	public double getWalkingSpeed() {
		// TODO personalise for this user!
		return DEFAULT_WALKING_SPEED;
	}
	
	public double getCurrentSpeed() {
		// TODO better :-) e.g. longer history
		if (mLocations.size()==0)
			return 0;
		// last known?!
		Location lastLoc = mLocations.get(0);
		if (lastLoc.elapsed1==0 || lastLoc.elapsed1 > MAX_GPS_INTERVAL)
			return 0;
		return lastLoc.distance1/lastLoc.elapsed1/1000;		
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
	}

	public synchronized void toJavascript(StringBuilder sb, Map<String,String> localWaypoints) {
		mLocalWaypoints = localWaypoints;
		Activity activity = getActivity();
		double currentSpeed = getCurrentSpeed();
		double walkingSpeed = getWalkingSpeed();
		sb.append("var activity='");
		sb.append(activity.name());
		sb.append("';");
		sb.append("var currentSpeed=");
		sb.append(currentSpeed);
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
		if (mContext!=null && localWaypoints!=null && localWaypoints.size()>0) {
			boolean lastWaypointIsLocal = false;
			waypointInfos = new HashMap<String,WaypointInfo>();
			for (HashMap.Entry<String,String> entry: localWaypoints.entrySet()) {
				if (mLastWaypoint!=null && mLastWaypoint.getName().equals(entry.getValue())) {
					lastWaypointIsLocal = true;
					lastWaypointName = entry.getKey();
				}
				WaypointInfo wi = mWaypointInfos.get(entry.getValue());
				if (wi!=null)
					waypointInfos.put(entry.getKey(), wi);
				else
					Log.w(TAG,"Cannot find waypoint "+entry.getValue()+" (local name "+entry.getKey()+")");
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
			sb.append(", distance: ");
			sb.append(wi.distance);
			sb.append(", near: ");
			sb.append(wi.near ? "true" : "false");
			sb.append(", timeAtCurrentSpeed: ");
			sb.append(wi.timeAtCurrentSpeed);
			sb.append(", timeAtWalkingSpeed: ");
			sb.append(wi.timeAtWalkingSpeed);
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
	private void updateWaypointInfos() {
		if (mLocations.size()==0)
			return;
		// last known?!
		Location lastLoc = mLocations.get(0);
		double currentSpeed = getCurrentSpeed();
		double walkingSpeed = getWalkingSpeed();
		for(Map.Entry<String, WaypointInfo> entry: mWaypointInfos.entrySet()) {
			WaypointInfo wi = entry.getValue();
			// TODO route and distance along route!
			wi.distance = Utils.distance(lastLoc.lat, lastLoc.lng, wi.waypoint.getLat(), wi.waypoint.getLng());
			wi.near = wi.distance < wi.waypoint.getNearDistance();
			if (currentSpeed>=0)
				wi.timeAtCurrentSpeed = wi.distance / currentSpeed;
			if (walkingSpeed>=0)
				wi.timeAtWalkingSpeed = wi.distance / walkingSpeed;
		}		
	}
	static class WaypointInfo {
		private Context.Waypoint waypoint;
		private double distance;
		private boolean near = false;
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
