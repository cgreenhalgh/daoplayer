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
import org.json.JSONStringer;
import org.opensharingtoolkit.daoplayer.ILog;

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
	private static final String REQUIRED_ACCURACY = "requiredAccuracy";
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
	private double mRequiredAccuracy = Double.MAX_VALUE;
	private double mRefX, mRefY;
	private double mRefMetre=1;
	
	public static class Waypoint {
		private String name;
		private double lat;
		private double lng;
		private double nearDistance;
		private HashSet<String> aliases;
		private boolean origin;
		private double x;
		private double y;
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
		/**
		 * @return the x
		 */
		public double getX() {
			return x;
		}
		/**
		 * @return the y
		 */
		public double getY() {
			return y;
		}
		/**
		 * @param x the x to set
		 */
		void setX(double x) {
			this.x = x;
		}
		/**
		 * @param y the y to set
		 */
		void setY(double y) {
			this.y = y;
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

	static public Context parse(JSONObject jcontext, ILog log) throws JSONException {
		Context context = new Context();
		context.parse(jcontext, false, log);
		return context;
	}
	public void parse(JSONObject jcontext, boolean merging, ILog log) throws JSONException {
		Context context = this;
		if (jcontext.has(REQUIRED_ACCURACY) && (!merging || context.mRequiredAccuracy==Double.MAX_VALUE))
			context.mRequiredAccuracy = jcontext.getDouble(REQUIRED_ACCURACY);
		if (jcontext.has(WAYPOINTS)) {
			JSONArray jwaypoints = jcontext.getJSONArray(WAYPOINTS);
			for (int i=0; i<jwaypoints.length(); i++) {
				JSONObject jwaypoint = jwaypoints.getJSONObject(i);
				String name = jwaypoint.has(NAME) ? jwaypoint.getString(NAME) : null;
				if (name==null) {
					log.logError("Unnamed waypoint "+i);
					name = "["+i+"]";
				}
				if (merging && context.mWaypoints.containsKey(name)) {
					log.logError("Ignore duplicate waypoint "+name+" when merging compositions");
					continue;
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
					log.logError("Ignoring route "+ri+" without from/to: "+jroute.get(FROM)+"->"+jroute.get(TO));
					continue;
				}
				String from = jroute.has(FROM) ? jroute.getString(FROM) : null;
				String to = jroute.has(TO) ? jroute.getString(TO) : null;
				double nearDistance = jroute.has(NEAR_DISTANCE) ? jroute.getDouble(NEAR_DISTANCE) : DEFAULT_ROUTE_NEAR_DISTANCE;
				Waypoint fromWaypoint = context.getWaypoint(from);
				Waypoint toWaypoint = context.getWaypoint(to);
				if (fromWaypoint==null) 
					log.logError("route "+ri+" with unknown from "+from);
				if (toWaypoint==null) 
					log.logError("route "+ri+" with unknown to "+to);
				Route route = new Route(from, to, fromWaypoint, toWaypoint, nearDistance);
				// TODO avoid duplicate routes?
				context.mRoutes.add(route);
			}
		}
		fixOrigin();
	}
	/** sort out x, y, ref */
	private void fixOrigin() {
		Waypoint origin = null;
		for (Waypoint w : mWaypoints.values()) {
			if (w.isOrigin())
				origin = w;
		}
		if (origin==null && mWaypoints.size()>0)
			origin = mWaypoints.values().iterator().next();
		if (origin!=null) {
			mRefX = Utils.mercX(origin.getLng());
			mRefY = Utils.mercY(origin.getLat());
			mRefMetre = Utils.mercMetre(origin.getLat(), origin.getLng());
		}
		else {
			mRefX = mRefY = 0;
			mRefMetre = 1;
		}
		for (Waypoint w : mWaypoints.values()) {
			w.setX(lng2x(w.getLng()));
			w.setY(lat2y(w.getLat()));
		}
	}
	public double lng2x(double lng) {
		return (Utils.mercX(lng)-mRefX)/mRefMetre;
	}
	public double lat2y(double lat) {
		return (Utils.mercY(lat)-mRefY)/mRefMetre;
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
	public double getRequiredAccuracy() {
		return mRequiredAccuracy;
	}
	/** generate javascript to initialise context in script engine */
	public String getInitScript() {
		StringBuilder sb = new StringBuilder();
		sb.append("window.allWaypoints=");
		JSONStringer js = new JSONStringer();
		try {
			js.object();
			for(Map.Entry<String, Waypoint> entry: mWaypoints.entrySet()) {
				js.key(entry.getKey());
				js.object();
				js.key("name");
				Waypoint wi = entry.getValue();
				js.value(wi.getName());
				js.key("lat");
				js.value(wi.getLat());
				js.key("lng");
				js.value(wi.getLng());
				js.key("x");
				js.value(wi.getX());
				js.key("y");
				js.value(wi.getY());
				js.endObject();
			}
			js.endObject();
		} catch (JSONException e) {
			Log.e(TAG,"error stringing waypoint: "+e, e);
		}
		sb.append(js.toString());
		sb.append(";return true;");
		return sb.toString();
	}
}
