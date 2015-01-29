/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.opensharingtoolkit.daoplayer.IAudio;
import org.opensharingtoolkit.daoplayer.ILog;

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
	private static int N_CHANNELS = 2; // stereo :-)
	private Context mContext;
	private ILog mLog;
	public static String TAG = "daoplayer-engine";
	private static int DEFAULT_SAMPLE_RATE = 44100;
	private int mSamplesPerBlock;
	private long mWrittenFramePosition = 0, mWrittenTime = 0;
	private Vector<StateRec> mStateQueue = new Vector<StateRec>();
	private FileCache mFileCache;
	
	static enum StateType { STATE_FUTURE, STATE_IN_PROGRESS, STATE_WRITTEN, STATE_DISCARDED };
	static class StateRec {
		AState mState;
		long mStartFramePosition;
		StateType mType = StateType.STATE_FUTURE;
	};
	
	public AudioEngine(ILog log) {
		mLog = log;
	}
	public void start(Context context) {
		mContext = context;
		AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
		if (mFileCache==null)
			mFileCache = new FileCache(context);
		
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
    		mLog.log("LOST AUDIO FOCUS");
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
		mLog.log("native(music) rate="+nrate+", minBufferSize="+bsize+" ("+(AUDIO_CHANNELS==AudioFormat.CHANNEL_OUT_STEREO ? "stereo" : "mono")+", 16bit pcm)");
		// samsung google nexus, android 4.3.1: 144 frames/buffer; native rate 44100; min buffer 6912 bytes (1728 frames, 39ms)
		//bsize *= 16;
		track = new AudioTrack(AudioManager.STREAM_MUSIC, nrate, AUDIO_CHANNELS, AudioFormat.ENCODING_PCM_16BIT, bsize, AudioTrack.MODE_STREAM);
		mSamplesPerBlock = bsize/ /*bytes/value*/2 /N_CHANNELS/ /*half buffer*/2;
		mWrittenFramePosition = 0;
		mWrittenTime = 0;
		thread = new PlayThread();
		thread.start();

		//test();

	}
	
	class PlayThread extends Thread {
		boolean stopped = false;
		public void run() {
			int nrate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
			// mono sounds ok
			// with 3x sounds quite good, whether track bsize is x1 or x8
			short sbuf[] = new short[mSamplesPerBlock*N_CHANNELS];
			int ibuf[] = new int[mSamplesPerBlock*N_CHANNELS];
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
					synchronized(AudioEngine.this) {
						mWrittenFramePosition += (sbuf.length/N_CHANNELS);
						mWrittenTime = System.currentTimeMillis();
					}
				}
				catch (Exception e) {
					Log.e(TAG,"Error doing track write: "+e.getMessage(), e);
				}
			}
			Log.d(TAG,"PlayThread exit");
		}
		private void fillBuffer(int[] buf) {
			for (int i=0; i<buf.length; i++)
				buf[i] = 0;
			// take next state
			AState current = null, last = null;
			synchronized(AudioEngine.this) {
				for (int i=0; i<mStateQueue.size(); i++) {
					StateRec srec = mStateQueue.get(i);
					if (srec.mType==StateType.STATE_WRITTEN || srec.mType==StateType.STATE_DISCARDED) {
						mStateQueue.remove(i);
						i--;						
					} else if (srec.mType==StateType.STATE_IN_PROGRESS) {
						srec.mType = StateType.STATE_WRITTEN;
						last = srec.mState;
					} else if (srec.mType==StateType.STATE_FUTURE) {
						srec.mType = StateType.STATE_IN_PROGRESS;
						current = srec.mState;
					}
				}
				if (current==null) {
					if (last!=null)
						current = last.advance(mSamplesPerBlock);
					else
						current = getIdleState();
					StateRec srec = new StateRec();
					srec.mState = current;
					srec.mType = StateType.STATE_IN_PROGRESS;
					mStateQueue.add(srec);
				}
				mFileCache.update(mStateQueue, mTracks, mSamplesPerBlock);
			}
			for (ATrack track : mTracks.values()) {
				AState.TrackRef tr = current.get(track);
				if (tr==null || tr.isPaused()) 
					continue;
				int tpos = tr.getPos();
				// 12 bit shift
				int vol = (int)(tr.getVolume() * 0x1000);
				if (vol<=0) {
					// stereo buffer
					continue;
				}
				for (ATrack.FileRef fr : track.mFileRefs) {
					int spos = tpos, epos = tpos+buf.length/2-1;
					if (fr.mTrackPos > epos || fr.mRepeats==0)
						continue;
					if (fr.mTrackPos > spos)
						spos = fr.mTrackPos;
					int repetition = 0;
					AFile file = (AFile)fr.mFile;
					int length = fr.mLength;
					if (length==IAudio.ITrack.LENGTH_ALL)
						repetition = 0;
					else {
						repetition = (spos - fr.mTrackPos)/length;
						if (fr.mRepeats!=IAudio.ITrack.REPEATS_FOREVER) {
							if (repetition >= fr.mRepeats)
								continue;
							if (fr.mTrackPos+length*fr.mRepeats<epos)
								epos = fr.mTrackPos+length*fr.mRepeats;
						}
					}
					// within file?
					FileCache.Block block = null;
					while (spos <= epos) {
						int fpos = fr.mFilePos+(spos-fr.mTrackPos-repetition*length);
						block = mFileCache.getBlock(file, fpos, block);
						if (block==null) {
							// past the end or not available - skip to next repetition
							if (length==IAudio.ITrack.LENGTH_ALL)
								// can't repeat length all (for now, anyway)
								break;
							repetition++;
							if (fr.mRepeats!=IAudio.ITrack.REPEATS_FOREVER && repetition >= fr.mRepeats)
								break;
							spos = fr.mTrackPos+repetition*length;
							continue;
						}
						short b[] = block.mSamples;
						int offset = fpos-block.mStartFrame;
						int len = (epos-spos+1);
						if (len + offset > b.length/block.mChannels)
							len = b.length/block.mChannels - offset;
						if (len==0) {
							Log.e(TAG,"Try to copy 0 bytes! epos="+epos+" spos="+spos+" fpos="+fpos+" bpos="+block.mStartFrame+" bix="+block.mIndex+" length="+b.length);
							break;
						}
						int boffset = 2*(spos-tpos);
						spos += len;
						//Log.d(TAG,"Mix file buffer="+bix+" offset="+offset+" len="+len+" into "+boffset);
						if (block.mChannels==1) {
							for (int i=0; i<len; i++) {
								int val = vol*b[offset+i];
								buf[boffset+2*i] += val;
								buf[boffset+2*i+1] += val;
							}
							
						} else if (block.mChannels==2) {
							offset *= 2;
							len *= 2;
							for (int i=0; i<len; i++) {
								buf[boffset+i] += vol*b[offset+i];
							}
						} else {
							// error?!
						}
					}
				}
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
		mFiles = new HashMap<String,AFile>();
		mTracks = new HashMap<Integer,ATrack>();
		mStateQueue = new Vector<StateRec>();
		if (mFileCache!=null)
			mFileCache.reset();
	}

	private Map<String,AFile> mFiles = new HashMap<String,AFile>();
	@Override
	public synchronized IFile addFile(String path) {
		AFile file = mFiles.get(path);
		if (file==null) {
			file = new AFile(path);
			mFiles.put(path, file);
		}
		return file;
	}

	public void test() {
		Log.d(TAG,"testing...");
		IFile f1 = this.addFile("file:///android_asset/audio/test/meeting by the river snippet.mp3");
		//((AFile)f1).decode(mContext);
		ITrack t1 = this.addTrack(true);
		t1.addFileRef(0, f1, 0, 44100*4, ITrack.REPEATS_FOREVER);
		AScene s1 = this.newAScene(false);
		s1.set(t1, 1.0f, 0, false);
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
		// new future state
		// find basis - current else written else idle
		synchronized(this) {
			AState current = null;
			for (int i=0; i<mStateQueue.size(); i++) {
				StateRec srec = mStateQueue.get(i);
				if (srec.mType==StateType.STATE_WRITTEN || srec.mType==StateType.STATE_IN_PROGRESS)
					current = srec.mState;
				else if (srec.mType==StateType.STATE_FUTURE) {
					// discard?!
					srec.mType = StateType.STATE_DISCARDED;
					mStateQueue.remove(i);
					i--;
				}
			}
			if (current==null)
				current = getIdleState();
				
			//Log.d(TAG,"CurrentState="+current);
			AState target = current.applyScene(ascene, mSamplesPerBlock);
			
			StateRec srec = new StateRec();
			srec.mType = StateType.STATE_FUTURE;
			srec.mState = target;
			mStateQueue.add(srec);
		}
	}
	
	AState getIdleState() {
		AState state = new AState();
		for (ATrack track : mTracks.values()) {
			state.set(track, 0, 0, true);
		}
		return state;
	}
	
	public Integer secondsToSamples(Double seconds) {
		if (seconds==null)
			return null;
		if (seconds<0)
			return -1;
		return new Double(seconds*DEFAULT_SAMPLE_RATE).intValue();
	}
}
