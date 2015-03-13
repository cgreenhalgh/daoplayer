package gps;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;;
import java.io.OutputStreamWriter;
import java.io.BufferedOutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import android.util.Log;

import org.opensharingtoolkit.daoplayer.audio.Context;
import org.opensharingtoolkit.daoplayer.audio.UserModel;
import org.opensharingtoolkit.daoplayer.audio.Context.Waypoint;
import org.opensharingtoolkit.daoplayer.audio.Utils;
import org.opensharingtoolkit.daoplayer.ILog;

/** gps / usermodel / filter test */
public class TestFilter {
	static final String TAG = "test-filter";
	static private SimpleDateFormat rfcdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", new Locale("","",""));
	static public final long NANOS_PER_MILLI = 1000000;
	
	public static void main(String args[]) {
		if (args.length!=3) {
			System.err.println("Usage: <composition.json> <logfile.log> <outfile.json>");
			System.exit(-1);
		}
		String composition = readFully(args[0]);
		Context context = null;
		try {
			JSONObject jcomp = new JSONObject(composition);
			if (jcomp.has("context")) {
				context = Context.parse(jcomp.getJSONObject("context"), new ILog());
			}
		}
		catch (Exception e) {
			System.err.println("Error parsing composition/context: "+e);
			System.exit(-2);
		}
		String logfile = readFully(args[1]);
		String lines[] = logfile.split("\n");
		System.out.println("Done");
		
		UserModel userModel = new UserModel();
		if (context!=null)
			userModel.setContext(context);
		
		JSONStringer jout = new JSONStringer();
		try {
			jout.object();
			// use first waypoint as reference
			Context.Waypoint origin = null;
			if (context!=null) {
				for (Waypoint waypoint : context.getWaypoints().values()) {
					if (waypoint.isOrigin())
						origin = waypoint;
				}
				if (origin==null && context.getWaypoints().size()>0)
					origin = context.getWaypoints().values().iterator().next();
			}
			if (origin!=null)
				System.out.println("Origin waypoint="+origin.getName()+" ("+origin.getLat()+","+origin.getLng()+")");
			double refX = origin!=null ? Utils.mercX(origin.getLng()) : 0;
			double refY = origin!=null ? Utils.mercY(origin.getLat()) : 0;
			double refMetre = origin!=null ? Utils.mercMetre(origin.getLat(), origin.getLng()) : 1;
			jout.key("origin");
			jout.object();
			if (origin!=null) {
				jout.key("name");
				jout.value(origin.getName());
			}
			jout.key("lat");
			jout.value(origin!=null ? origin.getLat() : 0);
			jout.key("lng");
			jout.value(origin!=null ? origin.getLng() : 0);
			jout.key("x");
			jout.value(refX);
			jout.key("y");
			jout.value(refY);
			jout.key("m");
			jout.value(refMetre);
			jout.endObject();
			System.out.println("Reference at (mercator) "+refX+","+refY+", where 1m="+refMetre);
			if (context!=null) {
				jout.key("waypoints");
				jout.array();
				for (Waypoint waypoint : context.getWaypoints().values()) {
					jout.object();
					jout.key("name");
					jout.value(waypoint.getName());
					jout.key("lat");
					jout.value(waypoint.getLat());
					jout.key("lng");
					jout.value(waypoint.getLng());
					jout.key("x");
					jout.value(Utils.mercX(waypoint.getLng())-refX);
					jout.key("y");
					jout.value(Utils.mercY(waypoint.getLat())-refY);
					jout.endObject();
				}
				jout.endArray();
			}

			// read log entries
			jout.key("locations");
			jout.array();
			long lastElapsed = 0;
			long lastTime = 0;
			for (String line : lines) {
				if (line.length()==0)
					continue;
				try {
					JSONObject rec = new JSONObject(line);
					String event = rec.has("event") ? rec.getString("event") : null;
					Date date = rec.has("time") ? new Date(rec.getLong("time")) : (rec.has("datetime") ? rfcdf.parse(rec.getString("datetime")) : null);
					Object oinfo = rec.has("info") ? rec.get("info"): null;
				    if ("on.location".equals(event) && oinfo instanceof JSONObject) {
				    	JSONObject info = (JSONObject)oinfo;
				        // lng, lat, accuracy
				        // mercator projection
				    	double lat = info.getDouble("lat");
				    	double lng = info.getDouble("lng");
				    	double accuracy = info.getDouble("accuracy");
						long elapsedtime = info.has("elapsedRealtimeNanos") ? info.getLong("elapsedRealtimeNanos")/NANOS_PER_MILLI : 0;
				        double x = (Utils.mercX(lng)-refX)/refMetre;
				        double y = (Utils.mercY(lat)-refY)/refMetre;

				        if (lastElapsed!=0 && elapsedtime>=lastElapsed+1500) {
				        	int catchup = (int)((elapsedtime-lastElapsed-500)/1000);
				        	System.out.println("Catch up "+catchup+" missing locations");
				        	for (int i=0; i<catchup; i++) {
				        		userModel.updateNoLocation(lastTime+1000*(i+1), lastElapsed+1000*(i+1));
				        	}
				        }
				        lastTime = date.getTime();
				        lastElapsed = elapsedtime;
				        
				        userModel.setLocation(lat, lng, accuracy, date.getTime(), elapsedtime);
				        UserModel.Activity activity = userModel.getActivity();
				        double currentSpeed = userModel.getCurrentSpeed();
				        double walkingSpeed = userModel.getWalkingSpeed();
				        double estimatedX = userModel.getX();
				        double estimatedY = userModel.getY();
				        double estimatedAccuracy = userModel.getAccuracy();
				        double speedAccuracy = userModel.getCurrentSpeedAccuracy();
				        
				        jout.object();
				        jout.key("time");
				        jout.value(date.getTime());
				        jout.key("lat");
				        jout.value(lat);
				        jout.key("lng");
				        jout.value(lng);
				        jout.key("rawaccuracy");
				        jout.value(accuracy);
				        jout.key("rawx");
				        jout.value(x);
				        jout.key("rawy");
				        jout.value(y);
				        jout.key("accuracy");
				        jout.value(estimatedAccuracy);
				        jout.key("x");
				        jout.value(estimatedX);
				        jout.key("y");
				        jout.value(estimatedY);
				        jout.key("currentSpeedAccuracy");
				        jout.value(speedAccuracy);
				        jout.key("activity");
				        jout.value(activity.name());
				        jout.key("currentSpeed");
				        jout.value(currentSpeed);
				        jout.key("walkingSpeed");
				        jout.value(walkingSpeed);
				        jout.endObject();
				    }
				}
				catch (Exception e) {
					System.err.println("Error parsing log entry: "+e+": "+line);
					continue;
				}
			}
			jout.endArray();
			
			jout.endObject();
		}
		catch (JSONException e) {
			System.err.println("Error generating output json: "+e);
			e.printStackTrace(System.err);
			System.exit(-3);
		}
		try {
			BufferedWriter bw = null;
			bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[2]), "UTF-8"));
			bw.append(jout.toString());
			bw.close();
		}
		catch (Exception e) {
			System.err.println("Error writing output file "+args[2]+": "+e);
			System.exit(-2);
		}
		System.out.println("Wrote output "+args[2]);
	}
	static String readFully(String filename) {
		System.out.println("Read "+filename);
		StringBuilder sb = new StringBuilder();
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
			char buf[] = new char[100000];
			while (true) {
				int cnt = br.read(buf);
				if (cnt<=0)
					break;
				sb.append(buf,0,cnt);
			}
			br.close();
			return sb.toString();
		} 
		catch (Exception e) {
			Log.e(TAG,"Error reading "+filename+": "+e);
			System.exit(-2);
		}
		return null; 
	}
}