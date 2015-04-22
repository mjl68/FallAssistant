package com.falldetect2015.android.fallassistant;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.hardware.Sensor;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import com.falldetect2015.android.fallassistant.R;

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
            mTextView.append("Sig Motion Detect\n");
            mTextView.append("Sig Motion Disable\n");
        }
        // Sensor is auto disabled.
    }
}


public class faController extends Activity {
    private static faController instance = null;
    private static Boolean instanceCreated = null;
    private static Boolean eventRegistered = false;
    private static int sensorMethod = 0; /* 0 == eventTrigger, 1 == sensorMonitor */
    private static int timeToWait = 45; /* Default == 60 seconds */
    private static Boolean waitAfterFall = true; /* Default == true */
    private final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    private long timestamp = System.currentTimeMillis()/1000;
    private SensorManager mSensorManager;
    private Sensor mSigMotion;
    private TriggerListener mListener;
    private TextView mTextView;
    //private  Sensor accel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    okio.Buffer chunk = new okio.Buffer();

    protected faController() {
    }

    public static faController getInstance() {
        if(instanceCreated == null) {
            instance = new faController();
            instanceCreated = true;
        }
        return instance;
    }

    public faController getInstance(int waitTime) {
        if(instanceCreated == null) {
            instance = new faController();
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


    public static Boolean getStatus() {
        if(eventRegistered  == true) {
            return eventRegistered;
        }
        return false;
    }

    public static Boolean getWait() {
        return waitAfterFall;
    }

    public static int getWaitSecs() {
        return timeToWait;
    }

    public void setWait(Boolean value) {
        this.waitAfterFall = value;
        if (waitAfterFall == false) {
            setWaitSecs(0);
        };
    }

    public void setWaitSecs(int value) {
        timeToWait = value;
    }

    public void startMonitor()  {
        try {
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            mSigMotion = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);

            mListener = new TriggerListener(this, mTextView);
            if (mSigMotion == null) {
                mTextView.append("No Significant Motion Sensor Available\n");
            } else {eventRegistered = true;}
        } catch (Exception e) {
            System.out.println("Sensor Service exception: " + e);
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

