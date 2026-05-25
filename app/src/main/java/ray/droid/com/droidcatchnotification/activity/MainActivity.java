package ray.droid.com.droidcatchnotification.activity;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

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
    private final Handler historyHandler = new Handler();
    private boolean autoRefreshRunning;
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

        buttonNotifications.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DroidCommon.ShowListener(context);
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
        List<String> history = DroidCommon.ReadLocalHistory(context);
        int total = history.size();

        if (notificationReady) {
            textStatus.setVisibility(View.GONE);
            buttonNotifications.setVisibility(View.GONE);
        } else {
            textStatus.setText(R.string.history_status_permission_missing);
            textStatus.setVisibility(View.VISIBLE);
            buttonNotifications.setVisibility(View.VISIBLE);
        }

        textHistoryCount.setText(getResources().getQuantityString(
                R.plurals.history_count, total, total));
        historyContainer.removeAllViews();
        textEmptyHistory.setVisibility(total == 0 ? View.VISIBLE : View.GONE);

        int visibleItems = Math.min(total, MAX_VISIBLE_HISTORY);
        for (int index = total - 1; index >= total - visibleItems; index--) {
            historyContainer.addView(createHistoryItem(history.get(index)));
        }
    }

    private View createHistoryItem(String line) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapper.setLayoutParams(wrapperParams);

        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(2), dp(7), dp(2), dp(7));
        wrapper.addView(item);

        HistoryText historyText = parseHistoryLine(line);

        TextView date = new TextView(context);
        date.setText(historyText.date);
        date.setTextColor(getResources().getColor(R.color.one_ui_text_secondary));
        date.setTextSize(9);
        date.setGravity(Gravity.TOP);
        item.addView(date, new LinearLayout.LayoutParams(dp(94), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(context);
        message.setText(historyText.message);
        message.setTextColor(getResources().getColor(R.color.one_ui_text_primary));
        message.setTextSize(13);
        message.setSingleLine(false);
        message.setMaxLines(2);
        message.setEllipsize(TextUtils.TruncateAt.END);
        message.setLineSpacing(0, 1);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1);
        item.addView(message, messageParams);

        View divider = new View(context);
        divider.setBackgroundColor(getResources().getColor(R.color.one_ui_stroke));
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))));

        return wrapper;
    }

    private HistoryText parseHistoryLine(String line) {
        String date = getString(R.string.history_item_unknown_date);
        String message = line;

        if (line.length() > 17 && line.charAt(2) == '/' && line.charAt(5) == '/'
                && line.charAt(10) == ' ' && line.charAt(13) == ':') {
            date = line.substring(0, 16);
            message = line.substring(17).trim();
        }

        return new HistoryText(date, message);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private static class HistoryText {
        final String date;
        final String message;

        HistoryText(String date, String message) {
            this.date = date;
            this.message = message;
        }
    }
}
