package com.moparisthebest.sms2xmpp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.ArrayList;

public class SMSReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final Bundle bundle = intent.getExtras();
        if (bundle == null)
            return;
        final Object[] pdus = (Object[]) bundle.get("pdus");
        for (int i = 0; i < pdus.length; ++i) {
            final SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdus[i]);
            final String from = msg.getOriginatingAddress();
            final String message = msg.getMessageBody();
            if (from == null || message == null)
                continue;
            // it IS a balance message, get to parsing...
            Log.d(Constants.TAG, "SMSReceiver: " + from + ": " + message);
            try {
                final SMSService instance = SMSService.instance;
                if (instance != null)
                    instance.receivedText(from, message, msg.getTimestampMillis());
            } catch (Exception e) {
                Log.e(Constants.TAG, "SMSReceiver: ", e);
            }
        }
    }

    public static void sendSms(final String toNumber, final String body) {
        Log.d(Constants.TAG, String.format("SMSReceiver: sending to '%s' body: '%s'", toNumber, body));
        // todo: better android way?
        new Thread(new Runnable() {
            public void run() {
                final SmsManager sms = SmsManager.getDefault();
                final ArrayList<String> parts = sms.divideMessage(body);
                if (parts.size() > 1)
                    sms.sendMultipartTextMessage(toNumber, null, parts, null, null);
                else
                    sms.sendTextMessage(toNumber, null, body, null, null);
            }
        }).start();
    }
}
