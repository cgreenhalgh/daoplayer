/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.opensharingtoolkit.daoplayer.audio.AudioEngine;
import org.opensharingtoolkit.daoplayer.audio.Composition;
import org.opensharingtoolkit.daoplayer.audio.IScriptEngine;
import org.opensharingtoolkit.daoplayer.ui.BrowserActivity;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.AudioTrack;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
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
public class Service extends android.app.Service implements OnSharedPreferenceChangeListener {

	private static final String TAG = "daoplayer-service";
	private static final int SERVICE_NOTIFICATION_ID = 1;
	public static final String ACTION_RELOAD = "org.opensharingtoolkit.daoplayer.RELOAD";
	private static final String DEFAULT_COMPOSITION = "composition.json";
	public static final String ACTION_DEFAULT_SCENE = "org.opensharingtoolkit.daoplayer.DEFAULT_SCENE";
	public static final String ACTION_NEXT_SCENE = "org.opensharingtoolkit.daoplayer.NEXT_SCENE";
	public static final String ACTION_PREV_SCENE = "org.opensharingtoolkit.daoplayer.PREV_SCENE";
	public static final String ACTION_SET_LATLNG = "org.opensharingtoolkit.daoplayer.SET_LATLNG";
	public static final String ACTION_UPDATE_SCENE = "org.opensharingtoolkit.daoplayer.UPDATE_SCENE";
	public static final String EXTRA_LAT = "lat";
	public static final String EXTRA_LNG = "lng";
	private AudioEngine mAudioEngine;
	private boolean started = false;
	private Composition mComposition = null;
	private String mScene = null;
	private WebView mWebView;
	
	/** Binder subclass (inner class) with methods for local interaction with service */
	public class LocalBinder extends android.os.Binder {
		// local methods... direct access to service
		public IAudio getAudio() {
			return mAudioEngine;
		}
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

		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		spref.registerOnSharedPreferenceChangeListener(this);

		// notification
		// API level 11
		Notification notification = new NotificationCompat.Builder(getApplicationContext())
				.setContentTitle(getText(R.string.notification_title))
				.setContentText(getText(R.string.notification_description))
				.setSmallIcon(R.drawable.notification_icon)
				.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, Preferences.class), 0))
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
	        });
			mWebView.setWebViewClient(new WebViewClient() {
	        	public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
	        		// this picks up local errors aswell
	        		Log.d(TAG,"onReceivedError errorCode="+errorCode+", description="+description+", failingUrl="+failingUrl); 
	        		Toast.makeText(Service.this, "Oh no! " + description, Toast.LENGTH_SHORT).show();
	        	}
				@Override
				public void onPageFinished(WebView view, String url) {
					Log.d(TAG,"Service webview loaded");
					super.onPageFinished(view, url);
					mWebView.loadUrl("javascript:console.log('daoplayer='+daoplayer);");
					// This is OK...
					synchronized (Service.this) {
						mWebViewLoaded = true;
						if (mSetSceneOnLoad!=null) {
							setScene(mSetSceneOnLoad);
							mSetSceneOnLoad = null;
						}
					}
				}
	        });
			Log.d(TAG,"Test service webview daoplayer...");
			mWebView.loadDataWithBaseURL("file:///android_asset/service.js",
					"<html><head><title>Service</title><script type='text/javascript'>"+
					"daoplayer.log('daoplayer.hello');"+
					//"setInterval(function(){ daoplayer.log('daoplayer.tick'); }, 1000);"+
					// see http://www.movable-type.co.uk/scripts/latlong.html
					"window.distance = function (lat1,lon1,lat2,lon2) { "+
						"var R = 6371000.0;"+ // m
						"var r1 = lat1*Math.PI/180.0;"+
						"var r2 = lat2*Math.PI/180.0;"+
						"var dr = (lat2-lat1)*Math.PI/180.0;"+
						"var dl = (lon2-lon1)*Math.PI/180.0;"+
						"var a = Math.sin(dr/2) * Math.sin(dr/2) + Math.cos(r1) * Math.cos(r2) * Math.sin(dl/2) * Math.sin(dl/2);"+
						"var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));"+
						"console.log('distance '+lat1+','+lon1+' - '+lat2+','+lon2+', R*c='+R*c);"+
						"return R * c;"+
					"}"+
					"</script></head><body></body></html>",
					"text/html", "UTF-8", null);
			mScriptEngine = new ScriptEngine(mWebView);
			// loads asynchronously!
			// Cannot do this: mWebView.loadUrl("javascript:console.log('daoplayer='+daoplayer);");
		} else {
			mWebView.resumeTimers();
		}

		if (mAudioEngine==null) {
			mAudioEngine = new AudioEngine();
			loadComposition();
		}
		mAudioEngine.start(this);	
	}

	@Override
	public void onDestroy() {
		Log.d(TAG,"onDestroy");
		super.onDestroy();
		onStop();
		if (mWebView!=null)
			mWebView.destroy();
	}
	
	private void onStop() {
		if (!started)
			return;
		Log.d(TAG,"onStop");
		started = false;
		
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
			if (mAudioEngine!=null) {
				mAudioEngine.reset();
				loadComposition();
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
		else if (ACTION_SET_LATLNG.equals(intent.getAction())) {
			double lat = intent.getDoubleExtra(EXTRA_LAT, 0.0);
			double lng = intent.getDoubleExtra(EXTRA_LNG, 0.0);
			Log.d(TAG,"Service setLatLng "+lat+","+lng);
			if (mWebViewLoaded) {
				StringBuilder sb = new StringBuilder();
				sb.append("window.position={lat:");
				sb.append(lat);
				sb.append(",lng:");
				sb.append(lng);
				sb.append(",time:"+System.currentTimeMillis());
				sb.append("}");
				mScriptEngine.runScript(sb.toString());
			}
			if (mComposition!=null) {
				if (mScene!=null)
					updateScene(mScene);
			}
		}
		else if (ACTION_UPDATE_SCENE.equals(intent.getAction())) {
			Log.d(TAG,"update scene");
			if (mComposition!=null) {
				if (mScene!=null)
					updateScene(mScene);
			}
		}
		checkService();
	}


	private void loadComposition() {
		Log.d(TAG,"loadComposition...");
		File filesDir = Compat.getExternalFilesDir(this);
		Composition comp = mComposition = new Composition(mAudioEngine);
		try {
			comp.read(new File(filesDir, DEFAULT_COMPOSITION));
		} catch (Exception e) {
			Log.w(TAG,"Error reading "+DEFAULT_COMPOSITION+": "+e, e);
			Toast.makeText(this, "Error reading composition: "+e.getMessage(), Toast.LENGTH_SHORT).show();
			return;
		}
		String defaultScene = mScene = comp.getDefaultScene();
		if (defaultScene!=null) {
			setScene(defaultScene);
			Log.i(TAG,"Read "+DEFAULT_COMPOSITION+"; playing scene "+defaultScene);
			Toast.makeText(this, "Read; playing scene "+defaultScene, Toast.LENGTH_SHORT).show();
		} else {
			Log.i(TAG,"Read "+DEFAULT_COMPOSITION+"; no default scene");
			Toast.makeText(this, "Read but no default scene", Toast.LENGTH_SHORT).show();			
		}
	}
	private boolean mWebViewLoaded = false;
	private String mSetSceneOnLoad = null;
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
	private class JavascriptHelper {
		@JavascriptInterface
		public void log(String msg) {
			Log.d(TAG,"Javascript: "+msg);
			// NOT a task main thread - can't do loadUrl
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
			sb.append(",function(){");
			sb.append(script);
			sb.append("}());");
			mWebView.loadUrl(sb.toString());
			Object result = mbox.get(SCRIPT_TIMEOUT);
			removeMBox(ix);
			if (result==null) {
				Log.d(TAG,"Script timeout: "+sb.toString());
				return null;
			}
			if (result instanceof String)
				return (String)result;
			else 
				return result.toString();
		}
	}
	private ScriptEngine mScriptEngine;
	private synchronized void setScene(String scene) {
		if (mWebViewLoaded)
			mComposition.setScene(scene, mScriptEngine);
		else
			mSetSceneOnLoad = scene;
	}
	private synchronized void updateScene(String scene) {
		if (mWebViewLoaded)
			mComposition.updateScene(scene, mScriptEngine);
		else
			Log.w(TAG,"dropped updateScene("+scene+") due to web view not loaded");
	}
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
	}

}
