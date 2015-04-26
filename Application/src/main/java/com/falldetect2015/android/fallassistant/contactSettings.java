package aexp.sensors;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;

public class contactSettings extends Activity {
    public static final boolean DEBUG = true;
    public static final String PREF_FILE = "prefs";
    public static final String PREF_SERVICE_STATE = "serviceState";
    public static final String PREF_SAMPLING_SPEED = "samplingSpeed";
    public static final String PREF_WAIT_SECS = "waitSeconds";
    private int defWaitSecs = 20;
    private SpinnerData spinnerData[];

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.falldetect2015.android.fallassistant.R.layout.settings);
        SharedPreferences appPrefs = getSharedPreferences(
                PREF_FILE,
                MODE_PRIVATE);
        boolean captureState =
                appPrefs.getBoolean(PREF_SERVICE_STATE, false);
        if (captureState) {
            CheckBox cb = (CheckBox) findViewById(com.falldetect2015.android.fallassistant.R.id.contact_options_cb);
            cb.setChecked(true);
        }
        int speedState = appPrefs.getInt(PREF_WAIT_SECS, defWaitSecs);
        Spinner spinner = (Spinner) findViewById(
                com.falldetect2015.android.fallassistant.R.id.settings_wait_timeout_spinner);
        spinnerData = new SpinnerData[14];
        for (int i = 1; i < 16; i++) {
            spinnerData[i] = new SpinnerData(Integer.toString(i * 5), i * 5);
        }
        ArrayAdapter<SpinnerData> adapter =
                new ArrayAdapter<SpinnerData>(
                        this,
                        android.R.layout.simple_spinner_item,
                        spinnerData);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        for (int i = 0; i < spinnerData.length; ++i)
            if (speedState == spinnerData[i].getValue()) {
                spinner.setSelection(i);
                break;
            }
    }

    protected void onPause() {
        super.onPause();
        SharedPreferences appPrefs = getSharedPreferences(
                PREF_FILE,
                MODE_PRIVATE);
        SharedPreferences.Editor ed = appPrefs.edit();
        CheckBox cb = (CheckBox) findViewById(com.falldetect2015.android.fallassistant.R.id.contact_options_cb);
        boolean captureState = cb.isChecked();
        Spinner spinner = (Spinner) findViewById(
                com.falldetect2015.android.fallassistant.R.id.settings_wait_timeout_spinner);
        SpinnerData sd = (SpinnerData) spinner.getSelectedItem();
        ed.putBoolean(PREF_SERVICE_STATE, captureState);
        ed.putInt(PREF_WAIT_SECS, sd.getValue());
        ed.commit();
    }

    class SpinnerData {
        String name;
        int value;

        SpinnerData(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String toString() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }
}
