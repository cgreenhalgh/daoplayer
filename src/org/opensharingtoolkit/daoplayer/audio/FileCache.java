/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

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

	public void update(Vector<AudioEngine.StateRec> mStateQueue) {
		// TODO Auto-generated method stub
		// What file spans do we need and how soon?
	}
}
