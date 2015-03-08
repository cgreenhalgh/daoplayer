/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.io.File;
import java.io.FileWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.opensharingtoolkit.daoplayer.IAudio.ITrack;
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
	 * @param targetDuration Time (samples) until end
	 * @return
	 */
	public Object[] selectSections(String currentSectionName, int currentSectionTime, int sceneTime, int targetDuration) {
		if (mUnitTime<=0 || mSubsections.length==0) {
			Log.w(TAG,"selectSections for track without unitTime/sections");
			return null;
		}
		int targetUnits = (targetDuration+mUnitTime/2)/mUnitTime;
		if (targetUnits>=mNumTimesteps) {
			mLog.logError("selectSections called for "+targetUnits+" time units; max is "+mNumTimesteps);
			targetUnits = mNumTimesteps;
		} 
		if (targetUnits<=0) {
			mLog.logError("selectSections called for 0 time units");
			return new String[0];
		}
		int si;
		Vector<Object> sections = new Vector<Object>();
		if (currentSectionName!=null) {
			// named section - find it
			Integer rsi = mSectionIndexes.get(currentSectionName);
			if (rsi==null) {
				mLog.logError("selectSections called with unknown currentSectionName "+currentSectionName+"; trying start");
				return selectSections(null, 0, sceneTime, targetDuration);
			}
			si = rsi;
			int currentUnits = (currentSectionTime+mUnitTime/2)/mUnitTime;
			while(si+1<mNumSubsections && mSubsections[si+1].section==mSubsections[si].section && mSubsections[si].unit<currentUnits)
				si++;
			if (currentUnits!=mSubsections[si].unit) {
				mLog.logError("selectSections with current section "+currentSectionName+" at "+currentUnits+" units is after end of section");
			}
			// insert end of current section scene time
			ATrack.Section currentSection = mTrack.getSections().get(currentSectionName);
			if (currentSection!=null) {
				if (currentSection.mLength!=ITrack.LENGTH_ALL && currentSectionTime<currentSection.mLength)
					sections.add(sceneTime+currentSection.mLength-currentSectionTime);
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
		return sections.toArray();
	}
	void dump(android.content.Context context) {
		try {
			File cacheDir = context.getExternalCacheDir();
			if (cacheDir==null) {
				Log.w(TAG,"No external cache dir");
				return;
			}
			File file = new File(cacheDir, mTrack.getName()+"-selectors.csv");
			FileWriter fw = new FileWriter(file);
			fw.append("section,startCost,endCost");
			Collection<ATrack.Section> sections = mTrack.mSections.values();
			for (ATrack.Section s : sections) {
				fw.append(","+s.mName);
			}
			fw.append("\n");
			for (ATrack.Section s : sections) {
				fw.append(s.mName);
				fw.append(","+s.mStartCost);
				fw.append(","+s.mEndCost);
				Vector<ATrack.NextSection> nss = s.mNext;
				for (ATrack.Section s2 : sections) {
					fw.append(",");
					for (ATrack.NextSection ns : nss) {
						if (ns.mName.equals(s2.mName))
							fw.append(""+ns.mCost);
					}
				}
				fw.append("\n");
			}
			fw.append("\n");
			fw.append("i,section,unit");
			for (int ti=0; ti<mNumTimesteps; ti++) {
				fw.append(",c"+ti);
				fw.append(",n"+ti);
			}
			fw.append("\n");
			for (int si=0; si<mNumSubsections; si++) {
				fw.append(""+si);
				fw.append(","+mSubsections[si].section.mName);
				fw.append(","+mSubsections[si].unit);
				for (int ti=0; ti<mNumTimesteps; ti++) {
					fw.append(",");
					if (mTimesteps[ti].costs[si]!=Double.MAX_VALUE)
						fw.append(""+mTimesteps[ti].costs[si]);
					fw.append(","+mTimesteps[ti].nextSubsections[si]);
				}
				fw.append("\n");
			}
			fw.close();
			Log.d(TAG,"Dumped SectionSelector to "+file);
		}
		catch (Exception e) {
			Log.w(TAG,"Error dumping SectionSelector", e);
		}
	}
}
