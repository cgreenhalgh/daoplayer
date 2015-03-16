/**
 * 
 */
package org.opensharingtoolkit.daoplayer.audio;

import java.util.Collection;
import java.util.HashMap;

import org.opensharingtoolkit.daoplayer.IAudio.ITrack;
import org.opensharingtoolkit.daoplayer.audio.AScene.TrackRef;

import android.util.Log;

/** Momentary state
 * @author pszcmg
 *
 */
public class AState {
	static class TrackRef {
		private ITrack mTrack;
		private float mVolume;
		private float mPwlVolume[];
		private int mPos;
		private int mAlign[];
		private boolean mPaused;
		public TrackRef(ITrack mTrack, float mVolume, int mPos, boolean paused) {
			super();
			this.mTrack = mTrack;
			this.mVolume = mVolume;
			this.mPos = mPos;
			this.mPaused = paused;
		}
		public TrackRef(ITrack mTrack, float [] mPwlVolume, int mPos, boolean paused) {
			super();
			this.mTrack = mTrack;
			this.mPwlVolume = mPwlVolume;
			this.mPos = mPos;
			this.mPaused = paused;
		}
		public TrackRef(ITrack mTrack, float mVolume, float [] mPwlVolume, int mPos, int[] align, boolean paused) {
			super();
			this.mTrack = mTrack;
			this.mVolume = mVolume;
			this.mPwlVolume = mPwlVolume;
			this.mPos = mPos;
			this.mAlign = align;
			this.mPaused = paused;
		}
		public ITrack getTrack() {
			return mTrack;
		}
		public float getVolume() {
			return mVolume;
		}
		public float[] getPwlVolume() {
			return mPwlVolume;
		}
		public int getPos() {
			return mPos;
		}
		public int[] getAlign() {
			return mAlign;
		}
		public boolean isPaused() {
			return mPaused;
		}
		@Override
		public String toString() {
			return "TrackRef [mTrack=" + mTrack.getId() + ", mVolume=" + mVolume
					+ ", mPos=" + mPos + ", mPaused=" + mPaused + "]";
		}
		/** special case for AudioEngine align */
		public void setPosFromAlign(int pos) {
			mPos = pos;
		}
		
	}
	private HashMap<Integer,TrackRef> mTrackRefs= new HashMap<Integer,TrackRef>();

	public AState() {		
	}
	/** copy-cons
	 * 
	 * @param state
	 */
	public AState(AState state) {
		for (TrackRef tr : state.mTrackRefs.values()) {
			mTrackRefs.put(tr.mTrack.getId(), new TrackRef(tr.mTrack, tr.mVolume, tr.mPwlVolume, tr.mPos, tr.mAlign, tr.mPaused));
		}
	}
	public void set(ITrack track, float volume, int pos, boolean paused) {
		mTrackRefs.put(track.getId(), new TrackRef(track, volume, pos, paused));
	}
	private void set(ITrack track, float volume, float[] pwlVolume, int pos,
			int[] align, boolean paused) {
		mTrackRefs.put(track.getId(), new TrackRef(track, volume, pwlVolume, pos, align, paused));
	}
	TrackRef get(ITrack track) {
		return mTrackRefs.get(track.getId());
	}
	AState applyScene(AScene scene, int defaultPosAdvance, int sceneTimeAdjustSamples, float sceneTimeAdjustSeconds) {
		AState state = new AState();
		Collection<AScene.TrackRef> strs = scene.getTrackRefs();
		for (AScene.TrackRef str : strs) {
			ITrack track = str.getTrack();
			TrackRef tr = mTrackRefs.get(track.getId());
			if (tr==null) {
				Log.e(AudioEngine.TAG,"Unknown track "+track.getId()+" in Scene");
				continue;
			}
			float volume = tr.mVolume;
			float pwlVolume[] = tr.mPwlVolume;
			if (str.getVolume()!=null) {
				volume = str.getVolume();
				pwlVolume = null;
			}
			if (str.getPwlVolume()!=null) {
				pwlVolume = str.getPwlVolume();
			} else if (pwlVolume!=null && sceneTimeAdjustSeconds!=0)
				pwlVolume = adjustPwlSceneTimes(pwlVolume, sceneTimeAdjustSeconds);
			boolean silent = volume<=0;
			if (pwlVolume!=null)
				// TODO silent if dynamic?! This would mean entirely silent. Note: need sceneTime
				silent = false;
			boolean wasPaused = (tr.mVolume<=0 && tr.mPwlVolume==null) && track.isPauseIfSilent();
			int pos = tr.mPos;
			if (!wasPaused)
				pos += defaultPosAdvance;
			int align[] = tr.mAlign;
			if (str.getPos()!=null) {
				pos = str.getPos();
				align = null;
			}
			if (str.getAlign()!=null) {
				align = str.getAlign();
			} else if (align!=null && sceneTimeAdjustSamples!=0)
				align = adjustAlignSceneTimes(align, sceneTimeAdjustSamples);
			state.set(track, volume, pwlVolume, pos, align, silent && track.isPauseIfSilent());
		}
		for (TrackRef tr : mTrackRefs.values()) {
			// unspecified tracks...
			if (!state.mTrackRefs.containsKey(tr.mTrack.getId())) {
				int pos = tr.mPos;
				// adjust for scene time change in pwl/align
				float pwlVolume[] = tr.mPwlVolume;
				if (pwlVolume!=null && sceneTimeAdjustSeconds!=0)
					pwlVolume = adjustPwlSceneTimes(pwlVolume, sceneTimeAdjustSeconds);
				int align[] = tr.mAlign;
				if (align!=null && sceneTimeAdjustSamples!=0)
					align = adjustAlignSceneTimes(align, sceneTimeAdjustSamples);
				if (!tr.mPaused)
					// TODO part-silent - would advance less than full amount. Note: need sceneTime
					pos += defaultPosAdvance;
				if (scene.isPartial()) 
					// copy existing
					state.set(tr.mTrack, tr.mVolume, pwlVolume, pos, align, tr.mPaused);
				else 
					// total -> silent; copies Align - not sure if it should!
					state.set(tr.mTrack, 0.0f, null, pos, align, tr.mTrack.isPauseIfSilent());
			}
		}
		return state;
	}
	private float[] adjustPwlSceneTimes(float[] pwlVolume, float sceneTimeAdjustSeconds) {
		float ret[] = new float[pwlVolume.length];
		for (int i=0; i<ret.length; i+=2) {
			ret[i] = pwlVolume[i]+sceneTimeAdjustSeconds;
			if (i+1<ret.length)
				ret[i+1] = pwlVolume[i+1];
		}
		return ret;
	}
	private int[] adjustAlignSceneTimes(int[] align, int sceneTimeAdjustSamples) {
		int ret[] = new int[align.length];
		for (int i=0; i<ret.length; i+=2) {
			ret[i] = align[i]+sceneTimeAdjustSamples;
			if (i+1<ret.length)
				ret[i+1] = align[i+1];
		}
		return ret;
	}
	AState advance(int posAdvance) {
		AState state = new AState();
		for (TrackRef tr : mTrackRefs.values()) {
			int pos = tr.mPos;
			if (!tr.mPaused)
				pos += posAdvance;
				// copy existing
			state.set(tr.mTrack, tr.mVolume, tr.mPwlVolume, pos, tr.mAlign, tr.mPaused);
		}
		return state;
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("AState [mTrackRefs=");
		for (TrackRef tr : mTrackRefs.values()) {
			sb.append(tr);
			sb.append(",");
		}
		sb.append("]");
		return sb.toString();
	}
	
}
