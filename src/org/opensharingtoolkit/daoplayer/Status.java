/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

import java.io.File;

import org.opensharingtoolkit.daoplayer.Service.GpsInfo;
import org.opensharingtoolkit.daoplayer.Service.TrackInfo;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * @author pszcmg
 *
 */
public class Status extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
	private static final String TAG = "status";
	protected boolean mBound = false;
	protected Service.LocalBinder mLocal;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.status);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		Preference button = (Preference)findPreference("status_play");
		button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference arg0) {
				setRunservice(true);
				return true;
			}
        });	
		button = (Preference)findPreference("status_pause");
		button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference arg0) {
				setRunservice(false);
				return true;
			}
        });	
	}
	@Override
	protected void onStart() {	
		super.onStart();
		Intent i = new Intent(this, Service.class);
		bindService(i, mConnection, Context.BIND_AUTO_CREATE);
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		spref.registerOnSharedPreferenceChangeListener(this);
		checkService();
		IntentFilter ifilter = new IntentFilter(Service.ACTION_SCENE_CHANGED);
		ifilter.addAction(Service.ACTION_GPS_STATUS);
		LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, ifilter);
	}
	private class MyReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (Service.ACTION_SCENE_CHANGED.equals(intent.getAction()))
				updateCurrentlyPlaying(intent.getExtras().getString(Service.EXTRA_TITLE, ""),
						intent.getExtras().getString(Service.EXTRA_ARTIST,""));
			else if (Service.ACTION_GPS_STATUS.equals(intent.getAction()))
				updateGps(intent.getExtras().getString(Service.EXTRA_STATUS, ""),
						intent.getExtras().getString(Service.EXTRA_SUBSTATUS,""));				
		}		
	};
	private MyReceiver mReceiver = new MyReceiver();
	@Override
	protected void onStop() {
		super.onStop();
		if (mBound){
			unbindService(mConnection);
			mBound = false;
		}
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		spref.unregisterOnSharedPreferenceChangeListener(this);
	}

	public void updateGps(String status, String substatus) {
		Preference gps = (Preference)findPreference("status_gps");
		gps.setTitle(status);
		gps.setSummary(substatus);
	}
	public void updateCurrentlyPlaying(String title, String artist) {
		Preference current = (Preference)findPreference("status_currentlyplaying");
		current.setTitle(title);
		current.setSummary(artist);
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mBound = true;
			mLocal = (Service.LocalBinder)service;
			onBind();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBound = false;
		}
	};
	protected void onBind() {
		// playing?
		TrackInfo ti = mLocal.getTrackInfo();
		if (ti!=null)
			updateCurrentlyPlaying(ti.title, ti.artist);
		// gps?
		GpsInfo gps = mLocal.getGpsInfo();
		if (gps!=null)
			updateGps(gps.status, gps.substatus);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.status_menu, menu);
	    return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	        case R.id.status_settings:
	        	startActivity(new Intent(this, Preferences.class));
	            return true;
//	        case R.id.status_reset:
//				Intent i = new Intent();
//				i.setAction(Service.ACTION_RELOAD);
//				i.setClass(getApplicationContext(), Service.class);
//				startService(i);
//	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
	protected void setRunservice(boolean runservice) {
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		spref.edit().putBoolean("pref_runservice", runservice).commit();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if ("pref_runservice".equals(key)) {
			checkService();
		}
	}

	private void checkService() {
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		if (spref.getBoolean("pref_runservice", false)) {
			// make sure service is running...
			startService(new Intent(getApplicationContext(), Service.class));
			Preference button = (Preference)findPreference("status_play");
			button.setEnabled(false);
			button.setSummary("Playing");
			button = (Preference)findPreference("status_pause");
			button.setEnabled(true);
			button.setSummary("Tap to pause audio");
		} else {
			Preference button = (Preference)findPreference("status_play");
			button.setEnabled(true);
			button.setSummary("Tap to resume audio");
			button = (Preference)findPreference("status_pause");
			button.setEnabled(false);
			button.setSummary("Paused");
		}
	}
}
