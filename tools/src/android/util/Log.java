/* testing stub */
package android.util;

public class Log {
	public static void e(String tag, String message) {
		System.out.println("Error   ("+tag+"): "+message);
	}
	public static void e(String tag, String message, Exception e) {
		System.out.println("Error   ("+tag+"): "+message);
		e.printStackTrace();
	}
	public static void w(String tag, String message) {
		System.out.println("Warning ("+tag+"): "+message);
	}
	public static void d(String tag, String message) {
		System.out.println("Debug   ("+tag+"): "+message);
	}
}