/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.Comparator;
import java.util.TreeSet;

import org.opensharingtoolkit.daoplayer.IAudio;
import org.opensharingtoolkit.daoplayer.IAudio.IFile;
import org.opensharingtoolkit.daoplayer.IAudio.ITrack;

/**
 * @author pszcmg
 *
 */
public class ATrack implements IAudio.ITrack {

	static class FileRef {
		int mTrackPos;
		IFile mFile;
		int mFilePos;
		int mLength;
		int mRepeats;
		public FileRef(int mTrackPos, IFile mFile, int mFilePos, int mLength,
				int mRepeats) {
			super();
			this.mTrackPos = mTrackPos;
			this.mFile = mFile;
			this.mFilePos = mFilePos;
			this.mLength = mLength;
			this.mRepeats = mRepeats;
		}
	}
	
	static class FileRefComparator implements Comparator<FileRef> {
		@Override
		public int compare(FileRef lhs, FileRef rhs) {
			return rhs.mTrackPos-lhs.mTrackPos;
		}
	}
	
	TreeSet<FileRef> mFileRefs = new TreeSet<FileRef>(new FileRefComparator());
	
	@Override
	public void addFileRef(int trackPos, IFile file) {
		addFileRef(trackPos, file, 0, ITrack.LENGTH_ALL, 1);
	}

	@Override
	public void addFileRef(int trackPos, IFile file, int filePos, int length,
			int repeats) {
		mFileRefs.add(new FileRef(trackPos, file, filePos, length, repeats));
	}

	private int mId;
	private static int sNextId = 0;
	private float mVolume = 0.0f;
	private int mPosition = 0;
	private boolean mPauseIfSilent = true;
	
	public ATrack(boolean pauseIfSilent) {
		mPauseIfSilent = pauseIfSilent;
		synchronized (ATrack.class) {
			mId = sNextId++;
		}
	}

	@Override
	public int getId() {
		return mId;
	}

	float getVolume() {
		return mVolume;
	}

	void setVolume(float volume) {
		this.mVolume = volume;
	}

	int getPosition() {
		return mPosition;
	}

	void setPosition(int position) {
		this.mPosition = position;
	}

	public boolean isPauseIfSilent() {
		return mPauseIfSilent;
	}
	
}
