package ray.droid.com.droidcatchnotification.common;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Created by Robson on 04/08/2017.
 */

public class DroidCommon {
    public static String MESSAGE = DroidCommon.getDateTimeFormated() + " the file was created";
    public static final String TAG = "DroidCatchNotification";
    public static final String HISTORY_FILE = "catch_notification_history.txt";
    public static final String TRASH_FILE = "catch_notification_trash.txt";
    public static final String VIEWED_FILE = "catch_notification_viewed.txt";

    public static void ShowListener(Context context) {
        Intent mIntent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(mIntent);
    }

    public static boolean IsNotificationListenerEnabled(Context context) {
        String packageName = context.getPackageName();
        String listeners = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners");

        if (TextUtils.isEmpty(listeners)) {
            return false;
        }

        String[] enabledListeners = listeners.split(":");
        for (String listener : enabledListeners) {
            if (listener.toLowerCase().contains(packageName.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static void AppendLocalHistory(Context context, String message) {
        if (IsRecentDuplicate(context, message)) {
            Log.i(TAG, "Duplicated local history ignored");
            return;
        }

        try (Writer writer = new OutputStreamWriter(
                context.openFileOutput(HISTORY_FILE, Context.MODE_APPEND))) {
            writer.write(message);
            writer.write("\n");
            Log.i(TAG, "Local history updated: " + HISTORY_FILE);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to update local history", ex);
        }
    }

    public static List<String> ReadLocalHistory(Context context) {
        return CompactDuplicateHistory(FilterSystemNoiseHistory(ReadLines(context, HISTORY_FILE)));
    }

    public static List<String> ReadTrashHistory(Context context) {
        return FilterSystemNoiseHistory(ReadLines(context, TRASH_FILE));
    }

    public static Set<String> ReadViewedHistory(Context context) {
        return new HashSet<>(ReadLines(context, VIEWED_FILE));
    }

    public static void MarkHistoryViewed(Context context, String message) {
        if (TextUtils.isEmpty(message) || ReadViewedHistory(context).contains(message)) {
            return;
        }

        AppendLine(context, VIEWED_FILE, message);
    }

    public static boolean MoveLocalHistoryToTrash(Context context, String message) {
        if (!RemoveFirstLine(context, HISTORY_FILE, message)) {
            return false;
        }

        AppendLine(context, TRASH_FILE, message);
        return true;
    }

    public static boolean RestoreTrashHistory(Context context, String message) {
        if (!RemoveFirstLine(context, TRASH_FILE, message)) {
            return false;
        }

        AppendLine(context, HISTORY_FILE, message);
        return true;
    }

    public static boolean ClearTrashHistory(Context context) {
        try (Writer writer = new OutputStreamWriter(
                context.openFileOutput(TRASH_FILE, Context.MODE_PRIVATE))) {
            Log.i(TAG, "Trash history cleared");
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Unable to clear trash history", ex);
            return false;
        }
    }

    private static List<String> ReadLines(Context context, String fileName) {
        List<String> history = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.openFileInput(fileName)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String cleanLine = line.trim();
                if (!cleanLine.isEmpty()) {
                    if (!history.isEmpty() && !IsHistoryLineStart(cleanLine)) {
                        int lastIndex = history.size() - 1;
                        history.set(lastIndex, history.get(lastIndex) + " " + cleanLine);
                    } else {
                        history.add(cleanLine);
                    }
                }
            }
        } catch (FileNotFoundException ex) {
            Log.i(TAG, "Local file does not exist yet: " + fileName);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to read local file: " + fileName, ex);
        }
        return history;
    }

    private static void AppendLine(Context context, String fileName, String message) {
        try (Writer writer = new OutputStreamWriter(
                context.openFileOutput(fileName, Context.MODE_APPEND))) {
            writer.write(message);
            writer.write("\n");
            Log.i(TAG, "Local file updated: " + fileName);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to append local file: " + fileName, ex);
        }
    }

    private static boolean RemoveFirstLine(Context context, String fileName, String message) {
        List<String> lines = ReadLines(context, fileName);
        boolean removed = false;
        List<String> updatedLines = new ArrayList<>();

        for (String line : lines) {
            if (!removed && line.equals(message)) {
                removed = true;
                continue;
            }
            updatedLines.add(line);
        }

        if (!removed) {
            return false;
        }

        try (Writer writer = new OutputStreamWriter(
                context.openFileOutput(fileName, Context.MODE_PRIVATE))) {
            for (String line : updatedLines) {
                writer.write(line);
                writer.write("\n");
            }
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Unable to rewrite local file: " + fileName, ex);
            return false;
        }
    }

    private static boolean IsRecentDuplicate(Context context, String message) {
        List<String> history = ReadLocalHistory(context);
        String messageDate = GetHistoryDate(message);
        String messageContent = GetHistoryContent(message);
        int firstIndex = Math.max(0, history.size() - 10);

        for (int index = history.size() - 1; index >= firstIndex; index--) {
            String savedLine = history.get(index);
            if (messageDate.equals(GetHistoryDate(savedLine))
                    && messageContent.equalsIgnoreCase(GetHistoryContent(savedLine))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> CompactDuplicateHistory(List<String> history) {
        List<String> compactHistory = new ArrayList<>();

        for (String line : history) {
            if (compactHistory.isEmpty()) {
                compactHistory.add(line);
                continue;
            }

            String previousLine = compactHistory.get(compactHistory.size() - 1);
            boolean sameMinute = GetHistoryDate(line).equals(GetHistoryDate(previousLine));
            boolean sameContent = GetHistoryContent(line).equalsIgnoreCase(GetHistoryContent(previousLine));

            if (!sameMinute || !sameContent) {
                compactHistory.add(line);
            }
        }

        return compactHistory;
    }

    private static List<String> FilterSystemNoiseHistory(List<String> history) {
        List<String> filteredHistory = new ArrayList<>();
        for (String line : history) {
            if (!IsSystemLoadingHistoryLine(line)) {
                filteredHistory.add(line);
            }
        }
        return filteredHistory;
    }

    private static boolean IsSystemLoadingHistoryLine(String line) {
        if (line == null) {
            return false;
        }

        String[] parts = line.split("\t", 3);
        if (parts.length < 3) {
            return false;
        }

        String packageName = parts[1].trim().toLowerCase();
        String content = parts[2].trim().replace(".", "").toLowerCase();
        boolean systemPackage = IsSystemPackageName(packageName);

        return systemPackage
                && (content.startsWith("carregando")
                || content.startsWith("carregamento")
                || content.startsWith("loading")
                || content.startsWith("charging")
                || content.startsWith("charge")
                || content.startsWith("cargando"));
    }

    private static boolean IsSystemPackageName(String packageName) {
        return packageName.equals("android")
                || packageName.equals("com.android.systemui")
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

    private static String GetHistoryDate(String line) {
        if (line != null && line.length() >= 16 && line.charAt(2) == '/'
                && line.charAt(5) == '/' && line.charAt(10) == ' ' && line.charAt(13) == ':') {
            return line.substring(0, 16);
        }
        return "";
    }

    private static boolean IsHistoryLineStart(String line) {
        return line != null && line.length() >= 16 && line.charAt(2) == '/'
                && line.charAt(5) == '/' && line.charAt(10) == ' ' && line.charAt(13) == ':';
    }

    private static String GetHistoryContent(String line) {
        if (line == null) {
            return "";
        }
        if (line.length() > 17 && line.charAt(2) == '/' && line.charAt(5) == '/'
                && line.charAt(10) == ' ' && line.charAt(13) == ':') {
            return line.substring(17).trim();
        }
        return line.trim();
    }

    public static String getDateTimeFormated()
    {
        SimpleDateFormat simpleFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm");
        return simpleFormat.format( new Date( System.currentTimeMillis() ));
    }

}
