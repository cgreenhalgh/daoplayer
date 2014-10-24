/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Vector;

import org.opensharingtoolkit.daoplayer.IAudio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

/**
 * @author pszcmg
 *
 */
public class AFile implements IAudio.IFile {
	public static String TAG = "daoplayer-file";
	private String mPath;
	private boolean mExtractFailed = false;
	private boolean mDecoded = false;
	private int mChannels;
	private int mRate;
	Vector<short[]> mBuffers = new Vector<short[]>();
	//private static int MAX_BUFFER_SIZE = 44100;
	public AFile(String path) {
		mPath = path;
	}
	@Override
	public String getPath() {
		return mPath;
	}
	static String FILE_ASSET = "file:///android_asset/";
	boolean decode(Context context) {
		if (mExtractFailed)
			return false;
		if (mDecoded)
			return true;
		mDecoded = true;
		Log.d(TAG,"decode "+mPath);
		MediaExtractor extractor = new MediaExtractor();
		// asset?
		if (mPath.startsWith(FILE_ASSET)) {
			AssetManager assets = context.getAssets();
			try {
				AssetFileDescriptor afd = assets.openFd(mPath.substring(FILE_ASSET.length()));
				FileDescriptor fd = afd.getFileDescriptor();
				extractor.setDataSource(fd);
			}
			catch (Exception e) {
				Log.d(TAG,"Error opening asset "+mPath+": "+e);
				mExtractFailed = true;
				extractor.release();
				return false;
			}
		} else {
			try {
				extractor.setDataSource(mPath);
			} catch (IOException e) {
				Log.e(TAG,"Error creating MediaExtractor for "+mPath+": "+e);
				mExtractFailed = true;
				extractor.release();
				return false;
			}
		}
		if (extractor.getTrackCount()<1) {
			Log.e(TAG,"Found no tracks in "+mPath);
			mExtractFailed = true;
			extractor.release();
			return false;
		}
		Log.d(TAG,"Found "+extractor.getTrackCount()+" tracks");
		MediaFormat format = extractor.getTrackFormat(0);
		String mime = format.containsKey(MediaFormat.KEY_MIME) ? format.getString(MediaFormat.KEY_MIME): "";
		mChannels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT): -1;
		int fmask = format.containsKey(MediaFormat.KEY_CHANNEL_MASK) ? format.getInteger(MediaFormat.KEY_CHANNEL_MASK): -1;
		mRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE): -1;
		Log.d(TAG,"track 0: mime="+mime+" channels="+mChannels+" fmask="+fmask+" rate="+mRate);
		// E.g. 0: mime=audio/mpeg channels=2 fmask=-1 rate=44100
		if (!mime.startsWith("audio/")) {
			Log.d(TAG,"Did not find audio track 0 in "+mPath);
			mExtractFailed = true;
			extractor.release();
			return false;
		}
		extractor.selectTrack(0);
		// see https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/DecoderTest.java
		MediaCodec codec = MediaCodec.createDecoderByType(mime);
		codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
		codec.start();
		ByteBuffer [] codecInputBuffers = codec.getInputBuffers();
		ByteBuffer [] codecOutputBuffers = codec.getOutputBuffers();

        //short [] decoded = new short[0];
        //int decodedIdx = 0;
        // start decoding
        final long kTimeOutUs = 10000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int noOutputCounter = 0;
        while (!sawOutputEOS && noOutputCounter < 50) {
            noOutputCounter++;
            if (!sawInputEOS) {
                int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);
                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                    int sampleSize =
                        extractor.readSampleData(dstBuf, 0 /* offset */);
                    long presentationTimeUs = 0;
                    if (sampleSize < 0) {
                        Log.d(TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    codec.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    if (!sawInputEOS) {
                        extractor.advance();
                    }
                }
            }
            int res = codec.dequeueOutputBuffer(info, kTimeOutUs);
            if (res >= 0) {
                Log.d(TAG, "got frame, size " + info.size + "/" + info.presentationTimeUs);
                if (info.size > 0) {
                    noOutputCounter = 0;
                }
                int outputBufIndex = res;
                if (info.size > 0) {
	                ByteBuffer buf = codecOutputBuffers[outputBufIndex];
	                //if (decodedIdx + (info.size / 2) >= decoded.length) {
	                //    decoded = Arrays.copyOf(decoded, decodedIdx + (info.size / 2));
	                //}
	                short decoded[] = new short[info.size / 2];
	                int decodedIdx = 0;
	                mBuffers.add(decoded);
	                for (int i = 0; i < info.size; i += 2) {
	                    decoded[decodedIdx++] = buf.getShort(i);
	                }
                }
                codec.releaseOutputBuffer(outputBufIndex, false /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = codec.getOutputFormat();
                Log.d(TAG, "output format has changed to " + oformat);
            } else {
                Log.d(TAG, "dequeueOutputBuffer returned " + res);
            }
        }
        codec.stop();
        codec.release();
 
        Log.d(TAG,"decoded "+mBuffers.size()+" blocks");
        
		extractor.release();

		// optional?!
		tidyEnds();
		
		return false;
	}
	private void tidyEnds() {
		// squash up to first zero-crossing
		short lval = 0;
		doneStart:
			for (int bix = 0; bix < mBuffers.size(); bix++) {
				short [] buf = mBuffers.get(bix);
				for (int i=0; i<buf.length; i+=2) {
					short val = (short)((buf[i]+buf[i+1])/2);
					if (val==0)
						break doneStart;
					if (lval==0)
						lval = val;
					else if ((lval>0 && val<0) || (lval<0 && val>0))
						break doneStart;
					buf[i] = buf[i+1] = 0;
				}
			}
		lval = 0;
		doneEnd:
			for (int bix = mBuffers.size()-1; bix>=0; bix--) {
				short [] buf = mBuffers.get(bix);
				for (int i=buf.length-2; i>=0; i-=2) {
					short val = (short)((buf[i]+buf[i+1])/2);
					if (val==0)
						break doneEnd;
					if (lval==0)
						lval = val;
					else if ((lval>0 && val<0) || (lval<0 && val>0))
						break doneEnd;
					buf[i] = buf[i+1] = 0;
				}
			}
	}
	public int getLength() {
		int len = 0;
		for (int i=0; i<mBuffers.size();i++)
			len += mBuffers.get(i).length;
		return len;
	}
}