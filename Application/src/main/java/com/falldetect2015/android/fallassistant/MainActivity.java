/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================================
 * (View) MainActivity
 * - Displays Home screen to users
 * - Calls to faModel object to start/stop sensor service
 * - <Todo>Calls to <Controller> object to perform actual "SMS / Email"
 * http://examples.javacodegeeks.com/android/core/hardware/sensor/android-accelerometer-example/
 */

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class MainActivity extends Activity implements AdapterView.OnItemClickListener, SensorEventListener {
    public static final boolean DEBUG = true;
    public static final String PREF_FILE = "prefs";
    public static final String PREF_SERVICE_STATE = "serviceState";
    public static final String PREF_SAMPLING_SPEED = "samplingSpeed";
    final static int fields[] = {};
    static final String LOG_TAG = "FallAssistant.";
    static final String SERVICESTARTED_KEY = "serviceStarted";
    private Sample[] mSamples;
    private GridView mGridView;
    private Boolean mSamplesSwitch = false;
    private Boolean svcRunning;
    private PowerManager.WakeLock samplingWakeLock;
    private String sensorName = null;
    private boolean captureState = false;
    private int rate = SensorManager.SENSOR_DELAY_UI;
    private SensorManager sensorManager;
    private PrintWriter captureFile;
    private long baseMillisec = -1L;
    private long samplesPerSec = 0;
    private String captureStateText;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.falldetect2015.android.fallassistant.R.layout.activity_main);
        mSamplesSwitch = false;
        svcRunning = false;

        // Prepare list of samples in this dashboard.
        mSamples = new Sample[]{
                new Sample(com.falldetect2015.android.fallassistant.R.string.nav_1_titl, com.falldetect2015.android.fallassistant.R.string.nav_1_desc,
                        NavigationDrawerActivity.class),
                new Sample(com.falldetect2015.android.fallassistant.R.string.nav_2_titl, com.falldetect2015.android.fallassistant.R.string.nav_2_desc,
                        NavigationDrawerActivity.class),
                new Sample(com.falldetect2015.android.fallassistant.R.string.nav_3_titl, com.falldetect2015.android.fallassistant.R.string.nav_3_desc,
                        NavigationDrawerActivity.class),
        };

        // Prepare the GridView
        mGridView = (GridView) findViewById(android.R.id.list);
        mGridView.setAdapter(new SampleAdapter());
        mGridView.setOnItemClickListener(this);
        //String sensorName = sensor.getName();
        Intent i = getIntent();
        if (i != null) {
            sensorName = i.getStringExtra("sensorname");
            if (DEBUG)
                Log.d(LOG_TAG, "sensorName: " + sensorName);
        }
        if (savedInstanceState != null) {
            svcRunning = false; // savedInstanceState.getBoolean( svcRunning, false );
            //sensorName = savedInstanceState.getString( SENSORNAME_KEY );
        }
        SharedPreferences appPrefs = getSharedPreferences(
                PREF_FILE,
                MODE_PRIVATE);
        Boolean svcState = appPrefs.getBoolean(PREF_SERVICE_STATE, false);
        String svcStateText = null;
        if (captureState) {
            File captureFileName = new File("/sdcard", "capture.csv");
            svcStateText = "Capture: " + captureFileName.getAbsolutePath();
            try {
// if we are restarting (e.g. due to orientation change), we append to the log file instead of overwriting it
                captureFile = new PrintWriter(new FileWriter(captureFileName, svcRunning));
            } catch (IOException ex) {
                Log.e(LOG_TAG, ex.getMessage(), ex);
                captureStateText = "Capture: " + ex.getMessage();
            }
        } else
            captureStateText = "Capture: OFF";
        rate = appPrefs.getInt(
                PREF_SAMPLING_SPEED,
                SensorManager.SENSOR_DELAY_UI);
        svcStateText += "; rate: " + getRateName(rate);
    }

    protected void onStart() {
        super.onStart();
        if (DEBUG)
            Log.d(LOG_TAG, "onStart");
        if (svcRunning != null && svcRunning == true) {
            startSampling();
        }
    }

    protected void onResume() {
        super.onResume();
        if (DEBUG)
            Log.d(LOG_TAG, "onResume");
        if (svcRunning) {
            stopSampling();
            startSampling();
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (DEBUG)
            Log.d(LOG_TAG, "onSaveInstanceState");
        outState.putBoolean(SERVICESTARTED_KEY, svcRunning);
    }

    protected void onPause() {
        super.onPause();
        if (DEBUG)
            Log.d(LOG_TAG, "onPause");
        if (svcRunning) {
            stopSampling();
            startSampling();
        }
    }

    protected void onStop() {
        super.onStop();
        if (DEBUG)
            Log.d(LOG_TAG, "onStop");
        if (svcRunning) {
            stopSampling();
            startSampling();
        }
    }

    private void stopSampling() {
        if (!svcRunning)
            return;
        if (sensorManager != null)
            sensorManager.unregisterListener(this);
        if (captureFile != null) {
            captureFile.close();
            captureFile = null;
        }
        if (samplingWakeLock != null) {
            samplingWakeLock.release();
            samplingWakeLock = null;
            Log.d(LOG_TAG, "PARTIAL_WAKE_LOCK released");
        }
        svcRunning = false;
    }

    private void startSampling() {
        if (svcRunning)
            return;
        if (sensorName != null) {
            sensorManager =
                    (SensorManager) getSystemService(SENSOR_SERVICE);
            List<Sensor> fallassistant = sensorManager.getSensorList(Sensor.TYPE_ALL);
            Sensor ourSensor = null;
            for (int i = 0; i < fallassistant.size(); ++i)
                if (sensorName.equals(fallassistant.get(i).getName())) {
                    ourSensor = fallassistant.get(i);
                    break;
                }
            if (ourSensor != null) {
                baseMillisec = -1L;
                sensorManager.registerListener(
                        this,
                        ourSensor,
                        rate);
            }
// Obtain partial wakelock so that sampling does not stop even if the device goes to sleep
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            samplingWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorMonitor");
            samplingWakeLock.acquire();
            Log.d(LOG_TAG, "PARTIAL_WAKE_LOCK acquired");

        }
        svcRunning = true;
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
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < sensorEvent.values.length; ++i) {
            if (i > 0)
                b.append(" , ");
            b.append(Float.toString(sensorEvent.values[i]));
        }
        if (DEBUG)
            Log.d(LOG_TAG, "onSensorChanged: [" + b + "]");
        if (captureFile != null) {
            captureFile.print(Long.toString(sensorEvent.timestamp));
            for (int i = 0; i < sensorEvent.values.length; ++i) {
                captureFile.print(",");
                captureFile.print(Float.toString(sensorEvent.values[i]));
            }
            captureFile.println();
        }
        long currentMillisec = System.currentTimeMillis();
        if (baseMillisec < 0) {
            baseMillisec = currentMillisec;
            samplesPerSec = 0;
        } else if ((currentMillisec - baseMillisec) < 1000L)
            ++samplesPerSec;
        else {
            int count = sensorEvent.values.length < fields.length ?
                    sensorEvent.values.length :
                    fields.length;
            samplesPerSec = 1;
            baseMillisec = currentMillisec;
        }
    }

    // SensorEventListener
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onItemClick(AdapterView<?> container, View view, int position, long id) {
        Boolean serviceState = svcRunning;
        int i = 200 + 1;
        if (position > 0) {
            startActivity(mSamples[position].intent);
        } else {
            if (svcRunning == false) {
                mSamples[0].titleResId = com.falldetect2015.android.fallassistant.R.string.nav_1a_titl;
                mSamples[0].descriptionResId = com.falldetect2015.android.fallassistant.R.string.nav_1a_desc;
                svcRunning = true;
                // Start Service, init vars, etc

                mGridView.invalidateViews();
            } else {
                mSamples[0].titleResId = com.falldetect2015.android.fallassistant.R.string.nav_1_titl;
                mSamples[0].descriptionResId = com.falldetect2015.android.fallassistant.R.string.nav_1_desc;
                svcRunning = false;
                // Stop Service, zero vars, etc
                mGridView.invalidateViews();
            }
        }
    }

    private class SampleAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mSamples.length;
        }

        @Override
        public Object getItem(int position) {
            return mSamples[position];
        }

        @Override
        public long getItemId(int position) {
            return mSamples[position].hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup container) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(com.falldetect2015.android.fallassistant.R.layout.sample_dashboard_item,
                        container, false);
            }

            ((TextView) convertView.findViewById(android.R.id.text1)).setText(
                    mSamples[position].titleResId);
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(
                    mSamples[position].descriptionResId);
            return convertView;
        }
    }

    private class Sample {
        int titleResId;
        int descriptionResId;
        Intent intent;

        private Sample(int titleResId, int descriptionResId,
                       Class<? extends Activity> activityClass) {
            this(titleResId, descriptionResId,
                    new Intent(MainActivity.this, activityClass));
        }

        private Sample(int titleResId, int descriptionResId, Intent intent) {
            this.intent = intent;
            this.titleResId = titleResId;
            this.descriptionResId = descriptionResId;
        }
    }
}
