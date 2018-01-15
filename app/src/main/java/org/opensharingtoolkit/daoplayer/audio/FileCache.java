/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.opensharingtoolkit.daoplayer.IAudio;
import org.opensharingtoolkit.daoplayer.ILog;
import org.opensharingtoolkit.daoplayer.audio.AudioEngine.StateType;

import android.content.Context;
import android.util.Log;

/** 
 * Maintains cache of decoded files, decoding more as required. Used by AudioEngine.
 * Updates based on AudioEngine queue State. Uses internal threads to decode in the 
 * background.
 * 
 * @author pszcmg
 */
public class FileCache {
	static boolean debug = true;
	
	public static class Block {
		short mSamples[];
		int mChannels;
		int mStartFrame; // Note frame number
		// internal
		int mIndex;
		long mLastRequestedTime;
		/**
		 * @return the mSamples
		 */
		public short[] getSamples() {
			return mSamples;
		}
		/**
		 * @return the mChannels
		 */
		public int getChannels() {
			return mChannels;
		}
		/**
		 * @return the mStartFrame
		 */
		public int getStartFrame() {
			return mStartFrame;
		}
		/**
		 * @return the mIndex
		 */
		public int getIndex() {
			return mIndex;
		}
		
	};
	static class File {
		String mPath;
		FileDecoder mDecoder;
		TreeMap<Integer,Block> mBlocks = new TreeMap<Integer,Block>();
		File(String path) {
			mPath = path;
		}
	}
	private static final String TAG = "daoplayer-filecache";
	private static final long CACHE_RETAIN_TIME_MS = 2000;
	private static final int CACHE_RATAIN_SAMPLES = (2*44100);
	private Map<String,File> mFiles = new HashMap<String,File>();
	private Vector<FileNeedTask> mTasks = new Vector<FileNeedTask>();
	private ExecutorService mExecutor;
	
	/** may use context to access assets */
	private Context mContext;
	private static ILog mLog;

	/**
	 * @param mContext
	 */
	public FileCache(Context mContext, ILog log) {
		this.mContext = mContext;
		mLog = log;
		mExecutor = Executors.newSingleThreadExecutor();
	}

	/** play out API */
	public Block getBlock(AFile afile, int frame, Block lastBlock) {
		File file = null;
		synchronized(this) {
			file = mFiles.get(afile.getPath());
		}
		if (file==null)
			return null;
		synchronized(file.mBlocks) {
			TreeMap.Entry<Integer,Block> ent = file.mBlocks.floorEntry(frame);
			if (ent!=null) {
				Block b = ent.getValue();
				if (b.mStartFrame<=frame && b.mStartFrame+b.mSamples.length/b.mChannels > frame) {
					// found in cache!
					b.mLastRequestedTime = System.currentTimeMillis();
					return b;
				}
			}
			// cache miss  
		}
		return null;
	}

	public void reset() {
		synchronized (this) {
			for (File file : mFiles.values()) {
				if (file.mDecoder!=null)
					file.mDecoder.cancel();
			}
			// TODO no need?!
			mFiles.clear();
		}
	}

	static class NeedRec {
		public NeedRec(AFile file) {
			mFile = file;
		}
		AFile mFile;
		TreeMap<Integer,Interval> mIntervals = new TreeMap<Integer,Interval>();
		public void merge(Interval ival) {
			int fromInclusive = ival.mFromInclusive;
			TreeMap.Entry<Integer,Interval> from = mIntervals.floorEntry(fromInclusive);
			if (from!=null)
				fromInclusive = from.getValue().mFromInclusive;
			// overlaps
			// copy
			Collection<Interval> overlaps = new LinkedList<Interval>();
			overlaps.addAll(mIntervals.subMap(fromInclusive, ival.mToExclusive).values());
			for (Interval overlap : overlaps) {
				if (overlap.mToExclusive<=ival.mFromInclusive)
					// shouldn't happen
					continue;
				else if (overlap.mToExclusive<=ival.mToExclusive) {
					// 1. ends at/before new
					if (overlap.mFromInclusive<ival.mFromInclusive) {
						// 1.1 starts before new (and ends at/before new)
						if (overlap.mPriority==ival.mPriority) {
							// merge (into new)
							ival.mFromInclusive = overlap.mFromInclusive;
							mIntervals.remove(overlap.mFromInclusive);
						}
						else if (overlap.mPriority>ival.mPriority){
							// we shrink
							ival.mFromInclusive = overlap.mToExclusive;
							// dead?
							if (ival.mFromInclusive>=ival.mToExclusive)
								return;
						}
						else {
							// they shrink
							overlap.mToExclusive = ival.mFromInclusive;
						}
					} else {
						// 1.2. start at/after new (and ends at/before new), i.e. dominated!
						if (overlap.mPriority==ival.mPriority) {
							// merge (into new)
							ival.mFromInclusive = overlap.mFromInclusive;
							mIntervals.remove(overlap.mFromInclusive);
						}
						else if (overlap.mPriority>ival.mPriority){
							// we shrink, possible split off before
							if (ival.mFromInclusive<overlap.mFromInclusive) {
								Interval fragment = new Interval(ival.mFromInclusive, overlap.mFromInclusive, ival.mPriority);
								mIntervals.put(fragment.mFromInclusive, fragment);
							}
							ival.mFromInclusive = overlap.mToExclusive;
							// dead?
							if (ival.mFromInclusive>=ival.mToExclusive)
								return;
						}
						else {
							// it shrinks = killed!
							mIntervals.remove(overlap.mFromInclusive);
						}						
					}
				} else {
					// 2. ends after new
					if (overlap.mFromInclusive<ival.mFromInclusive) {
						// 2.1 starts before new (and ends after new)
						if (overlap.mPriority==ival.mPriority) {
							// we are no needed! (merge)
							return;
						} else if (overlap.mPriority>ival.mPriority) {
							// we are no needed! (killed)
							return;
						} else {
							// we split it
							Interval fragment = new Interval(ival.mToExclusive, overlap.mToExclusive, overlap.mPriority);
							mIntervals.put(fragment.mFromInclusive, fragment);
							overlap.mToExclusive = ival.mFromInclusive;
						}
					} else {
						// 2.2 starts at/after new (and ends after new) 
						if (overlap.mPriority==ival.mPriority) {
							// merge (in order)
							ival.mToExclusive = overlap.mToExclusive;
							mIntervals.remove(overlap.mFromInclusive);
						} else if (overlap.mPriority>ival.mPriority) {
							// we shrink
							ival.mToExclusive = overlap.mFromInclusive;
							// dead?
							if (ival.mFromInclusive>=ival.mToExclusive)
								return;
						} else {
							// it shrinks
							if (ival.mToExclusive>=overlap.mToExclusive) 
								mIntervals.remove(overlap.mFromInclusive);
							else
								overlap.mFromInclusive = ival.mToExclusive;
						}
					}
				}
			}//for overlaps
			// add what is left!
			mIntervals.put(ival.mFromInclusive, ival);
		}
	};
	static int getPriority(AudioEngine.StateType type) {
		switch(type) {
		case STATE_DISCARDED:
		case STATE_WRITTEN: 
		default:
			return -1;
		case STATE_IN_PROGRESS:
			return 1;
		case STATE_NEXT:
			return 1;
		case STATE_FUTURE:
			return 0;
		case STATE_FUTURE2:
			return 0;
		}
	}
	static class Interval {
		int mFromInclusive;
		int mToExclusive;
		int mPriority;
		Interval(int fromInclusive, int toExclusive) {
			mFromInclusive = fromInclusive;
			mToExclusive = toExclusive;
		}
		public Interval(int fromInclusive, int toExclusive, int priority) {
			mFromInclusive = fromInclusive;
			mToExclusive = toExclusive;
			mPriority = priority;
		}
	}
	public void update(Vector<AudioEngine.StateRec> stateQueue, HashMap<Integer, ATrack> mTracks, int samplesPerBlock, AudioEngine engine) {
		// What file spans do we need and how soon?
		HashMap<String,NeedRec> needRecs = new HashMap<String,NeedRec>();
		for (AudioEngine.StateRec srec : stateQueue) {
			if (srec.mType==AudioEngine.StateType.STATE_IN_PROGRESS || srec.mType==AudioEngine.StateType.STATE_NEXT) {
				int blockLength = samplesPerBlock*2;
				for (ATrack track : mTracks.values()) {
					AState.TrackRef tr = srec.mState.get(track);
					if (tr==null || tr.isPaused()) 
						// TODO ok with align?
						continue;
					int [] align = tr.getAlign();
					// frames, inclusive
					int bufStart = 0, bufEnd = -1;

					int refPos = tr.getPos();
					float pwlVolume[] = tr.getPwlVolume();
					int vol = 0;
					if (pwlVolume==null) {
						// 12 bit shift
						vol = (int)(tr.getVolume() * 0x1000);
						if (vol<=0) {
							// silent
							continue;
						}
					}
					int sceneTime = engine.secondsToSamples(srec.mSceneTime);
					// each aligned sub-section
					for (int ai=0; ai<=(align!=null ? align.length : 0) && bufStart<=blockLength-1; ai+=2, bufStart = bufEnd+1)
					{
						int tpos = refPos+bufStart;
						if (align!=null && align.length>=2) {
							if (ai<align.length) {
								// up to an alignment
								if (align[ai]<=sceneTime+bufStart) {
									// already past
									refPos = align[ai+1]+sceneTime-align[ai];
									//Log.d(TAG,"Align track at +"+bufStart+": "+sceneTime+" -> "+tr.getPos()+" (align "+align[ai]+"->"+align[ai+1]+")");
									continue;
								}
								if (align[ai]>=sceneTime+blockLength)
									// after this buffer
									bufEnd = blockLength-1;
								else {
									// coming up...
									bufEnd = align[ai]-sceneTime-1;
									// for next time
									refPos = align[ai+1]+sceneTime-align[ai];
								}
							} else {
								// from last alignment
								bufEnd = blockLength-1;						
							}
						}
						else
							bufEnd = blockLength-1;
						
						if (pwlVolume!=null) {
							float fromSceneTime = (float)engine.samplesToSeconds(sceneTime+bufStart);
							float toSceneTime = (float)engine.samplesToSeconds(sceneTime+bufEnd);
							if (!AudioEngine.pwlNonzero(fromSceneTime, toSceneTime, pwlVolume)) {
								// silent
								continue;
							}
							Float cvol = AudioEngine.pwlConstant(fromSceneTime, toSceneTime, pwlVolume);
							if (cvol!=null) {
								pwlVolume = null;
								vol = (int)(cvol * 0x1000);
								if (vol<=0) {
									// silent
									continue;
								}
							}
						} 
						for (ATrack.FileRef fr : track.mFileRefs) {
							int spos = tpos, epos = tpos+bufEnd-bufStart;
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
							while (spos <= epos) {
								int fpos = fr.mFilePos+(spos-fr.mTrackPos-repetition*length);
								// fpos is position in file of spos in track
								NeedRec nr = needRecs.get(file.getPath());
								if (nr==null) {
									nr = new NeedRec(file);
									needRecs.put(file.getPath(), nr);
								}
								Interval ival = new Interval(fpos, fpos+epos-spos, getPriority(srec.mType));
								nr.merge(ival);
								// next repetition
								if (length==IAudio.ITrack.LENGTH_ALL)
									// can't repeat length all (for now, anyway)
									break;
								repetition++;
								if (fr.mRepeats!=IAudio.ITrack.REPEATS_FOREVER && repetition >= fr.mRepeats)
									break;
								spos = fr.mTrackPos+repetition*length;
								continue;
							}
						}
					}
				}
			}
		}
		// Throw this over the the background thread!!
		update(needRecs);
	}
	private synchronized void update(HashMap<String, NeedRec> needRecs) {
		// old tasks
		for (FileNeedTask task : mTasks) {
			task.cancel(false);
		}
		mTasks.clear();
		// eject old
		long now = System.currentTimeMillis();
		long old = now-CACHE_RETAIN_TIME_MS;
		for (File file : mFiles.values()) {
			synchronized(file.mBlocks) {
				Iterator<TreeMap.Entry<Integer,Block>> iter = file.mBlocks.tailMap(CACHE_RATAIN_SAMPLES).entrySet().iterator();
				while(iter.hasNext()) {
					TreeMap.Entry<Integer,Block> entry = iter.next();
					if (entry.getValue().mLastRequestedTime < old && entry.getValue().mStartFrame>=CACHE_RATAIN_SAMPLES) {
						if (debug)
							Log.d(TAG,"Eject block "+entry.getValue().mStartFrame+"("+entry.getValue().mSamples.length+") from cache for "+file.mPath);
						iter.remove();
					}
				}
			}
		}
		// decode / blocks...?
		for (NeedRec nrec : needRecs.values()) {
			File file = null;
			synchronized(this) {
				file = mFiles.get(nrec.mFile.getPath());
				if (file==null) {
					file = new File(nrec.mFile.getPath());
					mFiles.put(nrec.mFile.getPath(), file);
				}
				if (file.mDecoder==null) {
					file.mDecoder = new FileDecoder(nrec.mFile.getPath(), mContext);
					//file.mDecoder.queueDecode();
				}
			}
			FileNeedTask task = new FileNeedTask(nrec, file, 1);
			mTasks.add(task);
			mExecutor.execute(task);
		}
	}

	/** handle needed stuff from a File */
	static class FileNeedTask extends FutureTask<Boolean> {
		FileNeedTask(final NeedRec nrec, final File file, final int priority) {
			super(new Runnable() {
				public void run() {
					work(nrec, file, priority);
				}
			}, true);
		}
		private static void work(NeedRec nrec, File file, int priority) {
			//Log.d(TAG,"work pri="+priority+" on "+nrec.mFile.getPath());
			for (Interval needed : nrec.mIntervals.values()) {
				if (needed.mPriority!=priority) {
				//if (needed.mPriority<0)
					if (debug)
						Log.d(TAG,"ignore work pri="+needed.mPriority+"/"+priority+" "+needed.mFromInclusive+"-"+needed.mToExclusive+" of "+nrec.mFile.getPath());
					continue;
				}
				Collection<Block> blocks = null;
				synchronized(file.mBlocks) {
					// block at/immediately before
					int startFrame = needed.mFromInclusive;
					TreeMap.Entry<Integer,Block> ent = file.mBlocks.floorEntry(startFrame);
					if (ent!=null)
						startFrame = ent.getValue().mStartFrame;
					// candidate blocks
					blocks = file.mBlocks.subMap(startFrame, needed.mToExclusive).values();
				}
				if (debug)
					Log.d(TAG,"work pri="+needed.mPriority+" "+needed.mFromInclusive+"-"+needed.mToExclusive+" of "+nrec.mFile.getPath());
				// have we already got this?
				// gaps? from/length
				TreeMap<Integer,Interval> gaps = new TreeMap<Integer,Interval>();
				gaps.put(needed.mFromInclusive, new Interval(needed.mFromInclusive, needed.mToExclusive, needed.mPriority));
				long now = System.currentTimeMillis();
				for (Block b : blocks) {
					b.mLastRequestedTime = now;
					int length = b.mSamples.length/b.mChannels;
					int startFrame = b.mStartFrame;
					TreeMap.Entry<Integer,Interval> from = gaps.floorEntry(b.mStartFrame);
					if (from!=null)
						startFrame = from.getValue().mFromInclusive;
					// copy
					Collection<Interval> overlapGaps = new LinkedList<Interval>();
					overlapGaps.addAll(gaps.subMap(startFrame, b.mStartFrame+length).values());
					for (Interval overlapGap : overlapGaps) {
						// start after?
						if (overlapGap.mFromInclusive>=b.mStartFrame) {
							if (overlapGap.mToExclusive<=b.mStartFrame+length)
								//discard
								gaps.remove(overlapGap.mFromInclusive);
							else if (overlapGap.mFromInclusive<b.mStartFrame+length) {
								// clip start
								gaps.remove(overlapGap.mFromInclusive);
								overlapGap.mFromInclusive = b.mStartFrame+length;
								gaps.put(overlapGap.mFromInclusive, overlapGap);
							}
						} else if (overlapGap.mToExclusive>b.mStartFrame) {
							if (overlapGap.mToExclusive<=b.mStartFrame+length)
								// clip end
								overlapGap.mToExclusive = b.mStartFrame;
							else {
								if (debug)
									Log.d(TAG,"split gap "+overlapGap.mFromInclusive+"-"+overlapGap.mToExclusive+" with "+b.mStartFrame+"-"+(b.mStartFrame+length)+" in "+nrec.mFile.getPath());
								// split gap - post
								gaps.put(b.mStartFrame+length, new Interval(b.mStartFrame+length, overlapGap.mToExclusive, overlapGap.mPriority));
								// pre
								overlapGap.mToExclusive = b.mStartFrame;
							}
						}
					}
				}
				// these are the fragment(s) we are missing from that particular interval
				for (Interval gap : gaps.values()) {
					if (debug)
						Log.d(TAG,"gap "+gap.mFromInclusive+"-"+gap.mToExclusive+" in "+nrec.mFile.getPath());
					
					int bpos = gap.mFromInclusive;
					while (!file.mDecoder.isFailed() && bpos<gap.mToExclusive) {
						if (!file.mDecoder.isStarted()) {
							file.mDecoder.start();
							if (file.mDecoder.isFailed()) {
								Log.e(TAG,"Failed to start decoder for "+file.mPath);
								mLog.logError("Unable to read/decode audio file "+file.mPath);
								break;
							}
						}
						Block b = file.mDecoder.getBlock(bpos);
						if (b==null) {
							if (debug)
								Log.d(TAG,"Could not get block "+bpos+" for "+file.mPath);
							break;
						}
						b.mLastRequestedTime = now;
						synchronized (file.mBlocks) {
							if (debug)
								Log.d(TAG,"Got block "+bpos+" (+"+b.mSamples.length/b.mChannels+") for "+file.mPath);
							file.mBlocks.put(b.mStartFrame, b);
						}
						bpos += b.mSamples.length/b.mChannels;
					}
				}
				// TODO mark past end
			}
		}
	};
	/** warm up for files... */
	public void init(HashMap<Integer, ATrack> mTracks) {
		HashMap<String,NeedRec> needRecs = new HashMap<String,NeedRec>();
		for (ATrack atrack : mTracks.values()) {
			for (ATrack.FileRef fr : atrack.mFileRefs) {
				AFile file = (AFile)fr.mFile;
				NeedRec nr = needRecs.get(file.getPath());
				if (nr==null) {
					nr = new NeedRec(file);
					needRecs.put(file.getPath(), nr);
				}
				Interval ival = new Interval(0,1, getPriority(StateType.STATE_NEXT));
				nr.merge(ival);
			}
		}
		update(needRecs);
	}
	/** all done ? */
	public synchronized boolean isIdle() {
		for (FileNeedTask task : mTasks) {
			if (!task.isDone() && !task.isCancelled())
				return false;
		}
		return true;
	}
}
