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
public class BoundActivity extends Activity {

	protected boolean mBound = false;
	protected Service.LocalBinder mLocal;
	/* (non-Javadoc)
	 * @see android.app.Activity#onStart()
	 */
	@Override
	protected void onStart() {
		super.onStart();
		Intent i = new Intent(this, Service.class);
		bindService(i, mConnection, Context.BIND_AUTO_CREATE);
	}
	/* (non-Javadoc)
	 * @see android.app.Activity#onStop()
	 */
	@Override
	protected void onStop() {
		super.onStop();
		if (mBound){
			unbindService(mConnection);
			mBound = false;
		}
	}

	protected void onBind() {		
	}
	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mBound = true;
			mLocal = (Service.LocalBinder)service;
			onBind();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBound = false;
		}
	};
}
