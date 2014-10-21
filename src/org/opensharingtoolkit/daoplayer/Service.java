/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

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
public class Service extends android.app.Service implements OnSharedPreferenceChangeListener, OnAudioFocusChangeListener {

	private boolean started = false;
	private static final String TAG = "daoplayer-service";
	private static final int SERVICE_NOTIFICATION_ID = 1;
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
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
		
		AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);

		// Request audio focus for playback
		int result = am.requestAudioFocus(this,
		                                 // Use the music stream.
		                                 AudioManager.STREAM_MUSIC,
		                                 // Request permanent focus.
		                                 AudioManager.AUDIOFOCUS_GAIN);
		   
		if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
		    // Start playback.
			Log.d(TAG,"Got audio focus");
			startAudio();
		}
		else {
			Log.d(TAG,"Problem getting audio focus: "+result);
		}
		
		// ACTION_AUDIO_BECOMING_NOISY broadcast listener??
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
		
		stopAudio();
		AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
		am.abandonAudioFocus(this);
		
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

	@Override
	public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
    		Log.d(TAG,"onAudioFocusChange(LOSS_TRANSIENT)");
            // Pause playback
    		if (track!=null)
    			track.pause();
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
    		Log.d(TAG,"onAudioFocusChange(GAIN)");
            // Resume playback 
			startAudio();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
    		Log.d(TAG,"onAudioFocusChange(LOSS)");
            // Stop playback
    		stopAudio();
        } else {
        	Log.d(TAG,"onAudioFocusChange("+focusChange+")");
        }
	}				
	
	private AudioTrack track;
	private void startAudio() {
		Log.d(TAG,"startAudio");
		AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
		String frames = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
		String rate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
		int rateHz = 44100;
		try {
			rateHz = Integer.parseInt(rate);
		} catch (Exception e) {
			Log.e(TAG,"Error reading sample rate: "+rate+": "+e.getMessage());
		}
		Log.d(TAG,"frame/buffer="+frames+", rate="+rate);
		int nrate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
		Log.d(TAG,"native(music) rate="+nrate);
		int bsize = AudioTrack.getMinBufferSize(nrate, AudioFormat.CHANNEL_OUT_MONO/*STEREO*/, AudioFormat.ENCODING_PCM_16BIT);
		Log.d(TAG,"native(music) rate="+nrate+", minBufferSize="+bsize+" (stereo, 16bit pcm)");
		// samsung google nexus, android 4.3.1: 144 frames/buffer; native rate 44100; min buffer 6912 bytes (1728 frames, 39ms)
		//bsize *= 16;
		track = new AudioTrack(AudioManager.STREAM_MUSIC, nrate, AudioFormat.CHANNEL_OUT_MONO/*STEREO*/, AudioFormat.ENCODING_PCM_16BIT, bsize, AudioTrack.MODE_STREAM);
		thread = new PlayThread();
		thread.start();
	}
	
	class PlayThread extends Thread {
		boolean stopped = false;
		public void run() {
			int nrate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
			// mono sounds ok
			int bsize = AudioTrack.getMinBufferSize(nrate, AudioFormat.CHANNEL_OUT_MONO/*STEREO*/, AudioFormat.ENCODING_PCM_16BIT);
			// with 3x sounds quite good, whether track bsize is x1 or x8
			short buf[] = new short[bsize/4];
			int pos = 0;
			while (track!=null) {
				try {
					//for (int i=0; i<buf.length; i+=2)
					//	buf[i] = buf[i+1] = (short)(0x3fff*Math.sin(Math.PI*2*(pos+(i/2))*400/nrate));
					//for (int i=0; i<buf.length/2; i++)
					//	buf[i] = buf[i+buf.length/2] = (short)(0x3fff*Math.sin(Math.PI*2*(pos+(i))*400/nrate));
					for (int i=0; i<buf.length; i++)
						buf[i] = (short)(0x7fff*Math.sin(Math.PI*2*(pos+(i))*400/nrate)*0.5*(1+Math.sin(Math.PI*2*(pos+(i))*0.5/nrate)));
					int res = track.write(buf, 0, buf.length);
					if (pos==0)
						track.play();
					pos += buf.length;
					if (res==AudioTrack.ERROR_INVALID_OPERATION) {
						Log.w(TAG,"Error doing track write");
						break;
					}
				}
				catch (Exception e) {
					Log.e(TAG,"Error doing track write: "+e.getMessage());
				}
			}
			Log.d(TAG,"PlayThread exit");
		}
	}
	private PlayThread thread;
	private void stopAudio() {
		Log.d(TAG,"stopAudio");
		if (track!=null) {
			track.stop();
			track.release();
			track = null;
		}		
	}
}
