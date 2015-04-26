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
 * - <Todo>Calls to perform actual "Email"
 * http://examples.javacodegeeks.com/android/core/hardware/sensor/android-accelerometer-example/
 */

package com.falldetect2015.android.fallassistant;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class MainActivity extends Activity implements AdapterView.OnItemClickListener, TextToSpeech.OnInitListener {
    public static final boolean DEBUG = true;
    public static final String PREF_FILE = "prefs";
    public static final String PREF_SERVICE_STATE = "serviceState";
    public static final String PREF_SAMPLING_SPEED = "samplingSpeed";
    public static final String PREF_WAIT_SECS = "waitSeconds";
    private final static int fields[] = {};
    private static final String LOG_TAG = "FallAssistant.";
    private static final String SERVICESTARTED_KEY = "serviceStarted";
    public static Boolean svcRunning;
    private final int REQ_CODE_SPEECH_INPUT = 100;
    public int rate = SensorManager.SENSOR_DELAY_UI;
    private String PREF_CONTACT_NUMBER = "5126269115";
    private int defWaitSecs = 20;
    private int waitSeconds = defWaitSecs;
    private float normalThreshold = 15;
    private float fallenThreshold = 2;
    private float[] lastSensorValues = new float[3];
    private float[] mGravity;
    private float mAccel;
    private float mAccelCurrent;
    private float mAccelLast;
    private Sample[] mSamples;
    private GridView mGridView;
    private Boolean mSamplesSwitch = false;
    private PowerManager.WakeLock samplingWakeLock;
    private String sensorName = null;
    private boolean captureState = false;
    private SensorManager mSensorManager;
    private PrintWriter captureFile;
    private long baseMillisec = -1L;
    private long samplesPerSec = 0;
    private String captureStateText;
    private Boolean fallDetected = false;
    private Boolean noMovement = true;
    private TextToSpeech engine;
    private double pitch = 1.0;
    private double speed = 1.0;
    private LocationManager mlocManager = null;
    private LocationListener mLocationListener = null;
    private String myGeocodeLocation = null;
    private Location currentLocation;
    private double currentLattitude;
    private double currentLongitude;
    private String response = "";
    private Boolean needhelp = false;
    private Boolean exittimer = false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.falldetect2015.android.fallassistant.R.layout.activity_main);
        mSamplesSwitch = false;
        svcRunning = false;
        engine = new TextToSpeech(this, this);
        stopSampling();
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
        waitSeconds = appPrefs.getInt(PREF_WAIT_SECS, defWaitSecs);
        String svcStateText = null;
        if (captureState == true) {
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
    }

    protected void onStart() {
        super.onStart();
        if (DEBUG)
            Log.d(LOG_TAG, "onStart");
        if (svcRunning != null && svcRunning == true) {
            startService();
        }
    }

    protected void onResume() {
        super.onResume();
        if (DEBUG)
            Log.d(LOG_TAG, "onResume");
        if (svcRunning == true) {
            reStartService();
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
        if (svcRunning == true) {
            reStartService();
        }
    }

    protected void onStop() {
        super.onStop();
        if (DEBUG)
            Log.d(LOG_TAG, "onStop");
        if (svcRunning == true) {
            reStartService();
        }
    }

    /**
     * Receiving speech input
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    //set string to text goes here ex. txtSpeechInput.setText(result.get(0));
                    response = result.get(0);
                    if((response.contains("help"))||(response.contains("yes"))){
                        needhelp = true;
                    }
                }
                break;
            }
        }
    }

    private void stopService() {
        if( svcRunning ) {
            Intent i = new Intent();
            i.setClassName( "com.falldetect2015.android.fallassistant","com.falldetect2015.android.fallassistant.faSensorService" );
            stopService( i );

        }
        svcRunning = true;
    }

    private void startService() {
        stopService();
        if (svcRunning) {
            startService();
            return;
        }
        mSensorManager =
                (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        String sensorName = sensor.getName();
        Intent i = new Intent();
        i.setClassName("com.falldetect2015.android.fallassistant", "com.falldetect2015.android.fallassistant.faSensorService");
        i.putExtra("sensorname", sensorName);
        startService(i);
        svcRunning = true;
    }

    private void reStartService() {
        stopService();
        startService();
    }

    public void detectMovement() {
        noMovement = true;
        new CountDownTimer(waitSeconds, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                // do something after 1s
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
                    sendSmsByManager();
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
        }.start();
    }

    // SensorEventListener
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void sendSmsByManager() {
        try {
            // Get the default instance of the SmsManager
            if (mlocManager == null) {
                mlocManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            }
            mLocationListener = new myLocationListener();
            mlocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
            //getAddressFromLocation(mLastKownLocation, this, new GeocoderHandler());

            Geocoder gc = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> addresses = gc.getFromLocation(currentLattitude,
                        currentLongitude, 1);
                StringBuilder sb = new StringBuilder();
                if (addresses.size() > 0) {
                    Address address = addresses.get(0);
                    for (int i = 0; i < address.getMaxAddressLineIndex(); i++)
                        sb.append(address.getAddressLine(i)).append("\n");
                    sb.append(address.getLocality()).append("\n");
                    sb.append(address.getPostalCode()).append("\n");
                    sb.append(address.getCountryName());
                    myGeocodeLocation = sb.toString();
                }
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "Cant connect to Geocoder",
                        Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
            /*
            if (myGeocodeLocation == null) {
                showAlert("GeoCode", myGeocodeLocation);
                myGeocodeLocation = String.valueOf(currentLattitude) + " " + String.valueOf(currentLongitude);
            }*/
            String helpMessage = "I have fallen and need help, sent by fall assistant app";
            //+ " http://maps.google.com/maps?q=" + URLEncoder.encode(myGeocodeLocation, "utf-8")
            SmsManager smsManager = SmsManager.getDefault();
            //speech();
            //promptSpeechInput();
            smsManager.sendTextMessage(PREF_CONTACT_NUMBER,
                    null,
                    helpMessage,
                    null,
                    null);
            Toast.makeText(getApplicationContext(), "Your contacts have been notified",
                    Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            showAlert("GeoCode", myGeocodeLocation);
            Toast.makeText(getApplicationContext(), "Your sms has failed...",
                    Toast.LENGTH_LONG).show();
            ex.printStackTrace();
            mlocManager.removeUpdates(mLocationListener);
        }
        mlocManager.removeUpdates(mLocationListener);
    }

    public void showAlert(String title, String alertMessage) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                this);
        // set title
        alertDialogBuilder.setTitle("Fall Alert " + title);
        // set dialog message
        alertDialogBuilder
                .setMessage(alertMessage)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // if this button is clicked, close
                        // current activity
                        MainActivity.this.finish();
                    }
                });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    public void help() {
        speech();
        promptSpeechInput();
        if (needhelp) {
            sendSmsByManager();
        } else {
            exittimer = false;
            new CountDownTimer(waitSeconds, 1000) {

                @Override
                public void onTick(long millisUntilFinished) {
                    // do something after 1s
                    if (exittimer == true) {
                        cancel();
                    }
                }

                @Override
                public void onFinish() {
                    // do something end times 5s
                    if (exittimer == false) {
                        sendSmsByManager();
                    }
                }
            }.start();
        }
    }

    private void speech() {
        engine.speak("Do you Need help?", TextToSpeech.QUEUE_FLUSH, null);
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> container, View view, int position, long id) {
        Boolean serviceState = svcRunning;
        int x = 0;
        Boolean pos = position == 2;
        if (position == 1) {
            startActivity(mSamples[position].intent);
        } else {
            if (position == 2) {
                // position == 2, sending SMS
                help();
            }
            if (position == 0) {
                if (svcRunning == false) {
                    mSamples[0].titleResId = com.falldetect2015.android.fallassistant.R.string.nav_1a_titl;
                    mSamples[0].descriptionResId = com.falldetect2015.android.fallassistant.R.string.nav_1a_desc;
                    startService();
                    svcRunning = true;
                    mGridView.invalidateViews();
                } else {
                    mSamples[0].titleResId = com.falldetect2015.android.fallassistant.R.string.nav_1_titl;
                    mSamples[0].descriptionResId = com.falldetect2015.android.fallassistant.R.string.nav_1_desc;
                    stopService();
                    svcRunning = false;
                    mGridView.invalidateViews();
                }
            }
        }
    }

    public void showToast(CharSequence message) {
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(getApplicationContext(), message, duration);
        toast.show();
    }

    @Override
    public void onInit(int status) {
        Log.d("Speech", "OnInit - Status [" + status + "]");

        if (status == TextToSpeech.SUCCESS) {
            Log.d("Speech", "Success!");
            engine.setLanguage(Locale.US);
        }
    }

    public class myLocationListener implements LocationListener

    {
        @Override
        public void onLocationChanged(Location loc) {
            currentLocation = loc;
            loc.getLatitude();
            loc.getLongitude();
            showToast("Location now: " + Double.toString(loc.getLatitude()) + ", " + Double.toString(loc.getLongitude()));
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {
            Toast.makeText(getApplicationContext(),
                    "Gps Enabled",
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onProviderDisabled(String provider) {
            Toast.makeText(getApplicationContext(),
                    "Gps Disabled",
                    Toast.LENGTH_SHORT).show();
        }


    }/* End of Class MyLocationListener */

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

    class SensorItem {
        private Sensor sensor;
        private boolean sampling;

        SensorItem(Sensor sensor) {
            this.sensor = sensor;
            this.sampling = false;
        }

        public String getSensorName() {
            return sensor.getName();
        }

        Sensor getSensor() {
            return sensor;
        }

        boolean getSampling() {
            return sampling;
        }

        void setSampling(boolean sampling) {
            this.sampling = sampling;
        }
    }
}
