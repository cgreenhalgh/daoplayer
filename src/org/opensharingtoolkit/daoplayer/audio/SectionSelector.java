/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.opensharingtoolkit.daoplayer.ILog;
import org.opensharingtoolkit.daoplayer.audio.ATrack.Section;

import android.util.Log;

/** Class which attempts to make an optimal selection of track sections to fit a specified
 * time window, ending at a particular section.
 * 
 * @author pszcmg
 *
 */
public class SectionSelector {

	private static final String TAG = "section-selector";
	private static final int MAX_UNITS = 100;
	private ATrack mTrack;
	private boolean mPreparing;
	private boolean mReady;
	static class Subsection {
		ATrack.Section section;
		int unit;
		public Subsection(Section section, int unit) {
			super();
			this.section = section;
			this.unit = unit;
		}		
	}
	private int mNumSubsections;
	private Subsection[] mSubsections;
	static class Timestep {
		double costs[];
		int nextSubsections[];
		public Timestep(int numSubsections) {
			costs = new double[numSubsections];
			nextSubsections = new int[numSubsections];
		}
	}
	private Timestep[] mTimesteps;
	private int mUnitTime;
	private int mNumTimesteps;
	private ILog mLog;
	private int mMaxLength;
	private Map<String,Integer> mSectionIndexes = new HashMap<String,Integer>();
	
	public SectionSelector(ATrack track, int maxLength, ILog log) {
		mTrack = track;
		mLog = log;
		mMaxLength = maxLength;
	}
	/** prepare - may be slow! 
	 * @return true if prepared; false if in progress */
	public boolean prepare() {
		synchronized(this) {
			if (mReady)
				return true;
			if (mPreparing)
				return false;
			mPreparing = true;
		}
		// subsections...
		Map<String,Section> sections = mTrack.getSections();
		Vector<Subsection> subsections = new Vector<Subsection>();
		if (sections==null || sections.size()==0) {
			mLog.logError("SectionSelector called for track "+mTrack.getName()+" which has no sections");
			return prepared();
		}
		mUnitTime = mTrack.getUnitTime();
		if (mUnitTime<=0) {
			mLog.logError("SectionSelector called for track "+mTrack.getName()+" which has no unitTime");
			return prepared();
		}
		for (Section section: sections.values()) {
			int units = 1;
			if (mUnitTime>0 && section.mLength>0) {
				units = (section.mLength+mUnitTime/2)/mUnitTime;
				if (units<=0)
					units = 1;
				if (units>MAX_UNITS) {
					mLog.logError("SectionSelector called for track "+mTrack.getName()+" section "+section.mName+", which is too long ("+units+" units)");
					units = MAX_UNITS;
				}
			}
			mSectionIndexes.put(section.mName, subsections.size());
			for (int unit=0; unit<units; unit++) {
				Subsection s = new Subsection(section, unit);
				subsections.add(s);
			}
		}
		mNumSubsections = subsections.size();
		mSubsections = subsections.toArray(new Subsection[mNumSubsections]);
		mNumTimesteps = mMaxLength/mUnitTime;
		mTimesteps = new Timestep[mNumTimesteps];
		// initial timestep, i.e. last subsection
		Timestep timestep = new Timestep(mNumSubsections);
		mTimesteps[0] = timestep;
		for (int si=0; si<mNumSubsections; si++) {
			timestep.costs[si] = mSubsections[si].section.mEndCost;
			timestep.nextSubsections[si] = -1;
		}
		for (int ti=1; ti<mNumTimesteps; ti++) {
			timestep = new Timestep(mNumSubsections);
			mTimesteps[ti] = timestep;
			// initialise
			for (int si=0; si<mNumSubsections; si++) {
				timestep.costs[si] = Double.MAX_VALUE;
				timestep.nextSubsections[si] = -1;
			}
			// each subsequent subsections...
			Timestep nextTimestep = mTimesteps[ti-1];
			for (int si=0; si<mNumSubsections; si++) {
				Subsection sub = mSubsections[si];
				// within section?
				if (si+1<mNumSubsections && sub.section==mSubsections[si+1].section) {
					// must move to next step of subsection!
					timestep.costs[si] = nextTimestep.costs[si+1];
					timestep.nextSubsections[si] = si+1;
				} else {
					// valid next transitions?
					for (ATrack.NextSection next : sub.section.mNext) {
						if (next.mName==null) {
							if (ti==1)
								mLog.logError("Section "+sub.section.mName+" refers to unnamed next section");
							continue;
						}
						Integer nextsi = mSectionIndexes.get(next.mName);
						if (nextsi==null) {
							if (ti==1)
								mLog.logError("Section "+sub.section.mName+" refers to unknown next section "+next.mName);
							continue;
						}
						if (next.mCost!=Double.MAX_VALUE && nextTimestep.costs[nextsi]!=Double.MAX_VALUE) {
							double cost = next.mCost+nextTimestep.costs[nextsi];
							if (cost < timestep.costs[si]) {
								// best yet!
								timestep.costs[si] = cost;
								timestep.nextSubsections[si] = nextsi;
							}
						}
					}
				}
			}			
		}
		return prepared();
	}
	private boolean prepared() {
		synchronized(this) {
			mReady = true;
			mPreparing = false;
		}
		return true;
	}
	/**
	 * @param currentSectionName Name of currently playing section; null if start
	 * @param currentSectionTime Time (samples) in currently playing section
	 * @param targetTime Time (samples) until end
	 * @return
	 */
	public String[] selectSections(String currentSectionName, int currentSectionTime, int targetTime) {
		if (mUnitTime<=0 || mSubsections.length==0) {
			Log.w(TAG,"selectSections for track without unitTime/sections");
			return null;
		}
		int targetUnits = (targetTime+mUnitTime/2)/mUnitTime;
		if (targetUnits>=mNumTimesteps) {
			mLog.logError("selectSections called for "+targetUnits+" time units; max is "+mNumTimesteps);
			targetUnits = mNumTimesteps;
		} 
		if (targetUnits<=0) {
			mLog.logError("selectSections called for 0 time units");
			return new String[0];
		}
		int si;
		Vector<String> sections = new Vector<String>();
		if (currentSectionName!=null) {
			// named section - find it
			Integer rsi = mSectionIndexes.get(currentSectionName);
			if (rsi==null) {
				mLog.logError("selectSections called with unknown currentSectionName "+currentSectionName+"; trying start");
				return selectSections(null, 0, targetTime);
			}
			si = rsi;
			int currentUnits = (currentSectionTime+mUnitTime/2)/mUnitTime;
			while(si+1<mNumSubsections && mSubsections[si+1].section==mSubsections[si].section && mSubsections[si].unit<currentUnits)
				si++;
			if (currentUnits!=mSubsections[si].unit) {
				mLog.logError("selectSections with current section "+currentSectionName+" at "+currentUnits+" units is after end of section");
			}
		} else {
			// any section - find lowest cost+startCost
			si = -1;
			Timestep timestep = mTimesteps[targetUnits-1];
			double bestcost = Double.MAX_VALUE;
			for (int si2=0; si2<mNumSubsections; si2++) {
				if (timestep.costs[si2]!=Double.MAX_VALUE && mSubsections[si2].section.mStartCost!=Double.MAX_VALUE) {
					double cost = timestep.costs[si2]+mSubsections[si2].section.mStartCost;
					if (cost<bestcost) {
						bestcost = cost;
						si = si2;
					}
				}
			}
			if (si<0) {
				mLog.logError("sectionSelector Could not find a starting section for "+targetUnits+" units");
				return null;
			}
			sections.add(mSubsections[si].section.mName);
		}
		// sequence from si
		for (int ti=targetUnits-1; ti>0; ti--) {
			Timestep timestep = mTimesteps[ti];
			si = timestep.nextSubsections[si];
			if (si<0) {
				mLog.logError("sectionSelector had no valid next subsection at timestep "+ti);
				break;
			}
			if (mSubsections[si].unit==0)
				sections.add(mSubsections[si].section.mName);
		}
		return sections.toArray(new String[sections.size()]);
	}
}
