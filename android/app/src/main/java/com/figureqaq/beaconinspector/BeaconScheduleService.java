package com.figureqaq.beaconinspector;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.util.Calendar;

public class BeaconScheduleService extends Service {
    static final String ACTION_BOUNDARY = "com.figureqaq.beaconinspector.SCHEDULE_BOUNDARY";
    private static final String CHANNEL_ID = "beacon_schedule";
    private static final int NOTIFICATION_ID = 7;
    private static final int ALARM_REQUEST = 17;
    private static final int APPLE_COMPANY_ID = 0x004C;

    private BluetoothLeAdvertiser advertiser;
    private boolean advertising;
    private MainActivity.BeaconParams config;

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            advertising = true;
            updateNotification("正在广播测试信标");
        }

        @Override
        public void onStartFailure(int errorCode) {
            advertising = false;
            updateNotification("广播启动失败 · 错误 " + errorCode);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat(buildNotification("正在检查每日广播时段"));
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(MainActivity.KEY_DAILY_ENABLED, false)) {
            cancelNextBoundary(this);
            stopAdvertising();
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        config = MainActivity.BeaconParams.from(prefs);
        int startMinute = prefs.getInt(MainActivity.KEY_START_MINUTE, MainActivity.DEFAULT_START_MINUTE);
        int endMinute = prefs.getInt(MainActivity.KEY_END_MINUTE, MainActivity.DEFAULT_END_MINUTE);
        if (startMinute == endMinute) {
            updateNotification("每日计划时间无效");
            stopAdvertising();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        boolean inWindow = isWithinWindow(Calendar.getInstance(), startMinute, endMinute);
        if (inWindow) startAdvertising();
        else stopAdvertising();
        scheduleNextBoundary(startMinute, endMinute, inWindow);
        if (!inWindow) {
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        updateNotification("每日计划 · 正在广播");
        return START_STICKY;
    }

    private void startAdvertising() {
        if (advertising || advertiser != null) stopAdvertising();
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            updateNotification("请开启蓝牙以广播测试信标");
            return;
        }
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            updateNotification("此设备不支持 BLE 广播");
            return;
        }
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .build();
        AdvertiseData data = new AdvertiseData.Builder()
                .addManufacturerData(APPLE_COMPANY_ID, MainActivity.buildPayload(config))
                .setIncludeDeviceName(false)
                .build();
        try {
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (SecurityException | IllegalArgumentException exception) {
            advertiser = null;
            updateNotification("无法启动 BLE 广播");
        }
    }

    private void stopAdvertising() {
        if (advertiser != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (SecurityException ignored) {
                // The user may revoke nearby-device access while the schedule is active.
            }
        }
        advertiser = null;
        advertising = false;
    }

    private void scheduleNextBoundary(int startMinute, int endMinute, boolean inWindow) {
        Calendar now = Calendar.getInstance();
        int minute = inWindow ? endMinute : startMinute;
        Calendar boundary = (Calendar) now.clone();
        boundary.set(Calendar.HOUR_OF_DAY, minute / 60);
        boundary.set(Calendar.MINUTE, minute % 60);
        boundary.set(Calendar.SECOND, 0);
        boundary.set(Calendar.MILLISECOND, 0);
        if (!boundary.after(now)) boundary.add(Calendar.DAY_OF_YEAR, 1);

        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        if (alarmManager == null) return;
        PendingIntent alarm = boundaryIntent(this);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, boundary.getTimeInMillis(), alarm);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, boundary.getTimeInMillis(), alarm);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, boundary.getTimeInMillis(), alarm);
            }
        } catch (SecurityException exception) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, boundary.getTimeInMillis(), alarm);
        }
    }

    private boolean isWithinWindow(Calendar now, int startMinute, int endMinute) {
        int currentMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (startMinute < endMinute) return currentMinute >= startMinute && currentMinute < endMinute;
        return currentMinute >= startMinute || currentMinute < endMinute;
    }

    static void cancelNextBoundary(Context context) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager != null) alarmManager.cancel(boundaryIntent(context));
    }

    private static PendingIntent boundaryIntent(Context context) {
        Intent intent = new Intent(context, ScheduleAlarmReceiver.class).setAction(ACTION_BOUNDARY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, ALARM_REQUEST, intent, flags);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "每日信标广播", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示每日测试信标广播计划状态");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Intent openApp = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 1, openApp, flags);
        return builder.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Beacon Inspector")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        stopAdvertising();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
