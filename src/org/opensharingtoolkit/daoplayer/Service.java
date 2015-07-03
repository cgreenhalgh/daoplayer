/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.opensharingtoolkit.daoplayer.audio.AudioEngine;
import org.opensharingtoolkit.daoplayer.audio.Composition;
import org.opensharingtoolkit.daoplayer.audio.DynScene;
import org.opensharingtoolkit.daoplayer.audio.FileCache;
import org.opensharingtoolkit.daoplayer.audio.FileDecoder;
import org.opensharingtoolkit.daoplayer.audio.IScriptEngine;
import org.opensharingtoolkit.daoplayer.audio.UserModel;
import org.opensharingtoolkit.daoplayer.logging.Recorder;
import org.opensharingtoolkit.daoplayer.ui.BrowserActivity;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.Toast;

/**
 * @author pszcmg
 *
 */
public class Service extends android.app.Service implements OnSharedPreferenceChangeListener, ILog, OnInitListener, OnUtteranceCompletedListener {

	private static final String TAG = "daoplayer-service";
	private static final int SERVICE_NOTIFICATION_ID = 1;
	public static final String ACTION_RELOAD = "org.opensharingtoolkit.daoplayer.RELOAD";
	static final String DEFAULT_COMPOSITION = "composition.json";
	public static final String ACTION_DEFAULT_SCENE = "org.opensharingtoolkit.daoplayer.DEFAULT_SCENE";
	public static final String ACTION_NEXT_SCENE = "org.opensharingtoolkit.daoplayer.NEXT_SCENE";
	public static final String ACTION_PREV_SCENE = "org.opensharingtoolkit.daoplayer.PREV_SCENE";
	public static final String ACTION_SET_LATLNG = "org.opensharingtoolkit.daoplayer.SET_LATLNG";
	public static final String ACTION_SET_SCENE = "org.opensharingtoolkit.daoplayer.SET_SCENE";
	public static final String ACTION_CLEAR_LOGS = "org.opensharingtoolkit.daoplayer.CLEAR_LOGS";
	public static final String ACTION_RUN_TEST = "org.opensharingtoolkit.daoplayer.RUN_TEST";
	public static final String ACTION_SCENE_CHANGED = "org.opensharingtoolkit.daoplayer.SCENE_CHANGED";
	public static final String ACTION_GPS_STATUS = "org.opensharingtoolkit.daoplayer.GPS_STATUS";
	public static final String EXTRA_LAT = "lat";
	public static final String EXTRA_LNG = "lng";
	public static final String EXTRA_SCENE = "scene";
	public static final String EXTRA_TIME = "time";
	public static final String EXTRA_ACCURACY = "accuracy";
	public static final String EXTRA_ELAPSEDTIME = "elapsedtime";
	public static final String EXTRA_FILENAME = "filename";
	public static final String EXTRA_TITLE = "title";
	public static final String EXTRA_ARTIST = "artist";
	public static final String EXTRA_STATUS = "status";
	public static final String EXTRA_SUBSTATUS = "substatus";
	
	private static final String PREF_USEGPS = "pref_usegps";
	private static final String PREF_ENABLESPEECH = "pref_enablespeech";
	static final String PREF_FILENAME = "pref_filename";
	private static final double DEFAULT_SPEECH_VOLUME = 1;
	public static final long NANOS_PER_MILLI = 1000000;
	private static final long JAVASCRIPT_SET_SCENE_DELAY_MS = 0;
	private AudioEngine mAudioEngine;
	private boolean started = false;
	private boolean logGps = false;
	private Composition mComposition = null;
	private String mScene = null;
	private WebView mWebView;
	private double mLastLat, mLastLng, mLastAccuracy;
	private long mLastTime;
	private long mLastElapsedtime;
	private boolean mGpsStarted = false;
	private boolean mEnableSpeech = false;
	private TextToSpeech mTextToSpeech = null;
	private boolean mSpeechReady = false;
	private boolean mSpeechFailed = false;
	private Vector<String> mSpeechDelayed = new Vector<String>();
	HashMap<String, String> mSpeechParameters = new HashMap<String,String>();
	protected Recorder mRecorder = new Recorder(this, "daoplayer.service");
	private UserModel mUserModel = new UserModel(mRecorder);
	private boolean mWebViewLoaded = false;
	private String mSetSceneOnLoad = null;
	private String mSetSceneOnStart = null;
	private GpsInfo mGpsStatus = new GpsInfo();
			
	static enum LogEntryType { LOG_ERROR, LOG_INFO };
	class LogEntry {
		LogEntryType type;
		long time;
		String message;
	}
	private LinkedList<LogEntry> mLog = new LinkedList<LogEntry>();
	
	/** Binder subclass (inner class) with methods for local interaction with service */
	public class LocalBinder extends android.os.Binder {
		// local methods... direct access to service
		public IAudio getAudio() {
			return mAudioEngine;
		}
		public String getLogs(long fromTime, long toTime) {
			StringBuilder sb = new StringBuilder();
			synchronized (mLog) {
				for (LogEntry ent : mLog) {
					if (ent.time>=fromTime && ent.time<toTime) {
						if(ent.type==LogEntryType.LOG_ERROR)
							sb.append("ERROR: ");
						sb.append(ent.message);
						sb.append("\n");
					}
				}
			}
			return sb.toString();
		}
		public Collection<String> getScenes() {
			synchronized(Service.this) {
				if (mComposition!=null)
					return mComposition.getScenes();
			}
			return null;
		}
		public String getStatus() {
			JSONObject jstatus = new JSONObject();
			try {
				synchronized (Service.this) {
					if (mComposition!=null) 
						jstatus.put("composition", mComposition.getTitle());
					if (Service.this.mAudioEngine!=null) {
						jstatus.put("audioEngine",  mAudioEngine.getStatus());
					}
					String scene = Service.this.mScene;
					jstatus.put("scene", scene);
					Map<String,String> waypoints = null;
					Map<String,String> routes = null;
					if (Service.this.mComposition!=null) {
						waypoints = mComposition.getWaypoints(scene);
						routes = mComposition.getRoutes(scene);
					}					
					if (Service.this.mUserModel!=null) {
						StringBuilder sb = new StringBuilder();
						mUserModel.toJavascript(sb, waypoints, routes);
						jstatus.put("userModel", sb.toString());
					}
					jstatus.put("speechReady", mSpeechReady);
					jstatus.put("speechFailed", mSpeechFailed);
					jstatus.put("started", started);
				}
			} catch (JSONException e) {
				Log.w(TAG,"Error getting status", e);
			}
			try {
				return jstatus.toString(4);
			} catch (JSONException e) {
				Log.w(TAG,"Error returning status", e);
				return "\"Error returning status\"";
			}
		}
		public String getUserModel() {
			try {
				if (Service.this.mUserModel!=null) {
					StringBuilder sb = new StringBuilder();
					String scene = Service.this.mScene;
					Map<String,String> waypoints = null;
					Map<String,String> routes = null;
					if (Service.this.mComposition!=null) {
						waypoints = mComposition.getWaypoints(scene);
						routes = mComposition.getRoutes(scene);
					}					
					mUserModel.toJavascript(sb, waypoints, routes);
					return sb.toString();
				}
			}
			catch (Exception e) {
				Log.w(TAG,"Error getting usermodel", e);
			}
			return "\"Error getting usermodel\"";
		}
		public TrackInfo getTrackInfo() {
			return Service.this.getTrackInfo();
		}
		public GpsInfo getGpsInfo() {
			GpsInfo gpsStatus = new GpsInfo();
			gpsStatus.status = mGpsStatus!=null ? mGpsStatus.status : null;
			gpsStatus.substatus = mGpsStatus!=null ? mGpsStatus.substatus : null;
			return gpsStatus;
		}
	}
	protected TrackInfo getTrackInfo() {
		TrackInfo tinfo = new TrackInfo();
		if (mScene!=null && mComposition!=null) {
			DynScene scene = mComposition.getScene(mScene);
			if (scene!=null && scene.getMeta().containsKey(Composition.TITLE))
				tinfo.title = scene.getMeta().get(Composition.TITLE);
			if (scene!=null && scene.getMeta().containsKey(Composition.ARTIST))
				tinfo.artist = scene.getMeta().get(Composition.ARTIST);
			if (tinfo.title==null && mScene!=null)
				tinfo.title = mScene;
		}
		if (tinfo.artist==null && mComposition!=null && mComposition.getMeta().containsKey(Composition.ARTIST))
			tinfo.artist = mComposition.getMeta().get(Composition.ARTIST);
		if (tinfo.title==null && mComposition!=null && mComposition.getMeta().containsKey(Composition.TITLE))
			tinfo.title = mComposition.getMeta().get(Composition.TITLE);
		return tinfo;
	}
	public static class TrackInfo {
		String title;
		String artist;
	}
	public static class GpsInfo {
		String status;
		String substatus;
		protected long time;
	}
	
	public void log(String message) {
		log(LogEntryType.LOG_INFO, message);
	}
	public void log(LogEntryType type, String message) {
		long now = System.currentTimeMillis();
		Log.d(TAG,"Log "+type+": "+message);
		switch(type) {
		case LOG_ERROR:
			mRecorder.e("log.error", message);
			break;
		case LOG_INFO:
			mRecorder.e("log.info", message);
			break;
		}
		synchronized(mLog) {
			LogEntry ent = new LogEntry();
			ent.type = type;
			ent.time = now;
			ent.message = message;
			mLog.add(ent);
		}
	}
	public void logError(String message) {
		log(LogEntryType.LOG_ERROR, message);
	}
	private IBinder mBinder = new LocalBinder();
	@Override
	public IBinder onBind(Intent arg0) {
		Log.d(TAG,"service onBind => bound");
		return mBinder;
	}
	@Override
	public void onRebind(Intent intent) {
		Log.d(TAG,"service onRebind => bound");
		super.onRebind(intent);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.d(TAG,"service onUnbind => NOT bound");
		super.onUnbind(intent);
		// request onRebind
		return true;
	}
	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(TAG,"onCreate");
		checkService();
	}

	@SuppressLint("SetJavaScriptEnabled")
	private void onStart() {
		if (started)
			return;
		Log.d(TAG,"onStart");
		started = true;
		log("START AUDIO");

		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		spref.registerOnSharedPreferenceChangeListener(this);

		// notification
		// API level 11
		Notification notification = new NotificationCompat.Builder(getApplicationContext())
				.setContentTitle(getText(R.string.notification_title))
				.setContentText(getText(R.string.notification_description))
				.setSmallIcon(R.drawable.notification_icon)
				.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, Status.class), 0))
				.build();

		startForeground(SERVICE_NOTIFICATION_ID, notification);

		if (mWebView==null) {
			mWebView = new WebView(this);
			mWebView.setWillNotDraw(true);
			mWebView.getSettings().setJavaScriptEnabled(true);
			mWebView.addJavascriptInterface(new JavascriptHelper(), "daoplayer");
			mWebView.setWebChromeClient(new WebChromeClient() {
				@Override
				public boolean onJsAlert(WebView view, String url, String message,
						JsResult result) {
					Log.w(TAG,"onJsAlert: ("+url+") "+message+" ("+result+")");
					return super.onJsAlert(view, url, message, result);
				}
				@Override
				public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
					log(consoleMessage.messageLevel()+" ("+consoleMessage.sourceId()+":"+consoleMessage.lineNumber()+"): "+consoleMessage.message());
					return super.onConsoleMessage(consoleMessage);
				}
	        });
			mWebView.setWebViewClient(new WebViewClient() {
	        	public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
	        		// this picks up local errors aswell
	        		Log.d(TAG,"onReceivedError errorCode="+errorCode+", description="+description+", failingUrl="+failingUrl); 
	        		logError("WebView error: " + errorCode+": "+description+" for "+failingUrl);
	        	}
				@Override
				public void onPageFinished(WebView view, String url) {
					Log.d(TAG,"Service webview loaded");
					super.onPageFinished(view, url);
					mWebView.loadUrl("javascript:console.log('daoplayer='+daoplayer);");
					// This is OK...
					// delay on first load seems necessary
					mHandler.postDelayed(new Runnable() {
						public void run() {
							Log.d(TAG,"handling delayed webview loaded");
							synchronized (Service.this) {
								mWebViewLoaded = true;
								if (mSetSceneOnLoad!=null) {
									if (mComposition!=null && mComposition.getContext()!=null) {
										String res = mScriptEngine.runScript(mComposition.getContext().getInitScript());
										if (!"OK".equals(res))
											log("problem initialising context; got "+res);
									}
									setScene(mSetSceneOnLoad);
									mSetSceneOnLoad = null;
								}
							}
						}
					}, 500);
				}
	        });
			Log.d(TAG,"Test service webview daoplayer...");
			mWebView.loadDataWithBaseURL("file:///android_asset/service.js",
					"<html><head><title>Service</title><script type='text/javascript'>\n"+
					"daoplayer.selectSections = function(trackName,currentSection,sceneTime,targetDuration,totalTime) {\n"+
						"if (totalTime===undefined) totalTime= -1;\n"+
						"if (currentSection!=null)\n"+
							"return JSON.parse(daoplayer.selectSectionsInternal(trackName,currentSection.name,sceneTime-currentSection.startTime,sceneTime,targetDuration,totalTime));\n"+
						"else\n"+
							"return JSON.parse(daoplayer.selectSectionsInternal(trackName,null,0.0,sceneTime,targetDuration,totalTime));\n"+
					"}\n"+
					"daoplayer.log('daoplayer.hello');\n"+
					//"setInterval(function(){ daoplayer.log('daoplayer.tick'); }, 1000);"+
					// see http://www.movable-type.co.uk/scripts/latlong.html
					"window.distance = function (coord1,coord2) { \n"+
					    "if(!coord1 || !coord2) return null;\n"+
					    "var lat1=coord1.lat, lon1=coord1.lng, lat2=coord2.lat, lon2=coord2.lng;\n"+
						"var R = 6371000.0;\n"+ // m
						"var r1 = lat1*Math.PI/180.0;\n"+
						"var r2 = lat2*Math.PI/180.0;\n"+
						"var dr = (lat2-lat1)*Math.PI/180.0;\n"+
						"var dl = (lon2-lon1)*Math.PI/180.0;\n"+
						"var a = Math.sin(dr/2) * Math.sin(dr/2) + Math.cos(r1) * Math.cos(r2) * Math.sin(dl/2) * Math.sin(dl/2);\n"+
						"var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));\n"+
						"console.log('distance '+lat1+','+lon1+' - '+lat2+','+lon2+', R*c='+R*c);\n"+
						"return R * c;\n"+
					"};\n"+
					"window.pwl = function (inval, map, def) { \n"+
					    "if (inval===null || inval===undefined) return def;\n"+
					    "var lin=inval, lout=map[1], i;\n"+
					    "for (i=0; i+1<map.length; i=i+2) {\n"+
					        "if (inval==map[i]) return map[i+1];\n"+
					        "if (inval<map[i]) return lout+(map[i+1]-lout)*(inval-lin)/(map[i]-lin);\n"+
					        "lin=map[i]; lout=map[i+1];\n"+
					    "}\n"+
					    "if (map.length>=2) return map[map.length-1];\n"+
					    "return inval;\n"+
					"}\n"+
					"</script></head><body></body></html>",
					"text/html", "UTF-8", null);
			mScriptEngine = new ScriptEngine(mWebView);
			// loads asynchronously!
			// Cannot do this: mWebView.loadUrl("javascript:console.log('daoplayer='+daoplayer);");
		} else {
			mWebView.resumeTimers();
		}

		if (mAudioEngine==null) {
			mAudioEngine = new AudioEngine(this);
			loadComposition();
			mAudioEngine.init(this);
		}
		if (mComposition==null) {
			loadComposition();
			mAudioEngine.init(this);			
		}
		else if (mComposition!=null && mSetSceneOnStart!=null)  {
			// avoid race
			String scene = mSetSceneOnStart;
			mSetSceneOnStart = null;
			setScene(scene);
		}
		else if (mComposition!=null && mScene!=null)
			setSceneUpdateTimer(mComposition.getSceneUpdateDelay(mScene));
			
		mAudioEngine.start(this);	
		
		boolean usegps = spref.getBoolean(PREF_USEGPS, false);
		if (usegps)
			startGps();
		mEnableSpeech = spref.getBoolean(PREF_ENABLESPEECH, false);
		if (mEnableSpeech)
			enableSpeech();
	}

	private void enableSpeech() {
		if (mTextToSpeech==null && !mSpeechFailed) {
			Log.d(TAG,"Enable speech...");
			//mSpeechParameters.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_NOTIFICATION));
			//mSpeechParameters.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, String.valueOf(DEFAULT_SPEECH_VOLUME));
			
			mTextToSpeech = new TextToSpeech(this, this);			
		}
	}
	/** text to speech */
	@Override
	public void onInit(int status) {
		if(status==TextToSpeech.SUCCESS) {
			mTextToSpeech.setOnUtteranceCompletedListener(this);
			switch(mTextToSpeech.setLanguage(Locale.ENGLISH)) {
			case TextToSpeech.LANG_MISSING_DATA:
			case TextToSpeech.LANG_NOT_SUPPORTED:
				Log.e(TAG,"TextToSpeech language (ENGLISH) not available");
				logError("TextToSpeech language (ENGLISH) not available");
				mSpeechFailed = true;
				break;
			case TextToSpeech.LANG_AVAILABLE:
			case TextToSpeech.LANG_COUNTRY_AVAILABLE:
			case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
				mSpeechReady = true;
				mSpeechParameters.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "STARTUP");
				mTextToSpeech.playSilence(10, TextToSpeech.QUEUE_FLUSH, mSpeechParameters);
				synchronized(mSpeechDelayed) {
					while (mSpeechDelayed.size()>0) {
						String text = mSpeechDelayed.remove(0);
						mSpeechParameters.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
						mTextToSpeech.speak(text, TextToSpeech.QUEUE_ADD, mSpeechParameters);
					}
				}
				Toast.makeText(this, R.string.toast_speech_ready, Toast.LENGTH_SHORT).show();
				log("TextToSpeech ready");
			}
		} else {
			Log.e(TAG,"Speech onInit("+status+") = failed");
			mSpeechFailed = true;
			Toast.makeText(this, R.string.toast_speech_failed, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onDestroy() {
		Log.d(TAG,"onDestroy");
		super.onDestroy();
		onStop();
		if (mWebView!=null)
			mWebView.destroy();
		if (mTextToSpeech!=null)
			mTextToSpeech.shutdown();
	}
	
	private void onStop() {
		if (!started)
			return;
		Log.d(TAG,"onStop");
		started = false;
		log("STOP AUDIO");
		if (mTextToSpeech!=null && mSpeechReady) {
			synchronized(mSpeechDelayed) {
				mSpeechDelayed.clear();
			}
			mTextToSpeech.stop();
		}
	
		stopGps();
		
		mWebView.pauseTimers();
		
		// Note: this means we depend on Preferences Activity to (re)start us
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		spref.unregisterOnSharedPreferenceChangeListener(this);

		mAudioEngine.stop();

		// removes notification!
		stopForeground(true);
	}
	
	// starting service...
	private void handleCommand(Intent intent) {
		Log.d(TAG,"handleCommand "+intent.getAction());
		if (ACTION_RELOAD.equals(intent.getAction())) {
			String filename = intent.getStringExtra(EXTRA_FILENAME);
			if (filename!=null){
				Log.i(TAG,"Changing filename to "+filename+" on RELOAD intent");
				SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
				if (!spref.edit().putString(PREF_FILENAME, filename).commit())
					Log.e(TAG,"Attempt to change filename to "+filename+" failed");
			}
			if (mAudioEngine!=null) {
				log("RELOAD");
				boolean start = started;
				onStop();
				mAudioEngine.reset();
				mRecorder.startNewFile("RELOAD", null);
				mComposition = null;
				mScene = null;
				if (start)
					mHandler.postDelayed(new Runnable() {
						public void run() {
							Log.d(TAG,"Delayed restart on reload");
							checkService();
						}
					}, 500);
			}
		}
		else if (ACTION_DEFAULT_SCENE.equals(intent.getAction())) {
			if (mComposition!=null) {
				mScene = mComposition.getDefaultScene();
				if (mScene!=null) {
					setScene(mScene);
					Toast.makeText(this, "Load default scene "+mScene, Toast.LENGTH_SHORT).show();
				}				
				else 
					Log.w(TAG,"No default scene");
			}
		}
		else if (ACTION_NEXT_SCENE.equals(intent.getAction())) {
			if (mComposition!=null) {
				String oldScene = mScene;
				mScene = mComposition.getNextScene(mScene);
				if (mScene!=null) {
					setScene(mScene);
					Toast.makeText(this, "Load next scene "+mScene, Toast.LENGTH_SHORT).show();
				}				
				else 
					Log.w(TAG,"No next scene after "+oldScene);
			}
		}
		else if (ACTION_PREV_SCENE.equals(intent.getAction())) {
			if (mComposition!=null) {
				String oldScene = mScene;
				mScene = mComposition.getPrevScene(mScene);
				if (mScene!=null) {
					setScene(mScene);
					Toast.makeText(this, "Load prev scene "+mScene, Toast.LENGTH_SHORT).show();
				}				
				else 
					Log.w(TAG,"No prev scene before "+oldScene);
			}
		}
		else if (ACTION_SET_SCENE.equals(intent.getAction())) {
			if (mComposition!=null) {
				mScene = intent.getExtras().getString(EXTRA_SCENE, null);
				if (mScene!=null) {
					setScene(mScene);
					Toast.makeText(this, "Set scene "+mScene, Toast.LENGTH_SHORT).show();
					// debug test only!!!
					//updateScene();
				}				
				else 
					Log.w(TAG,"No scene specified for set");
			}
		}
		else if (ACTION_CLEAR_LOGS.equals(intent.getAction())) {
			synchronized (mLog) {
				Log.d(TAG,"clear logs");
				mLog.clear();
			}
		}
		else if (ACTION_SET_LATLNG.equals(intent.getAction())) {
			mLastLat = intent.getDoubleExtra(EXTRA_LAT, 0.0);
			mLastLng = intent.getDoubleExtra(EXTRA_LNG, 0.0);
			mLastAccuracy = intent.getDoubleExtra(EXTRA_ACCURACY, 0.0);
			// defaults mainly for mock updates
			long now = System.currentTimeMillis();
			mLastElapsedtime = intent.getLongExtra(EXTRA_ELAPSEDTIME, (mLastTime>0 ? mLastElapsedtime+now-mLastTime : 0));
			mLastTime = intent.getLongExtra(EXTRA_TIME, now);
			Log.d(TAG,"Service setLatLng "+mLastLat+","+mLastLng+" acc="+mLastAccuracy+" at "+mLastTime);
			if (logGps)
				log("SET POSITION "+mLastLat+","+mLastLng+" acc="+mLastAccuracy+" at "+mLastTime);
			try {
				JSONObject info = new JSONObject();
				info.put("lat", mLastLat);
				info.put("lng", mLastLng);
				info.put("accuracy", mLastAccuracy);
				info.put("time", mLastTime);
				info.put("elapsedtime", mLastElapsedtime);
				mRecorder.i("set.latlng", info);
			}
			catch (JSONException e) {
				Log.w(TAG,"Error logging position", e);
			}
			/*if (mWebViewLoaded) {
				StringBuilder sb = new StringBuilder();
				sb.append("window.position={lat:");
				sb.append(lat);
				sb.append(",lng:");
				sb.append(lng);
				sb.append(",time:"+System.currentTimeMillis());
				sb.append("}");
				mScriptEngine.runScript(sb.toString());
			}*/
			mUserModel.setLocation(mLastLat, mLastLng, mLastAccuracy, mLastTime, mLastElapsedtime);
			if (mComposition!=null) {
				updateScene(true);
			}
		} else if (ACTION_RUN_TEST.equals(intent.getAction())) {
			runTest();
		}
		checkService();
	}


	private void loadComposition() {
		Log.d(TAG,"loadComposition...");
		log("LOAD COMPOSITION");
		File filesDir = Compat.getExternalFilesDir(this);
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		String filename = spref.getString(PREF_FILENAME, DEFAULT_COMPOSITION);
		Composition comp = mComposition = new Composition(mAudioEngine, mUserModel);
		try {
			comp.read(new File(filesDir, filename), this, this);
		} catch (Exception e) {
			Log.w(TAG,"Error reading "+filename+": "+e, e);
			Toast.makeText(this, "Error reading composition: "+e.getMessage(), Toast.LENGTH_SHORT).show();
			logError("Error reading "+filename+": "+e);
			return;
		}
		mUserModel.setContext(comp.getContext());
		if (mWebViewLoaded && comp.getContext()!=null) {
			String res = mScriptEngine.runScript(mComposition.getContext().getInitScript());
			if (!"OK".equals(res))
				logError("problem initialising context; got "+res);
		}
		String defaultScene = mScene = comp.getDefaultScene();
		if (defaultScene!=null) {
			setScene(defaultScene);
			Log.i(TAG,"Read "+filename+"; playing scene "+defaultScene);
			log("Read; playing scene "+defaultScene);
		} else {
			Log.i(TAG,"Read "+filename+"; no default scene");
			logError("Read but no default scene");			
		}
	}
	static class MBox {
		private boolean mDone = false;
		private Object mValue;
		public synchronized void set(Object value) {
			mValue = value;
			mDone = true;
			notifyAll();
		}
		public synchronized Object get(int timeout) {
			long start = System.currentTimeMillis();
			while (!mDone) {
				long now = System.currentTimeMillis();
				long delay = timeout-(now-start);
				if (delay<=0)
					return null;
				try {
					wait(delay);
				}
				catch (InterruptedException ie) 
				{
					Log.d(TAG,"MBox interrupted");
					return mValue;
				}
			}
			return mValue;
		}
	}
	private String mDelayedScene = null;
	private Runnable mDelayedSetScene = new Runnable() {
		public void run() {
			String scene = mDelayedScene;
			Log.d(TAG,"delayedSetScene "+scene);
			final Intent i = new Intent(ACTION_SET_SCENE);
			i.putExtra(EXTRA_SCENE, scene);
			i.setClass(getApplicationContext(), Service.class);
			getApplicationContext().startService(i);					
		}
	};
	private class JavascriptHelper {
		@JavascriptInterface
		public void log(String msg) {
			Log.d(TAG,"Javascript: "+msg);
			// NOT a task main thread - can't do loadUrl
			Service.this.log(msg);
		}
		@JavascriptInterface
		public void returnDouble(int ix, double result) {
			Log.d(TAG,"returnDouble "+ix+": "+result);
			postMBox(ix, result);
		}
		@JavascriptInterface
		public void returnString(int ix, String result) {
			Log.d(TAG,"returnString"+ix+": "+result);
			postMBox(ix, result);
		}
		@JavascriptInterface
		public void setScene(String scene) {
			Log.d(TAG,"javascript:setScene("+scene+") - delayed "+JAVASCRIPT_SET_SCENE_DELAY_MS);
			mDelayedScene = scene;
			mHandler.removeCallbacks(mDelayedSetScene);
			mHandler.postDelayed(mDelayedSetScene, JAVASCRIPT_SET_SCENE_DELAY_MS);
		}
		@JavascriptInterface
		public void setLastWaypoint(String name) {
			Log.d(TAG,"javascript:setLastWaypoint("+name+")");
			mUserModel.setLastWaypoint(name);
		}
		@JavascriptInterface
		public void speak(final String text, final boolean flush) {
			Log.d(TAG,"Javascript: speak "+text+" (flush "+flush+")");			
			// NOT a task main thread 
			synchronized(mSpeechDelayed) {
				if (mEnableSpeech) {
					if (!mSpeechReady) {
						if (flush)
							mSpeechDelayed.clear();
						mSpeechDelayed.add(text);
					}
					else 
						mHandler.post(new Runnable() {
							public void run() {
								if (mTextToSpeech!=null && mSpeechReady && mEnableSpeech) {
									Log.d(TAG,"really speak "+text+" (flush "+flush+")");
									mSpeechParameters.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
									mTextToSpeech.speak(text, flush ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, mSpeechParameters);
								}
							}
						});	
				}
			}
		}
		@JavascriptInterface
		public String selectSectionsInternal(String trackName, String currentSectionName, double currentSectionTimeSeconds, double sceneTimeSeconds, double targetDurationSeconds, double totalTimeSeconds) {
			Log.d(TAG,"selectSectionsInterval("+trackName+","+currentSectionName+","+currentSectionTimeSeconds+","+sceneTimeSeconds+","+targetDurationSeconds+")");
			int currentSectionTime = mAudioEngine.secondsToSamples(currentSectionTimeSeconds);
			int targetDuration = mAudioEngine.secondsToSamples(targetDurationSeconds);
			int sceneTime = mAudioEngine.secondsToSamples(sceneTimeSeconds);
			Composition composition = mComposition;
			if (composition!=null) {
				Object sections[] = null;
				try {
					sections = composition.selectSections(trackName, currentSectionName, currentSectionTime, sceneTime, targetDuration);
					if (sections!=null && sections.length>0 && sections[0] instanceof Integer) {
						sections[0] = mAudioEngine.samplesToSeconds((Integer)sections[0]);
					}
				} catch (Exception e) {
					Log.w(TAG,"Error doing selectSections: "+e, e);
				}
				try {
					JSONObject info = new JSONObject();
					info.put("trackName", trackName);
					info.put("sceneTime", sceneTimeSeconds);
					if (totalTimeSeconds>=0)
						info.put("totalTime", totalTimeSeconds);
					info.put("currentSectionName", currentSectionName);
					info.put("currentSectionTime", currentSectionTimeSeconds);
					info.put("targetDuration", targetDurationSeconds);
					if (sections!=null) {
						JSONArray jsections= new JSONArray();
						info.put("result", jsections);
						for (Object section : sections)
							jsections.put(section);
					}
					mRecorder.i("sections.select", info);
				} catch (Exception e) {
					Log.w(TAG,"Error logging selectSections: "+e);
				}
				if (sections==null)
					return "null";
				JSONStringer js = new JSONStringer();
				try {
					js.array();
					for (int i=0;i<sections.length; i++)
						js.value(sections[i]);
					js.endArray();
				} catch (JSONException e) {
					Log.w(TAG,"Error marshalling selectSections return value", e);
				}
				String rval = js.toString();
				Log.d(TAG,"selectSections -> "+rval);
				return rval;
			}
			else
				return "null";
		}
	}
	private Map<Integer,MBox> mResults = new HashMap<Integer,MBox>();
	private int mNextResulti = 0;
	private int addMBox(MBox mbox) {
		synchronized (mResults) {
			int ix = mNextResulti++;
			mResults.put(ix, mbox);
			return ix;
		}
	}
	private void removeMBox(int ix) {
		synchronized (mResults) {
			mResults.remove(ix);
		}
	}
	private void postMBox(int ix, Object value) {
		MBox mbox = null;
		synchronized (mResults) {
			mbox = mResults.get(ix);
		}
		if (mbox==null) 
			Log.w(TAG,"Could not find MBox "+ix+" for value "+value);
		else
			mbox.set(value);
	}
	class ScriptEngine implements IScriptEngine {
		private static final int SCRIPT_TIMEOUT = 1000;
		private WebView mWebView;
		ScriptEngine(WebView webView) {
			mWebView = webView;
		}

		@Override
		public String runScript(String script) {
			MBox mbox = new MBox();
			int ix = addMBox(mbox);
			StringBuilder sb = new StringBuilder();
			sb.append("javascript:");
			sb.append("daoplayer.returnString(");
			sb.append(ix);
			sb.append(",(function(){");
			sb.append(script);
			sb.append("})());");
			mWebView.loadUrl(sb.toString());
			Object result = mbox.get(SCRIPT_TIMEOUT);
			removeMBox(ix);
			if (result==null) {
				Log.d(TAG,"Script timeout: "+sb.toString());
				logError("Timeout (Error) in script: "+sb.toString());
				return null;
			}
			if (result instanceof String)
				return (String)result;
			else 
				return result.toString();
		}
	}
	private ScriptEngine mScriptEngine;
	/* now in usermodel
	private String getPosition() {
		StringBuilder sb = new StringBuilder();
		if (mLastTime==0)
			sb.append("null");
		else {
			sb.append("{lat:");
			sb.append(mLastLat);
			sb.append(",lng:");
			sb.append(mLastLng);
			sb.append(",accuracy:");
			sb.append(mLastAccuracy);
			sb.append(",age:");
			sb.append((System.currentTimeMillis()-mLastTime)/1000.0);
			sb.append("}");
		}
		return sb.toString();
	}
	*/
	private synchronized void setScene(String scene) {
		if (mWebViewLoaded) {
			if (started && mComposition!=null) {
				log("SET SCENE "+scene);
				if (mLastTime>0 && mUserModel!=null) {
					Long now = System.currentTimeMillis();
					mUserModel.updateNoLocation(now, (now-mLastTime)+mLastElapsedtime);
				}
				try {
					mRecorder.i("scene.set", scene);
				}
				catch (Exception e) {
					Log.e(TAG,"error logging scene.set: "+e);
				}
				mComposition.setScene(scene, mScriptEngine);
				setSceneUpdateTimer(mComposition.getSceneUpdateDelay(scene));
				
				Intent i = new Intent(ACTION_SCENE_CHANGED);
				TrackInfo ti = getTrackInfo();
				if (ti.title!=null)
					i.putExtra(EXTRA_TITLE, ti.title);
				if (ti.artist!=null)
					i.putExtra(EXTRA_ARTIST, ti.artist);
				LocalBroadcastManager.getInstance(this).sendBroadcast(i);
			}
			else 
				mSetSceneOnStart = scene;
		}
		else
			mSetSceneOnLoad = scene;
	}
	private synchronized void updateScene(boolean fromGps) {
		if (mScene==null)
			return;
		if (mWebViewLoaded && started && mComposition!=null) {
			log("UPDATE SCENE "+mScene);
			if (mLastTime>0 && mUserModel!=null) {
				Long now = System.currentTimeMillis();
				mUserModel.updateNoLocation(now, (now-mLastTime)+mLastElapsedtime);
			}
			try {
				mRecorder.i("scene.update", mScene);
			}
			catch (Exception e) {
				Log.e(TAG,"error logging scene.set: "+e);
			}
			mComposition.updateScene(mScene, mScriptEngine, fromGps);
			setSceneUpdateTimer(mComposition.getSceneUpdateDelay(mScene));
		}
		else
			Log.w(TAG,"dropped updateScene("+mScene+") due to web view not loaded/started");
	}
	private void setSceneUpdateTimer(Long delay) {
		if (delay==null || mScene==null) 
			return;
		Log.d(TAG,"setSceneUpdateTimer "+delay+" for "+mScene);
		mHandler.removeCallbacks(mSceneUpdateTimer);
		mHandler.postDelayed(mSceneUpdateTimer, delay);
	}
	private Runnable mSceneUpdateTimer = new Runnable() {
		@Override
		public void run() {
			if (!started) {
				Log.d(TAG,"ignore delayed scene update when stopped");
				return;
			}
			if (mScene==null) {
				Log.d(TAG,"ignore delayed scene update when scene null");
				return;
			}
			Long delay = mComposition!=null ? mComposition.getSceneUpdateDelay(mScene) : null;
			if (delay==null)
				Log.w(TAG,"ignore delayed scene update for "+mScene+"; no longer needed?");
			else if (delay>0) {
				Log.w(TAG,"ignore delayed scene update for "+mScene+"; wait another "+delay);
				setSceneUpdateTimer(delay);
			}
			else {
				Log.d(TAG,"delayed scene update...");
				updateScene(false);
			}
		}		
	};
	private Handler mHandler = new Handler() {
		
	};
	// This is the old onStart method that will be called on the pre-2.0
	// platform.  On 2.0 or later we override onStartCommand() so this
	// method will not be called.
	@Override
	public void onStart(Intent intent, int startId) {
	    if (intent!=null)
	    	handleCommand(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    if (intent!=null)
	    	handleCommand(intent);
	    // We want this service to continue running until it is explicitly
	    // stopped, so return sticky.
	    return START_STICKY;
	}

	private void checkService() {
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		boolean runservice = spref.getBoolean("pref_runservice", false);
		if (runservice)
			onStart();
		else
			onStop();

	}
	@Override
	public void onSharedPreferenceChanged(SharedPreferences spref,
			String key) {
		if ("pref_runservice".equals(key)) {
			boolean runservice = spref.getBoolean("pref_runservice", false);
			Log.d(TAG,"service pref_runservice changed to "+runservice);
			checkService();
		}		
		else if (PREF_USEGPS.equals(key)) {
			boolean usegps = spref.getBoolean(PREF_USEGPS, false);
			Log.d(TAG,"service pref_usegps changed to "+usegps);
			if (started && usegps)
				startGps();
			else if (!usegps)
				stopGps();
		}
		else if (PREF_ENABLESPEECH.equals(key)) {
			mEnableSpeech = spref.getBoolean(PREF_ENABLESPEECH, false);
			Log.d(TAG,"service pref_enablespeech changed to "+mEnableSpeech);
			if (started && mEnableSpeech)
				enableSpeech();
			else if (!mEnableSpeech && mTextToSpeech!=null && mSpeechReady) {
				synchronized(mSpeechDelayed) {
					mSpeechDelayed.clear();	
				}
				mTextToSpeech.stop();
			}
		}
	}
	private HashSet<Integer> mSatUsedInFix = new HashSet<Integer>();
	private void startGps() {
		if (mGpsStarted)
			return;
		mGpsStarted = true;
		final LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		// Register the listener with the Location Manager to receive location updates
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
		locationManager.addGpsStatusListener(mStatusListener = new GpsStatus.Listener() {
			private GpsStatus status;
			@Override
			public void onGpsStatusChanged(int event) {
				status = locationManager.getGpsStatus(status);
				int inFix = 0;
				int total = 0;
				boolean changeUsedInFix = false;
				HashSet<Integer> satNotUsedInFix = new HashSet<Integer>();
				satNotUsedInFix.addAll(mSatUsedInFix);
				Vector<Float> snrs = new Vector<Float>();
				for (GpsSatellite sat : status.getSatellites()) {
					if (sat.usedInFix()) {
						inFix++;
						if (!mSatUsedInFix.contains(sat.getPrn())) {
							changeUsedInFix = true;
							mSatUsedInFix.add(sat.getPrn());
						} else {
							satNotUsedInFix.remove(sat.getPrn());
						}
					} 
					total++;
					snrs.add(sat.getSnr());
				}
				if (!satNotUsedInFix.isEmpty()) {
					mSatUsedInFix.removeAll(satNotUsedInFix);
					changeUsedInFix = true;
				}
				Float asnrs [] = snrs.toArray(new Float[snrs.size()]);
				Arrays.sort(asnrs);
				if (logGps)
					log("Gps status: Fixed="+inFix+" Total="+total+" "+(asnrs.length>0 ? " SNRs="+asnrs[asnrs.length-1]+(asnrs.length>3 ? "/"+asnrs[asnrs.length-4] : "") : ""));
				try {
					JSONObject info = new JSONObject();
					info.put("changeUsedInFix", changeUsedInFix);
					info.put("fixed", inFix);
					info.put("total", total);
					JSONArray jsnrs = new JSONArray();
					info.put("snrs", jsnrs);
					for (int i=0; i<asnrs.length; i++)
						jsnrs.put(asnrs[i]);
					mRecorder.i("on.gpsStatus", info);
				}
				catch (JSONException e) {
					Log.w(TAG,"Error logging on.location", e);
				}
				long now = System.currentTimeMillis();
				if (now-mGpsStatus.time > 2000) {
					mGpsStatus.status = "Searching...";
					// 4 at least 20 => 80%
					double percent = 0;
					if (asnrs.length==1)
						percent = snrToPercent(asnrs[0]);
					else if (asnrs.length==2)
						percent = 20+snrToPercent(asnrs[0]);
					else if (asnrs.length==3)
						percent = 40+snrToPercent(asnrs[0]);
					else if (asnrs.length>=4)
						percent = 60+snrToPercent2(asnrs[asnrs.length-4]);
					mGpsStatus.substatus = ""+((int)percent)+"%";
					Intent si = new Intent(ACTION_GPS_STATUS);
					si.putExtra(EXTRA_STATUS, mGpsStatus.status);
					si.putExtra(EXTRA_SUBSTATUS, mGpsStatus.substatus);
					LocalBroadcastManager.getInstance(Service.this).sendBroadcast(si);
				}
			}
		});
		Intent si = new Intent(ACTION_GPS_STATUS);
		mGpsStatus.time = System.currentTimeMillis();
		mGpsStatus.status = "Searching...";
		mGpsStatus.substatus = "0% (started)";
		si.putExtra(EXTRA_STATUS, mGpsStatus.status);
		si.putExtra(EXTRA_SUBSTATUS, mGpsStatus.substatus);
		LocalBroadcastManager.getInstance(Service.this).sendBroadcast(si);
	}
	public double snrToPercent(float asnr) {
		if (asnr<0)
			return 0;
		if (asnr>24)
			return 19;
		return asnr*19/24;
	}
	public double snrToPercent2(float asnr) {
		if (asnr<0)
			return 0;
		if (asnr>40)
			return 39;
		return asnr*39/40;
	}
	GpsStatus.Listener mStatusListener;
	// Define a listener that responds to location updates
	LocationListener mLocationListener = new LocationListener() {
		public void onLocationChanged(Location location) {
			// Called when a new location is found by the network location provider.
			//makeUseOfNewLocation(location);
			Log.d(TAG,"New gps location: "+location);
			if (logGps)
				log("New gps location "+location.getLatitude()+","+location.getLongitude()+" acc="+location.getAccuracy()+" at "+location.getTime());
			try {
				JSONObject info = new JSONObject();
				info.put("lat", location.getLatitude());
				info.put("lng", location.getLongitude());
				if (location.hasAccuracy())
					info.put("accuracy", location.getAccuracy());
				if (location.hasAltitude())
					info.put("altitude", location.getAltitude());
				if (location.hasBearing())
					info.put("bearing", location.getBearing());
				if (location.hasSpeed())
					info.put("speed", location.getSpeed());
				info.put("time", location.getTime());
				info.put("elapsedRealtimeNanos", location.getElapsedRealtimeNanos());
				mRecorder.i("on.location", info);
			}
			catch (JSONException e) {
				Log.w(TAG,"Error logging on.location", e);
			}
			Intent i = new Intent(Service.ACTION_SET_LATLNG);
			i.putExtra(EXTRA_LAT, location.getLatitude());
			i.putExtra(EXTRA_LNG, location.getLongitude());
			i.putExtra(EXTRA_ACCURACY, (double)location.getAccuracy());
			i.putExtra(EXTRA_TIME, location.getTime());
			i.putExtra(EXTRA_ELAPSEDTIME, (location.getElapsedRealtimeNanos()/NANOS_PER_MILLI));
			i.setClass(getApplicationContext(), Service.class);
			startService(i);
			
			Intent si = new Intent(ACTION_GPS_STATUS);
			mGpsStatus.time = System.currentTimeMillis();
			mGpsStatus.status = "Working";
			if (location.getAccuracy() <= 7)
				mGpsStatus.substatus = "good ("+location.getAccuracy()+"m)";
			else if (location.getAccuracy() <= 15)
				mGpsStatus.substatus = "ok ("+location.getAccuracy()+"m)";
			else
				mGpsStatus.substatus = "poor ("+location.getAccuracy()+"m)";
			si.putExtra(EXTRA_STATUS, mGpsStatus.status);
			si.putExtra(EXTRA_SUBSTATUS, mGpsStatus.substatus);
			LocalBroadcastManager.getInstance(Service.this).sendBroadcast(si);
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {}

		public void onProviderEnabled(String provider) {
			log("LOCATION PROVIDED ENABLED ("+provider+")");
			Intent si = new Intent(ACTION_GPS_STATUS);
			mGpsStatus.time = System.currentTimeMillis();
			mGpsStatus.status = "Searching...";
			mGpsStatus.substatus = "0% (enabled)";
			si.putExtra(EXTRA_STATUS, mGpsStatus.status);
			si.putExtra(EXTRA_SUBSTATUS, mGpsStatus.substatus);
			LocalBroadcastManager.getInstance(Service.this).sendBroadcast(si);
		}

		public void onProviderDisabled(String provider) {
			log("LOCATION PROVIDED DISABLED ("+provider+")");
			Intent si = new Intent(ACTION_GPS_STATUS);
			mGpsStatus.time = System.currentTimeMillis();
			mGpsStatus.status = "Disabled";
			si.putExtra(EXTRA_STATUS, mGpsStatus.status);
			si.putExtra(EXTRA_SUBSTATUS, mGpsStatus.substatus);
			LocalBroadcastManager.getInstance(Service.this).sendBroadcast(si);
		}
	};
	private void stopGps() {
		if (!mGpsStarted)
			return;
		mGpsStarted = false;
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		// Register the listener with the Location Manager to receive location updates
		locationManager.removeUpdates(mLocationListener);	
		locationManager.removeGpsStatusListener(mStatusListener);
		Intent si = new Intent(ACTION_GPS_STATUS);
		mGpsStatus.time = System.currentTimeMillis();
		mGpsStatus.status = "Stopped";
		mGpsStatus.substatus = "";
		si.putExtra(EXTRA_STATUS, mGpsStatus.status);
		si.putExtra(EXTRA_SUBSTATUS, mGpsStatus.substatus);
		LocalBroadcastManager.getInstance(Service.this).sendBroadcast(si);
	}
	private void runTest() {
		if (mComposition==null) {
			Log.d(TAG,"runTest: null composition");
			return;
		}
		String scene = mScene;
		if (scene==null)
			scene = mComposition.getDefaultScene();
		if (scene==null) {
			Log.d(TAG,"runTest: null scene (no default)");
			return;			
		}
		String path = mComposition.getTestTrack(scene);
		if (path==null) {
			Log.d(TAG,"runTest: no file found");
			return;
		}
		Log.d(TAG,"runTest: path "+path);
		FileDecoder fd = new FileDecoder(path, this);
		fd.start();
		if (fd.isFailed()) {
			Log.d(TAG,"runTest: could not start "+path);
			return;
		}
		int pos = 0;
		Vector<FileCache.Block> blocks =  new Vector<FileCache.Block>();
		while (true) {
			FileCache.Block block = fd.getBlock(pos);
			if (block!=null) {
				blocks.add(block);
				int len = (block.getSamples().length/block.getChannels());
				Log.d(TAG,"Read block "+pos+": @"+block.getStartFrame()+", len "+len);
				pos = block.getStartFrame()+len;
			}
			else
				break;
		}
		FileCache.Block block = blocks.get(0);
		FileCache.Block block0 = fd.getBlock(0);
		if (block0==null) 
			Log.d(TAG,"Could not read block 0");
		else {
			Log.d(TAG,"Read block 0: @"+block0.getStartFrame()+", len "+block0.getSamples().length/block0.getChannels());
			findBlock(block0, blocks);
		}
		fd.getBlock(700);
		FileCache.Block block1 = fd.getBlock(700);
		if (block1==null) 
			Log.d(TAG,"Could not read block 700");
		else {
			Log.d(TAG,"Read block 700: @"+block1.getStartFrame()+", len "+block1.getSamples().length/block1.getChannels());
			findBlock(block1, blocks);
		}
		FileCache.Block block2 = fd.getBlock(15000);
		if (block2==null) 
			Log.d(TAG,"Could not read block 15000");
		else  {
			Log.d(TAG,"Read block 15000: @"+block2.getStartFrame()+", len "+block2.getSamples().length/block2.getChannels());
			findBlock(block2, blocks);
			block2 = fd.getBlock(block2.getStartFrame()+block2.getSamples().length/block2.getChannels());
			findBlock(block2, blocks);
		}
	}
	void logSamples(FileCache.Block block2) {
		StringBuilder sb = new StringBuilder();
		sb.append("block @"+block2.getStartFrame()+", "+block2.getChannels()+"chan: ");
		short as[] = block2.getSamples();
		for (int i=0; i<1000 && i<as.length; i++) {
			sb.append(as[i]);
			sb.append(" ");
		}
		sb.append("...");
		Log.d(TAG,sb.toString());
	}
	void findBlock(FileCache.Block block2, Vector<FileCache.Block> blocks) {
		logSamples(block2);
		short as[] = block2.getSamples();
		for (int i=0; i<blocks.size(); i++ )
		{
			FileCache.Block block = blocks.get(i);
			short bs[] = block.getSamples();
			for (int j=0; j<bs.length; j+=block.getChannels()) {
				int k;
				for (k=0; k<as.length && j+k<bs.length; k++)
					if (as[k]!=bs[j+k])
						break;					
				if (j+k>=bs.length) {
					Log.d(TAG,"match "+k+" samples @"+block.getStartFrame()+"+"+j);
					break;
				}
			}
		}

	}
	boolean equal(FileCache.Block a, FileCache.Block b) {
		if (a==null || b==null)
			return false;
		short as[] = a.getSamples();
		short bs[] = b.getSamples();
		if (as.length!=bs.length)
			return false;
		for (int i=0; i<as.length; i++)
			if (as[i]!=bs[i]) {
				return false;
			}
		return true;
	}
	@Override
	public void onUtteranceCompleted(String utteranceId) {
		Log.d(TAG,"onUtteranceCompleted("+utteranceId+")");		
	}
	@Override
	public Recorder getRecorder() {
		return mRecorder;
	}
}
