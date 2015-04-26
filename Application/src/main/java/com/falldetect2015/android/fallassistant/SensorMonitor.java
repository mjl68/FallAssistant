package com.falldetect2015.android.fallassistant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import java.io.PrintWriter;

public class SensorMonitor extends Activity implements SensorEventListener {
    static final String LOG_TAG = "SENSORMONITOR";
    static final String SERVICESTARTED_KEY = "serviceStarted";
    static final String SENSORNAME_KEY = "sensorName";
    private static final String PREF_FILE = "prefs";
    private static final String PREF_SERVICE_STATE = "serviceState";
    private static final String PREF_SAMPLING_SPEED = "samplingSpeed";
    private static final String PREF_WAIT_SECS = "waitSeconds";
    private PowerManager.WakeLock sensorServiceWakeLock;
    private String sensorName;
    private boolean captureState = false;
    private int rate = SensorManager.SENSOR_DELAY_UI;
    private SensorManager mSensorManager;
    private PrintWriter captureFile;
    private long baseMillisec = -1L;
    private long samplesPerSec = 0;
    private String captureStateText;
    private Boolean DEBUG = MainActivity.DEBUG;
    private Boolean svcRunning;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences appPrefs = getSharedPreferences(
                PREF_FILE,
                MODE_PRIVATE);
        svcRunning = appPrefs.getBoolean(PREF_SERVICE_STATE, false);
        sensorName = null;
        //setContentView( R.layout.monitor );
        Intent i = getIntent();
        if (i != null) {
            sensorName = i.getStringExtra("sensorname");
            if (DEBUG)
                Log.d(LOG_TAG, "sensorName: " + sensorName);
            if (sensorName != null) {
                //TextView t = (TextView)findViewById( R.id.sensorname );
                //t.setText( sensorName );
            }
        }
        if (savedInstanceState != null) {
            svcRunning = savedInstanceState.getBoolean(SERVICESTARTED_KEY, false);
            sensorName = savedInstanceState.getString(SENSORNAME_KEY);
        }
        captureState = appPrefs.getBoolean(PREF_SERVICE_STATE, false);
        captureStateText = null;
        if (captureState) {
            //File captureFileName = new File("/sdcard", "capture.csv");
            //captureStateText = "Capture: " + captureFileName.getAbsolutePath();
          /*  try {
// if we are restarting (e.g. due to orientation change), we append to the log file instead of overwriting it
            //    captureFile = new PrintWriter(new FileWriter(captureFileName, svcRunning));
            } catch (IOException ex) {
                Log.e(LOG_TAG, ex.getMessage(), ex);
           //     captureStateText = "Capture: " + ex.getMessage();
            }*/
        } else
            captureStateText = "Capture: OFF";
        rate = appPrefs.getInt(
                PREF_SAMPLING_SPEED,
                mSensorManager.SENSOR_DELAY_UI);
        captureStateText += "; rate: " + getRateName(rate);
        //TextView t = (TextView)findViewById( R.id.capturestate );
        //t.setText( captureStateText );
    }

    protected void onStart() {
        super.onStart();
        if (DEBUG)
            Log.d(LOG_TAG, "onStart");
        startService();
    }

    protected void onResume() {
        super.onResume();
        if (DEBUG)
            Log.d(LOG_TAG, "onResume");
        startService();
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (DEBUG)
            Log.d(LOG_TAG, "onSaveInstanceState");
        outState.putBoolean(SERVICESTARTED_KEY, svcRunning);
        if (sensorName != null)
            outState.putString(SENSORNAME_KEY, sensorName);
    }

    protected void onPause() {
        super.onPause();
        if (DEBUG)
            Log.d(LOG_TAG, "onPause");
        stopService();
        startService();
    }

    protected void onStop() {
        super.onStop();
        if (DEBUG)
            Log.d(LOG_TAG, "onStop");
        stopService();
    }

    private void stopService() {
        if (svcRunning == false)
            return;
        if (mSensorManager != null)
            mSensorManager.unregisterListener(this);

        if (sensorServiceWakeLock != null) {
            sensorServiceWakeLock.release();
            sensorServiceWakeLock = null;
            Log.d(LOG_TAG, "PARTIAL_WAKE_LOCK released");
        }
        svcRunning = false;
    }

    private void startService() {
        if (svcRunning)
            return;
        if (sensorName != null) {
            mSensorManager =
                    (SensorManager) getSystemService(SENSOR_SERVICE);
            mSensorManager.registerListener(this,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_UI);

// Obtain partial wakelock so that sampling does not stop even if the device goes to sleep
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            sensorServiceWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorMonitor");
            sensorServiceWakeLock.acquire();
            Log.d(LOG_TAG, "PARTIAL_WAKE_LOCK acquired");
            svcRunning = true;
        }
    }

    private String getRateName(int sensorRate) {
        String result = "N/A";
        switch (sensorRate) {
            case SensorManager.SENSOR_DELAY_UI:
                result = "UI";
                break;
            case SensorManager.SENSOR_DELAY_NORMAL:
                result = "Normal";
                break;
            case SensorManager.SENSOR_DELAY_GAME:
                result = "Game";
                break;
            case SensorManager.SENSOR_DELAY_FASTEST:
                result = "Fastest";
                break;
        }
        return result;
    }

    public void onSensorChanged(SensorEvent sensorEvent) {

    }

    // SensorEventListener
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
