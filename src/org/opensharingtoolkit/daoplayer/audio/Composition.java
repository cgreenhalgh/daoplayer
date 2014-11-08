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
import java.util.Map;

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
	private AudioEngine mEngine;
	private String mDefaultScene;
	private Map<String,ITrack> mTracks = new HashMap<String,ITrack>();
	private Map<String,IScene> mScenes = new HashMap<String, IScene>();

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
		mScenes = new HashMap<String, IScene>();
		if (jcomp.has(SCENES)) {
			JSONArray jscenes = jcomp.getJSONArray(SCENES);
			for (int si=0; si<jscenes.length(); si++) {
				JSONObject jscene = jscenes.getJSONObject(si);
				String name = jscene.has(NAME) ? jscene.getString(NAME) : null;
				boolean partial = jscene.has(PARTIAL) && jscene.getBoolean(PARTIAL);
				IScene ascene = mEngine.newScene(partial);
				if (name!=null)
					mScenes.put(name, ascene);
				else 
					Log.w(TAG,"Unnamed scene "+si);
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
							Float volume = jtrack.has(VOLUME) ? (float)jtrack.getDouble(VOLUME) : null;
							ascene.set(atrack, volume, pos);
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
	public boolean setScene(String name) {
		IScene scene = mScenes.get(name);
		if (scene==null) {
			Log.w(TAG, "setScene unknown "+name);
			return false;			
		}
		mEngine.setScene(scene);
		return true;
	}
}
