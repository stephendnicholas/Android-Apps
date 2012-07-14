package com.stephendnicholas.tapper;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Chronometer;

/**
 * Launcher activity. Just has a button to kick off the background service and a
 * chronometer (which seems buggy) to help with practising taps :)
 * 
 * @author stephen.nicholas - stephendnicholas.com
 */
public class MainActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

	@Override
	protected void onResume() {
		Log.i("TAPPER", "onResume");

		final Chronometer chronometer = (Chronometer) findViewById(R.id.chronometer);

		final Button startStopButton = (Button) findViewById(R.id.startStopButton);

		if (BgService.isStarted()) {
			startStopButton.setText("Stop Service");
		} else {
			startStopButton.setText("Start Service");
		}

		startStopButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (BgService.isStarted()) {
					chronometer.stop();

					stopService(new Intent(MainActivity.this, BgService.class));

					startStopButton.setText("Start Service");
				} else {
					chronometer.start();

					startService(new Intent(MainActivity.this, BgService.class));

					startStopButton.setText("Stop Service");
				}

			}
		});

		super.onResume();
	}

	@Override
	protected void onPause() {
		Log.i("TAPPER", "onPause");
		super.onPause();
	}

	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		// Lock to portrait
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	}
}