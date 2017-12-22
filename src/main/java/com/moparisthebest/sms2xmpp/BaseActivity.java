/*
 * Copyright (C) 2012-2014 Dominik Schürmann <dominik@dominikschuermann.de>
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
 */

package com.moparisthebest.sms2xmpp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import org.openintents.xmpp.XmppError;
import org.openintents.xmpp.util.XmppAccountPreference;
import org.openintents.xmpp.util.XmppAppPreference;

import static com.moparisthebest.sms2xmpp.Constants.ACCOUNT_KEY;
import static com.moparisthebest.sms2xmpp.Constants.PROVIDER_KEY;

public class BaseActivity extends PreferenceActivity {

    private XmppAccountPreference accountPreference;
    private XmppAppPreference appPreference;

    // quick hack for now, better android-ish way?
    public static BaseActivity instance = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // load preferences from xml
        addPreferencesFromResource(R.xml.base_preference);

        // find preferences
        appPreference = (XmppAppPreference) findPreference("xmpp_provider_list");
        accountPreference = (XmppAccountPreference) findPreference("xmpp_account");

        accountPreference.setXmppProvider(appPreference.getValue());
        appPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                accountPreference.setXmppProvider((String) newValue);
                showToast(String.format("appPreference changed: '%s'", appPreference.getValue()));
                maybeChanged(appPreference.getValue(), accountPreference.getValue());
                return true;
            }
        });

        accountPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                showToast(String.format("accountPreference changed: '%s'", accountPreference.getValue()));
                maybeChanged(appPreference.getValue(), accountPreference.getValue());
                return true;
            }
        });
        maybeChanged(appPreference.getValue(), accountPreference.getValue());

        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (accountPreference.handleOnActivityResult(requestCode, resultCode, data)) {
            // handled by XmppAccountPreference
            showToast(String.format("onActivityResult picked '%s' and '%s'", appPreference.getValue(), accountPreference.getValue()));
            return;
        }
        // other request codes...
    }

    public void handleError(final XmppError error) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(BaseActivity.this,
                        "onError id:" + error.getErrorId() + "\n\n" + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                Log.e(Constants.TAG, "onError getErrorId:" + error.getErrorId());
                Log.e(Constants.TAG, "onError getMessage:" + error.getMessage());
            }
        });
    }

    public void showToast(final String message) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(BaseActivity.this,
                        message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private synchronized void maybeChanged(final String newPackage, final String newJid) {
        final SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(PROVIDER_KEY, newPackage).apply();
        editor.putString(ACCOUNT_KEY, newJid).apply();
        editor.apply();
        final SMSService instance = SMSService.instance;
        if(instance != null) {
            showToast("SMSService instance running");
            instance.maybeChanged(newPackage, newJid);
        } else {
            showToast("starting SMSService instance");
            Intent mIntentForService = new Intent(getApplicationContext(), SMSService.class);
            mIntentForService.setAction("other");
            getApplicationContext().startService(mIntentForService);
        }
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }
}
