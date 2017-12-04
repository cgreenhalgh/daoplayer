/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

import java.io.File;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * @author pszcmg
 *
 */
public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
	private static final String TAG = "preferences";
	protected static final int FILE_REQUEST_CODE = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		checkService();
		setServiceAction("pref_reload", Service.ACTION_RELOAD);
		setServiceAction("pref_defaultscene", Service.ACTION_DEFAULT_SCENE);
		//setServiceAction("pref_nextscene", Service.ACTION_NEXT_SCENE);
		//setServiceAction("pref_prevscene", Service.ACTION_PREV_SCENE);
		setServiceAction("pref_clearlogs", Service.ACTION_CLEAR_LOGS);
		setServiceAction("pref_runtest", Service.ACTION_RUN_TEST);
		Preference button = (Preference)findPreference("pref_pickfile");
		button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference arg0) {
				Intent i = new Intent();
				// open intents file manager - see http://openintents.org/~oi/filemanager
				i.setAction("org.openintents.action.PICK_FILE");
				SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(Preferences.this);
				String filename = spref.getString("pref_filename", Service.DEFAULT_COMPOSITION);
				File filesDir = Compat.getExternalFilesDir(Preferences.this);
				File file = new File(filesDir, filename);
				i.setData(Uri.fromFile(file));
				i.putExtra("org.openintents.extra.TITLE", "Selection composition");
				i.putExtra("org.openintents.extra.BUTTON_TEXT", "OK");
				Log.d(TAG,"startActivityForResult "+i);
				startActivityForResult(i, FILE_REQUEST_CODE);
				return true;
			}
        });	
	}
	private void updateFilename() {
		try {
			SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
			String filename = spref.getString("pref_filename", "Name of composition file to load");
			EditTextPreference filenamePreference = (EditTextPreference)findPreference("pref_filename");
			filenamePreference.setSummary(filename);
			filenamePreference.setText(filename);
		}
		catch (Exception e) {
			Log.e(TAG,"Error updating filename summary", e);
		}
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
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode==FILE_REQUEST_CODE) {
			if (resultCode==RESULT_OK) {
				String uri = data.getDataString();
				Log.d(TAG,"picked file "+uri);
				File filesDir = Compat.getExternalFilesDir(Preferences.this);
				String dirUri = Uri.fromFile(filesDir).toString();
				if (uri.startsWith(dirUri)) {
					String filename = uri.substring(dirUri.length());
					if (filename.startsWith("/"))
						filename = filename.substring(1);
					Log.d(TAG,"Set filename to "+filename+" from "+uri);
					SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
					spref.edit().putString(Service.PREF_FILENAME, filename).apply();
					Intent i = new Intent();
					i.setAction(Service.ACTION_RELOAD);
					i.setClass(getApplicationContext(), Service.class);
					startService(i);
				} else {
					String message= "Cannot set filename to "+uri+"; outside files dir "+dirUri;
					Log.w(TAG,message);
					Toast.makeText(this, message, Toast.LENGTH_LONG).show();
				}
			}
			else
				Log.w(TAG,"Error/cancel pick file");
		}
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
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
		updateFilename();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if ("pref_runservice".equals(key)) {
			checkService();
		}
		else if ("pref_filename".equals(key))
			updateFilename();
	}

	private void checkService() {
		SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(this);
		if (spref.getBoolean("pref_runservice", false))
			// make sure service is running...
			startService(new Intent(getApplicationContext(), Service.class));		
	}
	/* (non-Javadoc)
	 * @see android.app.Activity#onBackPressed()
	 */
	@Override
	public void onBackPressed() {
		startActivity(new Intent(this, Status.class));
		finish();
	}
	
}
