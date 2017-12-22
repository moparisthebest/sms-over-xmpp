package com.moparisthebest.sms2xmpp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class EventReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent mIntentForService = new Intent(context, SMSService.class);
        if (intent.getAction() != null) {
            mIntentForService.setAction(intent.getAction());
        } else {
            mIntentForService.setAction("other");
        }
        final String action = intent.getAction();
        //if (action.equals("ui") || hasEnabledAccounts(context)) {
        if (true) { // todo: check for setup here
            context.startService(mIntentForService);
        } else {
            Log.d(Constants.TAG, "EventReceiver ignored action " + mIntentForService.getAction());
        }
    }

}
