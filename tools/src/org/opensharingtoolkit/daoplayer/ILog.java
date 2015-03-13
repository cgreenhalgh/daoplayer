/**
 * 
 */
package org.opensharingtoolkit.daoplayer;

//import org.opensharingtoolkit.daoplayer.logging.Recorder;

/** Stub
 * 
 * @author pszcmg
 *
 */
public class ILog {
	public void log(String message) {
		System.out.println("Log: "+message);
	}
	public void logError(String message) {
		System.out.println("Log error: "+message);
	}
	//public Recorder getRecorder();
}
