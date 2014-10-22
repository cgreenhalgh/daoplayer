/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

import org.opensharingtoolkit.daoplayer.audio.AudioEngine;

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

/**
 * @author pszcmg
 *
 */
public class Service extends android.app.Service implements OnSharedPreferenceChangeListener {

	private static final String TAG = "daoplayer-service";
	private static final int SERVICE_NOTIFICATION_ID = 1;
	private AudioEngine mAudioEngine;
	private boolean started = false;

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

		if (mAudioEngine==null)
			mAudioEngine = new AudioEngine();
		mAudioEngine.start(this);
	}

	@Override
	public void onDestroy() {
		Log.d(TAG,"onDestroy");
		super.onDestroy();
		onStop();
	}
	
	private void onStop() {
		if (!started)
			return;
		Log.d(TAG,"onStop");
		started = false;
		
		// Note: this means we depend on Preferences Activity to (re)start us
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		spref.unregisterOnSharedPreferenceChangeListener(this);

		mAudioEngine.stop();

		// removes notification!
		stopForeground(true);
	}
	
	// starting service...
	private void handleCommand(Intent intent) {
		Log.d(TAG,"handleCommand "+(intent!=null ? intent.getAction() : "null"));
		checkService();
	}


	// This is the old onStart method that will be called on the pre-2.0
	// platform.  On 2.0 or later we override onStartCommand() so this
	// method will not be called.
	@Override
	public void onStart(Intent intent, int startId) {
	    handleCommand(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
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
