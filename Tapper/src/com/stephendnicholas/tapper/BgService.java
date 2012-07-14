package com.stephendnicholas.tapper;

import java.util.Arrays;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Main service that registers for accelerometer events and tries to detect taps
 * and patterns.
 * 
 * Current pattern: Tap - Pause for 1 - Tap - Pause for 2 - Tap
 * 
 * @author stephen.nicholas - stephendnicholas.com
 */
public class BgService extends Service implements SensorEventListener {

	private static boolean STARTED = false;

	public static boolean isStarted() {
		return STARTED;
	}

	// /////////////////////////////////////////////////////////////////

	private static final double ALPHA = 0.15;

	// Threshold for significant changes in accelerometer readings
	private static final double THRESHOLD = 0.05;

	// Max length of 'events' - only those shorter than this are considered taps
	private static final double EVENT_DURATION_THRESHOLD = 500;

	// Max & min durations for the desired inter-tap pauses
	private static final double AFTER_EVENT_1_MIN_PAUSE = 1000;
	private static final double AFTER_EVENT_1_MAX_PAUSE = 2000;

	private static final double AFTER_EVENT_2_MIN_PAUSE = 2000;
	private static final double AFTER_EVENT_2_MAX_PAUSE = 3000;

	// Whether we are 'in' a possible tap event
	private boolean inEvent = false;

	// The start time of the possible tap event
	private long eventStart = -1;

	// Last accelerometer reading received
	private float[] lastReading;

	// Count of accelerometer readings received
	private int readingCount = 0;

	// Rolling array of the last five readings
	private double[] lastFiveReadings = new double[5];

	// The index of the next / current reading in the array above
	private int currentReadingIndex = 0;

	// Count of tap events
	private int eventCount = 0;

	// Array of the end times of previous tap events
	private long[] previousEventsCompleteTimes = new long[3];

	// Index of the next / current tap event in the array above
	private int currentLastEventIndex = 0;

	private Toast toast;

	private PowerManager.WakeLock wakeLock;
	private NotificationManager notificationManager;

	private SensorManager mSensorManager;
	private Sensor mAccelerometer;

	/*
	 * BroadcastReceiver for handling ACTION_SCREEN_OFF. This is to hack around
	 * the fact that the accelerometer stops giving readings when the screen is
	 * powered off. Doesn't work for HTC & may not be necessary for latest
	 * Android version. See: http://nosemaj.org/android-persistent-sensors
	 */
	public BroadcastReceiver actionScreenOffReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// Check action just to be on the safe side.
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF) && STARTED) {

				Runnable runnable = new Runnable() {
					public void run() {
						Log.v("TAPPER", "Trying to re-register accelerometer");

						// Unregisters the listener and registers it again.
						mSensorManager.unregisterListener(BgService.this);
						mSensorManager.registerListener(BgService.this,
								mAccelerometer,
								SensorManager.SENSOR_DELAY_FASTEST);
					}
				};

				new Handler().postDelayed(runnable, 500);
			}
		}
	};

	@Override
	public void onCreate() {
		Log.v("TAPPER", "onCreate");

		// Get the sensor manager & accelerometer
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		// Get a wake lock
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "My wakelook");

		// Get the notification manager & toast service creation
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		toast = Toast.makeText(getApplicationContext(), "Service created.",
				Toast.LENGTH_SHORT);

		// Register our receiver for the ACTION_SCREEN_OFF action. This will
		// make our receiver code be called whenever the phone enters standby
		// mode.
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
		registerReceiver(actionScreenOffReceiver, filter);

		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v("TAPPER", "onStartCommand");

		// If not already started
		if (!STARTED) {
			STARTED = true;

			// Acquire the wake lock
			wakeLock.acquire();

			/*
			 * Start service in the foreground with a notification.
			 */
			Notification notification = new Notification();
			notification.when = System.currentTimeMillis();

			// Set it so the notification cannot be cleared
			notification.flags = Notification.FLAG_NO_CLEAR;

			notification.icon = R.drawable.tap;

			// Launch the home screen when the notification is clicked
			PendingIntent contentIntent = PendingIntent.getActivity(
					getApplicationContext(), 0, new Intent(
							getApplicationContext(), MainActivity.class), 0);

			notificationManager.cancel(1);

			notification.setLatestEventInfo(getApplicationContext(),
					"Running...", "Woot", contentIntent);

			this.startForeground(1, notification);

			/*
			 * Register this service to receive updates from the acceleromter
			 * AFAP.
			 */
			mSensorManager.registerListener(this, mAccelerometer,
					SensorManager.SENSOR_DELAY_FASTEST);

			// Toast start
			toast.setText("Service started");
			toast.show();
		}

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.i("TAPPER", "onDestroy");

		STARTED = false;

		// Unregsiter for accelerometer updates
		mSensorManager.unregisterListener(this);

		// Unregister the screen off receiver
		try {
			unregisterReceiver(actionScreenOffReceiver);
		} catch (Exception e) {
			// Don't care
		}

		// Release the wakelock
		wakeLock.release();

		super.onDestroy();
	}

	// /////////////////////////////////////////////////////////////////////////

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Don't care
	}

	@Override
	public void onSensorChanged(SensorEvent event) {

		// Synchronize so that each event is completely handled before the next
		synchronized (this) {
			readingCount++;

			// If we've got a previous reading - then we can start doing stuff
			if (lastReading != null) {

				// Calulate the difference between this reading and the previous
				// in each dimension
				double x = (event.values[0] - lastReading[0]) * ALPHA;
				double y = (event.values[1] - lastReading[1]) * ALPHA;
				double z = (event.values[2] - lastReading[2]) * ALPHA;

				// Get the 'absolute' magnitude of difference from all
				// 'directions'
				double absoluteReading = Math.sqrt((x * x) + (y * y) + (z * z));

				// Store the 'absolute' difference in the circular buffer of the
				// last 5 readings
				lastFiveReadings[currentReadingIndex] = absoluteReading;

				// If at least 5 things - then we can perform our 5-point
				// smoothing
				if (readingCount > 5) {

					/*
					 * Calculate the average of the last five readings
					 */
					double average = 0.0;

					for (int i = 0; i < 5; i++) {
						average += lastFiveReadings[i];
					}

					average = average / 5.0;

					/*
					 * If the averaged direction change is above the threshold -
					 * then event of interest - possible start of tap
					 */
					if (average > THRESHOLD) {
						Log.i("TAPPER", "Event of interest: "
								+ (readingCount - 2) + " Magnitude: " + average);

						// If not already 'in' a tap event
						if (!inEvent) {

							// Record event start & flag that we are now 'in'
							// the tap event
							eventStart = System.currentTimeMillis();
							inEvent = true;
						}
						// Else already in tap event, don't care about more
						// above threshold until we dip below
					}
					/*
					 * Else new change is below threshold - if 'in' event, then
					 * the event has finished and it's time to do stuff
					 */
					else if (inEvent) {
						// Flag no longer in event
						inEvent = false;

						// Calculate event duration
						long eventDuration = System.currentTimeMillis()
								- eventStart;

						Log.w("TAPPER", "Event detected. Duration: "
								+ eventDuration);

						// If the event was short enough (taps should be quick),
						// then of interest - else just ignore
						if (eventDuration < EVENT_DURATION_THRESHOLD) {

							eventCount++;

							// Store the end time of this event in the 'rolling'
							// array of such things - order doesn't matter, as
							// we sort later
							previousEventsCompleteTimes[currentLastEventIndex] = System
									.currentTimeMillis();

							Log.i("TAPPER", "Event count: " + eventCount);

							// If we've got three events (tap - pause - tap -
							// pause - tap)
							if (eventCount > 2) {

								// Clone into new array and sort so that the
								// newest is first and vice versa
								long[] clonedLastEvents = previousEventsCompleteTimes
										.clone();
								Arrays.sort(clonedLastEvents);

								/*
								 * Check if it matches the pattern - Q: are the
								 * between tap pauses of the appropriate
								 * duration?
								 */
								long event2Duration = clonedLastEvents[2]
										- clonedLastEvents[1];
								long event1Duration = clonedLastEvents[1]
										- clonedLastEvents[0];

								Log.i("TAPPER", "E2D: " + event2Duration
										+ ". E1D: " + event1Duration);

								// Toast the inter-tap durations for user
								// feedback
								toast.setText("E1D: " + event1Duration
										+ ". E2D: " + event2Duration);
								toast.show();

								if (event1Duration > AFTER_EVENT_1_MIN_PAUSE
										&& event1Duration < AFTER_EVENT_1_MAX_PAUSE) {

									Log.i("TAPPER", "Event 1 matches");

									if (event2Duration > AFTER_EVENT_2_MIN_PAUSE
											&& event2Duration < AFTER_EVENT_2_MAX_PAUSE) {

										Log.i("TAPPER", "Event 2 matches");

										// It matches - Launch our fake dialler
										// activity
										Log.w("TAPPER", "Boo yah!");
										Intent intent = new Intent(this,
												FakeCallActivity.class);
										intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
										startActivity(intent);
									}
								}
							}

							// Update last event index and wrap around if
							// necessary
							currentLastEventIndex++;

							if (currentLastEventIndex > 2) {
								currentLastEventIndex = 0;
							}
						}

					}
				}

			}

			// Move reading index - rolling around if necessary
			currentReadingIndex++;

			if (currentReadingIndex > 4) {
				currentReadingIndex = 0;
			}

			// Store this reading as the last reading
			lastReading = (float[]) event.values.clone();
		}
	}

	// /////////////////////////////////////////////////////////////////////////

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}