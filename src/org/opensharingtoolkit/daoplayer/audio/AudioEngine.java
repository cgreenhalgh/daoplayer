/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.opensharingtoolkit.daoplayer.IAudio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.util.Log;

/**
 * @author pszcmg
 *
 */
public class AudioEngine implements IAudio, OnAudioFocusChangeListener {
	private static int AUDIO_CHANNELS = AudioFormat.CHANNEL_OUT_STEREO;
	private Context mContext;
	public static String TAG = "daoplayer-engine";
	
	public void start(Context context) {
		mContext = context;
		AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

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
	
	public void stop() {
		stopAudio();
		AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
		am.abandonAudioFocus(this);
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
		AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
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
		int bsize = AudioTrack.getMinBufferSize(nrate, AUDIO_CHANNELS, AudioFormat.ENCODING_PCM_16BIT);
		Log.d(TAG,"native(music) rate="+nrate+", minBufferSize="+bsize+" ("+(AUDIO_CHANNELS==AudioFormat.CHANNEL_OUT_STEREO ? "stereo" : "mono")+", 16bit pcm)");
		// samsung google nexus, android 4.3.1: 144 frames/buffer; native rate 44100; min buffer 6912 bytes (1728 frames, 39ms)
		//bsize *= 16;
		track = new AudioTrack(AudioManager.STREAM_MUSIC, nrate, AUDIO_CHANNELS, AudioFormat.ENCODING_PCM_16BIT, bsize, AudioTrack.MODE_STREAM);
		thread = new PlayThread();
		thread.start();

		//test();

	}
	
	class PlayThread extends Thread {
		boolean stopped = false;
		public void run() {
			int nrate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
			// mono sounds ok
			int bsize = AudioTrack.getMinBufferSize(nrate, AUDIO_CHANNELS, AudioFormat.ENCODING_PCM_16BIT);
			// with 3x sounds quite good, whether track bsize is x1 or x8
			short sbuf[] = new short[bsize/4];
			int ibuf[] = new int[bsize/4];
			int pos = 0;
			while (track!=null) {
				try {
					fillBuffer(ibuf);
					// 12-bit shift
					for (int i=0; i<ibuf.length; i++)
						if (ibuf[i] > 0x7ffffff)
							sbuf[i] = 0x7fff;
						else if (ibuf[i] < -0x7ffffff)
							sbuf[i] = -0x7fff;
						else 
							sbuf[i] = (short)(ibuf[i] >> 12);
					//for (int i=0; i<buf.length; i+=2)
					//	buf[i] = buf[i+1] = (short)(0x3fff*Math.sin(Math.PI*2*(pos+(i/2))*400/nrate));
					//for (int i=0; i<buf.length/2; i++)
					//	buf[i] = buf[i+buf.length/2] = (short)(0x3fff*Math.sin(Math.PI*2*(pos+(i))*400/nrate));
					//for (int i=0; i<buf.length; i++)
					//	buf[i] = (short)(0x7fff*Math.sin(Math.PI*2*(pos+(i))*400/nrate)*0.5*(1+Math.sin(Math.PI*2*(pos+(i))*0.5/nrate)));
					int res = track.write(sbuf, 0, sbuf.length);
					if (pos==0)
						track.play();
					pos += sbuf.length;
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
		private void fillBuffer(int[] buf) {
			for (int i=0; i<buf.length; i++)
				buf[i] = 0;
			AState current = getCurrentState();
			for (ATrack track : mTracks.values()) {
				AState.TrackRef tr = current.get(track);
				if (tr.isPaused()) 
					continue;
				int tpos = track.getPosition();
				// 12 bit shift
				int vol = (int)(tr.getVolume() * 0x1000);
				if (vol<=0) {
					// TODO stereo/mono
					track.setPosition(tpos+buf.length);
					continue;
				}
				for (ATrack.FileRef fr : track.mFileRefs) {
					int spos = tpos, epos = tpos+buf.length-1;
					if (fr.mTrackPos > epos || fr.mRepeats==0)
						continue;
					if (fr.mTrackPos > spos)
						spos = fr.mTrackPos;
					int repetition = 0;
					AFile file = (AFile)fr.mFile;
					if (!file.isExtracted()) 
						continue;
					int length = fr.mLength;
					if (length==IAudio.ITrack.LENGTH_ALL)
						length = file.getLength();
					repetition = (spos - fr.mTrackPos)/length;
					if (fr.mRepeats!=IAudio.ITrack.REPEATS_FOREVER) {
						if (repetition >= fr.mRepeats)
							continue;
						if (fr.mTrackPos+length*fr.mRepeats<epos)
							epos = fr.mTrackPos+length*fr.mRepeats;
					}
					// within file?
					int bix = 0, bpos = 0;
					while (spos <= epos) {
						int fpos = fr.mFilePos+(spos-fr.mTrackPos-repetition*length);
						if (fpos < bpos) {
							bix = bpos = 0;
						}
						while (bpos <= fpos && bix < file.mBuffers.size() && bpos+file.mBuffers.get(bix).length <= fpos) {
							bpos += file.mBuffers.get(bix).length;
							bix++;
						}
						if (bix >= file.mBuffers.size()) {
							// past the end - skip to next repetition
							repetition++;
							if (fr.mRepeats!=IAudio.ITrack.REPEATS_FOREVER && repetition >= fr.mRepeats)
								break;
							spos = fr.mTrackPos+repetition*length;
							continue;
						}
						short b[] = file.mBuffers.get(bix);
						int offset = fpos-bpos;
						int len = (epos-spos+1);
						if (len + offset > b.length)
							len = b.length - offset;
						if (len==0) {
							Log.e(TAG,"Try to copy 0 bytes! epos="+epos+" spos="+spos+" fpos="+fpos+" bpos="+bpos+" bix="+bix+" length="+b.length);
							break;
						}
						int boffset = spos-tpos;
						//Log.d(TAG,"Mix file buffer="+bix+" offset="+offset+" len="+len+" into "+boffset);
						for (int i=0; i<len; i++)
							buf[boffset+i] += vol*b[offset+i];
						spos += len;
					}
				}
				track.setPosition(tpos+buf.length);
			}
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

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		for (AFile afile : mFiles.values())
			afile.cancel();
		mFiles = new HashMap<String,AFile>();
		mTracks = new HashMap<Integer,ATrack>();
	}

	private Map<String,AFile> mFiles = new HashMap<String,AFile>();
	@Override
	public synchronized IFile addFile(String path) {
		AFile file = mFiles.get(path);
		if (file==null) {
			file = new AFile(path);
			mFiles.put(path, file);
			file.queueDecode(mContext);
		}
		return file;
	}

	public void test() {
		Log.d(TAG,"testing...");
		IFile f1 = this.addFile("file:///android_asset/audio/test/meeting by the river snippet.mp3");
		//((AFile)f1).decode(mContext);
		ITrack t1 = this.addTrack(true);
		t1.addFileRef(0, f1, 0, f1.getLength(), ITrack.REPEATS_FOREVER);
		AScene s1 = this.newAScene(false);
		s1.set(t1, 1.0f, 0);
		this.setScene(s1);
	}

	private HashMap<Integer,ATrack> mTracks = new HashMap<Integer,ATrack>();
	
	@Override
	public ITrack addTrack(boolean pauseIfSilent) {
		ATrack track = new ATrack(pauseIfSilent);
		mTracks.put(track.getId(), track);
		return track;
	}

	public AScene newAScene(boolean partial) {
		return new AScene(partial);
	}

	public void setScene(AScene ascene) {
		AState current = getCurrentState();
		//Log.d(TAG,"CurrentState="+current);
		AState target = current.applyScene(ascene);
		//Log.d(TAG,"TargetState="+target);
		for (ATrack track : mTracks.values()) {
			AState.TrackRef tr = target.get(track);
			if (tr==null) {
				Log.w(TAG,"No state for track "+track.getId());
			} else {
				track.setPosition(tr.getPos());
				track.setVolume(tr.getVolume());
				Log.d(TAG,"Track "+track.getId()+" set to pos="+track.getPosition()+", vol="+track.getVolume());
			}
		}
	}
	
	AState getCurrentState() {
		AState state = new AState();
		for (ATrack track : mTracks.values()) {
			state.set(track, track.getVolume(), track.getPosition(), track.getVolume()<=0 && track.isPauseIfSilent());
		}
		return state;
	}
}
