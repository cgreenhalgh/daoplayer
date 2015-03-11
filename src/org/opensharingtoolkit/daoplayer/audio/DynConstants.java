/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import android.util.Log;

/** dynamic scene (javascript) constants
 * 
 * @author pszcmg
 *
 */
public class DynConstants {
	private Map<String,String> mMap = new HashMap<String,String>();
	public DynConstants() {		
	}
	public void parse(JSONObject jMap, boolean ignoreIfDefined) throws JSONException {
		Iterator<String> keys = jMap.keys();
		while(keys.hasNext()) {
			String key = keys.next();
			if (!ignoreIfDefined || !mMap.containsKey(key)) {
				try {
					JSONStringer js = new JSONStringer();
					js.array();
					js.value(jMap.get(key));
					js.endArray();
					String jstring = js.toString();
					
					mMap.put(key, jstring.substring(1,jstring.length()-1));
				}
				catch (JSONException e) {
					throw new JSONException("Error stringing constant "+key+"="+jMap.get(key)+": "+e);
				}
			}
		}
	}
	public void toJavascript(StringBuilder sb) {
		for (Map.Entry<String, String> ent : mMap.entrySet()) {
			sb.append("var ");
			sb.append(ent.getKey());
			sb.append("=");
			sb.append(ent.getValue());
			sb.append(";");
		}
	}
}
