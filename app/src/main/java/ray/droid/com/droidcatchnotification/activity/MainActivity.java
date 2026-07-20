package ray.droid.com.droidcatchnotification.activity;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ray.droid.com.droidcatchnotification.R;
import ray.droid.com.droidcatchnotification.common.DroidCommon;

public class MainActivity extends AppCompatActivity {
    private static final long AUTO_REFRESH_INTERVAL_MS = 1500;

    private Context context;
    private TextView textStatus;
    private TextView textHistoryCount;
    private TextView textEmptyHistory;
    private LinearLayout historyContainer;
    private TextView buttonNotifications;
    private TextView buttonTrash;
    private TextView buttonClearTrash;
    private EditText editSearch;
    private TextView buttonSearchClear;
    private final Handler historyHandler = new Handler();
    private final Set<String> expandedDays = new HashSet<>();
    private final Map<String, Integer> sourceColorCache = new HashMap<>();
    private Set<String> viewedHistory = new HashSet<>();
    private boolean autoRefreshRunning;
    private boolean showingTrash;
    private boolean waitingForNotificationPermission;
    private boolean notificationPermissionDeclined;
    private boolean notificationPermissionPromptShown;
    private String searchQuery = "";
    private final Runnable historyRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            renderHistory();
            if (autoRefreshRunning) {
                historyHandler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        textStatus = findViewById(R.id.textStatus);
        textHistoryCount = findViewById(R.id.textHistoryCount);
        textEmptyHistory = findViewById(R.id.textEmptyHistory);
        historyContainer = findViewById(R.id.historyContainer);
        buttonNotifications = findViewById(R.id.buttonNotifications);
        buttonTrash = findViewById(R.id.buttonTrash);
        buttonClearTrash = findViewById(R.id.buttonClearTrash);
        editSearch = findViewById(R.id.editSearch);
        buttonSearchClear = findViewById(R.id.buttonSearchClear);

        buttonNotifications.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNotificationPermissionDialog();
            }
        });

        buttonTrash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showingTrash = !showingTrash;
                renderHistory();
            }
        });

        buttonClearTrash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showClearTrashDialog();
            }
        });

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                searchQuery = charSequence.toString().trim();
                renderHistory();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        buttonSearchClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editSearch.setText("");
            }
        });

        registerSystemBackCallback();
        renderHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationPermissionReturnState();
        startAutoRefresh();
        renderHistory();
        maybeShowNotificationPermissionDialog();
    }

    @Override
    protected void onPause() {
        stopAutoRefresh();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (!handleBackNavigation()) {
            super.onBackPressed();
        }
    }

    private boolean handleBackNavigation() {
        if (!showingTrash) {
            return false;
        }

        showingTrash = false;
        renderHistory();
        return true;
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private void registerSystemBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        if (!handleBackNavigation()) {
                            finish();
                        }
                    }
                });
    }

    private void startAutoRefresh() {
        if (autoRefreshRunning) {
            return;
        }

        autoRefreshRunning = true;
        historyHandler.postDelayed(historyRefreshRunnable, AUTO_REFRESH_INTERVAL_MS);
    }

    private void stopAutoRefresh() {
        autoRefreshRunning = false;
        historyHandler.removeCallbacks(historyRefreshRunnable);
    }

    private void renderHistory() {
        if (textStatus == null || textHistoryCount == null || historyContainer == null) {
            return;
        }

        boolean notificationReady = DroidCommon.IsNotificationListenerEnabled(context);
        List<String> localHistory = DroidCommon.ReadLocalHistory(context);
        List<String> trashHistory = DroidCommon.ReadTrashHistory(context);
        viewedHistory = DroidCommon.ReadViewedHistory(context);
        List<String> history = showingTrash ? trashHistory : localHistory;
        List<String> visibleHistory = filterHistory(history);
        boolean searchActive = !TextUtils.isEmpty(searchQuery);
        int total = history.size();
        int visibleTotal = visibleHistory.size();
        boolean notificationMissing = !showingTrash && !notificationReady;
        boolean permissionEmptyState = notificationMissing && visibleTotal == 0 && !searchActive;

        if (showingTrash || notificationReady) {
            textStatus.setVisibility(View.GONE);
            buttonNotifications.setVisibility(View.GONE);
            notificationPermissionDeclined = false;
        } else {
            textStatus.setText(notificationPermissionDeclined
                    ? R.string.notification_permission_declined
                    : R.string.history_status_permission_missing);
            textStatus.setVisibility(permissionEmptyState ? View.GONE : View.VISIBLE);
            buttonNotifications.setVisibility(View.VISIBLE);
        }

        if (showingTrash) {
            buttonTrash.setText(R.string.history_back);
        } else {
            buttonTrash.setText(getString(R.string.history_open_trash, trashHistory.size()));
        }
        buttonClearTrash.setVisibility(showingTrash && !trashHistory.isEmpty() ? View.VISIBLE : View.GONE);

        if (searchActive) {
            textHistoryCount.setText(getResources().getQuantityString(
                    R.plurals.search_results, visibleTotal, visibleTotal));
        } else if (showingTrash) {
            textHistoryCount.setText(getResources().getQuantityString(
                    R.plurals.trash_count_title, total, total));
            textEmptyHistory.setText(R.string.trash_empty);
        } else {
            textHistoryCount.setText(getResources().getQuantityString(
                    R.plurals.history_count, total, total));
            textEmptyHistory.setText(notificationMissing
                    ? R.string.history_empty_permission_missing
                    : R.string.history_empty);
        }

        if (searchActive) {
            textEmptyHistory.setText(R.string.search_empty);
        }
        buttonSearchClear.setVisibility(searchActive ? View.VISIBLE : View.GONE);

        historyContainer.removeAllViews();
        textEmptyHistory.setVisibility(visibleTotal == 0 ? View.VISIBLE : View.GONE);

        List<HistoryGroup> groups = groupHistory(visibleHistory);
        for (HistoryGroup group : groups) {
            historyContainer.addView(createDayHeader(group));
            if (searchActive || expandedDays.contains(group.day)) {
                for (HistoryText item : group.items) {
                    historyContainer.addView(createHistoryItem(item));
                }
            }
        }
    }

    private List<String> filterHistory(List<String> history) {
        if (TextUtils.isEmpty(searchQuery)) {
            return history;
        }

        List<String> filteredHistory = new ArrayList<>();
        String query = searchQuery.toLowerCase();
        for (String line : history) {
            if (line.toLowerCase().contains(query)) {
                filteredHistory.add(line);
            }
        }
        return filteredHistory;
    }

    private List<HistoryGroup> groupHistory(List<String> history) {
        Map<String, HistoryGroup> groupedHistory = new LinkedHashMap<>();

        for (int index = history.size() - 1; index >= 0; index--) {
            HistoryText item = parseHistoryLine(history.get(index));
            HistoryGroup group = groupedHistory.get(item.day);
            if (group == null) {
                group = new HistoryGroup(item.day);
                groupedHistory.put(item.day, group);
            }
            group.items.add(item);
            if (!showingTrash && !item.viewed) {
                group.unreadCount++;
            }
        }

        return new ArrayList<>(groupedHistory.values());
    }

    private View createDayHeader(final HistoryGroup group) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        header.setFocusable(true);
        header.setPadding(dp(2), dp(8), dp(2), dp(8));

        boolean expanded = expandedDays.contains(group.day);

        TextView marker = new TextView(context);
        marker.setText(expanded ? "-" : "+");
        marker.setTextColor(getResources().getColor(R.color.one_ui_blue));
        marker.setTextSize(16);
        marker.setGravity(Gravity.CENTER);
        header.addView(marker, new LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView day = new TextView(context);
        day.setText(group.day);
        day.setTextColor(getResources().getColor(R.color.one_ui_text_primary));
        day.setTextSize(14);
        day.setSingleLine(true);
        LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1);
        header.addView(day, dayParams);

        TextView count = new TextView(context);
        count.setText(getGroupCountText(group));
        count.setTextColor(getResources().getColor(group.unreadCount > 0
                ? R.color.one_ui_blue
                : R.color.one_ui_text_secondary));
        count.setTextSize(12);
        count.setSingleLine(true);
        header.addView(count);

        ImageView groupAction = createHistoryActionButton(showingTrash);
        groupAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (showingTrash) {
                    showRestoreGroupDialog(group);
                } else {
                    showTrashGroupDialog(group);
                }
            }
        });
        LinearLayout.LayoutParams groupActionParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        groupActionParams.setMargins(dp(8), 0, 0, 0);
        header.addView(groupAction, groupActionParams);

        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (expandedDays.contains(group.day)) {
                    expandedDays.remove(group.day);
                } else {
                    expandedDays.add(group.day);
                }
                renderHistory();
            }
        });

        return header;
    }

    private String getGroupCountText(HistoryGroup group) {
        String totalText = getResources().getQuantityString(
                R.plurals.history_group_count, group.items.size(), group.items.size());
        if (group.unreadCount == 0) {
            return totalText;
        }

        String unreadText = getResources().getQuantityString(
                R.plurals.history_group_unread_count, group.unreadCount, group.unreadCount);
        return totalText + " • " + unreadText;
    }

    private View createHistoryItem(HistoryText historyText) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        View.OnClickListener openSourceListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSourceApp(historyText);
            }
        };
        wrapper.setOnClickListener(openSourceListener);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapper.setLayoutParams(wrapperParams);

        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.TOP);
        item.setPadding(dp(1), dp(3), dp(1), dp(3));
        item.setOnClickListener(openSourceListener);
        wrapper.addView(item);

        boolean unread = !showingTrash && !historyText.viewed;
        View unreadDot = new View(context);
        unreadDot.setBackgroundResource(R.drawable.one_ui_unread_dot);
        unreadDot.setVisibility(unread ? View.VISIBLE : View.INVISIBLE);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotParams.setMargins(0, dp(4), dp(5), 0);
        item.addView(unreadDot, dotParams);

        LinearLayout metaColumn = new LinearLayout(context);
        metaColumn.setOrientation(LinearLayout.VERTICAL);
        metaColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        metaColumn.setOnClickListener(openSourceListener);
        item.addView(metaColumn, new LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.WRAP_CONTENT));

        String sourceLabel = getSourceAppLabel(historyText.packageName);
        Drawable sourceIcon = getSourceAppIcon(historyText.packageName);
        if (sourceIcon != null) {
            ImageView source = new ImageView(context);
            source.setImageDrawable(sourceIcon);
            source.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            source.setBackgroundResource(R.drawable.one_ui_icon_background);
            source.setPadding(dp(3), dp(3), dp(3), dp(3));
            source.setContentDescription(sourceLabel);
            source.setOnClickListener(openSourceListener);
            LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            sourceParams.setMargins(0, 0, 0, dp(2));
            metaColumn.addView(source, sourceParams);
        } else if (!TextUtils.isEmpty(sourceLabel)) {
            TextView source = new TextView(context);
            source.setText(sourceLabel.substring(0, 1).toUpperCase());
            source.setTextColor(getSourceAppColor(historyText.packageName, unread));
            source.setTextSize(10);
            source.setGravity(Gravity.CENTER);
            source.setBackgroundResource(R.drawable.one_ui_icon_background);
            source.setIncludeFontPadding(false);
            source.setSingleLine(true);
            source.setOnClickListener(openSourceListener);
            LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            sourceParams.setMargins(0, 0, 0, dp(2));
            metaColumn.addView(source, sourceParams);
        }

        TextView date = new TextView(context);
        date.setText(historyText.time);
        date.setTextColor(getResources().getColor(R.color.one_ui_text_secondary));
        date.setTextSize(9);
        date.setGravity(Gravity.CENTER);
        date.setIncludeFontPadding(false);
        date.setSingleLine(true);
        date.setOnClickListener(openSourceListener);
        metaColumn.addView(date, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(context);
        message.setText(historyText.message);
        message.setTextColor(getResources().getColor(unread
                ? R.color.one_ui_text_primary
                : R.color.one_ui_text_secondary));
        message.setTextSize(13);
        message.setIncludeFontPadding(false);
        message.setSingleLine(false);
        message.setMaxLines(2);
        message.setEllipsize(TextUtils.TruncateAt.END);
        message.setLineSpacing(0, 1);
        message.setOnClickListener(openSourceListener);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1);
        messageParams.setMargins(dp(2), 0, 0, 0);
        item.addView(message, messageParams);

        Bitmap previewBitmap = getNotificationImage(historyText.imageFileName);
        if (previewBitmap != null) {
            ImageView imagePreview = new ImageView(context);
            imagePreview.setImageBitmap(previewBitmap);
            imagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imagePreview.setBackgroundResource(R.drawable.one_ui_card);
            imagePreview.setClickable(true);
            imagePreview.setFocusable(true);
            imagePreview.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    markHistoryViewed(historyText);
                    showImagePreviewDialog(historyText);
                    renderHistory();
                }
            });
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(54), dp(54));
            imageParams.setMargins(dp(8), 0, 0, 0);
            item.addView(imagePreview, imageParams);
        }

        ImageView itemAction = createHistoryActionButton(showingTrash);
        itemAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (showingTrash) {
                    showRestoreDialog(historyText);
                } else {
                    showTrashDialog(historyText);
                }
            }
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        actionParams.setMargins(dp(6), 0, 0, 0);
        item.addView(itemAction, actionParams);

        View divider = new View(context);
        divider.setBackgroundColor(getResources().getColor(R.color.one_ui_stroke));
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))));

        return wrapper;
    }

    private ImageView createHistoryActionButton(boolean restoreAction) {
        ImageView action = new ImageView(context);
        action.setImageResource(restoreAction ? R.drawable.ic_restore_24 : R.drawable.ic_trash_24);
        action.setColorFilter(getResources().getColor(restoreAction
                ? R.color.one_ui_blue
                : R.color.one_ui_red));
        action.setBackgroundResource(R.drawable.one_ui_icon_background);
        action.setPadding(dp(7), dp(7), dp(7), dp(7));
        action.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        action.setClickable(true);
        action.setFocusable(true);
        action.setContentDescription(getString(restoreAction
                ? R.string.action_restore_notification
                : R.string.action_send_to_trash));
        return action;
    }

    private Bitmap getNotificationImage(String imageFileName) {
        if (TextUtils.isEmpty(imageFileName)) {
            return null;
        }

        File imageFile = new File(new File(getFilesDir(), "notification_media"), imageFileName);
        if (!imageFile.exists()) {
            return null;
        }

        return BitmapFactory.decodeFile(imageFile.getAbsolutePath());
    }

    private void showImagePreviewDialog(HistoryText historyText) {
        Bitmap bitmap = getNotificationImage(historyText.imageFileName);
        if (bitmap == null) {
            Toast.makeText(this, R.string.notification_image_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.one_ui_card);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));

        ImageView image = new ImageView(context);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(image, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView openApp = createDialogButton(R.string.notification_image_open_app, true);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.gravity = Gravity.RIGHT;
        buttonParams.setMargins(0, dp(12), 0, 0);
        content.addView(openApp, buttonParams);
        openApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                openSourceApp(historyText);
            }
        });

        dialog.setView(content);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }
            }
        });
        dialog.show();
    }

    private void openSourceApp(HistoryText historyText) {
        markHistoryViewed(historyText);

        List<String> packageCandidates = getSourcePackageCandidates(historyText);
        for (String packageName : packageCandidates) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
                return;
            }
        }

        Toast.makeText(this, R.string.open_source_not_available, Toast.LENGTH_SHORT).show();
        renderHistory();
    }

    private void markHistoryViewed(HistoryText historyText) {
        if (!showingTrash && !historyText.viewed) {
            DroidCommon.MarkHistoryViewed(context, historyText.rawLine);
            viewedHistory.add(historyText.rawLine);
        }
    }

    private String getSourceAppLabel(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return "";
        }

        if (isSystemUiPackage(packageName)) {
            return "Sistema";
        }
        if (packageName.contains("whatsapp")) {
            return "WhatsApp";
        }
        if (packageName.contains("messaging") || packageName.contains("mms")) {
            return "Mensagens";
        }
        if (packageName.contains("gmail")) {
            return "Gmail";
        }
        if (packageName.contains("instagram")) {
            return "Instagram";
        }
        if (packageName.contains("facebook.katana")) {
            return "Facebook";
        }
        if (packageName.contains("facebook.orca")) {
            return "Messenger";
        }
        if (packageName.contains("telegram")) {
            return "Telegram";
        }
        if (packageName.contains("nu.production")) {
            return "Nubank";
        }
        if (packageName.contains("aliexpress") || packageName.contains("alibaba")) {
            return "AliExpress";
        }
        if (packageName.contains("shopee")) {
            return "Shopee";
        }
        if (packageName.contains("mercadolibre")) {
            return "Mercado Livre";
        }
        if (packageName.contains("amazon")) {
            return "Amazon";
        }
        if (packageName.contains("ifood")) {
            return "iFood";
        }
        if (packageName.contains("luizalabs") || packageName.contains("magalu")) {
            return "Magalu";
        }

        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(applicationInfo);
            if (label != null && !TextUtils.isEmpty(label.toString())) {
                return label.toString();
            }
        } catch (Exception ex) {
            return "";
        }

        return "";
    }

    private Drawable getSourceAppIcon(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }

        Drawable cachedIcon = getCachedSourceAppIcon(packageName);
        if (cachedIcon != null) {
            return cachedIcon;
        }

        try {
            return getPackageManager().getApplicationIcon(packageName);
        } catch (Exception ex) {
            return null;
        }
    }

    private Drawable getCachedSourceAppIcon(String packageName) {
        File iconFile = new File(new File(getFilesDir(), "notification_icons"),
                getSourceIconFileName(packageName));
        if (!iconFile.exists()) {
            return null;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
        if (bitmap == null) {
            return null;
        }

        return new BitmapDrawable(getResources(), bitmap);
    }

    private String getSourceIconFileName(String packageName) {
        return packageName.replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
    }

    private int getSourceAppColor(String packageName, boolean unread) {
        if (TextUtils.isEmpty(packageName)) {
            return getResources().getColor(unread
                    ? R.color.one_ui_blue
                    : R.color.one_ui_text_secondary);
        }

        if (isSystemUiPackage(packageName)) {
            return Color.parseColor("#4C8DFF");
        }

        Integer cachedColor = sourceColorCache.get(packageName);
        if (cachedColor != null) {
            return cachedColor;
        }

        Integer iconColor = getDominantIconColor(packageName);
        if (iconColor != null) {
            sourceColorCache.put(packageName, iconColor);
            return iconColor;
        }

        if (packageName.contains("whatsapp")) {
            return Color.parseColor("#25D366");
        }
        if (packageName.contains("messaging") || packageName.contains("mms")) {
            return Color.parseColor("#4C8DFF");
        }
        if (packageName.contains("gmail")) {
            return Color.parseColor("#EA4335");
        }
        if (packageName.contains("instagram")) {
            return Color.parseColor("#E4405F");
        }
        if (packageName.contains("facebook.katana")) {
            return Color.parseColor("#1877F2");
        }
        if (packageName.contains("facebook.orca")) {
            return Color.parseColor("#0084FF");
        }
        if (packageName.contains("telegram")) {
            return Color.parseColor("#229ED9");
        }
        if (packageName.contains("nu.production")) {
            return Color.parseColor("#8A05BE");
        }
        if (packageName.contains("aliexpress") || packageName.contains("alibaba")) {
            return Color.parseColor("#E62E04");
        }
        if (packageName.contains("shopee")) {
            return Color.parseColor("#EE4D2D");
        }
        if (packageName.contains("mercadolibre")) {
            return Color.parseColor("#FFE600");
        }
        if (packageName.contains("amazon")) {
            return Color.parseColor("#FF9900");
        }
        if (packageName.contains("ifood")) {
            return Color.parseColor("#EA1D2C");
        }
        if (packageName.contains("luizalabs") || packageName.contains("magalu")) {
            return Color.parseColor("#0086FF");
        }

        return getResources().getColor(unread
                ? R.color.one_ui_blue
                : R.color.one_ui_text_secondary);
    }

    private boolean isSystemUiPackage(String packageName) {
        return packageName.equals("android")
                || packageName.equals("com.android.systemui")
                || packageName.equals("com.samsung.android.app.cocktailbarservice")
                || packageName.equals("com.samsung.android.oneconnect")
                || packageName.startsWith("com.samsung.android.systemui");
    }

    private Integer getDominantIconColor(String packageName) {
        try {
            Drawable icon = getPackageManager().getApplicationIcon(packageName);
            Bitmap bitmap = Bitmap.createBitmap(dp(32), dp(32), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            icon.draw(canvas);

            Map<Integer, ColorBucket> buckets = new HashMap<>();
            for (int y = 0; y < bitmap.getHeight(); y += 2) {
                for (int x = 0; x < bitmap.getWidth(); x += 2) {
                    int color = bitmap.getPixel(x, y);
                    int alpha = Color.alpha(color);
                    if (alpha < 160 || isWeakIconColor(color)) {
                        continue;
                    }

                    int red = quantizeColor(Color.red(color));
                    int green = quantizeColor(Color.green(color));
                    int blue = quantizeColor(Color.blue(color));
                    int key = Color.rgb(red, green, blue);
                    ColorBucket bucket = buckets.get(key);
                    if (bucket == null) {
                        bucket = new ColorBucket(key);
                        buckets.put(key, bucket);
                    }
                    bucket.count++;
                }
            }

            ColorBucket bestBucket = null;
            for (ColorBucket bucket : buckets.values()) {
                if (bestBucket == null || bucket.score() > bestBucket.score()) {
                    bestBucket = bucket;
                }
            }

            if (bestBucket == null) {
                return null;
            }

            return bestBucket.color;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isWeakIconColor(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        int saturation = max - min;

        return saturation < 35 || max < 70 || min > 225;
    }

    private int quantizeColor(int value) {
        return Math.min(255, (value / 32) * 32 + 16);
    }

    private List<String> getSourcePackageCandidates(HistoryText historyText) {
        List<String> packageCandidates = new ArrayList<>();
        addPackageCandidate(packageCandidates, historyText.packageName);

        String message = historyText.message.toLowerCase();
        if (message.contains("whatsapp")) {
            addPackageCandidate(packageCandidates, "com.whatsapp");
        }
        if (message.contains("sms")) {
            addPackageCandidate(packageCandidates, "com.samsung.android.messaging");
            addPackageCandidate(packageCandidates, "com.google.android.apps.messaging");
            addPackageCandidate(packageCandidates, "com.android.mms");
        }
        if (message.contains("gmail")) {
            addPackageCandidate(packageCandidates, "com.google.android.gm");
        }
        if (message.contains("instagram")) {
            addPackageCandidate(packageCandidates, "com.instagram.android");
        }
        if (message.contains("facebook")) {
            addPackageCandidate(packageCandidates, "com.facebook.katana");
        }
        if (message.contains("messenger")) {
            addPackageCandidate(packageCandidates, "com.facebook.orca");
        }
        if (message.contains("telegram")) {
            addPackageCandidate(packageCandidates, "org.telegram.messenger");
        }
        if (message.contains("nubank")) {
            addPackageCandidate(packageCandidates, "com.nu.production");
        }

        return packageCandidates;
    }

    private void addPackageCandidate(List<String> packageCandidates, String packageName) {
        if (!TextUtils.isEmpty(packageName) && !packageCandidates.contains(packageName)) {
            packageCandidates.add(packageName);
        }
    }

    private void showNotificationPermissionDialog() {
        if (DroidCommon.IsNotificationListenerEnabled(context)) {
            notificationPermissionDeclined = false;
            renderHistory();
            return;
        }

        notificationPermissionPromptShown = true;
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.one_ui_card);
        content.setPadding(dp(22), dp(20), dp(22), dp(18));

        TextView title = new TextView(context);
        title.setText(R.string.notification_permission_title);
        title.setTextColor(getResources().getColor(R.color.one_ui_text_primary));
        title.setTextSize(18);
        title.setGravity(Gravity.START);
        title.setTypeface(null, 1);
        content.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(context);
        message.setText(R.string.notification_permission_message);
        message.setTextColor(getResources().getColor(R.color.one_ui_text_secondary));
        message.setTextSize(14);
        message.setLineSpacing(dp(2), 1);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, dp(10), 0, dp(18));
        content.addView(message, messageParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView later = createDialogButton(R.string.notification_permission_later, false);
        later.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                notificationPermissionDeclined = true;
                dialog.dismiss();
                renderHistory();
            }
        });
        actions.addView(later);

        TextView openSettings = createDialogButton(R.string.notification_permission_confirm, true);
        openSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                openNotificationPermissionSettings();
            }
        });
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        openParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(openSettings, openParams);

        content.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        dialog.setView(content);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }
            }
        });
        dialog.show();
    }

    private void maybeShowNotificationPermissionDialog() {
        if (notificationPermissionPromptShown
                || showingTrash
                || waitingForNotificationPermission
                || DroidCommon.IsNotificationListenerEnabled(context)) {
            return;
        }

        historyContainer.post(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !DroidCommon.IsNotificationListenerEnabled(context)) {
                    showNotificationPermissionDialog();
                }
            }
        });
    }

    private TextView createDialogButton(int textResId, boolean primary) {
        TextView button = new TextView(context);
        button.setText(textResId);
        button.setBackgroundResource(primary ? R.drawable.one_ui_button_secondary : 0);
        button.setClickable(true);
        button.setFocusable(true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(40));
        button.setPadding(dp(14), dp(7), dp(14), dp(7));
        button.setSingleLine(true);
        button.setTextColor(getResources().getColor(primary
                ? R.color.one_ui_blue
                : R.color.one_ui_text_secondary));
        button.setTextSize(13);
        button.setTypeface(null, 1);
        return button;
    }

    private void openNotificationPermissionSettings() {
        waitingForNotificationPermission = true;
        notificationPermissionDeclined = false;
        DroidCommon.ShowListener(context);
    }

    private void updateNotificationPermissionReturnState() {
        if (!waitingForNotificationPermission) {
            return;
        }

        waitingForNotificationPermission = false;
        notificationPermissionDeclined = !DroidCommon.IsNotificationListenerEnabled(context);
    }

    private void showTrashDialog(final HistoryText historyText) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.trash_send_title)
                .setMessage(R.string.trash_send_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.trash_send_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        DroidCommon.MoveLocalHistoryToTrash(context, historyText.rawLine);
                        renderHistory();
                    }
                })
                .show();
    }

    private void showTrashGroupDialog(final HistoryGroup group) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.trash_group_send_title)
                .setMessage(getString(R.string.trash_group_send_message, group.items.size()))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.trash_send_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        for (HistoryText item : group.items) {
                            DroidCommon.MoveLocalHistoryToTrash(context, item.rawLine);
                        }
                        expandedDays.remove(group.day);
                        renderHistory();
                    }
                })
                .show();
    }

    private void showRestoreDialog(final HistoryText historyText) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.trash_restore_title)
                .setMessage(R.string.trash_restore_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.trash_restore_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        DroidCommon.RestoreTrashHistory(context, historyText.rawLine);
                        renderHistory();
                    }
                })
                .show();
    }

    private void showRestoreGroupDialog(final HistoryGroup group) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.trash_group_restore_title)
                .setMessage(getString(R.string.trash_group_restore_message, group.items.size()))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.trash_restore_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        for (HistoryText item : group.items) {
                            DroidCommon.RestoreTrashHistory(context, item.rawLine);
                        }
                        expandedDays.remove(group.day);
                        renderHistory();
                    }
                })
                .show();
    }

    private void showClearTrashDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.trash_clear_title)
                .setMessage(R.string.trash_clear_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.trash_clear_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        DroidCommon.ClearTrashHistory(context);
                        expandedDays.clear();
                        renderHistory();
                    }
                })
                .show();
    }

    private HistoryText parseHistoryLine(String line) {
        String day = getString(R.string.history_item_unknown_date);
        String time = "";
        String message = line;
        String packageName = "";
        String imageFileName = "";

        String[] imageParts = line.split("\t", 4);
        if (imageParts.length == 4) {
            packageName = imageParts[1].trim();
            imageFileName = imageParts[2].trim();
            message = imageParts[3].trim();
            if (imageParts[0].length() >= 16) {
                day = imageParts[0].substring(0, 10);
                time = imageParts[0].substring(11, 16);
            }
            return new HistoryText(line, day, time, message, packageName, imageFileName, viewedHistory.contains(line));
        }

        String[] parts = line.split("\t", 3);
        if (parts.length == 3) {
            packageName = parts[1].trim();
            message = parts[2].trim();
            if (parts[0].length() >= 16) {
                day = parts[0].substring(0, 10);
                time = parts[0].substring(11, 16);
            }
            return new HistoryText(line, day, time, message, packageName, imageFileName, viewedHistory.contains(line));
        }

        if (line.length() > 17 && line.charAt(2) == '/' && line.charAt(5) == '/'
                && line.charAt(10) == ' ' && line.charAt(13) == ':') {
            day = line.substring(0, 10);
            time = line.substring(11, 16);
            message = line.substring(17).trim();
        }

        return new HistoryText(line, day, time, message, packageName, imageFileName, viewedHistory.contains(line));
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private static class HistoryText {
        final String rawLine;
        final String day;
        final String time;
        final String message;
        final String packageName;
        final String imageFileName;
        final boolean viewed;

        HistoryText(String rawLine, String day, String time, String message, String packageName,
                    String imageFileName, boolean viewed) {
            this.rawLine = rawLine;
            this.day = day;
            this.time = time;
            this.message = message;
            this.packageName = packageName;
            this.imageFileName = imageFileName;
            this.viewed = viewed;
        }
    }

    private static class HistoryGroup {
        final String day;
        final List<HistoryText> items = new ArrayList<>();
        int unreadCount;

        HistoryGroup(String day) {
            this.day = day;
        }
    }

    private static class ColorBucket {
        final int color;
        int count;

        ColorBucket(int color) {
            this.color = color;
        }

        int score() {
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            int max = Math.max(red, Math.max(green, blue));
            int min = Math.min(red, Math.min(green, blue));
            return count * (max - min);
        }
    }
}
