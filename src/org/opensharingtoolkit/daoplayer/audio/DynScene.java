/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.opensharingtoolkit.daoplayer.IAudio;
import org.opensharingtoolkit.daoplayer.IAudio.ITrack;

/** DynamicScene
 * 
 * @author pszcmg
 *
 */
public class DynScene implements IAudio.IScene {

	private boolean mPartial;
	private String mOnload;
	private String mOnupdate;
	private DynConstants mConstants;
	private DynConstants mVars;
	private Double mUpdatePeriod;
	private Map<String,String> mWaypoints;
	
	public DynScene(boolean mPartial) {
		super();
		this.mPartial = mPartial;
	}

	static class TrackRef {
		private ITrack mTrack;
		private Float mVolume;
		private String mDynVolume;
		private Integer mPos;
		private String mDynPos;
		private Boolean mPrepare;
		private Boolean mUpdate;
		public TrackRef(ITrack mTrack, Float mVolume, String mDynVolume, Integer mPos, String dynPos, Boolean mPrepare, Boolean update) {
			super();
			this.mTrack = mTrack;
			this.mVolume = mVolume;
			this.mDynVolume = mDynVolume;
			this.mPos = mPos;
			this.mDynPos = dynPos;
			this.mPrepare = mPrepare;
			this.mUpdate = update;
		}
		public ITrack getTrack() {
			return mTrack;
		}
		public Float getVolume() {
			return mVolume;
		}
		public String getDynVolume() {
			return mDynVolume;
		}
		public Integer getPos() {
			return mPos;
		}	
		public String getDynPos() {
			return mDynPos;
		}
		public Boolean getPrepare() {
			return mPrepare;
		}
		public Boolean getUpdate() {
			return mUpdate;
		}
		
	}
	
	private HashMap<Integer,TrackRef> mTrackRefs= new HashMap<Integer,TrackRef>();
	
	Collection<TrackRef> getTrackRefs() {
		return mTrackRefs.values();
	}
	ITrack getTrack(int id) {
		TrackRef tr = mTrackRefs.get(id);
		if (tr!=null)
			return tr.getTrack();
		return null;
	}
	
	public boolean isPartial() {
		return mPartial;
	}

	/**
	 * @return the mOnload
	 */
	public String getOnload() {
		return mOnload;
	}

	/**
	 * @param mOnload the mOnload to set
	 */
	public void setOnload(String mOnload) {
		this.mOnload = mOnload;
	}

	/**
	 * @return the mOnupdate
	 */
	public String getOnupdate() {
		return mOnupdate;
	}

	/**
	 * @param mOnupdate the mOnupdate to set
	 */
	public void setOnupdate(String mOnupdate) {
		this.mOnupdate = mOnupdate;
	}

	@Override
	public void set(ITrack track, Float volume, Integer pos, Boolean prepare) {
		set(track, volume, null, pos, null, prepare, null);
	}

	public void set(ITrack track, Float volume, String dynVolume, Integer pos, String dynPos, Boolean prepare, Boolean update) {
		TrackRef tref = new TrackRef(track, volume, dynVolume, pos, dynPos, prepare, update);
		mTrackRefs.put(track.getId(), tref);		
	}

	@Override
	public void setVolume(ITrack track, float volume) {
		TrackRef tref = mTrackRefs.get(track.getId());
		if (tref!=null)
			tref.mVolume = volume;
		else 
			set(track, volume, null, null);		
	}

	@Override
	public void setPosition(ITrack track, int pos) {
		TrackRef tref = mTrackRefs.get(track.getId());
		if (tref!=null)
			tref.mPos = pos;
		else 
			set(track, null, pos, null);		
	}

	/**
	 * @return the mConstants
	 */
	public DynConstants getConstants() {
		return mConstants;
	}

	/**
	 * @param mConstants the mConstants to set
	 */
	public void setConstants(DynConstants constants) {
		this.mConstants = constants;
	}

	/**
	 * @return the mVars
	 */
	public DynConstants getVars() {
		return mVars;
	}

	/**
	 * @param mVars the mVars to set
	 */
	public void setVars(DynConstants vars) {
		this.mVars = vars;
	}

	/**
	 * @return the mUpdatePeriod
	 */
	public Double getUpdatePeriod() {
		return mUpdatePeriod;
	}

	/**
	 * @param updatePeriod the mUpdatePeriod to set
	 */
	public void setUpdatePeriod(Double updatePeriod) {
		this.mUpdatePeriod = updatePeriod;
	}

	/**
	 * @return the mWaypoints
	 */
	public Map<String, String> getWaypoints() {
		return mWaypoints;
	}

	/**
	 * @param mWaypoints the mWaypoints to set
	 */
	public void setWaypoints(Map<String, String> mWaypoints) {
		this.mWaypoints = mWaypoints;
	}

}
