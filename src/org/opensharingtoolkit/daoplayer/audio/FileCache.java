/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.opensharingtoolkit.daoplayer.IAudio;

import android.content.Context;

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
	private Map<String,FileDecoder> mDecoders = new HashMap<String,FileDecoder>();
	
	/** may use context to access assets */
	private Context mContext;

	/**
	 * @param mContext
	 */
	public FileCache(Context mContext) {
		this.mContext = mContext;
	}

	/** play out API */
	public Block getBlock(AFile file, int frame, Block lastBlock) {
		FileDecoder fd = null;
		synchronized(this) {
			fd = mDecoders.get(file.getPath());
			if (fd==null) {
				fd = new FileDecoder(file.getPath(), mContext);
				fd.queueDecode();
			}
			mDecoders.put(file.getPath(), fd);
		}
		// placeholder
		if (fd.isExtracted()) {
			int bix = 0, bpos = 0;
			while (bpos <= frame && bix < fd.mBuffers.size() && bpos+fd.mBuffers.get(bix).length/fd.mChannels <= frame) {
				bpos += fd.mBuffers.get(bix).length/fd.mChannels;
				bix++;
			}
			if (bix >= fd.mBuffers.size()/fd.mChannels) {
				// past end
				return null;
			}
			Block b = new Block();
			b.mChannels = fd.mChannels;
			b.mIndex = bix;
			b.mSamples = fd.mBuffers.get(bix);
			b.mStartFrame = bpos;
			return b;
		}
		return null;
	}

	public void reset() {
		synchronized (this) {
			for (FileDecoder fd : mDecoders.values()) {
				fd.cancel();
			}
			mDecoders.clear();
		}
	}

	static class NeedRec {
		AFile mFile;
		int mStartFrame;
		int mLength;
		AudioEngine.StateType mWhen;
	};
	public void update(Vector<AudioEngine.StateRec> stateQueue, HashMap<Integer, ATrack> mTracks, int samplesPerBlock) {
		// What file spans do we need and how soon?
		Vector<NeedRec> needRecs = new Vector<NeedRec>();
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
							NeedRec nr = new NeedRec();
							nr.mFile = file;
							nr.mLength = epos-spos;
							nr.mStartFrame = fpos;
							nr.mWhen = srec.mType;
							needRecs.add(nr);
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
}
