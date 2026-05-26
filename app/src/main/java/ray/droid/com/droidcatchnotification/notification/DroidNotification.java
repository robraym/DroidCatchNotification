package ray.droid.com.droidcatchnotification.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

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
    String sourcePackageName;
    String imageFileName;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.i(TAG, "onNotificationPosted");
        Context context = getBaseContext();
        getNotificationKitKat(sbn, context);


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

    private boolean isDuplicateNotification() {
        long now = System.currentTimeMillis();
        String fingerprint = (sourcePackageName + "|" + tit + "|" + msg).trim().toLowerCase();

        if (fingerprint.equals(lastFingerprint) && now - lastFingerprintTime < DUPLICATE_WINDOW_MS) {
            return true;
        }

        lastFingerprint = fingerprint;
        lastFingerprintTime = now;
        return false;
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

        Bundle extras = mStatusBarNotification.getNotification().extras;
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

        imageFileName = saveNotificationImage(context, extras);

        if (isBackgroundCheckingNotification()) {
            tit = "";
            msg = "";
            return;
        }

        if (isNoiseNotification(pack, extras)) {
            Log.i(TAG, "System/loading notification ignored: " + pack);
            tit = "";
            msg = "";
        }
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
