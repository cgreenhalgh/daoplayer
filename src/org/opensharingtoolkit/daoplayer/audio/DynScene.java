/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.Collection;
import java.util.HashMap;

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
	private Double mUpdatePeriod;
	
	public DynScene(boolean mPartial) {
		super();
		this.mPartial = mPartial;
	}

	static class TrackRef {
		private ITrack mTrack;
		private Float mVolume;
		private String mDynVolume;
		private Integer mPos;
		private Boolean mPrepare;
		public TrackRef(ITrack mTrack, Float mVolume, String mDynVolume, Integer mPos, Boolean mPrepare) {
			super();
			this.mTrack = mTrack;
			this.mVolume = mVolume;
			this.mDynVolume = mDynVolume;
			this.mPos = mPos;
			this.mPrepare = mPrepare;
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
		public Boolean getPrepare() {
			return mPrepare;
		}
		
	}
	
	private HashMap<Integer,TrackRef> mTrackRefs= new HashMap<Integer,TrackRef>();
	
	Collection<TrackRef> getTrackRefs() {
		return mTrackRefs.values();
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
		set(track, volume, null, pos, prepare);
	}

	public void set(ITrack track, Float volume, String dynVolume, Integer pos, Boolean prepare) {
		TrackRef tref = new TrackRef(track, volume, dynVolume, pos, prepare);
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
	
}
