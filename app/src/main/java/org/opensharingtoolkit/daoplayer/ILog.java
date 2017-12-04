/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

import org.opensharingtoolkit.daoplayer.logging.Recorder;

/**
 * @author pszcmg
 *
 */
public interface ILog {
	public void log(String message);
	public void logError(String message);
	public Recorder getRecorder();
}
