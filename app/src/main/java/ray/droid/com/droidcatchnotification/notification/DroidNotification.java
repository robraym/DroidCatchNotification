package ray.droid.com.droidcatchnotification.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

import ray.droid.com.droidcatchnotification.common.DroidCommon;

import static ray.droid.com.droidcatchnotification.common.DroidCommon.TAG;


/**
 * Created by Robson on 03/02/2016.
 */

public class DroidNotification extends DroidBaseNotification {
    private static final long DUPLICATE_WINDOW_MS = 15000;
    private static final int MAX_TRACKED_NOTIFICATION_KEYS = 100;
    private static final String STATE_PREFS = "catch_notification_state";
    private static final String PREF_CHARGING_ACTIVE = "charging_active";
    private static String lastFingerprint = "";
    private static long lastFingerprintTime = 0;
    private static final Map<String, String> lastContentByNotificationKey = new HashMap<>();

    CharSequence tit;
    String msg;
    String sourcePackageName;
    String imageFileName;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.i(TAG, "onNotificationPosted");
        Context context = getBaseContext();
        if (handleChargingNotificationPosted(sbn, context)) {
            return;
        }

        getNotificationKitKat(sbn, context);


        if (tit != null && !tit.toString().isEmpty()) {

            try {
                if (isDuplicateNotification(sbn)) {
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

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Context context = getBaseContext();
        if (!isChargingNotification(sbn)) {
            return;
        }

        if (!isChargingActive(context)) {
            return;
        }

        setChargingActive(context, false);
        appendChargingEvent(context, sbn.getPackageName(), "Carregamento desconectado");
    }

    private String getDataNotification() {
        return DroidCommon.getDateTimeFormated()
                + "\t" + sanitizeHistoryField(sourcePackageName)
                + "\t" + sanitizeHistoryField(imageFileName)
                + "\t" + sanitizeHistoryField(tit) + " " + sanitizeHistoryField(msg);
    }

    private String sanitizeHistoryField(Object value) {
        if (value == null) {
            return "";
        }

        return value.toString()
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll(" +", " ")
                .trim();
    }

    private boolean isDuplicateNotification(StatusBarNotification sbn) {
        long now = System.currentTimeMillis();
        String contentFingerprint = (sourcePackageName + "|" + tit + "|" + msg).trim().toLowerCase();
        String notificationKey = sbn == null ? "" : sanitizeHistoryField(sbn.getKey());

        if (!TextUtils.isEmpty(notificationKey)) {
            String previousContent = lastContentByNotificationKey.get(notificationKey);
            if (contentFingerprint.equals(previousContent)) {
                return true;
            }

            if (lastContentByNotificationKey.size() > MAX_TRACKED_NOTIFICATION_KEYS) {
                lastContentByNotificationKey.clear();
            }
            lastContentByNotificationKey.put(notificationKey, contentFingerprint);
        }

        if (contentFingerprint.equals(lastFingerprint) && now - lastFingerprintTime < DUPLICATE_WINDOW_MS) {
            return true;
        }

        lastFingerprint = contentFingerprint;
        lastFingerprintTime = now;
        return false;
    }

    private boolean handleChargingNotificationPosted(StatusBarNotification sbn, Context context) {
        if (!isChargingNotification(sbn)) {
            return false;
        }

        if (!isChargingActive(context)) {
            setChargingActive(context, true);
            appendChargingEvent(context, sbn.getPackageName(), "Carregamento conectado");
        } else {
            Log.i(TAG, "Charging update ignored");
        }

        return true;
    }

    private void appendChargingEvent(Context context, String packageName, String message) {
        sourcePackageName = packageName;
        imageFileName = "";
        tit = message;
        msg = "";
        DroidCommon.AppendLocalHistory(context, getDataNotification());
    }

    private boolean isChargingActive(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_CHARGING_ACTIVE, false);
    }

    private void setChargingActive(Context context, boolean active) {
        SharedPreferences.Editor editor = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit();
        editor.putBoolean(PREF_CHARGING_ACTIVE, active);
        editor.apply();
    }

    private boolean isChargingNotification(StatusBarNotification sbn) {
        if (sbn == null || !isSystemPackage(sbn.getPackageName())) {
            return false;
        }

        String tag = sanitizeHistoryField(sbn.getTag()).toLowerCase();
        String groupKey = sanitizeHistoryField(sbn.getGroupKey()).toLowerCase();
        if (tag.contains("charging") || tag.contains("charge") || groupKey.contains("charging")) {
            return true;
        }

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) {
            return false;
        }

        Bundle extras = notification.extras;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence titleBig = extras.getCharSequence("android.title.big");
        String content = (sanitizeHistoryField(title) + " "
                + sanitizeHistoryField(text) + " "
                + sanitizeHistoryField(bigText) + " "
                + sanitizeHistoryField(titleBig)).toLowerCase();

        return isChargingText(content);
    }

    private boolean isChargingText(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        return text.contains("carregando")
                || text.contains("carregamento")
                || text.contains("charging")
                || text.contains("cargando");
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void getNotificationKitKat(StatusBarNotification mStatusBarNotification, Context context) {
        String pack = mStatusBarNotification.getPackageName();// Package Name
        sourcePackageName = pack;
        imageFileName = "";
        msg = "";
        tit = "";
        if (pack.equals(getPackageName())) {
            return;
        }

        Notification notification = mStatusBarNotification.getNotification();
        if (notification == null) {
            return;
        }

        Bundle extras = notification.extras;
        if (extras == null) {
            return;
        }

        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        tit = title == null ? "" : title;

        CharSequence[] descArray = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (descArray != null && descArray.length > 0) {
            msg = descArray[descArray.length - 1].toString();
        }

        if (msg.isEmpty()) {
            CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (bigText != null) {
                msg = bigText.toString();
            }
        }

        if (msg.isEmpty()) {
            CharSequence desc = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (desc != null) {
                msg = desc.toString();
            }
        }

        if (msg.isEmpty()) {
            CharSequence summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT);
            if (summary != null) {
                msg = summary.toString();
            }
        }

        if (isBackgroundCheckingNotification()) {
            tit = "";
            msg = "";
            return;
        }

        if (isNoiseNotification(pack, extras)) {
            Log.i(TAG, "System/loading notification ignored: " + pack);
            tit = "";
            msg = "";
            return;
        }

        saveSourceAppIcon(context, pack, notification);
        imageFileName = saveNotificationImage(context, extras);
    }

    private void saveSourceAppIcon(Context context, String packageName, Notification notification) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        File iconDir = new File(context.getFilesDir(), "notification_icons");
        if (!iconDir.exists() && !iconDir.mkdirs()) {
            return;
        }

        File iconFile = new File(iconDir, getSourceIconFileName(packageName));
        if (iconFile.exists()) {
            return;
        }

        Drawable icon = getApplicationIconDrawable(packageName);
        if (icon == null) {
            icon = getNotificationSmallIconDrawable(notification);
        }
        if (icon == null) {
            return;
        }

        Bitmap bitmap = drawableToBitmap(icon, Math.max(48, Math.round(48 * getResources().getDisplayMetrics().density)));
        try (FileOutputStream outputStream = new FileOutputStream(iconFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream);
        } catch (Exception ex) {
            Log.d(TAG, "Unable to save source app icon: " + packageName);
        }
    }

    private Drawable getApplicationIconDrawable(String packageName) {
        try {
            return getPackageManager().getApplicationIcon(packageName);
        } catch (Exception ex) {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private Drawable getNotificationSmallIconDrawable(Notification notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || notification == null
                || notification.getSmallIcon() == null) {
            return null;
        }

        try {
            return notification.getSmallIcon().loadDrawable(this);
        } catch (Exception ex) {
            return null;
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable, int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private String getSourceIconFileName(String packageName) {
        return packageName.replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
    }

    private String saveNotificationImage(Context context, Bundle extras) {
        Bitmap bitmap = getNotificationImageBitmap(extras);
        if (bitmap == null) {
            return "";
        }

        File mediaDir = new File(context.getFilesDir(), "notification_media");
        if (!mediaDir.exists() && !mediaDir.mkdirs()) {
            return "";
        }

        String fileName = "notification_" + System.currentTimeMillis() + ".png";
        File file = new File(mediaDir, fileName);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream);
            return fileName;
        } catch (Exception ex) {
            Log.e(TAG, "Unable to save notification image", ex);
            return "";
        }
    }

    private Bitmap getNotificationImageBitmap(Bundle extras) {
        Bitmap picture = getBitmapExtra(extras, Notification.EXTRA_PICTURE);
        if (picture != null) {
            return picture;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Bitmap pictureIcon = getIconBitmapExtra(extras, "android.pictureIcon");
            if (pictureIcon != null) {
                return pictureIcon;
            }
        }

        return null;
    }

    private Bitmap getBitmapExtra(Bundle extras, String key) {
        try {
            Parcelable value = extras.getParcelable(key);
            if (value instanceof Bitmap) {
                return (Bitmap) value;
            }
        } catch (Exception ex) {
            Log.d(TAG, "Unable to read bitmap extra: " + key);
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private Bitmap getIconBitmapExtra(Bundle extras, String key) {
        try {
            Parcelable value = extras.getParcelable(key);
            if (value instanceof Icon) {
                Drawable drawable = ((Icon) value).loadDrawable(this);
                if (drawable == null) {
                    return null;
                }
                Bitmap bitmap = Bitmap.createBitmap(
                        Math.max(1, drawable.getIntrinsicWidth()),
                        Math.max(1, drawable.getIntrinsicHeight()),
                        Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                return bitmap;
            }
        } catch (Exception ex) {
            Log.d(TAG, "Unable to read icon extra: " + key);
        }
        return null;
    }

    private boolean isNoiseNotification(String packageName, Bundle extras) {
        if (isSystemPackage(packageName) && (isLoadingText(tit) || isLoadingText(msg))) {
            return true;
        }

        if (!isSystemPackage(packageName)) {
            return false;
        }

        int progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
        boolean progressIndeterminate = extras.getBoolean(
                Notification.EXTRA_PROGRESS_INDETERMINATE,
                false);

        return progressMax > 0 || progressIndeterminate;
    }

    private boolean isBackgroundCheckingNotification() {
        String content = (sanitizeHistoryField(tit) + " " + sanitizeHistoryField(msg)).toLowerCase();
        return content.contains("procurando novas mensagens")
                || content.contains("checking for new messages");
    }

    private boolean isLoadingText(Object value) {
        if (value == null) {
            return false;
        }

        String text = value.toString()
                .trim()
                .replace(".", "")
                .toLowerCase();

        return text.equals("carregando")
                || text.startsWith("carregando ")
                || text.startsWith("carregando(")
                || text.equals("cargando")
                || text.startsWith("cargando ")
                || text.startsWith("cargando(")
                || text.equals("charging")
                || text.startsWith("charging ")
                || text.startsWith("charging(")
                || text.equals("loading")
                || text.startsWith("loading ")
                || text.startsWith("loading(")
                || text.equals("loading…")
                || text.equals("carregamento")
                || text.startsWith("carregamento ")
                || text.equals("charge")
                || text.startsWith("charge ");
    }

    private boolean isSystemPackage(String packageName) {
        if (packageName == null) {
            return false;
        }

        return "android".equals(packageName)
                || "com.android.systemui".equals(packageName)
                || packageName.startsWith("com.android.")
                || packageName.startsWith("com.samsung.android.")
                || packageName.startsWith("com.miui.")
                || packageName.startsWith("com.xiaomi.")
                || packageName.startsWith("com.motorola.")
                || packageName.startsWith("com.oplus.")
                || packageName.startsWith("com.coloros.")
                || packageName.startsWith("com.oneplus.")
                || packageName.startsWith("com.huawei.systemmanager");
    }

}
