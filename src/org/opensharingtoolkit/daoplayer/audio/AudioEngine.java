/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.Arrays;
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
	private boolean debug = false;
	
	static enum StateType { STATE_FUTURE, STATE_NEXT, STATE_IN_PROGRESS, STATE_WRITTEN, STATE_DISCARDED };
	public static class StateRec {
		AState mState;
		long mWrittenFramePosition;
		long mWrittenTime;
		double mTotalTime;
		double mSceneTime;
		StateType mType = StateType.STATE_FUTURE;
		public AState getState() {
			return mState;
		}
		public long getWrittenFramePosition() {
			return mWrittenFramePosition;
		}
		public long getWrittenTime() {
			return mWrittenTime;
		}
		public StateType getType() {
			return mType;
		}
		public double getTotalTime() {
			return mTotalTime;
		}
		public double getSceneTime() {
			return mSceneTime;
		}
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
	static public float pwl(float inval, float [] map) {
		if (map.length<2)
			return 0;
	    float lin=inval, lout=map[1];
	    for (int i=0; i+1<map.length; i=i+2) {
	    	if (inval==map[i]) 
	    		return map[i+1];
	    	if (inval<map[i]) 
	    		return lout+(map[i+1]-lout)*(inval-lin)/(map[i]-lin);
	    	lin=map[i]; 
	    	lout=map[i+1];
	    }
	    if (map.length>=2) 
	    	return map[map.length-1];
	    return 0;
	}
	static public boolean pwlNonzero(float fromval, float toval, float [] map) {
		if (map.length<2)
			return false;
		float lout = map[1];
	    for (int i=0; i+1<map.length; i=i+2) {
	    	if (toval < map[i]) 
	    		// next is after us; no more to check
		    	return lout>0 || map[i+1]>0;
		    if (fromval > map[i])
		    	// all before us - depends what happens next
		    	lout = map[i+1];
		    else {
		    	// ok, inc. last
		    	if (lout>0 || map[i+1]>0)
		    		return true;
		    }
	    }
	    // the last one still matters
	    return lout>0;
	}
	static public Float pwlConstant(float fromval, float toval, float [] map) {
		if (map.length<=2)
			return 0.0f;
		if (toval <= map[0])
			return map[1];
		if (fromval >= map[(map.length/2-1)*2])
			return map[map.length-1];
		// TODO linear bits in the middle!
		return null;
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
			StateRec current = null, last = null, future = null;
			synchronized(AudioEngine.this) {
				for (int i=0; i<mStateQueue.size(); i++) {
					StateRec srec = mStateQueue.get(i);
					if (srec.mType==StateType.STATE_WRITTEN || srec.mType==StateType.STATE_DISCARDED) {
						last = srec;
						mStateQueue.remove(i);
						i--;						
					} else if (srec.mType==StateType.STATE_IN_PROGRESS) {
						srec.mType = StateType.STATE_WRITTEN;
						srec.mWrittenFramePosition = mWrittenFramePosition;
						srec.mWrittenTime = mWrittenTime;
						last = srec;
					} else if (srec.mType==StateType.STATE_NEXT) {
						srec.mType = StateType.STATE_IN_PROGRESS;
						current = srec;
					} else if (srec.mType==StateType.STATE_FUTURE) {
						srec.mType = StateType.STATE_NEXT;
						future = srec;
					}
				}
				if (current==null) {
					current = new StateRec();
					if (last!=null) {
						current.mState = last.mState.advance(mSamplesPerBlock);
						current.mTotalTime = last.mTotalTime+samplesToSeconds(mSamplesPerBlock);
						current.mSceneTime = last.mSceneTime+samplesToSeconds(mSamplesPerBlock);
					} else {
						current.mState = getIdleState();
						current.mTotalTime = current.mSceneTime = 0;
					}
					current.mType = StateType.STATE_IN_PROGRESS;
					mStateQueue.add(current);
				}
				if (future==null) {
					future = new StateRec();
					future.mState = current.mState.advance(mSamplesPerBlock);
					future.mType = StateType.STATE_NEXT;
					future.mTotalTime = current.mTotalTime+samplesToSeconds(mSamplesPerBlock);
					future.mSceneTime = current.mSceneTime+samplesToSeconds(mSamplesPerBlock);
					mStateQueue.add(future);
				}
				mFileCache.update(mStateQueue, mTracks, mSamplesPerBlock, AudioEngine.this);
			}
			for (ATrack track : mTracks.values()) {
				AState.TrackRef tr = current.mState.get(track);
				if (tr==null || tr.isPaused()) 
					continue;
				int tpos = tr.getPos();
				// pwl volume?
				float pwlVolume[] = tr.getPwlVolume();
				int vol = 0;
				if (pwlVolume!=null) {
					float fromTrackTime = (float)samplesToSeconds(tpos);
					float toTrackTime = (float)samplesToSeconds(tpos+buf.length/2-1);
					if (!pwlNonzero(fromTrackTime, toTrackTime, pwlVolume)) {
						// silent
						if (debug)
							Log.d(TAG,"Track "+tr.getTrack().getId()+" pwl silent "+fromTrackTime+"-"+toTrackTime+" for "+Arrays.toString(pwlVolume));
						continue;
					}
					Float cvol = pwlConstant(fromTrackTime, toTrackTime, pwlVolume);
					if (cvol!=null) {
						pwlVolume = null;
						vol = (int)(cvol * 0x1000);
						if (vol<=0) {
							// silent
							continue;
						}
					}
				} else {
					// 12 bit shift
					vol = (int)(tr.getVolume() * 0x1000);
					if (vol<=0) {
						// silent
						continue;
					}
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
						int boffset = 2*(spos-tpos);
						block = mFileCache.getBlock(file, fpos, block);
						if (block==null) {
							// past the end or not available - skip to next repetition
							Log.d(TAG,"Null buffer @"+fpos+" into "+boffset);
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
						if (debug)
							Log.d(TAG,"Mix file buffer @"+block.mStartFrame+"+"+offset+" len="+len+" into "+boffset);
						if (block.mChannels==1) {
							if (pwlVolume!=null)
								for (int i=0; i<len; i++) {
									// 12 bit shift
									vol = (int)(pwl((float)samplesToSeconds(spos+i), pwlVolume) * 0x1000);
									int val = vol*b[offset+i];
									buf[boffset+2*i] += val;
									buf[boffset+2*i+1] += val;
								}
							else
								for (int i=0; i<len; i++) {
									int val = vol*b[offset+i];
									buf[boffset+2*i] += val;
									buf[boffset+2*i+1] += val;
								}
								
						} else if (block.mChannels==2) {
							offset *= 2;
							int len2 = len * 2;
							if (pwlVolume!=null)
								for (int i=0; i+1<len2; i+=2) {
									// 12 bit shift
									vol = (int)(pwl((float)samplesToSeconds(spos+i/2), pwlVolume) * 0x1000);
									buf[boffset+i] += vol*b[offset+i];
									buf[boffset+i+1] += vol*b[offset+i+1];
								}
							else
								for (int i=0; i<len2; i++) {
									buf[boffset+i] += vol*b[offset+i];
								}
						} else {
							// error?!
						}
						spos += len;
					}
				}
			}
		}
	}
	public StateRec getNextState() {
		synchronized(this) {
			for (int i=0; i<mStateQueue.size(); i++) {
				StateRec srec = mStateQueue.get(i);
				if (srec.mType==StateType.STATE_NEXT) 
					return srec;
			}
		}
		return null;
	}
	public int getFutureOffset() {
		return mSamplesPerBlock;
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
		this.setScene(s1, true);
	}

	private HashMap<Integer,ATrack> mTracks = new HashMap<Integer,ATrack>();
	
	@Override
	public ITrack addTrack(boolean pauseIfSilent) {
		ATrack track = new ATrack(pauseIfSilent);
		mTracks.put(track.getId(), track);
		Log.d(TAG,"addTrack -> "+track.getId());
		return track;
	}

	public AScene newAScene(boolean partial) {
		return new AScene(partial);
	}

	public void setScene(AScene ascene, boolean newScene) {
		// new future state
		// find basis - current else written else idle
		synchronized(this) {
			StateRec current = null;
			for (int i=0; i<mStateQueue.size(); i++) {
				StateRec srec = mStateQueue.get(i);
				if (srec.mType==StateType.STATE_WRITTEN || srec.mType==StateType.STATE_IN_PROGRESS || srec.mType==StateType.STATE_NEXT)
					current = srec;
				else if (srec.mType==StateType.STATE_FUTURE) {
					// discard?!
					srec.mType = StateType.STATE_DISCARDED;
					mStateQueue.remove(i);
					i--;
				}
			}
			if (current==null) {
				// temporary placeholder!
				current = new StateRec();
				current.mState = getIdleState();
				current.mTotalTime = 0;
				current.mSceneTime = current.mTotalTime;
			}
				
			//Log.d(TAG,"CurrentState="+current);
			AState target = current.mState.applyScene(ascene, mSamplesPerBlock);
			
			StateRec srec = new StateRec();
			srec.mType = StateType.STATE_FUTURE;
			srec.mState = target;
			srec.mTotalTime = current.mTotalTime+samplesToSeconds(mSamplesPerBlock);
			if (newScene)
				srec.mSceneTime = 0;
			else 
				srec.mSceneTime = current.mSceneTime+samplesToSeconds(mSamplesPerBlock);
			
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
	public double samplesToSeconds(int trackPos) {
		return trackPos*1.0/DEFAULT_SAMPLE_RATE;
	}
}
