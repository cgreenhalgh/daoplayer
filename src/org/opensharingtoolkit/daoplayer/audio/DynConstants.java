/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

/** dynamic scene (javascript) constants
 * 
 * @author pszcmg
 *
 */
public class DynConstants {
	private Map<String,String> mMap = new HashMap<String,String>();
	public DynConstants() {		
	}
	public void parse(JSONObject jMap) throws JSONException {
		Iterator<String> keys = jMap.keys();
		while(keys.hasNext()) {
			String key = keys.next();
			mMap.put(key, jMap.get(key).toString());
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
