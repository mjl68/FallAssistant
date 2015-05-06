package com.falldetect2015.android.fallassistant;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.squareup.otto.Bus;

import java.io.PrintWriter;
import java.util.Date;

public class faSensorService extends Service implements SensorEventListener {
    static final String LOG_TAG = "faSensorService";
    static final boolean KEEPAWAKE_HACK = false;
    static final boolean MINIMAL_ENERGY = false;
    static final long MINIMAL_ENERGY_LOG_PERIOD = 500L;
    private static final String SAMPLING_SERVICE_POSITION_KEY = "sensorServicePosition";
    private static final String PREF_FILE = "faPrefs";
    private static final String PREF_SERVICE_STATE = "serviceState";
    private static final String PREF_SAMPLING_SPEED = "samplingSpeed";
    private static final String PREF_WAIT_SECS = "waitSeconds";
    //private Timer timer = new Timer();
    private static final long UPDATE_INTERVAL = 5000;
    public static Bus bus;
    private static MainActivity MAIN_ACTIVITY;
    private String sensorName;
    private int rate = SensorManager.SENSOR_DELAY_UI;
    private SensorManager mSensorManager;
    private PrintWriter captureFile;
    //private ScreenOffBroadcastReceiver screenOffBroadcastReceiver = null;
    private Sensor mAccelerometer;
    //private GenerateUserActivityThread generateUserActivityThread = null;
    private long logCounter = 0;
    private PowerManager.WakeLock serviceInProgressWakeLock;
    private Date serviceStartedTimeStamp;
    private boolean sensorServiceRunning = false;
    private int sensorServicePosition = 0;
    private float normalThreshold = 10;
    private float fallenThreshold = 2;
    private Boolean fallDetected = false;
    private float[] lastSensorValues = new float[3];
    private float[] mGravity;
    private float mAccelCurrent;
    private float mAccelLast;
    private float mAccel;
    private float maxAccelSeen = 0;
    private long baseMillisec = -1L;
    private long samplesPerSec = 0;
    private int defWaitSecs = 20;
    private int waitSeconds;
    private int svcState;
    private Boolean DEBUG = MainActivity.DEBUG;
    private Boolean svcRunning;
    private SharedPreferences appPrefs;

    public static void setMainActivity(MainActivity activity) {
        MAIN_ACTIVITY = activity;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        appPrefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        svcRunning = appPrefs.getBoolean(PREF_SERVICE_STATE, false);
        waitSeconds = appPrefs.getInt(PREF_WAIT_SECS, defWaitSecs);
        if (DEBUG)
            Log.d(LOG_TAG, "onStartCommand");
        svcRunning = false;
        stopSensorService();        // just in case the activity-level service management fails
        sensorName = intent.getStringExtra("sensorname");
        if (DEBUG)
            Log.d(LOG_TAG, "sensorName: " + sensorName);
        rate = appPrefs.getInt(
                PREF_SAMPLING_SPEED,
                SensorManager.SENSOR_DELAY_UI);
        if (DEBUG)
            Log.d(LOG_TAG, "rate: " + rate);

        //screenOffBroadcastReceiver = new ScreenOffBroadcastReceiver();
        //IntentFilter screenOffFilter = new IntentFilter();
        //screenOffFilter.addAction(Intent.ACTION_SCREEN_OFF);
        /*if (KEEPAWAKE_HACK)
            registerReceiver(screenOffBroadcastReceiver, screenOffFilter);*/
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        startSensorService();
        if (DEBUG) Log.d(LOG_TAG, "onStartCommand ends");
        return START_STICKY;
    }

    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) {
            Log.d(LOG_TAG, "onDestroy");
        }
        if (KEEPAWAKE_HACK) {
            //unregisterReceiver(screenOffBroadcastReceiver);
        }
        if (mSensorManager != null) {
            stopSensorService();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void stopSensorService() {
        if ((svcRunning != null) && (svcRunning != true))
            return;

        bus.unregister(this);

        if (mSensorManager != null) {
            if (DEBUG) Log.d(LOG_TAG, "unregisterListener/faSensorService");
            mSensorManager.unregisterListener(this);
        }

        svcRunning = false;
        serviceInProgressWakeLock.release();
        serviceInProgressWakeLock = null;
        Date serviceStoppedTimeStamp = new Date();
        long secondsEllapsed =
                (serviceStoppedTimeStamp.getTime() -
                        serviceStartedTimeStamp.getTime()) / 1000L;
        bus.post("Sensor ServiceX: Stopped");
        Log.d(LOG_TAG, "Service ServiceX: " +
                serviceStartedTimeStamp.toString() +
                "; Service stopped: " +
                serviceStoppedTimeStamp.toString() +
                " (" + secondsEllapsed + " seconds) " +
                "; samples collected: " + logCounter);
        stopSelf();
    }

    private void startSensorService() {
        if ((svcRunning != null) && (svcRunning == true)) {
            return;
        }

        /*new Thread(new Runnable() {
            public void run() {
                detectMovement();
            }
        }).start(); */
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);

        bus.post("Sensor ServiceX: Started");

        if (DEBUG) Log.d(LOG_TAG, "registerListener/faSensorService");
        serviceStartedTimeStamp = new Date();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        serviceInProgressWakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SamplingInProgress");
        serviceInProgressWakeLock.acquire();
        if (DEBUG) Log.d(LOG_TAG, "Sensor ServiceX: Started");
        svcRunning = true;
    }

    public void onSensorChanged(SensorEvent sensorEvent) {
        Log.d(LOG_TAG, "Sensor ServiceX: onSensorChanged");
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double threshold = (fallDetected == true) ? fallenThreshold : normalThreshold;
            mGravity = sensorEvent.values.clone();
            MainActivity.TestData mTestData = null;
            // fall / cant get up detection
            mTestData.x = mGravity[0];
            lastSensorValues[0] = mTestData.x;
            mTestData.y = mGravity[1];
            lastSensorValues[1] = mTestData.y;
            mTestData.z = mGravity[2];
            lastSensorValues[2] = mTestData.z;
            mAccelLast = mAccelCurrent;
            mAccelCurrent = (float) Math.sqrt(mTestData.x * mTestData.x + mTestData.y * mTestData.y + mTestData.z * mTestData.z);
            mTestData.delta = mAccelCurrent - mAccelLast;
            mAccel = Math.abs(mAccel * 0.9f) + mTestData.delta;
            if (mAccel > maxAccelSeen) {
                maxAccelSeen = mAccel;
                mTestData.message = "Increased";
            }
            mTestData.maxAccelSeen = maxAccelSeen;
            mTestData.timeStamp = System.currentTimeMillis();

            if (DEBUG)
                Log.d(LOG_TAG, "Sensor ServiceX: onChange mAccel=" + mAccel + " maxAccelSeen=" + maxAccelSeen + " threshold=" + threshold);
            if (mAccel > threshold) {
                maxAccelSeen = 0;
                if ((fallDetected == true) && (mAccel > fallenThreshold)) {
                    //MainActivity.sendSmsByManager();
                    mTestData.message = "Send for Help";
                } else {
                    if ((fallDetected == false) && (mAccel > normalThreshold)) {
                        fallDetected = true;
                        detectMovement();
                    }
                }
            }
            bus.post(mTestData);
        }
    }

    // SensorEventListener
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void detectMovement() {
        if (DEBUG) {
            Log.d(LOG_TAG, "Sensor ServiceX:Fall detected, getting ready to run CountDownTimer");
            bus.post("Start Timer");
        }

    }

}















/*
import android.app.IntentService;
import android.content.Intent;
import android.content.Context;

public class faSensorService extends IntentService {
    // TODO: Rename actions, choose action names that describe tasks that this
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_FOO = "com.falldetect2015.android.fallassistant.action.FOO";
    private static final String ACTION_BAZ = "com.falldetect2015.android.fallassistant.action.BAZ";

    // TODO: Rename parameters
    private static final String EXTRA_PARAM1 = "com.falldetect2015.android.fallassistant.extra.PARAM1";
    private static final String EXTRA_PARAM2 = "com.falldetect2015.android.fallassistant.extra.PARAM2";


    // TODO: Customize helper method
    public static void startActionFoo(Context context, String param1, String param2) {
        Intent intent = new Intent(context, faSensorService.class);
        intent.setAction(ACTION_FOO);
        intent.putExtra(EXTRA_PARAM1, param1);
        intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }


    // TODO: Customize helper method
    public static void startActionBaz(Context context, String param1, String param2) {
        Intent intent = new Intent(context, faSensorService.class);
        intent.setAction(ACTION_BAZ);
        intent.putExtra(EXTRA_PARAM1, param1);
        intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }

    public faSensorService() {
        super("faSensorService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_FOO.equals(action)) {
                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleActionFoo(param1, param2);
            } else if (ACTION_BAZ.equals(action)) {
                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleActionBaz(param1, param2);
            }
        }
    }


    private void handleActionFoo(String param1, String param2) {
        // TODO: Handle action Foo
        throw new UnsupportedOperationException("Not yet implemented");
    }


    private void handleActionBaz(String param1, String param2) {
        // TODO: Handle action Baz
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
*/