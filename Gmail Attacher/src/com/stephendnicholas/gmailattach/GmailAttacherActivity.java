package com.stephendnicholas.gmailattach;

import java.io.IOException;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

/**
 * Simple main application activity with a single button that does stuff when
 * clicked.
 * 
 * @author stephendnicholas.com
 */
public class GmailAttacherActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Button button = (Button) findViewById(R.id.dostuff);
		button.setOnClickListener(new OnClickListener() {

			@Override
			// When the user clicks the button:
			public void onClick(View v) {
				try {
					// Write a dummy text file to this application's internal
					// cache dir.
					Utils.createCachedFile(GmailAttacherActivity.this,
							"Test.txt", "This is a test");

					// Then launch the activity to send that file via gmail.
					startActivity(Utils.getSendEmailIntent(
							GmailAttacherActivity.this,
							// TODO - Change email to yours
							"<YOUR_EMAIL_HERE>@<YOUR_DOMAIN>.com", "Test",
							"See attached", "Test.txt"));
				} catch (IOException e) {
					e.printStackTrace();
				}
				// Catch if Gmail is not available on this device
				catch (ActivityNotFoundException e) {
					Toast.makeText(GmailAttacherActivity.this,
							"Gmail is not available on this device.",
							Toast.LENGTH_SHORT).show();
				}
			}
		});
	}
}