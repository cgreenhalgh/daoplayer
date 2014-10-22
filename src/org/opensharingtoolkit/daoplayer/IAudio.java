/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

/** Audio API
 * 
 * @author pszcmg
 *
 */
public interface IAudio {
	/** stop/clear all
	 */
	public void reset();
	
	public static interface IFile {
		public String getPath();
		public int getLength();
	};
	/** add a file 
	 */
	public IFile addFile(String path);
	
	public static interface ITrack {
		public boolean isPauseIfSilent();
		public int getId();
		public static int LENGTH_ALL = -1;
		public static int REPEATS_FOREVER = -1;
		public void addFileRef(int trackPos, IFile file);
		// Note: not both LENGTH_ALL and repeats > 1 (incl. REPEATS_FOREVER)
		public void addFileRef(int trackPos, IFile file, int filePos, int length, int repeats);
	};
	/** add a track 
	 */
	public ITrack addTrack(boolean pauseIfSilent);

	public static interface IScene {
		public boolean isPartial();
		public void set(ITrack track, Float volume, Integer pos);
		public void setVolume(ITrack track, float volume);
		public void setPosition(ITrack track, int pos);
	}
	public IScene newScene(boolean partial);
	
	public void setScene(IScene scene);
}
