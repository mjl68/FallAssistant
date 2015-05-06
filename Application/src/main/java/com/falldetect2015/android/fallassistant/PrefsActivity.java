package com.falldetect2015.android.fallassistant;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


public class PrefsActivity extends Activity implements View.OnClickListener {
    private EditText phone, hMsg;
    private Button oSave, oReset;
    private SeekBar sensorMax;
    private TextView titleTV;
    private SharedPreferences sp;
    private SharedPreferences.Editor edit;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prefs);
        phone = (EditText) findViewById(R.id.phoneNum);
        hMsg = (EditText) findViewById(R.id.helpMsg);
        sensorMax = (SeekBar) findViewById(R.id.snsrMaxAcc);
        titleTV = (TextView) findViewById(R.id.settingsTV);
        titleTV.setText(getString(R.string.options_message));
        titleTV.invalidate();
        //sensorMax.setMax(40);
        sensorMax.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                // TODO Auto-generated method stub

                Log.d(MainActivity.LOG_TAG, "sensorMax Current Progress:" + progress
                        + System.getProperty("line.separator")
                        + "Progress changed by User:" + fromUser);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
                //Log.d(MainActivity.LOG_TAG, "sensorMax Tracking Started...");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
                //Log.d(MainActivity.LOG_TAG, "sensorMax... Tracking Stopped");
            }
        });

        sp = getSharedPreferences(MainActivity.PREF_FILE, MODE_PRIVATE);
        edit = sp.edit();

        loadPrefs();

        oSave = (Button) findViewById(R.id.btnOpSave);
        oSave.setOnClickListener(this);
        oReset = (Button) findViewById(R.id.btnOptReset);
        oReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.phoneNum = "5126469115";
                MainActivity.helpMsg = getString(R.string.fall_message);
                loadPrefs();
            }
        });
    }

    private void loadPrefs() {
        String strVal = null;
        int intVal;
        float floatVal = 0;

        try {
            sp.getString(MainActivity.PREF_CONTACT_NUMBER, strVal);
            if (strVal == null) strVal = MainActivity.phoneNum;
            phone.setText(strVal);
            strVal = null;
            Log.d(MainActivity.LOG_TAG, "LoadPrefs loaded: " + strVal);
            if (strVal == null) strVal = MainActivity.helpMsg;
            sp.getString(MainActivity.PREF_HELP_MSG, strVal);
            hMsg.setText(strVal);
            Log.d(MainActivity.LOG_TAG, "LoadPrefs loaded: " + strVal);
            sp.getFloat(MainActivity.PREF_SENSOR_MAX, floatVal);
            sensorMax.setProgress(40 - Math.round((floatVal - 15) * 4));
            Log.d(MainActivity.LOG_TAG, "LoadPrefs loaded: " + floatVal + " set " + sensorMax.getProgress());
            phone.invalidate();
            hMsg.invalidate();
            sensorMax.invalidate();
        } catch (Exception ex) {
            if (MainActivity.DEBUG)
                Log.d(MainActivity.LOG_TAG, "LoadPrefs Error: " + ex.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_prefs, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void savePrefs(String key, float value) {

        edit.putFloat(key, value);
        edit.commit();
    }


    private void savePrefs(String key, String value) {
        Log.d(MainActivity.LOG_TAG, "SavePrefs loaded: " + key + ": " + value);

        edit.putString(key, value.trim());
        edit.commit();
        String val = "";
        sp.getString(MainActivity.PREF_CONTACT_NUMBER, val);
        Toast.makeText(this, val, Toast.LENGTH_SHORT);
    }


    @Override
    public void onClick(View v) {
        MainActivity.phoneNum = phone.getText().toString();
        MainActivity.helpMsg = hMsg.getText().toString();
        faSensorService.normalThreshold = new Float(Math.abs(15 + 0.4 * (sensorMax.getProgress() - 40)));
        savePrefs(MainActivity.PREF_CONTACT_NUMBER, phone.getText().toString());
        savePrefs(MainActivity.PREF_HELP_MSG, hMsg.getText().toString());
        savePrefs(MainActivity.PREF_SENSOR_MAX, faSensorService.normalThreshold);
    }
}
