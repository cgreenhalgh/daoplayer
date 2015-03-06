package org.opensharingtoolkit.daoplayer.ui;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.opensharingtoolkit.daoplayer.R;
import org.opensharingtoolkit.daoplayer.Service;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class SceneSelectActivity extends BoundActivity {

	protected static final String TAG = "scene-selector";
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
       setContentView(R.layout.scene_select);
	}
	@Override
	protected void onBind() {
		final List<String> scenes = new LinkedList<String>();
		Collection<String> ss = mLocal.getScenes();
		if (ss!=null)
			scenes.addAll(ss);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1/*R.layout.scene_select_item*/, scenes);
		ListView listView = (ListView) findViewById(R.id.scene_select_list);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView parent, View v, int position, long id) {
				Log.d(TAG,"select scene "+position+" ("+scenes.get(position)+")");
				Intent i = new Intent(Service.ACTION_SET_SCENE);
				i.setClass(getApplicationContext(), Service.class);
				i.putExtra(Service.EXTRA_SCENE, scenes.get(position));
				startService(i);
			}
		});
	}
}
