package com.stephendnicholas.tapper;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;

/**
 * The fake call activity - simply displays an image of a dialler screen and
 * plays the phone's ringtone. No interaction, buttons, etc. Press back to
 * escape and stop the ringtone.
 * 
 * @author stephen.nicholas - stephendnicholas.com
 */
public class FakeCallActivity extends Activity {

	private Ringtone r;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.fake);

		Uri notification = RingtoneManager
				.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
		r = RingtoneManager.getRingtone(getApplicationContext(), notification);
	}

	@Override
	protected void onResume() {
		r.play();

		stopService(new Intent(this, BgService.class));

		super.onResume();
	}

	@Override
	protected void onPause() {
		r.stop();

		super.onPause();
	}

	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		// Lock to portrait
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	}
}
