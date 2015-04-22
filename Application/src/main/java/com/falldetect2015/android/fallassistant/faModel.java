package com.falldetect2015.android.fallassistant;
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
 *
 * ========================================================================
 *
 * (Model) faModel controls the sensor communication.
 * - Uses SensorManager,TriggerEventListener for sensor data
 * - <Todo>Query APIs to verify HW functionality
 * - <Todo>Performs wait based
 * - <Todo>Saves/Restore settings from phone (if available, defaults if not)
 *
 */

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.widget.TextView;
import android.widget.Toast;

class TriggerListener extends TriggerEventListener {
    private Context mContext;
    private TextView mTextView;

    TriggerListener(Context context, TextView textView) {
        mContext = context;
        mTextView = textView;
    }
    @Override
    public void onTrigger(TriggerEvent event) {
        if (event.values[0] == 1) {
            mTextView.append("Significant Motion Detected\n");
            mTextView.append("Significant Motion Trigger Now Disabled\n");
        }
        // Sensor is auto disabled.
    }
}


public class faModel extends Activity {
    private static faModel instance = null;
    private static Boolean instanceCreated = null;
    private static Boolean eventRegistered = false;
    private static int sensorMethod = 0; /* 0 == eventTrigger, 1 == sensorMonitor */
    private static int timeToWait = 45; /* Default == 60 seconds */
    private static Boolean waitAfterFall = true; /* Default == true */
    private final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    //private  Sensor accel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    okio.Buffer chunk = new okio.Buffer();
    private long timestamp = System.currentTimeMillis()/1000;
    private SensorManager mSensorManager;
    private Sensor mSigMotion;
    private TriggerListener mListener;
    private TextView mTextView;

    protected faModel() {
    }

    public static faModel getInstance() {
        if(instanceCreated == null) {
            instance = new faModel();
            instanceCreated = true;
        }
        return instance;
    }

    public static Boolean getStatus() {
        if (eventRegistered == true) {
            return eventRegistered;
        }
        return false;
    }

    public static Boolean getWait() {
        return waitAfterFall;
    }

    public void setWait(Boolean value) {
        this.waitAfterFall = value;
        if (waitAfterFall == false) {
            setWaitSecs(0);
        }
        ;
    }

    public static int getWaitSecs() {
        return timeToWait;
    }

    public void setWaitSecs(int value) {
        timeToWait = value;
    }

    public faModel getInstance(int waitTime) {
        if (instanceCreated == null) {
            instance = new faModel();
            instanceCreated = true;

            if (eventRegistered == true) startMonitor();
            if (waitTime > 0) {
                setWait(true);
                setWaitSecs(waitTime);
            } else {
                setWait(false);
                setWaitSecs(0);
            }
        }

        return instance;
    }

    public void startMonitor()  {
        try {
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            mSigMotion = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);

            mListener = new TriggerListener(this, mTextView);
            if (mSigMotion == null) {
                mTextView.append("No Significant Motion Sensor Available\n");
            } else {
                eventRegistered = true;
                NewMessageNotification mNotification = new NewMessageNotification();
                mNotification.notify(this,
                        "@String/notification_text_service_up",
                        0);
            }
        } catch (Exception e) {
            Toast.makeText(this, "@String/service_fail", Toast.LENGTH_LONG).show();
        }
    }

    protected void stopMonitor() {
        // Call disable to ensure that the trigger request has been canceled.
        if (eventRegistered == true) {
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            mSensorManager.cancelTriggerSensor(mListener, mSigMotion);
            mSensorManager.cancelTriggerSensor(mListener, mSigMotion);
            eventRegistered = false;
        }
    }
}

