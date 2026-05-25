package ray.droid.com.droidcatchnotification.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import ray.droid.com.droidcatchnotification.R;
import ray.droid.com.droidcatchnotification.common.DroidCommon;
import ray.droid.com.droidcatchnotification.gdrive.CreateFileActivity;

public class MainActivity extends AppCompatActivity  {
    private Context context;
    private TextView textStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        textStatus = findViewById(R.id.textStatus);
        findViewById(R.id.buttonNotifications).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DroidCommon.ShowListener(context);
            }
        });
        findViewById(R.id.buttonDrive).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openDriveSetup();
            }
        });
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        if (textStatus == null) {
            return;
        }
        if (DroidCommon.GetDriveFile(context) == null) {
            textStatus.setText(R.string.main_status_drive_missing);
        } else {
            textStatus.setText(R.string.main_status_ready);
        }
    }

    private void openDriveSetup() {
        Intent intent = new Intent(context, CreateFileActivity.class);
        startActivity(intent);
    }
}
