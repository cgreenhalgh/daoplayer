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
public class StatusActivity extends LogsActivity {
	@Override
	protected void updateLogs() {
		if (mBound) {
			long now = System.currentTimeMillis()-500;
			String status = mLocal.getStatus();
			mLog.setText(status);
			mLastLogTime = now;
		}
	}
}
