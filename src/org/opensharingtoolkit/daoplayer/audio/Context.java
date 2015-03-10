/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/** Composition Context, i.e. location-related stuff at the moment.
 * 
 * @author pszcmg
 *
 */
public class Context {
	private static final String ALIASES = "aliases";
	private static final String FROM = "from";
	private static final String LAT = "lat";
	private static final String LNG = "lng";
	//private static final String ONEWAY = "oneway";
	private static final String NAME = "name";
	private static final String NEAR_DISTANCE = "nearDistance";
	private static final String ORIGIN = "origin";
	private static final String ROUTES = "routes";
	private static final String TO = "to";
	//private static final String VIA = "via";
	private static final String WAYPOINTS = "waypoints";
	private static final String TAG = "context";
	
	private static final double DEFAULT_WAYPOINT_NEAR_DISTANCE = 15;
	private static final double DEFAULT_ROUTE_NEAR_DISTANCE = 5;

	private Map<String,Waypoint> mWaypoints = new HashMap<String,Waypoint>();
	private Map<String,String> mWaypointAliases = new HashMap<String,String>();
	private Vector<Route> mRoutes = new Vector<Route>();
	
	public static class Waypoint {
		private String name;
		private double lat;
		private double lng;
		private double nearDistance;
		private HashSet<String> aliases;
		private boolean origin;
		/**
		 * @param name
		 * @param lat
		 * @param lng
		 * @param nearDistance
		 * @param aliases
		 */
		public Waypoint(String name, double lat, double lng,
				double nearDistance, HashSet<String> aliases, boolean origin) {
			super();
			this.name = name;
			this.lat = lat;
			this.lng = lng;
			this.nearDistance = nearDistance;
			this.aliases = aliases;
			this.origin = origin;
		}
		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}
		/**
		 * @return the lat
		 */
		public double getLat() {
			return lat;
		}
		/**
		 * @return the lng
		 */
		public double getLng() {
			return lng;
		}
		/**
		 * @return the nearDistance
		 */
		public double getNearDistance() {
			return nearDistance;
		}
		/**
		 * @return the aliases
		 */
		public HashSet<String> getAliases() {
			return aliases;
		}
		/**
		 * @return the origin
		 */
		public boolean isOrigin() {
			return origin;
		}
		
	}
	
	public static class Route {
		private String from;
		private String to;
		private Waypoint fromWaypoint;
		private Waypoint toWaypoint;
		private double nearDistance;
		/**
		 * @param from
		 * @param to
		 * @param fromWaypoint
		 * @param toWaypoint
		 * @param nearDistance
		 */
		public Route(String from, String to, Waypoint fromWaypoint,
				Waypoint toWaypoint, double nearDistance) {
			super();
			this.from = from;
			this.to = to;
			this.fromWaypoint = fromWaypoint;
			this.toWaypoint = toWaypoint;
			this.nearDistance = nearDistance;
		}
		/**
		 * @return the from
		 */
		public String getFrom() {
			return from;
		}
		/**
		 * @return the to
		 */
		public String getTo() {
			return to;
		}
		/**
		 * @return the fromWaypoint
		 */
		public Waypoint getFromWaypoint() {
			return fromWaypoint;
		}
		/**
		 * @return the toWaypoint
		 */
		public Waypoint getToWaypoint() {
			return toWaypoint;
		}
		/**
		 * @return the nearDistance
		 */
		public double getNearDistance() {
			return nearDistance;
		}
		
	}

	static public Context parse(JSONObject jcontext) throws JSONException {
		Context context = new Context();
		if (jcontext.has(WAYPOINTS)) {
			JSONArray jwaypoints = jcontext.getJSONArray(WAYPOINTS);
			for (int i=0; i<jwaypoints.length(); i++) {
				JSONObject jwaypoint = jwaypoints.getJSONObject(i);
				String name = jwaypoint.has(NAME) ? jwaypoint.getString(NAME) : null;
				if (name==null) {
					Log.w(TAG,"Unnamed waypoint "+i);
					name = "["+i+"]";
				}
				double lat = jwaypoint.has(LAT) ? jwaypoint.getDouble(LAT) : 0;
				double lng = jwaypoint.has(LNG) ? jwaypoint.getDouble(LNG) : 0;
				double nearDistance = jwaypoint.has(NEAR_DISTANCE) ? jwaypoint.getDouble(NEAR_DISTANCE) : DEFAULT_WAYPOINT_NEAR_DISTANCE;
				HashSet<String> aliases = new HashSet<String>();
				if (jwaypoint.has(ALIASES)) {
					JSONArray jaliases = jwaypoint.getJSONArray(ALIASES);
					for (int ai=0; ai<jaliases.length(); ai++) {
						String alias = jaliases.getString(ai);
						aliases.add(alias);
						context.mWaypointAliases.put(alias, name);
					}
				}
				boolean origin = jwaypoint.has(ORIGIN) ? jwaypoint.getBoolean(ORIGIN) : false;
				Waypoint waypoint = new Waypoint(name, lat, lng, nearDistance, aliases, origin);
				context.mWaypoints.put(name,  waypoint);
			}
		}
		if (jcontext.has(ROUTES)) {
			JSONArray jroutes = jcontext.getJSONArray(ROUTES);
			for (int ri=0; ri<jroutes.length(); ri++) {
				JSONObject jroute = jroutes.getJSONObject(ri);
				if (!jroute.has(FROM) || !jroute.has(TO)) {
					Log.w(TAG,"Ignoring route "+ri+" without from/to: "+jroute.get(FROM)+"->"+jroute.get(TO));
					continue;
				}
				String from = jroute.has(FROM) ? jroute.getString(FROM) : null;
				String to = jroute.has(TO) ? jroute.getString(TO) : null;
				double nearDistance = jroute.has(NEAR_DISTANCE) ? jroute.getDouble(NEAR_DISTANCE) : DEFAULT_ROUTE_NEAR_DISTANCE;
				Waypoint fromWaypoint = context.getWaypoint(from);
				Waypoint toWaypoint = context.getWaypoint(to);
				if (fromWaypoint==null) 
					Log.d(TAG,"route "+ri+" with unknonwn from "+from);
				if (toWaypoint==null) 
					Log.d(TAG,"route "+ri+" with unknonwn to "+to);
				Route route = new Route(from, to, fromWaypoint, toWaypoint, nearDistance);
				context.mRoutes.add(route);
			}
		}
		return context;
	}
	public Map<String,Waypoint> getWaypoints() {
		return mWaypoints;
	}
	public Waypoint getWaypoint(String name) {
		Waypoint waypoint = mWaypoints.get(name);
		if (waypoint==null) {
			String aname = mWaypointAliases.get(name);
			if (aname!=null) {
				waypoint = mWaypoints.get(aname);
			}			
		}
		return waypoint;
	}
}
