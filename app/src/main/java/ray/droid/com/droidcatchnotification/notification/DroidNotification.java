package ray.droid.com.droidcatchnotification.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import ray.droid.com.droidcatchnotification.common.DroidCommon;

import static ray.droid.com.droidcatchnotification.common.DroidCommon.TAG;


/**
 * Created by Robson on 03/02/2016.
 */

public class DroidNotification extends DroidBaseNotification {
    private static final long DUPLICATE_WINDOW_MS = 15000;
    private static String lastFingerprint = "";
    private static long lastFingerprintTime = 0;

    CharSequence tit;
    String msg;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.i(TAG, "onNotificationPosted");
        Context context = getBaseContext();
        getNotificationKitKat(sbn);


        if (tit != null && !tit.toString().isEmpty()) {

            try {
                if (isDuplicateNotification()) {
                    Log.i(TAG, "Duplicated notification ignored");
                    return;
                }
                DroidCommon.MESSAGE = getDataNotification();
                DroidCommon.AppendLocalHistory(context, DroidCommon.MESSAGE);
            } catch (Exception ex) {
                Log.d(TAG, "onNotificationPosted " + ex.getMessage());
            }
        }


    }

    private String getDataNotification() {
        return DroidCommon.getDateTimeFormated() + " " + tit + " " + msg;
    }

    private boolean isDuplicateNotification() {
        long now = System.currentTimeMillis();
        String fingerprint = (tit + "|" + msg).trim().toLowerCase();

        if (fingerprint.equals(lastFingerprint) && now - lastFingerprintTime < DUPLICATE_WINDOW_MS) {
            return true;
        }

        lastFingerprint = fingerprint;
        lastFingerprintTime = now;
        return false;
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void getNotificationKitKat(StatusBarNotification mStatusBarNotification) {
        String pack = mStatusBarNotification.getPackageName();// Package Name
        msg = "";
        tit = "";
        if (pack.contains("com.whatsapp") ||
                pack.contains("com.android.mms") ||
                pack.contains("com.facebook.orca")) {
            Bundle extras = mStatusBarNotification.getNotification().extras;
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE); // Title
            tit = title == null ? "" : title;
            CharSequence desc = extras.getCharSequence(Notification.EXTRA_TEXT); // / Description
            try {
                Bundle bigExtras = mStatusBarNotification.getNotification().extras;
                CharSequence[] descArray = bigExtras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                if (descArray != null && descArray.length > 0) {
                    msg = descArray[descArray.length - 1].toString();
                }

            } catch (Exception ex) {

            }
            if (msg.isEmpty() && desc != null) {
                msg = desc.toString();
            }

            if (msg.equals("procurando novas mensagens") || msg.equals("Checking for new messages")) {
                tit = "";
                msg = "";
            }

        }
    }

}
