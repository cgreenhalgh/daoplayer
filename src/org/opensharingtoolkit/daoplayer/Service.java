/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

import java.io.File;
import java.io.IOException;

import org.json.JSONException;
import org.opensharingtoolkit.daoplayer.audio.AudioEngine;
import org.opensharingtoolkit.daoplayer.audio.Composition;
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

		if (mAudioEngine==null) {
			mAudioEngine = new AudioEngine();
			loadComposition();
		}
		mAudioEngine.start(this);
		
		if (mWebView==null) {
			mWebView = new WebView(this);
			mWebView.setWillNotDraw(true);
			mWebView.getSettings().setJavaScriptEnabled(true);
			mWebView.addJavascriptInterface(this, "daoplayer");
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
					// This is OK...
					mWebView.loadUrl("javascript:console.log('daoplayer='+daoplayer);");
				}
	        });
			Log.d(TAG,"Test service webview daoplayer...");
			mWebView.loadDataWithBaseURL("file:///android_asset/service.js",
					"<html><head><title>Service</title><script type='text/javascript'>daoplayer.log('daoplayer.hello');setInterval(function(){ daoplayer.log('daoplayer.tick'); }, 1000);</script></head><body></body></html>",
					"text/html", "UTF-8", null);
			// loads asynchronously!
			// Cannot do this: mWebView.loadUrl("javascript:console.log('daoplayer='+daoplayer);");
		} else {
			mWebView.resumeTimers();
		}
	}

	@JavascriptInterface
	public void log(String msg) {
		Log.d(TAG,"Javascript: "+msg);
		// NOT a task main thread - can't do loadUrl
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
					mComposition.setScene(mScene);
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
					mComposition.setScene(mScene);
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
					mComposition.setScene(mScene);
					Toast.makeText(this, "Load prev scene "+mScene, Toast.LENGTH_SHORT).show();
				}				
				else 
					Log.w(TAG,"No prev scene before "+oldScene);
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
			comp.setScene(defaultScene);
			Log.i(TAG,"Read "+DEFAULT_COMPOSITION+"; playing scene "+defaultScene);
			Toast.makeText(this, "Read; playing scene "+defaultScene, Toast.LENGTH_SHORT).show();
		} else {
			Log.i(TAG,"Read "+DEFAULT_COMPOSITION+"; no default scene");
			Toast.makeText(this, "Read but no default scene", Toast.LENGTH_SHORT).show();			
		}
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
