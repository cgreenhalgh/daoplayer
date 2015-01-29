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
		private int mPos;
		private boolean mPaused;
		public TrackRef(ITrack mTrack, float mVolume, int mPos, boolean paused) {
			super();
			this.mTrack = mTrack;
			this.mVolume = mVolume;
			this.mPos = mPos;
			this.mPaused = paused;
		}
		public ITrack getTrack() {
			return mTrack;
		}
		public float getVolume() {
			return mVolume;
		}
		public int getPos() {
			return mPos;
		}
		public boolean isPaused() {
			return mPaused;
		}
		@Override
		public String toString() {
			return "TrackRef [mTrack=" + mTrack.getId() + ", mVolume=" + mVolume
					+ ", mPos=" + mPos + ", mPaused=" + mPaused + "]";
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
			mTrackRefs.put(tr.mTrack.getId(), new TrackRef(tr.mTrack, tr.mVolume, tr.mPos, tr.mPaused));
		}
	}
	public void set(ITrack track, float volume, int pos, boolean paused) {
		mTrackRefs.put(track.getId(), new TrackRef(track, volume, pos, paused));
	}
	TrackRef get(ITrack track) {
		return mTrackRefs.get(track.getId());
	}
	AState applyScene(AScene scene, int defaultPosAdvance) {
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
			if (str.getVolume()!=null)
				volume = str.getVolume();
			boolean wasPaused = tr.mVolume<=0 && track.isPauseIfSilent();
			int defaultPos = tr.mPos;
			if (!wasPaused)
				defaultPos += defaultPosAdvance;
			state.set(track, volume, str.getPos()!=null ? str.getPos() : defaultPos, volume<=0 && track.isPauseIfSilent());
		}
		for (TrackRef tr : mTrackRefs.values()) {
			if (!state.mTrackRefs.containsKey(tr.mTrack.getId())) {
				int pos = tr.mPos;
				if (!tr.mPaused)
					pos += defaultPosAdvance;
				if (scene.isPartial()) 
					// copy existing
					state.set(tr.mTrack, tr.mVolume, pos, tr.mPaused);
				else 
					// total -> silent
					state.set(tr.mTrack, 0.0f, pos, tr.mTrack.isPauseIfSilent());
			}
		}
		return state;
	}
	AState advance(int posAdvance) {
		AState state = new AState();
		for (TrackRef tr : mTrackRefs.values()) {
			int pos = tr.mPos;
			if (!tr.mPaused)
				pos += posAdvance;
				// copy existing
			state.set(tr.mTrack, tr.mVolume, pos, tr.mPaused);
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
