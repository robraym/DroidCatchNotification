package ray.droid.com.droidcatchnotification.common;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResourceClient;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;


/**
 * Created by Robson on 04/08/2017.
 */

public class DroidCommon {
    public static String MESSAGE = DroidCommon.getDateTimeFormated() + " the file was created";
    public static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    public static final String TAG = "DroidCatchNotification";
    public static final String HISTORY_FILE = "catch_notification_history.txt";
    private static DriveFile driveFile;

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

    public static void TimeSleep(Integer seg) {
        try {
            Thread.sleep(seg);
        } catch (Exception ex) {
        }
    }

    public static DriveId GetDriveID(Context context) {
        String driveId = DroidPreferences.GetString(context, "DriveId");
        if (driveId.isEmpty()) return null;
        else return DriveId.decodeFromString(driveId);
    }

    public static DriveFile GetDriveFile(Context context) {
        if (DroidCommon.driveFile == null || DroidCommon.driveFile.toString().isEmpty()) {
            DriveId driveId = DroidCommon.GetDriveID(context);
            if (driveId == null)
                return null;
            else {
                DroidCommon.driveFile = driveId.asDriveFile();
            }
        }
        return DroidCommon.driveFile;
    }

    public static void SetDriveFile(DriveFile driveFile) {
        DroidCommon.driveFile = driveFile;
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
        List<String> history = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.openFileInput(HISTORY_FILE)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String cleanLine = line.trim();
                if (!cleanLine.isEmpty()) {
                    history.add(cleanLine);
                }
            }
        } catch (FileNotFoundException ex) {
            Log.i(TAG, "Local history file does not exist yet: " + HISTORY_FILE);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to read local history", ex);
        }
        return CompactDuplicateHistory(history);
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

    private static String GetHistoryDate(String line) {
        if (line != null && line.length() >= 16 && line.charAt(2) == '/'
                && line.charAt(5) == '/' && line.charAt(10) == ' ' && line.charAt(13) == ':') {
            return line.substring(0, 16);
        }
        return "";
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

    public static String getEmail(Context context) {
        Pattern emailPattern = Patterns.EMAIL_ADDRESS; // API level 8+
        Account[] accounts = AccountManager.get(context).getAccounts();

        String possibleEmail = "";
        for (Account account : accounts) {
            if (account.type.equalsIgnoreCase("com.google") && emailPattern.matcher(account.name).matches()) {
                possibleEmail = account.name;
                break;
            }
        }

        if (possibleEmail.isEmpty()) {
            for (Account account : accounts) {
                if (emailPattern.matcher(account.name).matches()) {
                    possibleEmail = account.name;
                    break;
                }
            }
        }
        return possibleEmail;
    }

    public static String getAccount(Context context) {
        String account = "padrao";
        try {
            String email = getEmail(context);
            String[] accounts = email.split("@");
            account = accounts[0];
        }catch (Exception ex)
        {
        }
        return account;
    }

    public static String getDateTimeFormated()
    {
        SimpleDateFormat simpleFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm");
        return simpleFormat.format( new Date( System.currentTimeMillis() ));
    }


    public static void showMessage(final Activity activity, String mensagem) {
        AlertDialog.Builder alerta = new AlertDialog.Builder(activity);
        alerta.setMessage(mensagem);
        alerta.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // activity.finish();
            }
        });
        alerta.show();
    }

    public static boolean checkPlayServices(Activity activity) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(activity);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(activity, resultCode, DroidCommon.PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                DroidCommon.showMessage(activity, "Dispositivo não suportado");
                Log.d(DroidCommon.TAG, "This device is not supported.");
                activity.finish();
            }
            return false;
        }
        return true;
    }

    public static String getNameDevice(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return tm.getMmsUserAgent();
        }
        else return "Padrao";
    }



}
