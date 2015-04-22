package com.falldetect2015.android.fallassistant;

/**
 * Created by mjl68 on 4/22/2015.
 */


import android.app.Activity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class faController extends Activity {


    // validating email id
    private boolean isValidEmail(String email) {
        String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
                + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

        Pattern pattern = Pattern.compile(EMAIL_PATTERN);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }
}
