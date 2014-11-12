/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

/**
 * @author pszcmg
 *
 */
public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		checkService();
		setServiceAction("pref_reload", Service.ACTION_RELOAD);
		setServiceAction("pref_defaultscene", Service.ACTION_DEFAULT_SCENE);
		setServiceAction("pref_nextscene", Service.ACTION_NEXT_SCENE);
		setServiceAction("pref_prevscene", Service.ACTION_PREV_SCENE);
		setServiceAction("pref_updatescene", Service.ACTION_UPDATE_SCENE);
	}
	private void setServiceAction(String key, final String action) {
		Preference button = (Preference)findPreference(key);
		button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference arg0) {
				Intent i = new Intent();
				i.setAction(action);
				i.setClass(getApplicationContext(), Service.class);
				startService(i);
				return true;
			}
        });	
	}
	@Override
	protected void onPause() {
		super.onPause();
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		spref.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		spref.registerOnSharedPreferenceChangeListener(this);
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
		if (spref.getBoolean("pref_runservice", false))
			// make sure service is running...
			startService(new Intent(getApplicationContext(), Service.class));		
	}
}
