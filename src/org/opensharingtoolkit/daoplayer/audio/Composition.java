/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opensharingtoolkit.daoplayer.IAudio;
import org.opensharingtoolkit.daoplayer.IAudio.IFile;
import org.opensharingtoolkit.daoplayer.IAudio.IScene;
import org.opensharingtoolkit.daoplayer.IAudio.ITrack;

import android.util.Log;

/** Read a "composition" file.
 * 
 * @author pszcmg
 *
 */
public class Composition {
	private static final String TAG = "daoplayer-compositionreader";
	private static final String DEFAULT_SCENE = "defaultScene";
	private static final String TRACKS = "tracks";
	private static final String NAME = "name";
	private static final String PAUSE_IF_SILENT = "pauseIfSilent";
	private static final String FILES = "files";
	private static final String PATH = "path";
	private static final String TRACK_POS = "trackPos";
	private static final String FILE_POS = "filePos";
	private static final String LENGTH = "length";
	private static final String REPEATS = "repeats";
	private static final String SCENES = "scenes";
	private static final String PARTIAL = "partial";
	private static final String VOLUME = "volume";
	private static final String POS = "pos";
	private static final String ONANY = "onany";
	private static final String ONLOAD = "onload";
	private static final String ONUPDATE = "onupdate";
	private AudioEngine mEngine;
	private String mDefaultScene;
	private Map<String,ITrack> mTracks = new HashMap<String,ITrack>();
	private Map<String,DynScene> mScenes = new HashMap<String, DynScene>();
	private Vector<String> mScenesInOrder = new Vector<String>();

	public Composition(AudioEngine engine) {		
		mEngine = engine;
	}
	public static String readFully(File file) throws IOException {
		StringBuilder sb = new StringBuilder();
		Reader br = new InputStreamReader(new BufferedInputStream(new FileInputStream(file)));
		char buf[] = new char[10000];
		while(true) {
			int cnt = br.read(buf);
			if (cnt<0)
				break;
			sb.append(buf, 0, cnt);
		}
		br.close();
		return sb.toString();
	}
	public void read(File file) throws IOException, JSONException {
		Log.d(TAG,"read composition from "+file);
		File parent = file.getParentFile();
		String data = readFully(file);
		JSONObject jcomp = new JSONObject(data);
		// TODO meta
		mDefaultScene = (jcomp.has(DEFAULT_SCENE) ? jcomp.getString(DEFAULT_SCENE) : null);
		mTracks = new HashMap<String,ITrack>();
		if (jcomp.has(TRACKS)) {
			JSONArray jtracks = jcomp.getJSONArray(TRACKS);
			for (int ti=0; ti<jtracks.length(); ti++) {
				JSONObject jtrack = jtracks.getJSONObject(ti);
				String name = jtrack.has(NAME) ? jtrack.getString(NAME) : null;
				boolean pauseIfSilent = jtrack.has(PAUSE_IF_SILENT) && jtrack.getBoolean(PAUSE_IF_SILENT);
				ITrack atrack = mEngine.addTrack(pauseIfSilent);
				if (name!=null)
					mTracks.put(name, atrack);
				else
					Log.w(TAG,"Unnamed track "+ti);
				if (jtrack.has(FILES)) {
					JSONArray jfiles = jtrack.getJSONArray(FILES);
					for (int fi=0; fi<jfiles.length(); fi++) {
						JSONObject jfile = jfiles.getJSONObject(fi);
						if (!jfile.has(PATH)) {
							Log.w(TAG,"track "+ti+" references unspecified file "+fi);
							continue;
						}
						String fpath = jfile.getString(PATH);
						IFile afile = mEngine.addFile(new File(parent, fpath).getCanonicalPath());
						int trackPos = jfile.has(TRACK_POS) ? jfile.getInt(TRACK_POS) : 0;
						int filePos = jfile.has(FILE_POS) ? jfile.getInt(FILE_POS) : 0;
						int length = jfile.has(LENGTH) ? jfile.getInt(LENGTH) : IAudio.ITrack.LENGTH_ALL;
						int repeats = jfile.has(REPEATS) ? jfile.getInt(REPEATS) : 1;
						atrack.addFileRef(trackPos, afile, filePos, length, repeats);
					}
				}
			}
		}
		mScenes = new HashMap<String, DynScene>();
		mScenesInOrder = new Vector<String>();
		if (jcomp.has(SCENES)) {
			JSONArray jscenes = jcomp.getJSONArray(SCENES);
			for (int si=0; si<jscenes.length(); si++) {
				JSONObject jscene = jscenes.getJSONObject(si);
				String name = jscene.has(NAME) ? jscene.getString(NAME) : null;
				mScenesInOrder.add(name);
				boolean partial = jscene.has(PARTIAL) && jscene.getBoolean(PARTIAL);
				DynScene ascene = new DynScene(partial);
				if (name!=null)
					mScenes.put(name, ascene);
				else 
					Log.w(TAG,"Unnamed scene "+si);
				if (jscene.has(ONANY))
					ascene.setOnany(jscene.getString(ONANY));
				if (jscene.has(ONLOAD))
					ascene.setOnload(jscene.getString(ONLOAD));
				if (jscene.has(ONUPDATE))
					ascene.setOnload(jscene.getString(ONUPDATE));
				if (jscene.has(TRACKS)) {
					JSONArray jtracks = jscene.getJSONArray(TRACKS);
					for (int ti=0; ti<jtracks.length(); ti++) {
						JSONObject jtrack = jtracks.getJSONObject(ti);
						String trackName = jtrack.getString(NAME);
						ITrack atrack = mTracks.get(trackName);
						if (atrack==null) {
							Log.w(TAG,"Scene "+name+" refers to unknown track "+trackName);
						} else {
							Integer pos = jtrack.has(POS) ? jtrack.getInt(POS) : null;
							Float volume = jtrack.has(VOLUME) && jtrack.get(VOLUME) instanceof Number ? (float)jtrack.getDouble(VOLUME) : null;
							String dynVolume = jtrack.has(VOLUME) && jtrack.get(VOLUME) instanceof String ? jtrack.getString(VOLUME) : null;
							ascene.set(atrack, volume, dynVolume, pos);
						}
					}
				}
			}
		}
		Log.i(TAG,"Read composition "+file);
	}
	/**
	 * @return the mDefaultScene
	 */
	public String getDefaultScene() {
		return mDefaultScene;
	}
	private static float MIN_VOLUME = 0.0f;
	private static float MAX_VOLUME = 16.0f;
	private Map<Integer,Float> getDynVolumes(IScriptEngine scriptEngine, DynScene scene, boolean loadFlag) {
		StringBuilder sb = new StringBuilder();
		if (scene.getOnany()!=null) {
			sb.append(scene.getOnany());
			sb.append(";");
		}
		if (loadFlag && scene.getOnload()!=null) {
			sb.append(scene.getOnload());
			sb.append(";");
		} else if (!loadFlag && scene.getOnupdate()!=null) {
			sb.append(scene.getOnupdate());
			sb.append(";");			
		}
		sb.append("var vs={};");
		for (DynScene.TrackRef tr : scene.getTrackRefs()) {
			String dynVolume = tr.getDynVolume();
			if (dynVolume!=null) {
				sb.append("vs['");
				sb.append(tr.getTrack().getId());
				sb.append("']=");
				sb.append(dynVolume);
				sb.append(";");
			}			
		}
		sb.append("return JSON.stringify(vs);");
		String res = scriptEngine.runScript(sb.toString());
		Log.d(TAG,"Load script: "+res+"="+sb.toString());
		Map<Integer,Float> dynVolumes = new HashMap<Integer,Float>();
		try {
			JSONObject vs = new JSONObject(res);
			Iterator<String> keys = vs.keys();
			while(keys.hasNext()) {
				String key = keys.next();
				int id = Integer.valueOf(key);
				String val = vs.get(key).toString();
				try {
					float fval = Float.valueOf(val);
					if (fval<MIN_VOLUME)
						fval = MIN_VOLUME;
					if (fval>MAX_VOLUME)
						fval = MAX_VOLUME;
					dynVolumes.put(id, fval);
				}
				catch (NumberFormatException nfe) {
					Log.w(TAG,"Script returned non-number "+val+" (track "+key+")");
				}				
			}
		}
		catch (Exception e) {
			Log.w(TAG,"error parsing load script result "+res+": "+e);
		}
		return dynVolumes;
	}
	public boolean setScene(String name, IScriptEngine scriptEngine) {
		DynScene scene = mScenes.get(name);
		if (scene==null) {
			Log.w(TAG, "setScene unknown "+name);
			return false;			
		}
		AScene ascene = mEngine.newAScene(scene.isPartial());
		Map<Integer,Float> dynVolumes = getDynVolumes(scriptEngine, scene, true);
		for (DynScene.TrackRef tr : scene.getTrackRefs()) {
			Float volume = dynVolumes.get(tr.getTrack().getId());
			if (volume==null)
				volume = tr.getVolume();
			ascene.set(tr.getTrack(), volume, tr.getPos());
		}
		mEngine.setScene(ascene);
		return true;
	}
	public boolean updateScene(String name, IScriptEngine scriptEngine) {
		DynScene scene = mScenes.get(name);
		if (scene==null) {
			Log.w(TAG, "updateScene unknown "+name);
			return false;			
		}
		// partial
		AScene ascene = mEngine.newAScene(true);
		Map<Integer,Float> dynVolumes = getDynVolumes(scriptEngine, scene, false);
		for (DynScene.TrackRef tr : scene.getTrackRefs()) {
			Float volume = dynVolumes.get(tr.getTrack().getId());
			if (volume!=null)
				ascene.set(tr.getTrack(), volume, null);
		}
		mEngine.setScene(ascene);
		return true;
	}
	public String getNextScene(String name) {
		int ix = mScenesInOrder.indexOf(name);
		if (ix<0) {
			Log.w(TAG,"Could not find scene "+name);
			if (mScenesInOrder.size()>0)
				return mScenesInOrder.get(0);
			return null;
		}
		else {
			ix = ix + 1;
			if (ix >= mScenesInOrder.size())
				ix = 0;
			return mScenesInOrder.get(ix);
		}			
	}
	public String getPrevScene(String name) {
		int ix = mScenesInOrder.indexOf(name);
		if (ix<0) {
			Log.w(TAG,"Could not find scene "+name);
			if (mScenesInOrder.size()>0)
				return mScenesInOrder.get(0);
			return null;
		}
		else {
			ix = ix - 1;
			if (ix <0)
				ix = mScenesInOrder.size() - 1;
			return mScenesInOrder.get(ix);
		}			
	}
}
