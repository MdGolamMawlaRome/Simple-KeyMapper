package com.gmr.simplekeymapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Switch toggleMapper;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle Bundle) {
        super.onCreate(Bundle);

        createNotificationChannel();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);

        statusText = new TextView(this);
        statusText.setText("Checking permissions...");
        statusText.setTextSize(16);
        statusText.setPadding(0, 0, 0, 40);
        layout.addView(statusText);

        toggleMapper = new Switch(this);
        toggleMapper.setText("Start Keymapper Engine");
        toggleMapper.setTextSize(20);
        toggleMapper.setEnabled(false); // পারমিশন না পাওয়া পর্যন্ত এটি লক থাকবে

        toggleMapper.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (MapperAccessibilityService.instance != null) {
                    MapperAccessibilityService.instance.startKeymapperUI();
                    startKeepAliveService();
                    Toast.makeText(MainActivity.this, "Keymapper Active", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (MapperAccessibilityService.instance != null) {
                    MapperAccessibilityService.instance.stopKeymapperUI();
                }
                stopKeepAliveService();
            }
        });
        layout.addView(toggleMapper);

        setContentView(layout);
    }

    // অ্যাপ ওপেন হলেই বা ব্যাকগ্রাউন্ড থেকে অ্যাপে ফিরে আসলেই এই মেথডটি রান হবে
    @Override
    protected void onResume() {
        super.onResume();
        checkAndForcePermissions();
    }

    // পারমিশন চেক এবং জোরপূর্বক পারমিশন চাওয়ার আসল লজিক
    private void checkAndForcePermissions() {
        boolean overlayAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        boolean accessibilityAllowed = isAccessibilityServiceEnabled(this, MapperAccessibilityService.class);

        if (overlayAllowed && accessibilityAllowed) {
            // দুটি পারমিশনই থাকলে মেইন সুইচ আনলক হয়ে যাবে
            statusText.setText("Status: All Permissions Granted! Ready to play.");
            statusText.setTextColor(0xFF00FF00); // সবুজ রঙ
            toggleMapper.setEnabled(true);
        } else {
            // পারমিশন মিসিং থাকলে মেইন সুইচ লক থাকবে এবং পপ-আপ মেসেজ আসবে
            toggleMapper.setEnabled(false);
            toggleMapper.setChecked(false);
            statusText.setText("Status: Permissions Missing! Please allow them.");
            statusText.setTextColor(0xFFFF0000); // লাল রঙ
            
            showPermissionDialog(overlayAllowed, accessibilityAllowed);
        }
    }

    // ইউজারকে বাধ্য করার জন্য পপ-আপ ডায়ালগ
    private void showPermissionDialog(boolean overlay, boolean accessibility) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permissions Required!");
        builder.setCancelable(false); // স্ক্রিনের বাইরে ক্লিক করলে ডায়ালগ কাটবে না

        if (!overlay) {
            builder.setMessage("This app requires 'Overlay Permission' to show buttons over your game. Click OK to enable it.");
            builder.setPositiveButton("OK", (dialog, which) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    startActivity(intent);
                }
            });
        } else if (!accessibility) {
            builder.setMessage("This app requires 'Accessibility Service' to simulate clicks and mouse movement. Click OK, find 'Simple Keymapper' and turn it ON.");
            builder.setPositiveButton("OK", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            });
        }

        builder.show();
    }

    // অ্যাক্সেসিবিলিটি সার্ভিস অন আছে কি না তা নিখুঁতভাবে চেক করার মেথড
    private boolean isAccessibilityServiceEnabled(Context context, Class<?> service) {
        String settingValue = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (settingValue != null) {
            return settingValue.contains(service.getCanonicalName());
        }
        return false;
    }

    private void startKeepAliveService() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification notification = new Notification.Builder(this, "gmr_channel")
                    .setContentTitle("Simple Keymapper Running")
                    .setContentText("Mapping Engine locked in background.")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setOngoing(true) // নোটিফিকেশনটি যাতে ইউজার সোয়াইপ করে কাটতে না পারে
                    .build();
            manager.notify(99, notification);
        }
    }

    private void stopKeepAliveService() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(99);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("gmr_channel", "Keymapper Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
