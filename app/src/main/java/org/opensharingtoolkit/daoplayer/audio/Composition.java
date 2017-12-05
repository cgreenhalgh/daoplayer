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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.opensharingtoolkit.daoplayer.IAudio;
import org.opensharingtoolkit.daoplayer.IAudio.IFile;
import org.opensharingtoolkit.daoplayer.IAudio.IScene;
import org.opensharingtoolkit.daoplayer.IAudio.ITrack;
import org.opensharingtoolkit.daoplayer.ILog;
import org.opensharingtoolkit.daoplayer.audio.ATrack.Section;
import org.opensharingtoolkit.daoplayer.audio.AudioEngine.StateType;

import android.util.JsonWriter;
import android.util.Log;

/** Read a "composition" file.
 * 
 * @author pszcmg
 *
 */
public class Composition {
	private static final String TAG = "daoplayer-compositionreader";

	public static final String ARTIST = "artist";
	private static final String CONSTANTS = "constants";
	private static final String CONTEXT = "context";	
	private static final String COST = "cost";	
	private static final String DEFAULT_NEXT_SECTION_COST = "defaultNextSectionCost";
	private static final String DEFAULT_END_COST = "defaultEndCost";
	private static final String DEFAULT_SCENE = "defaultScene";
	private static final String DESCRIPTION = "description";
	private static final String END_COST = "endCost";	
	private static final String END_COST_EXTRA = "endCostExtra";	
	private static final String FILES = "files";
	private static final String FILE_POS = "filePos";
	private static final String LENGTH = "length";
	private static final String MAX_DURATION = "maxDuration";
	private static final String META = "meta";
	private static final String MERGE = "merge";
	private static final String MIMETYPE = "mimetype";
	private static final String NAME = "name";
	private static final String NEXT = "next";	
	private static final String ONLOAD = "onload";
	private static final String ONUPDATE = "onupdate";
	private static final String PARTIAL = "partial";
	private static final String PATH = "path";
	private static final String PAUSE_IF_SILENT = "pauseIfSilent";
	private static final String POS = "pos";
	private static final String PREPARE = "prepare";
	private static final String REPEATS = "repeats";
	private static final String SCENES = "scenes";
	private static final String SECTIONS = "sections";
	private static final String START_COST = "startCost";	
	public static final String TITLE = "title";
	private static final String TRACKS = "tracks";
	private static final String TRACK_POS = "trackPos";
	private static final String UNIT_TIME = "unitTime";
	private static final String UPDATE = "update";
	private static final String UPDATE_PERIOD = "updatePeriod";
	private static final String VARS = "vars";
	private static final String VERSION = "version";
	private static final String VOLUME = "volume";
	private static final String WAYPOINTS = "waypoints";
	private static final String ROUTES = "routes";

	private static final String COMPOSITION_MIMETYPE = "application/x-daoplayer-composition";
	private static final int MAJOR_VERSION = 1;
	private static final int MINOR_VERSION = 0;
	
	private Pattern mVersionPattern = Pattern.compile("^(\\d+)(\\.(d+)(\\.(d+)(\\-(\\w+))?)?)?$");
	
	private static final double DEFAULT_START_COST = 100000000;
	private static final double DEFAULT_FIRST_SECTION_START_COST = 0;
	private static final double DEFAULT_NEXT_COST = 100000000;
	private static final double DEFAULT_NEXT_SECTION_NEXT_COST = 0;
	private static final double DEFAULT_END_COST_VALUE = 10000;
	private static final double DEFAULT_END_COST_EXTRA = 0;

	private static final long UPDATE_DELAY_MIN_GPS_OFFSET_MS = 250;

	private AudioEngine mEngine;
	private String mDefaultScene;
	private DynConstants mConstants = new DynConstants();
	private Map<String,ITrack> mTracks = new HashMap<String,ITrack>();
	private Map<String,DynScene> mScenes = new HashMap<String, DynScene>();
	private Vector<String> mScenesInOrder = new Vector<String>();
	private Context mContext = new Context();
	private long mFirstSceneLoadTime;
	private long mLastSceneUpdateTime;
	private boolean mLastSceneUpdateFromGps;
	private long mLastSceneLoadTime;
	private UserModel mUserModel;
	private Map<String,String> mMeta = new HashMap<String,String>();
	private File mFile;
	
	public Composition(AudioEngine engine, UserModel userModel) {		
		mEngine = engine;
		mUserModel = userModel;
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
	public void read(File file, ILog log, android.content.Context androidContext) throws IOException, JSONException {
		Log.d(TAG,"read composition from "+file);
		mTracks = new HashMap<String,ITrack>();
		mScenes = new HashMap<String, DynScene>();
		mScenesInOrder = new Vector<String>();
		HashSet<String> mergedFiles = new HashSet<String>();
		merge(file, log, androidContext, false, mergedFiles);
	}
	public static String getVersion() {
		return ""+MAJOR_VERSION+"."+MINOR_VERSION;
	}
	public void merge(File file, ILog log, android.content.Context androidContext, boolean merging, HashSet<String> mergedFiles) throws IOException, JSONException {
		File parent = file.getParentFile();
		String data = readFully(file);
		data = handleIncludes(file, data, log);
		JSONObject jcomp = new JSONObject(data);
		// meta
		if (!jcomp.has(META) || !(jcomp.get(META) instanceof JSONObject)) 
			throw new IOException("File "+file+" is not a valid composition file: no meta section");
		JSONObject jmeta = jcomp.getJSONObject(META);
		if (!jmeta.has(MIMETYPE) || !jmeta.has(VERSION))
			throw new IOException("File "+file+" is not a valid composition file: missing mimetype or version in meta section");
		String mimetype = jmeta.get(MIMETYPE).toString();
		if (!mimetype.equals(COMPOSITION_MIMETYPE))
			throw new IOException("File "+file+" is not a valid composition file: mimetype "+mimetype+" (not "+COMPOSITION_MIMETYPE+")");
		String version = jmeta.getString(VERSION).toString();
		Matcher matcher = mVersionPattern.matcher(version);
		if (!matcher.matches())
			throw new IOException("File "+file+" is not a valid composition file: version "+version+" (not of form a.b.c-d; currently "+getVersion()+")");
		int majorVersion;
		try {
			majorVersion = Integer.parseInt(matcher.group(1));
		}
		catch (NumberFormatException nfe) {
			Log.e(TAG,"Error parsing major version numer "+matcher.group(1)+" from "+version, nfe);
			throw new IOException("File "+file+" is not a valid composition file: version "+version+" (not of form a.b.c-d; currently "+getVersion()+")");
		}
		if (majorVersion!=MAJOR_VERSION)
			throw new IOException("File "+file+" is not supported by this player: version "+version+" (this supports "+getVersion()+")");
		// Minor version?
		int minorVersion = 0;
		if (matcher.group(3)!=null && matcher.group(3).length()>0) {
			try {
				minorVersion = Integer.parseInt(matcher.group(3));
			}
			catch (NumberFormatException nfe) {
				Log.e(TAG,"Error parsing minor version numer "+matcher.group(3)+" from "+version, nfe);
			}			
		}
		if (minorVersion<MINOR_VERSION)
			log.log("Note: composition version is "+version+"; player is "+getVersion());
		else if (minorVersion>MINOR_VERSION)
			log.logError("composition version is "+version+"; player is "+getVersion()+"; some features may not be supported");
		if (!merging) {
			Iterator<String> keys = jmeta.keys();
			while(keys.hasNext()) {
				String key = keys.next();
				mMeta.put(key,  jmeta.get(key).toString());
			}
		}
		if (!merging || mContext==null) {
			mFile = file;
			if (jcomp.has(CONTEXT))
				mContext = Context.parse(jcomp.getJSONObject(CONTEXT), log);
			else
				// empty context
				mContext = new Context();
		} else if (jcomp.has(CONTEXT)) {
			mContext.parse(jcomp.getJSONObject(CONTEXT), merging, log);
		}
		if (!merging || mDefaultScene==null) {
			mDefaultScene = (jcomp.has(DEFAULT_SCENE) ? jcomp.getString(DEFAULT_SCENE) : null);
		}
		if (jcomp.has(CONSTANTS)) {
			mConstants.parse(jcomp.getJSONObject(CONSTANTS), merging);
		}
		if (jcomp.has(TRACKS)) {
			JSONArray jtracks = jcomp.getJSONArray(TRACKS);
			for (int ti=0; ti<jtracks.length(); ti++) {
				if (jtracks.isNull(ti))
					continue;
				JSONObject jtrack = jtracks.getJSONObject(ti);
				String name = jtrack.has(NAME) ? jtrack.getString(NAME) : null;
				boolean pauseIfSilent = jtrack.has(PAUSE_IF_SILENT) && jtrack.getBoolean(PAUSE_IF_SILENT);
				ATrack atrack = (ATrack)mEngine.addTrack(pauseIfSilent);
				atrack.setName(name);
				try {
					JSONObject jlog = new JSONObject();
					jlog.put("id",atrack.getId());
					if(name!=null)
						jlog.put("name", name);
					jlog.put("file", file.getName());
					log.getRecorder().i("track.add", jlog);
				} catch (Exception e) {
					Log.w(TAG,"Error logging addTrack: "+e);
				}
				if (name!=null) {
					if (!merging || !mTracks.containsKey(name))
						mTracks.put(name, atrack);
					else
						log.logError("Ignoring duplicate merged track "+name+" from "+file.getName());
				}
				else
					Log.w(TAG,"Unnamed track "+ti);
				if (jtrack.has(FILES)) {
					JSONArray jfiles = jtrack.getJSONArray(FILES);
					for (int fi=0; fi<jfiles.length(); fi++) {
						if (jfiles.isNull(fi))
							continue;
						JSONObject jfile = jfiles.getJSONObject(fi);
						if (!jfile.has(PATH)) {
							log.logError("track "+ti+" references unspecified file "+fi);
							continue;
						}
						String fpath = jfile.getString(PATH);
						IFile afile = mEngine.addFile(new File(parent, fpath).getCanonicalPath());
						int trackPos = jfile.has(TRACK_POS) ? mEngine.secondsToSamples(jfile.getDouble(TRACK_POS)) : 0;
						int filePos = jfile.has(FILE_POS) ? mEngine.secondsToSamples(jfile.getDouble(FILE_POS)) : 0;
						int length = jfile.has(LENGTH) ? (jfile.getInt(LENGTH)<0 ? jfile.getInt(LENGTH) : mEngine.secondsToSamples(jfile.getDouble(LENGTH))) : IAudio.ITrack.LENGTH_ALL;
						int repeats = jfile.has(REPEATS) ? jfile.getInt(REPEATS) : 1;
						atrack.addFileRef(trackPos, afile, filePos, length, repeats);
					}
				}
				int minSectionLength = 0;
				int maxDuration = 0;
				if (jtrack.has(SECTIONS)) {
					double defaultNextSectionCost = jtrack.has(DEFAULT_NEXT_SECTION_COST) ? jtrack.getDouble(DEFAULT_NEXT_SECTION_COST) : DEFAULT_NEXT_SECTION_NEXT_COST;
					double defaultEndCost = jtrack.has(DEFAULT_END_COST) ? jtrack.getDouble(DEFAULT_END_COST) : DEFAULT_END_COST_VALUE;
					JSONArray jsections = jtrack.getJSONArray(SECTIONS);
					Section lastSection = null;
					for (int fi=0; fi<jsections.length(); fi++) {
						if (jsections.isNull(fi))
							continue;
						JSONObject jsection = jsections.getJSONObject(fi);
						if (!jsection.has(NAME)) {
							log.logError("track "+ti+" references unnamed section "+fi);
							continue;
						}
						String sname = jsection.getString(NAME);
						int trackPos = jsection.has(TRACK_POS) ? mEngine.secondsToSamples(jsection.getDouble(TRACK_POS)) : 0;
						if (trackPos*2>maxDuration)
							maxDuration = trackPos*2;
						if (lastSection!=null && lastSection.mLength == IAudio.ITrack.LENGTH_ALL)
							lastSection.mLength = (trackPos > lastSection.mTrackPos) ? trackPos-lastSection.mTrackPos : 0;
						int length = jsection.has(LENGTH) ? (jsection.getInt(LENGTH)<0 ? jsection.getInt(LENGTH) : mEngine.secondsToSamples(jsection.getDouble(LENGTH))) : IAudio.ITrack.LENGTH_ALL;
						if (length>0 && (minSectionLength==0 || length<minSectionLength))
							minSectionLength = length;
						// TODO altLengths
						double startCost = jsection.has(START_COST) ? jsection.getDouble(START_COST) : (fi==0 ? DEFAULT_FIRST_SECTION_START_COST : DEFAULT_START_COST);
						double endCost = jsection.has(END_COST) ? jsection.getDouble(END_COST) : defaultEndCost;
						double endCostExtra = jsection.has(END_COST_EXTRA) ? jsection.getDouble(END_COST_EXTRA) : DEFAULT_END_COST_EXTRA;
						// adjust to per sample
						endCostExtra = endCostExtra/mEngine.secondsToSamples(1.0);
						Section section = new Section(sname, trackPos, length, startCost, endCost, endCostExtra);
						if (jsection.has(NEXT)) {
							JSONArray jnext = jsection.getJSONArray(NEXT);
							for (int ni=0; ni<jnext.length(); ni++) {
								if (jnext.isNull(ni))
									continue;
								JSONObject jnextSection = jnext.getJSONObject(ni);
								if (!jnextSection.has(NAME)) {
									log.logError("track "+ti+" section "+sname+" has unnamed next section "+ni);
									continue;
								}
								String nextName = jnextSection.getString(NAME);
								double cost = jnextSection.has(COST) ? jnextSection.getDouble(COST) : 0;
								section.addNext(nextName, cost);
							}
						}
						if (lastSection!=null) {
							boolean found = false;
							if (lastSection.mNext!=null)
								for (ATrack.NextSection next : lastSection.mNext)
									if (next.mName.equals(sname))
										found = true;
							if (!found)
								lastSection.addNext(sname, defaultNextSectionCost);
						}
						atrack.addSection(section);
						lastSection = section;
					}
				}
				if (jtrack.has(UNIT_TIME))
					atrack.setUnitTime(mEngine.secondsToSamples(jtrack.getDouble(UNIT_TIME)));
				else
					atrack.setUnitTime(minSectionLength);
				if (jtrack.has(MAX_DURATION))
					atrack.setMaxDuration(mEngine.secondsToSamples(jtrack.getDouble(MAX_DURATION)));
				else
					atrack.setMaxDuration(maxDuration);
				if (atrack.getSections()!=null && atrack.getSections().size()>0) {
					Log.d(TAG,"Create SectionSelector for "+atrack.getName());
					SectionSelector selector = new SectionSelector(atrack, atrack.getMaxDuration(), mEngine.getLog());
					selector.prepare();
					// debug
					selector.dump(androidContext);
					mSectionSelectors.put(atrack.getName(), selector);
				}
			}
		}
		if (jcomp.has(SCENES)) {
			JSONArray jscenes = jcomp.getJSONArray(SCENES);
			for (int si=0; si<jscenes.length(); si++) {
				if (jscenes.isNull(si))
					continue;
				JSONObject jscene = jscenes.getJSONObject(si);
				String name = jscene.has(NAME) ? jscene.getString(NAME) : null;
				mScenesInOrder.add(name);
				boolean partial = jscene.has(PARTIAL) && jscene.getBoolean(PARTIAL);
				DynScene ascene = new DynScene(partial);
				if (name!=null) {
					if (!merging || !mScenes.containsKey(name))
						mScenes.put(name, ascene);
					else
						log.logError("Ignoring duplicate merged scene "+name+" from "+file.getName());
				}
				else 
					log.logError("Unnamed scene "+si);
				Map<String,String> smeta = ascene.getMeta();
				if (jscene.has(TITLE))
					smeta.put(TITLE, jscene.getString(TITLE));
				if (jscene.has(DESCRIPTION))
					smeta.put(DESCRIPTION, jscene.getString(DESCRIPTION));
				if (jscene.has(ARTIST))
					smeta.put(ARTIST, jscene.getString(ARTIST));
				if (jscene.has(CONSTANTS)) {
					DynConstants cons = new DynConstants();
					cons.parse(jscene.getJSONObject(CONSTANTS), false);
					ascene.setConstants(cons);
				}
				if (jscene.has(VARS)) {
					DynConstants vars = new DynConstants();
					vars.parse(jscene.getJSONObject(VARS), false);
					ascene.setVars(vars);
				}
				if (jscene.has(UPDATE_PERIOD))
					ascene.setUpdatePeriod(jscene.getDouble(UPDATE_PERIOD));
				if (jscene.has(ONLOAD))
					ascene.setOnload(jscene.getString(ONLOAD));
				if (jscene.has(ONUPDATE))
					ascene.setOnupdate(jscene.getString(ONUPDATE));
				if (jscene.has(WAYPOINTS)) {
					Map<String,String> waypoints = new HashMap<String,String>();
					JSONObject jwaypoints = jscene.getJSONObject(WAYPOINTS);
					Iterator<String> keys = jwaypoints.keys();
					while (keys.hasNext()) {
						String key = keys.next();
						String value = jwaypoints.getString(key);
						waypoints.put(key, value);
					}
					ascene.setWaypoints(waypoints);
				}
				if (jscene.has(ROUTES)) {
					Map<String,String> routes = new HashMap<String,String>();
					JSONObject jroutes = jscene.getJSONObject(ROUTES);
					Iterator<String> keys = jroutes.keys();
					while (keys.hasNext()) {
						String key = keys.next();
						String value = jroutes.getString(key);
						routes.put(key, value);
					}
					ascene.setRoutes(routes);
				}
				if (jscene.has(TRACKS)) {
					JSONArray jtracks = jscene.getJSONArray(TRACKS);
					for (int ti=0; ti<jtracks.length(); ti++) {
						if (jtracks.isNull(ti))
							continue;
						JSONObject jtrack = jtracks.getJSONObject(ti);
						if (!jtrack.has(NAME)) {
							log.logError("Scene "+name+" include unnamed track "+ti);
							continue;
						}
						String trackName = jtrack.getString(NAME);
						ITrack atrack = mTracks.get(trackName);
						if (atrack==null) {
							log.logError("Scene "+name+" refers to unknown track "+trackName);
						} else {
							Log.d(TAG,"Scene "+name+" uses track "+atrack.getId()+" as "+trackName);
							Integer pos = jtrack.has(POS) && jtrack.get(POS) instanceof Number ? mEngine.secondsToSamples(jtrack.getDouble(POS)) : null;
							String dynPos = jtrack.has(POS) && jtrack.get(POS) instanceof String ? jtrack.getString(POS) : null;
							Float volume = jtrack.has(VOLUME) && jtrack.get(VOLUME) instanceof Number ? (float)jtrack.getDouble(VOLUME) : null;
							String dynVolume = jtrack.has(VOLUME) && jtrack.get(VOLUME) instanceof String ? jtrack.getString(VOLUME) : null;
							Boolean prepare = jtrack.has(PREPARE) && jtrack.get(PREPARE) instanceof Boolean ? jtrack.getBoolean(PREPARE) : null;
							boolean update = jtrack.has(UPDATE) && jtrack.get(UPDATE) instanceof Boolean ? jtrack.getBoolean(UPDATE) : true;
							ascene.set(atrack, volume, dynVolume, pos, dynPos, prepare, update);
						}
					}
				}
			}
		}
		log.log("Read composition "+(jmeta.has(TITLE) ? jmeta.getString(TITLE) : "(unnamed)")+" from "+file);
		if (jcomp.has(MERGE)) {
			JSONArray files = jcomp.getJSONArray(MERGE);
			for (int i=0; i<files.length(); i++) {
				String filename = files.getString(i);
				if (mergedFiles.contains(filename)) 
					log.log("ignore duplicate merge of "+filename);
				else {
					Log.d(TAG,"merge file "+filename);
					mergedFiles.add(filename);
					merge(new File(file.getParent(), filename), log, androidContext, true, mergedFiles);
				}
			}
		}
	}
	private Pattern includePattern = Pattern.compile("[\"][#]((json)|(string))\\s+([^\"]+)[\"]");

	private String handleIncludes(File file, String data, ILog log) throws IOException {
		Matcher m = includePattern.matcher(data);
		StringBuilder sb = new StringBuilder();
		int pos = 0;
		while(m.find()) {
			sb.append(data, pos, m.start());
			pos = m.end();
			String op = m.group(1);
			String filename = m.group(4);
			String incdata;
			Log.d(TAG,"Found #"+op+" "+filename+" in "+file);
			try {
				incdata = readFully(new File(file.getParentFile(), filename));
			}
			catch (IOException e) {
				throw new IOException("Error reading #"+op+" file "+filename+" in "+file+": "+e);
			}
			if (op.equals("string")) {
				JSONStringer js = new JSONStringer();
				try {
					js.array();
					js.value(incdata);
					js.endArray();
					String jstring = js.toString();
					sb.append(jstring,1,jstring.length()-1);
				} catch (JSONException e) {
					Log.e(TAG,"stringing #string "+filename+" ("+incdata+"): "+e, e);
					throw new IOException("Error handling #string "+filename+" in "+file+": "+e);
				}
			} else if (op.equals("json")) {
				try {
					JSONObject jobj = new JSONObject(incdata);
					sb.append(jobj.toString());
				}
				catch (JSONException e) {
					throw new IOException("Error parsing (#json) "+filename+": "+e+" in "+incdata);
				}
			} else
				throw new IOException("Unsupported operator #"+op+" in "+file);
		}
		// rest of string
		Log.d(TAG, "No #op found in last "+pos+"-"+data.length());
		sb.append(data, pos, data.length());
		return sb.toString();
	}
	public File getFile() {
		return mFile;
	}
	public Map<String,String> getMeta() {
		return mMeta;
	}
	public String getTitle() {
		String title = mMeta.get(TITLE);
		if (title==null) {
			if (mFile==null)
				title = "(no file loaded)";
			else
				title = "untitled (file "+mFile.getName()+")";
		}
		return title;
	}
	/**
	 * @return the context
	 */
	public Context getContext() {
		return mContext;
	}
	/**
	 * @return the mDefaultScene
	 */
	public String getDefaultScene() {
		return mDefaultScene;
	}
	private static float MIN_VOLUME = 0.0f;
	private static float MAX_VOLUME = 16.0f;
	static class DynInfo {
		Float volume;
		float [] pwlVolume;
		int [] align;
	}
	/** value in map is either null, Float (single volume) or float[] (array of args for pwl) */
	private Map<Integer,DynInfo> getDynInfo(IScriptEngine scriptEngine, DynScene scene, boolean loadFlag, long time) {
		AudioEngine.StateRec srec = mEngine.getFutureState();
		AState astate = (srec!=null ? srec.getState() : null);
		JSONObject loginfo = new JSONObject();
		try {
			loginfo.put("loadFlag",loadFlag);
		} catch (JSONException e2) {
			Log.w(TAG,"Error marshalling loadFlag", e2);
		}
		StringBuilder sb = new StringBuilder();
		// "built-in"
		sb.append("var pwl=window.pwl;\n");
		sb.append(";\n");
		sb.append("var distance=function(coord1,coord2){return window.distance(coord1,coord2 ? coord2 : position);};\n");
		sb.append("var sceneTime=");
		double oldSceneTime = (srec==null) ? 0 : srec.mSceneTime+(srec.mType!=StateType.STATE_FUTURE ? mEngine.samplesToSeconds(mEngine.getFutureOffset()) : 0);
		double newSceneTime = loadFlag ? 0 : oldSceneTime;
		sb.append(newSceneTime);
		try {
			loginfo.put("sceneTime", newSceneTime);
		} catch (JSONException e2) {
			Log.w(TAG,"Error marshalling sceneTime", e2);
		}
		sb.append(";\n");
		sb.append("var totalTime=");
		double totalTime = (srec!=null) ? srec.mTotalTime+(srec.mType!=StateType.STATE_FUTURE ? mEngine.samplesToSeconds(mEngine.getFutureOffset()) : 0) : 0;
		sb.append(totalTime);
		try {
			loginfo.put("totalTime", totalTime);
		} catch (JSONException e2) {
			Log.w(TAG,"Error marshalling totalTime", e2);
		}
		sb.append(";\n");
		StringBuilder umsb = new StringBuilder();
		mUserModel.toJavascript(umsb, scene.getWaypoints(), scene.getRoutes());
		String ums = umsb.toString();
		sb.append(ums);
		try {
			loginfo.put("userModel", ums);
		} catch (JSONException e1) {
			Log.d(TAG,"Error marshalling userModel", e1);
		}
		// constants: composition, scene
		mConstants.toJavascript(sb);
		if (scene.getConstants()!=null)
			scene.getConstants().toJavascript(sb);
		if (scene.getVars()!=null)
			scene.getVars().toJavascript(sb);
		// late text
		StringBuilder sb2 = new StringBuilder();
		if (loadFlag && scene.getOnload()!=null) {
			sb2.append(scene.getOnload());
			sb2.append(";\n");
		} else if (!loadFlag && scene.getOnupdate()!=null) {
			sb2.append(scene.getOnupdate());
			sb2.append(";\n");
		}
		sb.append("var vs={};\n");
		sb.append("var ps={};\n");
		// track volumes
		sb.append("var tvs={};\n");
		// track positions
		sb.append("var tps={};\n");
		// track sections
		sb.append("var tss={};\n");
		for (DynScene.TrackRef tr : scene.getTrackRefs()) {
			String dynVolume = tr.getDynVolume();
			String dynPos = tr.getDynPos();
			if (dynVolume!=null || dynPos!=null) {
				AState.TrackRef atr = (astate!=null) ? astate.get(tr.getTrack()) : null;
				if (!loadFlag && tr.getUpdate()==Boolean.FALSE)
					continue;
				int trackPos = 0;
				float currentVolume = 0; /* default?! */
				if (loadFlag && tr.getPos()!=null)
					trackPos = tr.getPos();
				// partial default is current
				else {
					// extrapolate...
					if (atr!=null) {
						trackPos = atr.getPos();
						if (!atr.isPaused())
							trackPos += mEngine.getFutureOffset();
						int align[] = atr.getAlign();
						if (align!=null) {
							int sceneTime = mEngine.secondsToSamples(oldSceneTime);
							// each aligned sub-section
							for (int ai=0; ai<=(align!=null ? align.length : 0); ai+=2)
							{
								if (align!=null && align.length>=2) {
									if (ai<align.length) {
										// up to an alignment (but not the "new" alignment so currentSection will be last played)
										if (align[ai]<sceneTime) {
											// already past
											trackPos = align[ai+1]+sceneTime-align[ai];
											//Log.d(TAG,"Align track at +"+bufStart+": "+sceneTime+" -> "+tr.getPos()+" (align "+align[ai]+"->"+align[ai+1]+")");
											continue;
										}
									}
								}
								break;
							}
						}
					}
				}
				// for volume we want the old volume. This may be undefined on
				// first load of a composition.
				if (atr!=null) {
					float pwlVolume[] = atr.getPwlVolume();
					if (pwlVolume!=null)
						currentVolume = AudioEngine.pwl((float)oldSceneTime, pwlVolume);
					else
						currentVolume = atr.getVolume();
				}
				else
					Log.d(TAG, "Could not find track "+tr.getTrack().getId()+" for currentVolume (load="+loadFlag+")");
				double trackTime = mEngine.samplesToSeconds(trackPos);
				if (tr.getTrack() instanceof ATrack) {
					ATrack at = (ATrack)tr.getTrack();
					sb.append("tps['");
					sb.append(at.getName());
					sb.append("']=");
					sb.append(trackTime);
					sb.append(";");
					sb.append("tvs['");
					sb.append(at.getName());
					sb.append("']=");
					sb.append(currentVolume);
					sb.append(";");
				}
				if (dynVolume!=null) {
					sb2.append("vs['");
					sb2.append(tr.getTrack().getId());
					sb2.append("']=(function(trackTime,trackVolume){return(");
					sb2.append(dynVolume);
					sb2.append(");})(");
					sb2.append(trackTime);
					sb2.append(",");
					sb2.append(currentVolume);
					sb2.append(");\n");
				}
				if (dynPos!=null) {
					// current section just based on trackTime
					Section section = null;
					ATrack atrack = (ATrack)tr.getTrack();
					for (Section s : atrack.getSections().values()) {
						// NB last thing we played
						if (trackPos-1>=s.mTrackPos && (s.mLength==IAudio.ITrack.LENGTH_ALL || trackPos-1-s.mTrackPos<s.mLength)) {
							section = s;
							//Log.d(TAG,"Pos "+trackPos+" in section "+s.mName+" ("+s.mTrackPos+" + "+s.mLength+")");
							break;
						}
						//else
						//	Log.d(TAG,"Pos "+trackPos+" not in section "+s.mName+" ("+s.mTrackPos+" + "+s.mLength+")");
					}
					sb2.append("ps['");
					sb2.append(tr.getTrack().getId());
					sb2.append("']=(function(trackId,trackTime,currentSection){return(");
					sb2.append(dynPos);
					sb2.append(");})(");
					sb2.append(tr.getTrack().getId());
					sb2.append(",");
					sb2.append(trackTime);
					sb2.append(",");
					if (section==null)
						sb2.append("null");
					else {
						sb.append("var ts");
						sb.append(tr.getTrack().getId());
						sb.append("=");
						sb.append("{name:");
						sb.append(escapeJavascriptString(section.mName));
						sb.append(",startTime:");
						// typically negative if new scene
						sb.append(newSceneTime+mEngine.samplesToSeconds(section.mTrackPos)-trackTime);
						if (section.mLength!=IAudio.ITrack.LENGTH_ALL) {
							sb.append(",endTime:");
							sb.append(newSceneTime+mEngine.samplesToSeconds(section.mTrackPos+section.mLength)-trackTime);
						}
						sb.append("};");
						sb2.append("ts");
						sb2.append(tr.getTrack().getId());
						if (tr.getTrack() instanceof ATrack) {
							ATrack at = (ATrack) tr.getTrack();
							sb.append("tss['");
							sb.append(at.getName());
							sb.append("']=ts");
							sb.append(tr.getTrack().getId());
							sb.append(";");
						}
					}
					sb2.append(");\n");
				}
			}			
		}
		sb2.append("return JSON.stringify({vs:vs,ps:ps});");
		sb.append(sb2.toString());
		String res = scriptEngine.runScript(sb.toString());
		try {
			loginfo.put("res", res);
		} catch (JSONException e1) {
			Log.w(TAG,"Error marshalling res", e1);
		}
		Map<Integer, DynInfo> dynInfos = new HashMap<Integer, DynInfo>();
		if (res==null) {
			// Error details already logged at a lower level
			Log.d(TAG,"update script timed out");
		} else {
			Log.d(TAG, "run script: " + sb.toString() + "\n-> " + res);
			try {
				JSONObject jres = new JSONObject(res);
				JSONObject vs = jres.getJSONObject("vs");
				Iterator<String> keys = vs.keys();
				while (keys.hasNext()) {
					String key = keys.next();
					int id = Integer.valueOf(key);
					DynInfo di = new DynInfo();
					dynInfos.put(id, di);
					Object val = vs.get(key);
					if (val instanceof JSONArray) {
						JSONArray aval = (JSONArray) val;
						float fvals[] = new float[aval.length()];
						for (int i = 0; i < aval.length(); i += 2) {
							fvals[i] = extractFloat(aval.get(i));
							if (i + 1 < aval.length())
								fvals[i + 1] = clipVolume(extractFloat(aval.get(i + 1)));
						}
						di.pwlVolume = fvals;
					} else if (val instanceof Number || (val instanceof String && !"".equals(val))) {
						float fval = clipVolume(extractFloat(val));
						di.volume = fval;
					} else if (val == null || "".equals(val)) {
						// null
					} else {
						mEngine.getLog().logError("Volume script returned non-number/string " + val);
						di.volume = 0.0f;
					}
				}
				JSONObject ps = jres.getJSONObject("ps");
				keys = ps.keys();
				while (keys.hasNext()) {
					String key = keys.next();
					int id = Integer.valueOf(key);
					DynInfo di = dynInfos.get(id);
					if (di == null) {
						di = new DynInfo();
						dynInfos.put(id, di);
					}
					Object val = ps.get(key);
					if (val instanceof JSONArray) {
						JSONArray aval = (JSONArray) val;
						// map strings to track section names; scene time(s) default to sceneTime (first) or end of current section
						int outlen = 0;
						for (int i = 0; i < aval.length(); i++) {
							if (aval.get(i) instanceof String) {
								if ((outlen % 2) == 0)
									// infer position
									outlen += 2;
								else
									outlen++;
							} else
								outlen++;
						}
						int ivals[] = new int[outlen];
						int nextSceneTime = mEngine.secondsToSamples(newSceneTime);
						for (int i = 0, j = 0; i < aval.length(); i++, j++) {
							if (aval.get(i) instanceof String) {
								if ((j % 2) == 0) {
									// infer scene time
									ivals[j++] = nextSceneTime;
								}
								// map section to track time
								String sname = aval.getString(i);
								ITrack track = scene.getTrack(id);
								if (track == null || !(track instanceof ATrack)) {
									mEngine.getLog().logError("Pos script returned section name '" + sname + "' for unknown track " + id);
									continue;
								}
								ATrack atrack = (ATrack) track;
								Map<String, Section> sections = atrack.getSections();
								Section section = (sections != null) ? sections.get(sname) : null;
								if (section == null) {
									mEngine.getLog().logError("Pos script returned unknown section name '" + sname + "' for track " + id);
									continue;
								}
								// start of section
								ivals[j] = section.mTrackPos;
								// adjust next scene time for length
								nextSceneTime += section.mLength;
							} else if (aval.isNull(i) && (j % 2) == 0) {
								// special case null time
								ivals[j] = nextSceneTime;
							} else {
								int ival = (int) mEngine.secondsToSamples((double) extractFloat(aval.get(i)));
								ivals[j] = ival;
								if ((j % 2) == 0)
									nextSceneTime = ival;
								Log.d(TAG, "dynpos align " + j + " (" + i + ") = " + ival + " samples (" + aval.get(i) + ")");
							}
						}
						di.align = ivals;
					} else if (val instanceof Number || (val instanceof String && !"".equals(val))) {
						float fval = extractFloat(val);
						//Log.d(TAG,"dynPos single value "+fval+"-> array");
						di.align = new int[2];
						di.align[0] = (int) mEngine.secondsToSamples(newSceneTime); // ? was srec!=null ? srec.mSceneTime : 0);
						di.align[1] = (int) mEngine.secondsToSamples((double) fval);
					} else if (val == null || "".equals(val)) {
						// null
					} else {
						mEngine.getLog().logError("Pos script returned non-number/string " + val);
					}
				}
			} catch (Exception e) {
				Log.w(TAG, "error parsing load script result " + res + ": " + e, e);
				mEngine.getLog().logError("Script returned error: " + res + ", from " + sb.toString());
			}
		}
		mEngine.getLog().getRecorder().i("update", loginfo);
		return dynInfos;
	}
	private String escapeJavascriptString(String mName) {
		StringBuilder sb = new StringBuilder();
		sb.append("'");
		sb.append(mName.replace("\\", "\\\\").replace("'", "\\'"));
		sb.append("'");
		return sb.toString();
	}
	private float extractFloat(Object val) {
		if (val instanceof Number) {
			return ((Number)val).floatValue();
		}
		else if (val instanceof String) {
			try {
				float fval = Float.valueOf((String)val);
				return fval;
			}
			catch (NumberFormatException nfe) {
				mEngine.getLog().logError("Script returned non-number "+val);
				return 0;
			}				
		}
		else {
			mEngine.getLog().logError("Script returned non-number/string "+val);
			return 0;			
		}
	}
	private float clipVolume(float fval) {
		if (fval<MIN_VOLUME)
			fval = MIN_VOLUME;
		if (fval>MAX_VOLUME)
			fval = MAX_VOLUME;
		return fval;
	}
	public boolean setScene(String name, IScriptEngine scriptEngine) {
		DynScene scene = mScenes.get(name);
		if (scene==null) {
			mEngine.getLog().logError("setScene unknown "+name);
			return false;			
		}
		AScene ascene = mEngine.newAScene(scene.isPartial());
		mLastSceneLoadTime = System.currentTimeMillis();
		if (mFirstSceneLoadTime==0)
			mFirstSceneLoadTime = mLastSceneLoadTime;
		mLastSceneUpdateTime = mLastSceneLoadTime;
		mLastSceneUpdateFromGps = false;
		Map<Integer,DynInfo> dynInfos = getDynInfo(scriptEngine, scene, true, mLastSceneLoadTime);
		for (DynScene.TrackRef tr : scene.getTrackRefs()) {
			DynInfo di = dynInfos.get(tr.getTrack().getId());
			if (di!=null && di.volume!=null) {
				if (di.align!=null)
					ascene.set(tr.getTrack(), di.volume, di.align, tr.getPrepare());
				else
					ascene.set(tr.getTrack(), di.volume, tr.getPos(), tr.getPrepare());
			}
			else if (di!=null && di.pwlVolume!=null) {
				if (di.align!=null)
					ascene.set(tr.getTrack(), di.pwlVolume, di.align, tr.getPrepare());
				else
					ascene.set(tr.getTrack(), di.pwlVolume, tr.getPos(), tr.getPrepare());
			}
			else if (di!=null && di.align!=null)
				ascene.set(tr.getTrack(), tr.getVolume(), di.align, tr.getPrepare());
			else
				ascene.set(tr.getTrack(), tr.getVolume(), tr.getPos(), tr.getPrepare());
		}
		mEngine.setScene(ascene, true);
		return true;
	}
	public boolean updateScene(String name, IScriptEngine scriptEngine, boolean fromGps) {
		DynScene scene = mScenes.get(name);
		if (scene==null) {
			Log.w(TAG, "updateScene unknown "+name);
			return false;			
		}
		// partial
		AScene ascene = mEngine.newAScene(true);
		mLastSceneUpdateTime = System.currentTimeMillis();
		mLastSceneUpdateFromGps = fromGps;
		Map<Integer,DynInfo> dynInfos = getDynInfo(scriptEngine, scene, false, mLastSceneUpdateTime);
		for (DynScene.TrackRef tr : scene.getTrackRefs()) {
			DynInfo di = dynInfos.get(tr.getTrack().getId());
			if (di!=null && di.volume!=null) {
				if (di.align!=null)
					ascene.set(tr.getTrack(), di.volume, di.align, (Boolean)null);
				else
					ascene.set(tr.getTrack(), di.volume, (Integer)null, null);
			}
			else if (di!=null && di.pwlVolume!=null) {
				if (di.align!=null)
					ascene.set(tr.getTrack(), di.pwlVolume, di.align, null);
				else
					ascene.set(tr.getTrack(), di.pwlVolume, (Integer)null, null);
			}
			else if (di!=null && di.align!=null)
				ascene.set(tr.getTrack(), (Float)null, di.align, null);
		}
		mEngine.setScene(ascene, false);
		return true;
	}
	public Long getSceneUpdateDelay(String name) {
		DynScene scene = mScenes.get(name);
		if (scene==null) {
			Log.w(TAG, "getSceneUpdateDelay unknown "+name);
			return null;			
		}
		Double period = scene.getUpdatePeriod();
		if (period==null || period<=0)
			return null;
		long now = System.currentTimeMillis();
		long elapsed = now-mLastSceneUpdateTime;
		long lperiod = ((long)(period*1000));
		if (mLastSceneUpdateFromGps && elapsed<1000+UPDATE_DELAY_MIN_GPS_OFFSET_MS) {
			// fiddle to avoid GPS race
			if (lperiod>1000-UPDATE_DELAY_MIN_GPS_OFFSET_MS && lperiod<1000+UPDATE_DELAY_MIN_GPS_OFFSET_MS)
				lperiod = 1000+UPDATE_DELAY_MIN_GPS_OFFSET_MS;
		}
		long delay = lperiod-elapsed;
		if (delay<0)
			return 0L;
		return delay;
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
	/** return copy of scene names in order */
	public Collection<String> getScenes() {
		Vector<String> scenes = new Vector<String>();
		if (mScenesInOrder!=null)
			scenes.addAll(mScenesInOrder);
		return scenes;
	}
	public DynScene getScene(String sceneName) {
		if (mScenes==null)
			return null;
		return mScenes.get(sceneName);
	}
	public String getTestTrack(String name) {
		DynScene scene = mScenes.get(name);
		if (scene==null) {
			Log.w(TAG, "getTestTrack: scene unknown "+name);
			return null;			
		}
		for (DynScene.TrackRef tr : scene.getTrackRefs()) {
			if (tr.getVolume()!=null && tr.getVolume()<=0)
				continue;
			ATrack track = (ATrack)tr.getTrack();
			ATrack.FileRef fr = track.mFileRefs.first();
			if (fr!=null)
				return fr.mFile.getPath();
		}
		return null;
	}
	public synchronized Map<String,String> getWaypoints(String sceneName) {
		DynScene scene = mScenes.get(sceneName);
		if (scene==null) {
			Log.w(TAG, "getWaypoints: scene unknown "+sceneName);
			return null;			
		}
		return scene.getWaypoints();		
	}
	private Map<String,SectionSelector> mSectionSelectors = new HashMap<String,SectionSelector>();
	public Object[] selectSections(String trackName, String currentSectionName,
			int currentSectionTime, int sceneTime, int targetDuration) {
		SectionSelector selector = mSectionSelectors.get(trackName);
		if (selector==null) {
			mEngine.getLog().logError("selectSections called for unknown track "+trackName);
			return null;
		}
		return selector.selectSections(currentSectionName, currentSectionTime, sceneTime, targetDuration);
	}
	public Map<String, String> getRoutes(String sceneName) {
		DynScene scene = mScenes.get(sceneName);
		if (scene==null) {
			Log.w(TAG, "getRoutes: scene unknown "+sceneName);
			return null;			
		}
		return scene.getRoutes();		
	}
}
