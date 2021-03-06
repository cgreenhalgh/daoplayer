/**
 * 
 */
package org.opensharingtoolkit.daoplayer.ui;

import org.opensharingtoolkit.daoplayer.R;
import org.opensharingtoolkit.daoplayer.Service;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * @author pszcmg
 *
 */
@SuppressLint("Registered")
public class BrowserActivity extends BoundActivity {

	public static final String TAG = "daoplayer-browser";
	public static final double DEFAULT_ACCURACY = 10;
	protected boolean mWebViewLoaded;

	@SuppressLint({ "SetJavaScriptEnabled", "NewApi" })
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        WebView webView = (WebView)findViewById(R.id.webView);
        webView.addJavascriptInterface(mJavascriptHelper, "daoplayer");
        webView.getSettings().setJavaScriptEnabled(true);
        // API level 16
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			webView.getSettings().setAllowFileAccessFromFileURLs(true);
		}
		//webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
		webView.getSettings().setBlockNetworkLoads(false);
		webView.getSettings().setBlockNetworkImage(false);
        // API level 5
        //webView.getSettings().setDatabaseEnabled(true);
        //webView.getSettings().setBuiltInZoomControls(false);
        // API level 5
        //webView.getSettings().setDatabasePath(getApplicationContext().getFilesDir().getPath()+"/org.opensharingtoolkit.daoplayer/databases/");
        // API level 7
        webView.getSettings().setDomStorageEnabled(true);
        // API level 17
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
		}
        webView.setWebChromeClient(new WebChromeClient() {
        	// onConsoleMessage - level 8 - default is ok anyway
        	// onExceededDatabaseQuota - level 5
        	// onGeolocationPermissionsHidePrompt - level 5
        	// onGeolocationPermissionsShowPrompt - level 5
			@Override
			public boolean onJsAlert(WebView view, String url, String message,
					JsResult result) {
				Log.w(TAG,"onJsAlert: ("+url+") "+message+" ("+result+")");
				return super.onJsAlert(view, url, message, result);
			}

			// onReachedMaxAppCacheSize - level 7 
        });
        webView.setWebViewClient(new WebViewClient() {
        	public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        		// this picks up local errors aswell
        		Log.d(TAG,"onReceivedError errorCode="+errorCode+", description="+description+", failingUrl="+failingUrl); 
        		Toast.makeText(BrowserActivity.this, "Oh no! " + description, Toast.LENGTH_SHORT).show();
        	}
			// onReceivedLoginRequest - level 12
			// onReceivedSslError - level 8
			// shouldInterceptRequest - level 11
			@Override
			public void onPageFinished(WebView view, String url) {
				Log.d(TAG,"browser activity webview loaded");
				mWebViewLoaded = true;
				super.onPageFinished(view, url);
			}
        });

        String url = getString(R.string.webui_default_url);
        Log.d(TAG,"load default url "+url);
        webView.loadUrl(url);
    }
	protected void onBind() {
		// get context model
		mHandler.post(mPoll);
	}
	private Handler mHandler = new Handler();
	private Runnable mPoll = new Runnable() {
		public void run() {
			if (mBound)
				mHandler.postDelayed(mPoll, 1000);
			Log.d(TAG,"poll status");
			loadStatus();
		}
	};
	private void loadStatus() {
		if (!mBound) {
			Log.e(TAG,"loadStatus called when not bound");
			return;
		}
		if (!mWebViewLoaded){
			Log.e(TAG,"loadStatus called when webview not loaded - ignored");
			return;			
		}
        WebView webView = (WebView)findViewById(R.id.webView);
        if (webView==null) {
			Log.e(TAG,"loadStatus called with not webview");
			return;        	
        }
		String jstatus = mLocal.getUserModel();
		StringBuilder sb = new StringBuilder();
		sb.append("javascript:(function(){");
		sb.append(jstatus);
		sb.append(";try {window.onstatus(position,waypoints);} "+
				"catch (err) {console.log('error onstatus: '+err.message);}");
		sb.append("})();");
		String s = sb.toString();
		Log.d(TAG,"Browser view, load: "+s);
		webView.loadUrl(s);
	}
	protected void reload() {
        WebView webView = (WebView)findViewById(R.id.webView);
        if (webView!=null) {
	        String url = getString(R.string.webui_default_url);
	        Log.d(TAG,"reload default url "+url);
	        webView.loadUrl(url);
        }
	}
	protected boolean handleBackPressed() {
        WebView webView = (WebView)findViewById(R.id.webView);
        if (webView!=null && webView.canGoBack()) {
    		Log.d(TAG,"Back in web history");
        	webView.goBack();
        	return true;
        }		
        return false;
	}
	
	@Override
	public void onBackPressed() {
		if (!handleBackPressed())
			// level 5
			super.onBackPressed();
	}    
	class JavascriptHelper {
		@JavascriptInterface
		public void signalService(String action) {
			Log.d(TAG,"signalService("+action+")");
			Intent i = new Intent();
			i.setAction(action);
			i.setClass(getApplicationContext(), Service.class);
			startService(i);
		}
		@JavascriptInterface
		public void setLatLng(double lat, double lng) {
			Log.d(TAG,"setLatLng("+lat+","+lng+")");
			Intent i = new Intent(Service.ACTION_SET_LATLNG);
			i.setClass(getApplicationContext(), Service.class);
			i.putExtra(Service.EXTRA_LAT, lat);
			i.putExtra(Service.EXTRA_LNG, lng);
			i.putExtra(Service.EXTRA_ACCURACY, DEFAULT_ACCURACY);
			startService(i);
		}

	}
	private JavascriptHelper mJavascriptHelper = new JavascriptHelper();
}