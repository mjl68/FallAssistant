/*
* Copyright 2013 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.falldetect2015.android.fallassistant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

/**
 * A simple launcher activity offering access to the individual samples in this project.
 */
public class MainActivity extends Activity implements AdapterView.OnItemClickListener {
    private Sample[] mSamples;
    private GridView mGridView;
    private Boolean mSamplesSwitch;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.falldetect2015.android.fallassistant.R.layout.activity_main);
        mSamplesSwitch = false;
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
    }

    @Override
    public void onItemClick(AdapterView<?> container, View view, int position, long id) {
        if (position > 0) {
            startActivity(mSamples[position].intent);
        } else {
            if (mSamplesSwitch == false) {
                mSamples[0].titleResId = com.falldetect2015.android.fallassistant.R.string.nav_1a_titl;
                mSamples[0].descriptionResId = com.falldetect2015.android.fallassistant.R.string.nav_1a_desc;
                mSamplesSwitch = true;
                mGridView.invalidateViews();
            } else{
                mSamples[0].titleResId = com.falldetect2015.android.fallassistant.R.string.nav_1_titl;
                mSamples[0].descriptionResId = com.falldetect2015.android.fallassistant.R.string.nav_1_desc;
                mSamplesSwitch = false;
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

        private Sample(int titleResId, int descriptionResId, Intent intent) {
            this.intent = intent;
            this.titleResId = titleResId;
            this.descriptionResId = descriptionResId;
        }

        private Sample(int titleResId, int descriptionResId,
                Class<? extends Activity> activityClass) {
            this(titleResId, descriptionResId,
                    new Intent(MainActivity.this, activityClass));
        }
    }
}
