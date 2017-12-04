/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.io.BufferedInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Vector;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.Obuffer;

import org.opensharingtoolkit.daoplayer.IAudio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

/**
 * @author pszcmg
 *
 */
public class AFile implements IAudio.IFile {
	public static String TAG = "daoplayer-file";
	private String mPath;
	//private static int MAX_BUFFER_SIZE = 44100;
	public AFile(String path) {
		mPath = path;
	}
	@Override
	public String getPath() {
		return mPath;
	}
}