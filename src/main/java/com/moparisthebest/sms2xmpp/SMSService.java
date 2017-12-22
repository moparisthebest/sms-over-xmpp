package com.moparisthebest.sms2xmpp;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import org.openintents.xmpp.AbstractXmppPluginCallback;
import org.openintents.xmpp.XmppError;
import org.openintents.xmpp.util.XmppPluginCallbackApi;
import org.openintents.xmpp.util.XmppServiceApi;
import org.openintents.xmpp.util.XmppServiceConnection;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import static com.moparisthebest.sms2xmpp.Constants.ACCOUNT_KEY;
import static com.moparisthebest.sms2xmpp.Constants.PROVIDER_KEY;
import static org.openintents.xmpp.util.XmppUtils.getSuccess;

public class SMSService extends Service {

    public static final String ECHO_SERVER = "echo.burtrum.org";

    private XmppServiceConnection serviceConnection;

    private String selectedPackage = null, accountJid = null;

    public static final int REQUEST_CODE_SEND_MESSAGE = 9910;
    public static final int REQUEST_CODE_REGISTER_CALLBACK = 9915;
    public static final int REQUEST_CODE_UNREGISTER_CALLBACK = 9920;

    // quick hack for now, better android-ish way?
    public static SMSService instance = null;

    public SMSService() {
        instance = this;
    }

    private final IBinder mBinder = new SMSBinder();

    public class SMSBinder extends Binder {
        public SMSService getService() {
            return SMSService.this;
        }
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        maybeChanged();
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService();
    }

    private void handleError(final XmppError error) {
        final BaseActivity instance = BaseActivity.instance;
        if (instance != null) {
            instance.handleError(error);
        } else {
            Log.e(Constants.TAG, "onError getErrorId:" + error.getErrorId());
            Log.e(Constants.TAG, "onError getMessage:" + error.getMessage());
        }
    }

    private void showToast(final String message) {
        final BaseActivity instance = BaseActivity.instance;
        if (instance != null) {
            instance.showToast(message);
        } else {
            Log.e(Constants.TAG, "showToast:" + message);
        }
    }

    private synchronized void stopService() {
        selectedPackage = null;
        accountJid = null;
        if (serviceConnection != null) {
            serviceConnection.unbindFromService();
            serviceConnection = null;
        }
    }

    public void maybeChanged() {
        final SharedPreferences prefs = getPreferences();
        maybeChanged(prefs.getString(PROVIDER_KEY, null), prefs.getString(ACCOUNT_KEY, null));
    }

    public synchronized void maybeChanged(final String newPackage, final String newJid) {
        if (newPackage == null || newJid == null) {
            stopService();
        } else if (!newPackage.equals(selectedPackage) || !newJid.equals(accountJid)) {
            stopService();
            selectedPackage = newPackage;
            accountJid = newJid;
            // bind to service
            serviceConnection = new XmppServiceConnection(
                    SMSService.this.getApplicationContext(),
                    selectedPackage,
                    new XmppServiceConnection.OnBound() {
                        @Override
                        public void onBound(XmppServiceApi serviceApi) {
                            Log.d(XmppServiceApi.TAG, "onBound!");
                            registerMessageCallback();
                        }

                        @Override
                        public void onError(Exception e) {
                            Log.e(XmppServiceApi.TAG, "exception when binding!", e);
                        }
                    }
            );
            serviceConnection.bindToService();
        }
    }

    public void receivedText(final String fromNumber, final String body, final long timestampMillis) {
        final Intent data = new Intent();
        data.setAction(XmppServiceApi.ACTION_SEND_RAW_XML);
        data.putExtra(XmppServiceApi.EXTRA_ACCOUNT_JID, accountJid);
        data.putExtra(XmppServiceApi.EXTRA_RAW_XML,
                "<message xmlns=\"jabber:client\" to=\"" + fromNumber + "@" + ECHO_SERVER + "\" type=\"normal\" from=\"" + accountJid + "\">\n" +
                        "    <echo xmlns=\"https://code.moparisthebest.com/moparisthebest/xmpp-echo-self\"/>\n" +
                        "    <forwarded xmlns=\"urn:xmpp:forward:0\">\n" +
                        "        <message xmlns=\"jabber:client\" from=\"" + fromNumber + "@" + ECHO_SERVER + "\" type=\"chat\" to=\"" + accountJid + "\">\n" +
                        // todo: add delay here with timestampMillis
                        "            <body>" + body + "</body>\n" +
                        "        </message>\n" +
                        "    </forwarded>\n" +
                        "</message>");

        serviceConnection.getApi().executeApiAsync(data, null, null, new SMSService.MyCallback(false, null, REQUEST_CODE_SEND_MESSAGE));
    }

    public void registerMessageCallback() {
        showToast("registerMessageCallback");
        final Intent data = new Intent();
        data.setAction(XmppServiceApi.ACTION_REGISTER_PLUGIN_CALLBACK);
        data.putExtra(XmppServiceApi.EXTRA_ACCOUNT_JID, accountJid);
        // null means all localpart
        //data.putExtra(XmppServiceApi.EXTRA_JID_LOCAL_PART, localPart);
        data.putExtra(XmppServiceApi.EXTRA_JID_DOMAIN, ECHO_SERVER);

        serviceConnection.getApi().callbackApiAsync(data, pluginCallback, new SMSService.MyCallback(true, null, REQUEST_CODE_REGISTER_CALLBACK));
    }

    public synchronized void unRegisterMessageCallback() {
        accountJid = null;
        final Intent data = new Intent();
        data.setAction(XmppServiceApi.ACTION_UNREGISTER_PLUGIN_CALLBACK);
        data.putExtra(XmppServiceApi.EXTRA_ACCOUNT_JID, accountJid);
        serviceConnection.getApi().callbackApiAsync(data, pluginCallback, new SMSService.MyCallback(true, null, REQUEST_CODE_UNREGISTER_CALLBACK));
    }

    private AbstractXmppPluginCallback pluginCallback = new AbstractXmppPluginCallback() {
        /**
         * This is called by the remote service regularly to tell us about
         * new values.  Note that IPC calls are dispatched through a thread
         * pool running in each process, so the code executing here will
         * NOT be running in our main thread like most other things -- so,
         * to update the UI, we need to use a Handler to hop over there.
         */
        @Override
        public Intent execute(final Intent data, final InputStream inputStream, final OutputStream outputStream) {
            if (XmppPluginCallbackApi.ACTION_NEW_MESSAGE.equals(data.getAction())) {
                final Jid from = Jid.fromString(data.getStringExtra(XmppPluginCallbackApi.EXTRA_MESSAGE_FROM));
                final Jid to = Jid.fromString(data.getStringExtra(XmppPluginCallbackApi.EXTRA_MESSAGE_TO));
                final String body = data.getStringExtra(XmppPluginCallbackApi.EXTRA_MESSAGE_BODY);
                showToast(String.format(Locale.US, "status: %d, from: '%s', to: '%s', body: '%s'",
                        data.getIntExtra(XmppPluginCallbackApi.EXTRA_MESSAGE_STATUS, -1),
                        from, to, body));
                if (accountJid.equals(from.toBareJid().toString()) && ECHO_SERVER.equals(to.getDomainpart())) {
                    // todo: check that this looks like phone number??? I guess SMS just fails for now
                    // if our JID sent it to an account on the echo server, it's an outgoing text, send it...
                    SMSReceiver.sendSms(to.getLocalpart(), body);
                }
                return getSuccess();
            }
            return getSuccess(); // we don't care about anything else for now...
            //return getError(XmppError.INCOMPATIBLE_API_VERSIONS, "action not implemented");
        }
    };

    private class MyCallback implements XmppServiceApi.IXmppCallback {
        boolean returnToCiphertextField;
        ByteArrayOutputStream os;
        int requestCode;

        private MyCallback(boolean returnToCiphertextField, ByteArrayOutputStream os, int requestCode) {
            this.returnToCiphertextField = returnToCiphertextField;
            this.os = os;
            this.requestCode = requestCode;
        }

        @Override
        public void onReturn(Intent result) {
            switch (result.getIntExtra(XmppServiceApi.RESULT_CODE, XmppServiceApi.RESULT_CODE_ERROR)) {
                case XmppServiceApi.RESULT_CODE_SUCCESS: {
                    showToast("RESULT_CODE_SUCCESS");
                    break;
                }
                case XmppServiceApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                    showToast("RESULT_CODE_USER_INTERACTION_REQUIRED");

                    /*
                    // todo: what
                    PendingIntent pi = result.getParcelableExtra(XmppServiceApi.RESULT_INTENT);
                    try {
                        SMSService.this.startIntentSenderFromChild(
                                SMSService.this, pi.getIntentSender(),
                                requestCode, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e(Constants.TAG, "SendIntentException", e);
                    }
                    */
                    break;
                }
                case XmppServiceApi.RESULT_CODE_ERROR: {
                    showToast("RESULT_CODE_ERROR");

                    XmppError error = result.getParcelableExtra(XmppServiceApi.RESULT_ERROR);
                    handleError(error);
                    break;
                }
            }
        }
    }
}

