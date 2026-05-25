package ray.droid.com.droidcatchnotification.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ray.droid.com.droidcatchnotification.R;
import ray.droid.com.droidcatchnotification.common.DroidCommon;

public class MainActivity extends AppCompatActivity {
    private static final int MAX_VISIBLE_HISTORY = 100;
    private static final long AUTO_REFRESH_INTERVAL_MS = 1500;

    private Context context;
    private TextView textStatus;
    private TextView textHistoryCount;
    private TextView textEmptyHistory;
    private LinearLayout historyContainer;
    private TextView buttonNotifications;
    private TextView buttonTrash;
    private EditText editSearch;
    private TextView buttonSearchClear;
    private final Handler historyHandler = new Handler();
    private final Set<String> expandedDays = new HashSet<>();
    private boolean autoRefreshRunning;
    private boolean showingTrash;
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
        editSearch = findViewById(R.id.editSearch);
        buttonSearchClear = findViewById(R.id.buttonSearchClear);

        buttonNotifications.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DroidCommon.ShowListener(context);
            }
        });

        buttonTrash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showingTrash = !showingTrash;
                renderHistory();
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

        renderHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startAutoRefresh();
        renderHistory();
    }

    @Override
    protected void onPause() {
        stopAutoRefresh();
        super.onPause();
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
        List<String> history = showingTrash
                ? DroidCommon.ReadTrashHistory(context)
                : DroidCommon.ReadLocalHistory(context);
        List<String> visibleHistory = filterHistory(history);
        boolean searchActive = !TextUtils.isEmpty(searchQuery);
        int total = history.size();
        int visibleTotal = visibleHistory.size();

        if (showingTrash || notificationReady) {
            textStatus.setVisibility(View.GONE);
            buttonNotifications.setVisibility(View.GONE);
        } else {
            textStatus.setText(R.string.history_status_permission_missing);
            textStatus.setVisibility(View.VISIBLE);
            buttonNotifications.setVisibility(View.VISIBLE);
        }

        buttonTrash.setText(showingTrash ? R.string.history_back : R.string.history_open_trash);

        if (searchActive) {
            textHistoryCount.setText(getResources().getQuantityString(
                    R.plurals.search_results, visibleTotal, visibleTotal));
        } else if (showingTrash) {
            textHistoryCount.setText(getResources().getQuantityString(
                    R.plurals.trash_count, total, total));
            textEmptyHistory.setText(R.string.trash_empty);
        } else {
            textHistoryCount.setText(getResources().getQuantityString(
                    R.plurals.history_count, total, total));
            textEmptyHistory.setText(R.string.history_empty);
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
        int visibleItems = Math.min(history.size(), MAX_VISIBLE_HISTORY);

        for (int index = history.size() - 1; index >= history.size() - visibleItems; index--) {
            HistoryText item = parseHistoryLine(history.get(index));
            HistoryGroup group = groupedHistory.get(item.day);
            if (group == null) {
                group = new HistoryGroup(item.day);
                groupedHistory.put(item.day, group);
            }
            group.items.add(item);
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
        count.setText(getResources().getQuantityString(
                R.plurals.history_group_count, group.items.size(), group.items.size()));
        count.setTextColor(getResources().getColor(R.color.one_ui_text_secondary));
        count.setTextSize(12);
        count.setSingleLine(true);
        header.addView(count);

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

    private View createHistoryItem(HistoryText historyText) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        View.OnLongClickListener actionListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (showingTrash) {
                    showRestoreDialog(historyText);
                } else {
                    showTrashDialog(historyText);
                }
                return true;
            }
        };
        wrapper.setOnLongClickListener(actionListener);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapper.setLayoutParams(wrapperParams);

        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(2), dp(6), dp(2), dp(6));
        wrapper.addView(item);

        TextView date = new TextView(context);
        date.setText(historyText.time);
        date.setTextColor(getResources().getColor(R.color.one_ui_text_secondary));
        date.setTextSize(9);
        date.setGravity(Gravity.TOP);
        date.setOnLongClickListener(actionListener);
        item.addView(date, new LinearLayout.LayoutParams(dp(50), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(context);
        message.setText(historyText.message);
        message.setTextColor(getResources().getColor(R.color.one_ui_text_primary));
        message.setTextSize(13);
        message.setSingleLine(false);
        message.setMaxLines(2);
        message.setEllipsize(TextUtils.TruncateAt.END);
        message.setLineSpacing(0, 1);
        message.setOnLongClickListener(actionListener);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1);
        item.addView(message, messageParams);
        item.setOnLongClickListener(actionListener);

        View divider = new View(context);
        divider.setBackgroundColor(getResources().getColor(R.color.one_ui_stroke));
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))));

        return wrapper;
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

    private HistoryText parseHistoryLine(String line) {
        String day = getString(R.string.history_item_unknown_date);
        String time = "";
        String message = line;

        if (line.length() > 17 && line.charAt(2) == '/' && line.charAt(5) == '/'
                && line.charAt(10) == ' ' && line.charAt(13) == ':') {
            day = line.substring(0, 10);
            time = line.substring(11, 16);
            message = line.substring(17).trim();
        }

        return new HistoryText(line, day, time, message);
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

        HistoryText(String rawLine, String day, String time, String message) {
            this.rawLine = rawLine;
            this.day = day;
            this.time = time;
            this.message = message;
        }
    }

    private static class HistoryGroup {
        final String day;
        final List<HistoryText> items = new ArrayList<>();

        HistoryGroup(String day) {
            this.day = day;
        }
    }
}
