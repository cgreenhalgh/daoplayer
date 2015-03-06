/**
 * 
 */
package org.opensharingtoolkit.daoplayer.ui;

import org.opensharingtoolkit.daoplayer.R;
import org.opensharingtoolkit.daoplayer.Service;
import org.opensharingtoolkit.daoplayer.R.id;
import org.opensharingtoolkit.daoplayer.R.layout;
import org.opensharingtoolkit.daoplayer.Service.LocalBinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

/**
 * @author pszcmg
 *
 */
public class LogsActivity extends BoundActivity {

	private static final long POLL_INTERVAL_MS = 1000;
	protected TextView mLog;
	protected long mLastLogTime = 0;
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
	       setContentView(R.layout.activity_logs);
	       mLog = (TextView)findViewById(R.id.logs_log);
	       mLog.setMovementMethod(new ScrollingMovementMethod());
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		super.onPause();
		mHandler.removeCallbacks(mUpdateLogs);
	}
	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		updateLogs();
		mHandler.postDelayed(mUpdateLogs, POLL_INTERVAL_MS);
	}

	private Handler mHandler = new Handler() {
	};
	private Runnable mUpdateLogs = new Runnable() {
		public void run() {
			updateLogs();
			mHandler.postDelayed(mUpdateLogs, POLL_INTERVAL_MS);
		}
	};
	protected void updateLogs() {
		if (mBound) {
			long now = System.currentTimeMillis()-500;
			String logs = mLocal.getLogs(mLastLogTime, now);
			mLog.append(logs);
			mLastLogTime = now;
		}
	}
	protected void onBind() {
		updateLogs();
	}
}
