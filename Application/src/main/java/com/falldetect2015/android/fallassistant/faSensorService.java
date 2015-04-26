package com.falldetect2015.android.fallassistant;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.io.PrintWriter;
import java.util.Date;

public class faSensorService extends Service implements SensorEventListener {
    static final String LOG_TAG = "faSensorService";
    static final boolean KEEPAWAKE_HACK = false;
    static final boolean MINIMAL_ENERGY = false;
    static final long MINIMAL_ENERGY_LOG_PERIOD = 500L;
    private static final String SAMPLING_SERVICE_POSITION_KEY = "sensorServicePositon";
    private static final String PREF_FILE = "prefs";
    private static final String PREF_SERVICE_STATE = "serviceState";
    private static final String PREF_SAMPLING_SPEED = "samplingSpeed";
    private static final String PREF_WAIT_SECS = "waitSeconds";
    private String sensorName;
    private int rate = SensorManager.SENSOR_DELAY_UI;
    private SensorManager mSensorManager;
    private PrintWriter captureFile;
    private ScreenOffBroadcastReceiver screenOffBroadcastReceiver = null;
    private Sensor mAccelerometer;
    private GenerateUserActivityThread generateUserActivityThread = null;
    private long logCounter = 0;
    private PowerManager.WakeLock serviceInProgressWakeLock;
    private Date serviceStartedTimeStamp;
    private boolean sensorServiceRunning = false;
    private int sensorServicePosition = 0;
    private float normalThreshold = 10;
    private float fallenThreshold = 2;
    private Boolean fallDetected = false;
    private Boolean noMovement = true;
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

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        SharedPreferences appPrefs = getSharedPreferences(
                PREF_FILE,
                MODE_PRIVATE);
        svcRunning = appPrefs.getBoolean(PREF_SERVICE_STATE, false);
        waitSeconds = appPrefs.getInt(PREF_WAIT_SECS, defWaitSecs);
        if (MainActivity.DEBUG)
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

        screenOffBroadcastReceiver = new ScreenOffBroadcastReceiver();
        IntentFilter screenOffFilter = new IntentFilter();
        screenOffFilter.addAction(Intent.ACTION_SCREEN_OFF);
        if (KEEPAWAKE_HACK)
            registerReceiver(screenOffBroadcastReceiver, screenOffFilter);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        startSensorService();
        if (DEBUG)
            Log.d(LOG_TAG, "onStartCommand ends");
        return START_NOT_STICKY;
    }

    public void onDestroy() {
        super.onDestroy();
        if (MainActivity.DEBUG)
            Log.d(LOG_TAG, "onDestroy");
        stopSensorService();
        if (KEEPAWAKE_HACK)
            unregisterReceiver(screenOffBroadcastReceiver);
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
    }

    public IBinder onBind(Intent intent) {
        return null;
    }   // cannot bind

    private void stopSensorService() {
        if ((svcRunning != null) && (svcRunning != true))
            return;
        if (generateUserActivityThread != null) {
            generateUserActivityThread.stopThread();
            generateUserActivityThread = null;
        }
        if (mSensorManager != null) {
            if (DEBUG)
                Log.d(LOG_TAG, "unregisterListener/faSensorService");
            mSensorManager.unregisterListener(this);
        }
        if (captureFile != null) {
            //captureFile.close();
            //captureFile = null;
        }
        svcRunning = false;
        serviceInProgressWakeLock.release();
        serviceInProgressWakeLock = null;
        Date serviceStoppedTimeStamp = new Date();
        long secondsEllapsed =
                (serviceStoppedTimeStamp.getTime() -
                        serviceStartedTimeStamp.getTime()) / 1000L;
        Log.d(LOG_TAG, "Service started: " +
                serviceStartedTimeStamp.toString() +
                "; Service stopped: " +
                serviceStoppedTimeStamp.toString() +
                " (" + secondsEllapsed + " seconds) " +
                "; samples collected: " + logCounter);
    }

    private void startSensorService() {
        if ((svcRunning != null) && (svcRunning == true)) {
            return;
        }
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);
        if (DEBUG)
                    Log.d(LOG_TAG, "registerListener/faSensorService");
            serviceStartedTimeStamp = new Date();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            serviceInProgressWakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SamplingInProgress");
            serviceInProgressWakeLock.acquire();
        if (DEBUG)
            Log.d(LOG_TAG, "Sensor Service Starting...");
        svcRunning = true;
    }

    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double threshold = (fallDetected == true) ? fallenThreshold : normalThreshold;
            mGravity = sensorEvent.values.clone();
            // fall / cant get up detection
            float x = mGravity[0];
            lastSensorValues[0] = x;
            float y = mGravity[1];
            lastSensorValues[1] = y;
            float z = mGravity[2];
            lastSensorValues[2] = z;
            mAccelLast = mAccelCurrent;
            mAccelCurrent = (float) Math.sqrt(x * x + y * y + z * z);
            float delta = mAccelCurrent - mAccelLast;
            mAccel = Math.abs(mAccel * 0.9f) + delta;
            if (mAccel > maxAccelSeen) {
                maxAccelSeen = mAccel;
            }
            if (DEBUG)
                Log.d(LOG_TAG, "Sensor onChange mAccel=" + mAccel + " maxAccelSeen=" + maxAccelSeen + " threshold=" + threshold);
            if (mAccel > threshold) {
                maxAccelSeen = 0;
                if ((fallDetected == true) && (mAccel > fallenThreshold)) {
                    //MainActivity.sendSmsByManager();
                    noMovement = false;
                } else {
                    if ((fallDetected == false) && (mAccel > normalThreshold)) {
                        fallDetected = true;
                        noMovement = true;
                        new Thread(new Runnable() {
                            public void run() {
                                detectMovement();
                            }
                        }).start();
                    }
                }
            }
        }
        long currentMillisec = System.currentTimeMillis();
    }

    // SensorEventListener
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void detectMovement() {
        noMovement = true;
        int waitSecs = waitSeconds;
        new CountDownTimer(waitSecs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // check noMovement flag after 1s
                if (noMovement == false) {
                    Toast.makeText(getApplicationContext(), "Welcome Back",
                            Toast.LENGTH_LONG).show();
                    cancel();
                }
            }

            @Override
            public void onFinish() {
                // do something end times 5s
                if (noMovement == true) {
                    //MainActivity.sendSmsByManager();
                    noMovement = false;
                } else {
                    if ((fallDetected == false) && (mAccel > normalThreshold)) {
                        fallDetected = true;
                        noMovement = true;
                        new Thread(new Runnable() {
                            public void run() {
                                detectMovement();
                            }
                        }).start();
                    }
                    ;
                }
            }
        };
    }

    class ScreenOffBroadcastReceiver extends BroadcastReceiver {
        private static final String LOG_TAG = "ScreenOffBroadcastReceiver";

        public void onReceive(Context context, Intent intent) {
            if (DEBUG)
                Log.d(LOG_TAG, "onReceive: " + intent);
            if ((mSensorManager != null) && (svcRunning == true)) {
                if (generateUserActivityThread != null) {
                    generateUserActivityThread.stopThread();
                    generateUserActivityThread = null;
                }
                generateUserActivityThread = new GenerateUserActivityThread();
                generateUserActivityThread.start();
            }
        }
    }

    class GenerateUserActivityThread extends Thread {
        PowerManager.WakeLock userActivityWakeLock;

        public void run() {
            if (DEBUG)
                Log.d(LOG_TAG, "Waiting 2 sec for switching back the screen ...");
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ex) {
            }
            if (DEBUG)
                Log.d(LOG_TAG, "User activity generation thread started");

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            userActivityWakeLock =
                    pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "GenerateUserActivity");
            userActivityWakeLock.acquire();
            if (DEBUG)
                Log.d(LOG_TAG, "User activity generation thread exiting");
        }

        public void stopThread() {
            if (DEBUG)
                Log.d(LOG_TAG, "User activity wake lock released");
            userActivityWakeLock.release();
            userActivityWakeLock = null;
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