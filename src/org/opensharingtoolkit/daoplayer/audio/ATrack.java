/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.Vector;

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
	public synchronized void addFileRef(int trackPos, IFile file, int filePos, int length,
			int repeats) {
		mFileRefs.add(new FileRef(trackPos, file, filePos, length, repeats));
	}

	static class NextSection {
		String mName;
		double mCost;
		/**
		 * @param mName
		 * @param cost
		 */
		public NextSection(String mName, double cost) {
			super();
			this.mName = mName;
			this.mCost = cost;
		}
		
	}
	
	static class Section {
		String mName;
		int mTrackPos;
		int mLength;
		double mStartCost;
		double mEndCost;
		Vector<NextSection> mNext;
		
		/**
		 * @param mName
		 * @param mTrackPos
		 * @param mLength
		 * @param mStartCost
		 * @param mEndCost
		 */
		public Section(String mName, int mTrackPos, int mLength,
				double mStartCost, double mEndCost) {
			super();
			this.mName = mName;
			this.mTrackPos = mTrackPos;
			this.mLength = mLength;
			this.mStartCost = mStartCost;
			this.mEndCost = mEndCost;
		}

		public void addNext(String name, double cost) {
			mNext.add(new NextSection(name, cost));
		}
	}
	
	HashMap<String,Section> mSections = new HashMap<String,Section>();

	public synchronized void addSection(Section section) {
		mSections.put(section.mName, section);
	}
	public synchronized HashMap<String,Section> getSections() {
		return mSections;
	}
	
	private int mId;
	private String mName;
	private static int sNextId = 0;
	private float mVolume = 0.0f;
	private int mPosition = 0;
	private boolean mPauseIfSilent = true;
	private boolean mDynamic = false;
	private int mUnitTime;
	
	public ATrack(boolean pauseIfSilent) {
		mPauseIfSilent = pauseIfSilent;
		synchronized (ATrack.class) {
			mId = sNextId++;
		}
	}
	public ATrack(boolean pauseIfSilent, boolean dynamic) {
		mPauseIfSilent = pauseIfSilent;
		mDynamic = dynamic;
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

	public boolean isDynamic() {
		return mDynamic;
	}

	public void setName(String name) {
		mName = name;
	}
	public String getName() {
		return mName;
	}
	public void setUnitTime(int unitTime) {
		mUnitTime = unitTime;
	}
	public int getUnitTime() {
		return mUnitTime;
	}
}
