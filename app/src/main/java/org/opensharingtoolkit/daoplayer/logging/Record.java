/**
 * 
 */
package org.opensharingtoolkit.daoplayer.logging;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Add to permanent/study record (if logging app installed)
 * @author pszcmg
 *
 */
public class Record {
	public static final int LEVEL_TRACE = 0;
	public static final int LEVEL_DEBUG = 2;
	public static final int LEVEL_INFO = 4;
	public static final int LEVEL_WARN = 6;
	public static final int LEVEL_ERROR = 8;
	public static final int LEVEL_SEVERE = 10;
	private static final String TAG = "record";

	public static void t(Context context, String component, String event, Object info) {
		log(context, LEVEL_TRACE, component, event, info, false);
	}
	public static void d(Context context, String component, String event, Object info) {
		log(context, LEVEL_DEBUG, component, event, info, false);
	}
	public static void i(Context context, String component, String event, Object info) {
		log(context, LEVEL_INFO, component, event, info, false);
	}
	public static void w(Context context, String component, String event, Object info) {
		log(context, LEVEL_WARN, component, event, info, false);
	}
	public static void e(Context context, String component, String event, Object info) {
		log(context, LEVEL_ERROR, component, event, info, false);
	}
	public static void s(Context context, String component, String event, Object info) {
		log(context, LEVEL_SEVERE, component, event, info, false);
	}
	public static void log(Context context, int level, String component, String event, Object oinfo) {
		log(context, level, component, event, oinfo, false);
	}
	public static void log(Context context, int level, String component,
			String event, Object oinfo, boolean startNewFile) {
		long now = System.currentTimeMillis();
		String info = packInfo(oinfo);
		Intent i = new Intent(); //"org.opensharingtoolkit.intent.action.LOG");
		i.setClass(context, LoggingService.class);
		i.putExtra("time", now);
		i.putExtra("level", level);
		i.putExtra("component", component);
		i.putExtra("event", event);
		if (startNewFile)
			i.putExtra("newFile", true);
		if (info!=null)
			i.putExtra("info", info);
		try {
			if (context.startService(i)==null) 
				Log.e(TAG,"Could not log (no service): "+level+" "+component+" "+event+" "+info);
		}
		catch (Exception e) {
			Log.e(TAG,"Could not log: "+level+" "+component+" "+event+": "+e);
		}
	}
	public static void logJson(Context context, int level, String component, String event, String jsoninfo) {
		long now = System.currentTimeMillis();
		Intent i = new Intent(); //"org.opensharingtoolkit.intent.action.LOG");
		i.setClass(context, LoggingService.class);
		i.putExtra("time", now);
		i.putExtra("level", level);
		i.putExtra("component", component);
		i.putExtra("event", event);
		if (jsoninfo!=null)
			i.putExtra("info", jsoninfo);
		try {
			if (context.startService(i)==null) 
				Log.e(TAG,"Could not log (no service): "+level+" "+component+" "+event+" "+jsoninfo);
		}
		catch (Exception e) {
			Log.e(TAG,"Could not log: "+level+" "+component+" "+event+": "+e);
		}
	}
	private static String packInfo(Object oinfo) {
		if (oinfo==null)
			return null;
		if (oinfo instanceof Integer || oinfo instanceof Long || oinfo instanceof Boolean || oinfo instanceof Double) {
			return oinfo.toString();
		}
		if (oinfo instanceof String) {
			// escape JSON string?!
			JSONStringer s = new JSONStringer();
			try {
				s.array();
				s.value(oinfo);
				s.endArray();
				String info = s.toString();
				// skip [ ... ]
				return info.substring(1,info.length()-1);
			} catch (JSONException e) {
				Log.w(TAG,"Error stringing string "+oinfo+": "+e);
				return null;
			}
		}
		if (oinfo instanceof JSONObject) 
			return ((JSONObject)oinfo).toString();
		if (oinfo instanceof JSONArray) 
			return ((JSONArray)oinfo).toString();
		Log.w(TAG,"Unhandled info type "+oinfo.getClass().getName());
		return null;
	}
}
