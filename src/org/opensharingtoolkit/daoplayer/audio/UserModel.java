/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.ejml.data.DenseMatrix64F;
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
	private double mEstimatedX, mEstimatedY, mEstimatedAccuracy, mEstimatedSpeedAccuracy;
	
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
		updateEstimates(time, elapsedtime);
	}
	private void updateEstimates(long time, long elapsedtime) {
		mLastElapsedtime = elapsedtime;
		Log.d(TAG,"updateEstimates("+time+","+elapsedtime+")");
		
		DenseMatrix64F cov = mKalmanFilter.getCovariance();
		DenseMatrix64F state = mKalmanFilter.getState();
		Log.d(TAG,"Kalman: "+state.get(0)+","+state.get(1)+" v="+state.get(2)+","+state.get(3)+", cov="+cov.get(0,0)+","+cov.get(1,1)+","+cov.get(2,2)+","+cov.get(3,3));
		
		// current speed
		mEstimatedCurrentSpeed = Math.sqrt(state.get(KalmanFilter.STATE_VX)*state.get(KalmanFilter.STATE_VX)+state.get(KalmanFilter.STATE_VY)*state.get(KalmanFilter.STATE_VY));
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
		
		if (mEstimatedAccuracy > mContext.getRequiredAccuracy() || mEstimatedAccuracy>MAX_REQUIRED_ACCURACY)
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
	}
	/* note elapsedtime should be comparable with gps elapsedTimeNanos mapped to ms */
	public void updateNoLocation(long time, long elapsedtime) {
		if (elapsedtime<mLastElapsedtime+MIN_UPDATE_INTERVAL) {
			Log.d(TAG,"Ignore updateNoLocation("+time+","+elapsedtime+"); last elapsedtime="+mLastElapsedtime);
			return;
		}
		for (long t=mLastElapsedtime+1000; t<elapsedtime; t+=1000)
			mKalmanFilter.predict();

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
	}

	public synchronized void toJavascript(StringBuilder sb, Map<String,String> localWaypoints) {
		mLocalWaypoints = localWaypoints;
		Activity activity = getActivity();
		double currentSpeed = getCurrentSpeed();
		double currentSpeedAccuracy = getCurrentSpeedAccuracy();
		double walkingSpeed = getWalkingSpeed();
		// TODO position
		sb.append("var position=");
		if (mEstimatedAccuracy < mContext.getRequiredAccuracy() && mEstimatedAccuracy < MAX_REQUIRED_ACCURACY) {
			sb.append("{x:");
			sb.append(mEstimatedX);
			sb.append(",y:");
			sb.append(mEstimatedY);
			sb.append(",accuracy:");
			sb.append(mEstimatedAccuracy);
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
		if (mContext!=null && localWaypoints!=null && localWaypoints.size()>0) {
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
		if (waypointInfos==null) 
			return;
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
	private void updateWaypointInfos() {
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
				double dx = mEstimatedX-wi.waypoint.getX();
				double dy = mEstimatedY-wi.waypoint.getY();
				wi.distance = Math.sqrt(dx*dx+dy*dy);
				wi.near = wi.distance < wi.waypoint.getNearDistance();
				if (currentSpeed>=0)
					wi.timeAtCurrentSpeed = wi.distance / currentSpeed;
				if (walkingSpeed>=0)
					wi.timeAtWalkingSpeed = wi.distance / walkingSpeed;
				wi.valid = true;
			}		
		}
	}
	static class WaypointInfo {
		private Context.Waypoint waypoint;
		private boolean near = false;
		private boolean valid = false;
		private double distance;
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
