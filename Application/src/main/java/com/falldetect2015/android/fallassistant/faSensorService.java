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
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Config;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;

public class faSensorService extends Service implements SensorEventListener {
    static final String LOG_TAG = "faSensorService";
    static final boolean KEEPAWAKE_HACK = false;
    static final boolean MINIMAL_ENERGY = false;
    static final long MINIMAL_ENERGY_LOG_PERIOD = 500L;
    private static final String SAMPLING_SERVICE_POSITION_KEY = "sensorServicePositon";
    private String sensorName;
    private int rate = SensorManager.SENSOR_DELAY_UI;
    private SensorManager sensorManager;
    private PrintWriter captureFile;
    private ScreenOffBroadcastReceiver screenOffBroadcastReceiver = null;
    private Sensor ourSensor;
    private GenerateUserActivityThread generateUserActivityThread = null;
    private long logCounter = 0;
    private PowerManager.WakeLock serviceInProgressWakeLock;
    private Date serviceStartedTimeStamp;
    private boolean sensorServiceRunning = false;
    private int sensorServicePosition = 0;

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (MainActivity.DEBUG)
            Log.d(LOG_TAG, "onStartCommand");
        stopSensorService();        // just in case the activity-level service management fails
        sensorName = intent.getStringExtra("sensorname");
        if (MainActivity.DEBUG)
            Log.d(LOG_TAG, "sensorName: " + sensorName);
        SharedPreferences appPrefs = getSharedPreferences(
                MainActivity.PREF_FILE,
                MODE_PRIVATE);
        rate = appPrefs.getInt(
                MainActivity.PREF_SAMPLING_SPEED,
                SensorManager.SENSOR_DELAY_UI);
        if (MainActivity.DEBUG)
            Log.d(LOG_TAG, "rate: " + rate);

        screenOffBroadcastReceiver = new ScreenOffBroadcastReceiver();
        IntentFilter screenOffFilter = new IntentFilter();
        screenOffFilter.addAction(Intent.ACTION_SCREEN_OFF);
        if (KEEPAWAKE_HACK)
            registerReceiver(screenOffBroadcastReceiver, screenOffFilter);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        startSensorService();
        if (MainActivity.DEBUG)
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
    }

    public IBinder onBind(Intent intent) {
        return null;
    }   // cannot bind

    private void stopSensorService() {
        if (!MainActivity.svcRunning)
            return;
        if (generateUserActivityThread != null) {
            generateUserActivityThread.stopThread();
            generateUserActivityThread = null;
        }
        if (sensorManager != null) {
            if (Config.DEBUG)
                Log.d(LOG_TAG, "unregisterListener/faSensorService");
            sensorManager.unregisterListener(this);
        }
        if (captureFile != null) {
            captureFile.close();
            captureFile = null;
        }
        MainActivity.svcRunning = false;
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
        if ((MainActivity.svcRunning != null) && (MainActivity.svcRunning == true)) {
            return;
        }
        if (sensorName != null) {
            List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
            ourSensor = null;
            ;
            for (int i = 0; i < sensors.size(); ++i)
                if (sensorName.equals(sensors.get(i).getName())) {
                    ourSensor = sensors.get(i);
                    break;
                }
            if (ourSensor != null) {
                if (MainActivity.DEBUG)
                    Log.d(LOG_TAG, "registerListener/faSensorService");
                sensorManager.registerListener(
                        this,
                        ourSensor,
                        rate);
            }
            serviceStartedTimeStamp = new Date();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            serviceInProgressWakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SamplingInProgress");
            serviceInProgressWakeLock.acquire();
            captureFile = null;
            if (MainActivity.DEBUG)
                Log.d(LOG_TAG, "Capture file created");
            File captureFileName = new File("/sdcard", "capture.csv");
            try {
                captureFile = new PrintWriter(new FileWriter(captureFileName, false));
            } catch (IOException ex) {
                Log.e(LOG_TAG, ex.getMessage(), ex);
            }
            MainActivity.svcRunning = true;
        }
    }

    public void onSensorChanged(SensorEvent sensorEvent) {
        ++logCounter;
        if (!MINIMAL_ENERGY) {
            if (MainActivity.DEBUG) {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < sensorEvent.values.length; ++i) {
                    if (i > 0)
                        b.append(" , ");
                    b.append(Float.toString(sensorEvent.values[i]));
                }
                Log.d(LOG_TAG, "onSensorChanged: " + new Date().toString() + " [" + b + "]");
            }
            if (captureFile != null) {
                captureFile.print(Long.toString(sensorEvent.timestamp));
                for (int i = 0; i < sensorEvent.values.length; ++i) {
                    captureFile.print(",");
                    captureFile.print(Float.toString(sensorEvent.values[i]));
                }
                captureFile.println();
            }
        } else {
            ++logCounter;
            if ((logCounter % MINIMAL_ENERGY_LOG_PERIOD) == 0L)
                Log.d(LOG_TAG, "logCounter: " + logCounter + " at " + new Date().toString());
        }
    }

    // SensorEventListener
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    class ScreenOffBroadcastReceiver extends BroadcastReceiver {
        private static final String LOG_TAG = "ScreenOffBroadcastReceiver";

        public void onReceive(Context context, Intent intent) {
            if (MainActivity.DEBUG)
                Log.d(LOG_TAG, "onReceive: " + intent);
            if (sensorManager != null && MainActivity.svcRunning) {
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
            if (MainActivity.DEBUG)
                Log.d(LOG_TAG, "Waiting 2 sec for switching back the screen ...");
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ex) {
            }
            if (MainActivity.DEBUG)
                Log.d(LOG_TAG, "User activity generation thread started");

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            userActivityWakeLock =
                    pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "GenerateUserActivity");
            userActivityWakeLock.acquire();
            if (MainActivity.DEBUG)
                Log.d(LOG_TAG, "User activity generation thread exiting");
        }

        public void stopThread() {
            if (MainActivity.DEBUG)
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