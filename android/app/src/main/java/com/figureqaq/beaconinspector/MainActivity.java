package com.figureqaq.beaconinspector;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    static final String PREFS_NAME = "beacon_settings";
    static final String KEY_UUID = "uuid";
    static final String KEY_MAJOR = "major";
    static final String KEY_MINOR = "minor";
    static final String KEY_TX_POWER = "txPower";
    static final String KEY_DAILY_ENABLED = "dailyEnabled";
    static final String KEY_START_MINUTE = "startMinute";
    static final String KEY_END_MINUTE = "endMinute";
    static final String DEFAULT_UUID = "FDA50693-A4E2-4FB1-AFCF-C6EB07647825";
    private static final String PREVIOUS_DEFAULT_UUID = "7E57D004-2B97-4E7A-B45F-5387367791CD";
    private static final String KEY_DEFAULT_UUID_MIGRATED = "defaultUuidMigrated";
    static final int DEFAULT_MAJOR = 1;
    static final int DEFAULT_MINOR = 1;
    static final int DEFAULT_TX_POWER = -59;
    static final int DEFAULT_START_MINUTE = 9 * 60;
    static final int DEFAULT_END_MINUTE = 18 * 60;
    private static final int APPLE_COMPANY_ID = 0x004C;
    private static final int REQUEST_PERMISSIONS = 41;
    private static final int REQUEST_EXACT_ALARM = 42;

    private DirectBroadcast directBroadcast;
    private boolean permissionRequestPending;
    private boolean exactAlarmSettingsPending;
    private boolean autoStart = true;
    private TextView statusText;
    private Button toggleButton;
    private Button startTimeButton;
    private Button endTimeButton;
    private EditText uuidInput;
    private EditText majorInput;
    private EditText minorInput;
    private EditText txPowerInput;
    private CheckBox dailySchedule;
    private int startMinute = DEFAULT_START_MINUTE;
    private int endMinute = DEFAULT_END_MINUTE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFFF4F7F6);
        int systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().setNavigationBarColor(0xFFF4F7F6);
            systemUiVisibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(systemUiVisibility);
        setContentView(createContent());
        requestPermissionAndStart();
    }

    private View createContent() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        BeaconParams savedParams = BeaconParams.from(prefs);
        startMinute = prefs.getInt(KEY_START_MINUTE, DEFAULT_START_MINUTE);
        endMinute = prefs.getInt(KEY_END_MINUTE, DEFAULT_END_MINUTE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF4F7F6);

        FrameLayout frame = new FrameLayout(this);
        scroll.addView(frame);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFFF4F7F6);
        page.setPadding(dp(22), dp(18), dp(22), dp(24));
        int contentWidth = Math.min(getResources().getDisplayMetrics().widthPixels, dp(640));
        frame.addView(page, new FrameLayout.LayoutParams(contentWidth, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        page.addView(text("Beacon Inspector", 24, 0xFF142B2A, true));
        TextView subtitle = text("本地测试信标", 14, 0xFF637574, false);
        LinearLayout.LayoutParams subtitleParams = params(-2, -2);
        subtitleParams.topMargin = dp(4);
        page.addView(subtitle, subtitleParams);

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        status.setBackgroundColor(0xFFE6EFEC);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(20);
        page.addView(status, statusParams);
        statusText = text("正在准备广播", 14, 0xFF355450, true);
        status.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1));

        uuidInput = addInput(page, "UUID", savedParams.uuid,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        LinearLayout numberRow = new LinearLayout(this);
        numberRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams numberRowParams = params(-1, -2);
        numberRowParams.topMargin = dp(12);
        page.addView(numberRow, numberRowParams);
        majorInput = addInput(numberRow, "Major", String.valueOf(savedParams.major), InputType.TYPE_CLASS_NUMBER);
        minorInput = addInput(numberRow, "Minor", String.valueOf(savedParams.minor), InputType.TYPE_CLASS_NUMBER);
        txPowerInput = addInput(page, "Tx Power (dBm)", String.valueOf(savedParams.txPower),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

        dailySchedule = new CheckBox(this);
        dailySchedule.setText("每日定时广播");
        dailySchedule.setTextSize(14);
        dailySchedule.setTextColor(0xFF355450);
        LinearLayout.LayoutParams scheduleParams = params(-2, -2);
        scheduleParams.topMargin = dp(18);
        page.addView(dailySchedule, scheduleParams);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams timeRowParams = params(-1, dp(48));
        timeRowParams.topMargin = dp(4);
        page.addView(timeRow, timeRowParams);
        startTimeButton = timeButton(timeRow, "开始时间", startMinute);
        endTimeButton = timeButton(timeRow, "结束时间", endMinute);
        dailySchedule.setChecked(prefs.getBoolean(KEY_DAILY_ENABLED, false));

        toggleButton = new Button(this);
        toggleButton.setText("开始广播");
        toggleButton.setTextSize(15);
        toggleButton.setAllCaps(false);
        toggleButton.setTextColor(0xFFFFFFFF);
        toggleButton.setBackgroundTintList(ColorStateList.valueOf(0xFF167D72));
        LinearLayout.LayoutParams buttonParams = params(-1, dp(50));
        buttonParams.topMargin = dp(20);
        page.addView(toggleButton, buttonParams);

        dailySchedule.setOnCheckedChangeListener((button, checked) -> onScheduleChanged(checked));
        startTimeButton.setOnClickListener(view -> chooseTime(true));
        endTimeButton.setOnClickListener(view -> chooseTime(false));
        toggleButton.setOnClickListener(view -> {
            if (dailySchedule.isChecked()) {
                dailySchedule.setOnCheckedChangeListener(null);
                dailySchedule.setChecked(false);
                dailySchedule.setOnCheckedChangeListener((button, checked) -> onScheduleChanged(checked));
                saveScheduleEnabled(false);
                stopService(new Intent(this, BeaconScheduleService.class));
                BeaconScheduleService.cancelNextBoundary(this);
                autoStart = false;
                updateBroadcastStatus("每日计划已停止");
            } else if (directBroadcast != null) {
                stopDirectBroadcast(true);
            } else {
                autoStart = true;
                requestPermissionAndStart();
            }
        });
        TextWatcher parameterWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable text) {
                try {
                    saveParams(readParams());
                } catch (IllegalArgumentException ignored) {
                    // Keep the last valid parameters while an edit is incomplete or invalid.
                }
            }
        };
        uuidInput.addTextChangedListener(parameterWatcher);
        majorInput.addTextChangedListener(parameterWatcher);
        minorInput.addTextChangedListener(parameterWatcher);
        txPowerInput.addTextChangedListener(parameterWatcher);
        return scroll;
    }

    private EditText addInput(LinearLayout parent, String label, String value, int inputType) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                0, -2, parent.getOrientation() == LinearLayout.HORIZONTAL ? 1 : 0);
        if (parent.getOrientation() == LinearLayout.VERTICAL) fieldParams.width = -1;
        if (parent.getOrientation() == LinearLayout.HORIZONTAL && parent.getChildCount() > 0)
            fieldParams.leftMargin = dp(14);
        parent.addView(field, fieldParams);

        field.addView(text(label, 12, 0xFF637574, true));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(0xFF142B2A);
        input.setInputType(inputType);
        input.setPadding(0, dp(7), 0, dp(7));
        input.setText(value);
        field.addView(input, params(-1, dp(46)));
        return input;
    }

    private Button timeButton(LinearLayout parent, String label, int minute) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(0xFF355450);
        button.setText(timeLabel(label, minute));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, -1, 1);
        if (parent.getChildCount() > 0) buttonParams.leftMargin = dp(8);
        parent.addView(button, buttonParams);
        return button;
    }

    private void chooseTime(boolean isStart) {
        int previousStart = startMinute;
        int previousEnd = endMinute;
        int current = isStart ? startMinute : endMinute;
        new TimePickerDialog(this, (picker, hour, minute) -> {
            int selected = hour * 60 + minute;
            if (isStart) startMinute = selected;
            else endMinute = selected;
            if (startMinute == endMinute) {
                startMinute = previousStart;
                endMinute = previousEnd;
                Toast.makeText(this, "开始和结束时间不能相同", Toast.LENGTH_SHORT).show();
                return;
            }
            startTimeButton.setText(timeLabel("开始时间", startMinute));
            endTimeButton.setText(timeLabel("结束时间", endMinute));
            saveScheduleTimes();
            if (dailySchedule.isChecked()) startDailyScheduleService();
        }, current / 60, current % 60, true).show();
    }

    private void requestPermissionAndStart() {
        if (permissionRequestPending || exactAlarmSettingsPending) return;
        ArrayList<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (dailySchedule != null && dailySchedule.isChecked() && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!needed.isEmpty()) {
            permissionRequestPending = true;
            requestPermissions(needed.toArray(new String[0]), REQUEST_PERMISSIONS);
            return;
        }
        startSelectedMode();
    }

    private void startSelectedMode() {
        BeaconParams params;
        try {
            params = readParams();
        } catch (IllegalArgumentException exception) {
            updateBroadcastStatus(exception.getMessage());
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        saveParams(params);
        if (dailySchedule != null && dailySchedule.isChecked()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager alarmManager = getSystemService(AlarmManager.class);
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    exactAlarmSettingsPending = true;
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_EXACT_ALARM);
                    return;
                }
            }
            startDailyScheduleService();
        } else {
            saveScheduleEnabled(false);
            startDirectBroadcast(params);
        }
    }

    private void startDailyScheduleService() {
        if (startMinute == endMinute) return;
        saveScheduleEnabled(true);
        Intent service = new Intent(this, BeaconScheduleService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        updateBroadcastStatus("每日计划已启用");
    }

    private void startDirectBroadcast(BeaconParams params) {
        if (directBroadcast != null) return;
        try {
            BluetoothManager manager = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                updateBroadcastStatus("请开启蓝牙");
                return;
            }
            BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();
            if (advertiser == null) {
                updateBroadcastStatus("此设备不支持 BLE 广播");
                return;
            }
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(false)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .addManufacturerData(APPLE_COMPANY_ID, buildPayload(params))
                    .setIncludeDeviceName(false)
                    .build();
            directBroadcast = new DirectBroadcast(advertiser);
            updateBroadcastStatus("正在启动广播");
            advertiser.startAdvertising(settings, data, directBroadcast);
        } catch (SecurityException exception) {
            clearDirectBroadcast();
            updateBroadcastStatus("未获得附近设备权限");
        } catch (IllegalArgumentException exception) {
            clearDirectBroadcast();
            updateBroadcastStatus("设备不支持此广播格式");
        }
    }

    private final class DirectBroadcast extends AdvertiseCallback {
        private final BluetoothLeAdvertiser advertiser;

        DirectBroadcast(BluetoothLeAdvertiser advertiser) {
            this.advertiser = advertiser;
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            runOnUiThread(() -> {
                if (directBroadcast == this) updateBroadcastStatus("正在广播测试信标");
                else stop();
            });
        }

        @Override
        public void onStartFailure(int errorCode) {
            runOnUiThread(() -> {
                if (directBroadcast != this) return;
                clearDirectBroadcast();
                updateBroadcastStatus("广播启动失败 · 错误 " + errorCode);
                Toast.makeText(MainActivity.this, "设备未能启动 BLE 广播", Toast.LENGTH_SHORT).show();
            });
        }

        private void stop() {
            try {
                advertiser.stopAdvertising(this);
            } catch (SecurityException ignored) {
                // The user may revoke nearby-device access while the app is open.
            }
        }
    }

    private BeaconParams readParams() {
        String uuidText = uuidInput.getText().toString().trim().toUpperCase(Locale.US);
        try {
            UUID parsedUuid = UUID.fromString(uuidText);
            if (!parsedUuid.toString().equalsIgnoreCase(uuidText)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("UUID 格式无效");
        }
        int major = parseNumber(majorInput, "Major", 0, 65_535);
        int minor = parseNumber(minorInput, "Minor", 0, 65_535);
        int txPower = parseNumber(txPowerInput, "Tx Power", -127, 127);
        return new BeaconParams(uuidText, major, minor, txPower);
    }

    private int parseNumber(EditText input, String label, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " 范围应为 " + minimum + " 至 " + maximum);
        }
    }

    private void saveScheduleTimes() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(KEY_START_MINUTE, startMinute)
                .putInt(KEY_END_MINUTE, endMinute)
                .apply();
    }

    private void saveParams(BeaconParams params) {
        params.save(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()).apply();
    }

    private void saveScheduleEnabled(boolean enabled) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_DAILY_ENABLED, enabled).apply();
    }

    static byte[] buildPayload(BeaconParams params) {
        byte[] payload = new byte[23];
        payload[0] = 0x02;
        payload[1] = 0x15;
        String hexUuid = params.uuid.replace("-", "");
        for (int index = 0; index < 16; index++) {
            payload[index + 2] = (byte) Integer.parseInt(hexUuid.substring(index * 2, index * 2 + 2), 16);
        }
        payload[18] = (byte) (params.major >>> 8);
        payload[19] = (byte) params.major;
        payload[20] = (byte) (params.minor >>> 8);
        payload[21] = (byte) params.minor;
        payload[22] = (byte) params.txPower;
        return payload;
    }

    private void stopDirectBroadcast(boolean disableAutoStart) {
        if (disableAutoStart) autoStart = false;
        clearDirectBroadcast();
        updateBroadcastStatus("广播已停止");
    }

    private void clearDirectBroadcast() {
        DirectBroadcast broadcast = directBroadcast;
        directBroadcast = null;
        if (broadcast != null) broadcast.stop();
    }

    private void updateBroadcastStatus(String status) {
        statusText.setText(status);
        toggleButton.setText(dailySchedule.isChecked() ? "停止每日计划"
                : directBroadcast != null ? "停止广播" : "开始广播");
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) return;
        permissionRequestPending = false;
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
        if (granted) startSelectedMode();
        else {
            if (dailySchedule.isChecked()) {
                dailySchedule.setOnCheckedChangeListener(null);
                dailySchedule.setChecked(false);
                dailySchedule.setOnCheckedChangeListener((button, checked) -> onScheduleChanged(checked));
                saveScheduleEnabled(false);
            }
            updateBroadcastStatus("未获得所需权限");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXACT_ALARM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactAlarmSettingsPending = false;
            AlarmManager alarmManager = getSystemService(AlarmManager.class);
            if (alarmManager != null && alarmManager.canScheduleExactAlarms() && dailySchedule.isChecked()) {
                startSelectedMode();
            } else {
                dailySchedule.setChecked(false);
                Toast.makeText(this, "每日计划需要允许定时提醒", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionRequestPending || exactAlarmSettingsPending) return;
        if (!dailySchedule.isChecked() && autoStart && directBroadcast == null && hasBroadcastPermissions()) {
            try {
                startDirectBroadcast(readParams());
            } catch (IllegalArgumentException exception) {
                updateBroadcastStatus(exception.getMessage());
            }
        }
    }

    private boolean hasBroadcastPermissions() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    protected void onPause() {
        if (dailySchedule == null || !dailySchedule.isChecked()) stopDirectBroadcast(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (dailySchedule == null || !dailySchedule.isChecked()) stopDirectBroadcast(false);
        super.onDestroy();
    }

    private void onScheduleChanged(boolean enabled) {
        if (enabled) {
            if (startMinute == endMinute) {
                dailySchedule.setChecked(false);
                Toast.makeText(this, "开始和结束时间不能相同", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                readParams();
            } catch (IllegalArgumentException exception) {
                dailySchedule.setChecked(false);
                Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            stopDirectBroadcast(false);
            requestPermissionAndStart();
        } else {
            saveScheduleEnabled(false);
            stopService(new Intent(this, BeaconScheduleService.class));
            BeaconScheduleService.cancelNextBoundary(this);
            autoStart = true;
            requestPermissionAndStart();
        }
    }

    private static String timeLabel(String label, int minuteOfDay) {
        return String.format(Locale.getDefault(), "%s  %02d:%02d", label, minuteOfDay / 60, minuteOfDay % 60);
    }

    static final class BeaconParams {
        final String uuid;
        final int major;
        final int minor;
        final int txPower;

        BeaconParams(String uuid, int major, int minor, int txPower) {
            this.uuid = uuid;
            this.major = major;
            this.minor = minor;
            this.txPower = txPower;
        }

        static BeaconParams from(SharedPreferences prefs) {
            String uuid = prefs.getString(KEY_UUID, DEFAULT_UUID);
            if (!prefs.getBoolean(KEY_DEFAULT_UUID_MIGRATED, false)) {
                SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_DEFAULT_UUID_MIGRATED, true);
                // Older versions saved the original default when they opened the form.
                if (PREVIOUS_DEFAULT_UUID.equalsIgnoreCase(uuid)) {
                    uuid = DEFAULT_UUID;
                    editor.putString(KEY_UUID, uuid);
                }
                editor.apply();
            }
            return new BeaconParams(
                    uuid,
                    prefs.getInt(KEY_MAJOR, DEFAULT_MAJOR),
                    prefs.getInt(KEY_MINOR, DEFAULT_MINOR),
                    prefs.getInt(KEY_TX_POWER, DEFAULT_TX_POWER));
        }

        SharedPreferences.Editor save(SharedPreferences.Editor editor) {
            return editor.putString(KEY_UUID, uuid)
                    .putInt(KEY_MAJOR, major)
                    .putInt(KEY_MINOR, minor)
                    .putInt(KEY_TX_POWER, txPower);
        }
    }
}
