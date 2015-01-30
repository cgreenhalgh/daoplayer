/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.opensharingtoolkit.daoplayer.IAudio;
import org.opensharingtoolkit.daoplayer.audio.AudioEngine.StateType;

import android.content.Context;
import android.util.Log;

/** 
 * Maintains cache of decoded files, decoding more as required. Used by AudioEngine.
 * Updates based on AudioEngine queue State. Uses internal threads to decode in the 
 * background.
 * 
 * @author pszcmg
 *
 */
public class FileCache {
	public static class Block {
		short mSamples[];
		int mChannels;
		int mStartFrame; // Note frame number
		// internal
		int mIndex;
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
	private Map<String,File> mFiles = new HashMap<String,File>();
	private Vector<FileNeedTask> mTasks = new Vector<FileNeedTask>();
	private ExecutorService mExecutor;
	
	/** may use context to access assets */
	private Context mContext;

	/**
	 * @param mContext
	 */
	public FileCache(Context mContext) {
		this.mContext = mContext;
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
				if (b.mStartFrame<=frame && b.mStartFrame+b.mSamples.length/b.mChannels > frame)
					// found in cache!
					return b;
			}
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
			return 0;
		case STATE_FUTURE:
			return 1;
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
	public void update(Vector<AudioEngine.StateRec> stateQueue, HashMap<Integer, ATrack> mTracks, int samplesPerBlock) {
		// What file spans do we need and how soon?
		HashMap<String,NeedRec> needRecs = new HashMap<String,NeedRec>();
		for (AudioEngine.StateRec srec : stateQueue) {
			if (srec.mType==AudioEngine.StateType.STATE_IN_PROGRESS || srec.mType==AudioEngine.StateType.STATE_FUTURE) {
				int blockLength = samplesPerBlock*2;
				for (ATrack track : mTracks.values()) {
					AState.TrackRef tr = srec.mState.get(track);
					if (tr==null || tr.isPaused()) 
						continue;
					int tpos = tr.getPos();
					// 12 bit shift
					int vol = (int)(tr.getVolume() * 0x1000);
					// TODO prepare?
					if (vol<=0) {
						continue;
					}
					for (ATrack.FileRef fr : track.mFileRefs) {
						int spos = tpos, epos = tpos+blockLength-1;
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
		// Throw this over the the background thread!!
		
		// old tasks
		for (FileNeedTask task : mTasks) {
			task.cancel(false);
		}
		mTasks.clear();
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
				Log.d(TAG,"work pri="+needed.mPriority+"/"+priority+" "+needed.mFromInclusive+"-"+needed.mToExclusive+" of "+nrec.mFile.getPath());
				if (needed.mPriority!=priority)
				//if (needed.mPriority<0)
					continue;
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
				// have we already got this?
				// gaps? from/length
				TreeMap<Integer,Interval> gaps = new TreeMap<Integer,Interval>();
				gaps.put(needed.mFromInclusive, new Interval(needed.mFromInclusive, needed.mToExclusive, needed.mPriority));
				for (Block b : blocks) {
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
						} else if (overlapGap.mToExclusive>b.mStartFrame)
							// clip end
							overlapGap.mToExclusive = b.mStartFrame;
					}
				}
				// these are the fragment(s) we are missing from that particular interval
				for (Interval gap : gaps.values()) {
					Log.d(TAG,"gap "+gap.mFromInclusive+"-"+gap.mToExclusive+" in "+nrec.mFile.getPath());
					
					int bpos = gap.mFromInclusive;
					while (!file.mDecoder.isFailed() && bpos<gap.mToExclusive) {
						if (!file.mDecoder.isStarted()) {
							file.mDecoder.start();
							if (file.mDecoder.isFailed()) {
								Log.e(TAG,"Failed to start decoder for "+file.mPath);
								break;
							}
						}
						Block b = file.mDecoder.getBlock(bpos);
						if (b==null) {
							Log.d(TAG,"Could not get block "+bpos+" for "+file.mPath);
							break;
						}
						synchronized (file.mBlocks) {
							file.mBlocks.put(b.mStartFrame, b);
						}
						bpos += b.mSamples.length/b.mChannels;
					}
				}
				// TODO mark past end
			}
		}
	};
}
