package com.he.lagswitch;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.FileInputStream;

public class MainActivity extends AppCompatActivity {

    private static final int VPN_REQUEST_CODE = 0x0F;
    private static final int OVERLAY_REQUEST_CODE = 0x0E;
    private boolean isLagging = false;
    
    private Button btnToggle;
    private TextView txtStatus;
    
    private WindowManager windowManager;
    private Button floatingBtn;
    private WindowManager.LayoutParams floatingParams;
    private boolean isFloatingAdded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        applyAntiBan(); // Chạy Anti-Ban ngay khi mở app

        // === GIAO DIỆN CHÍNH ===
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#121212"));
        layout.setPadding(50, 50, 50, 50);

        txtStatus = new TextView(this);
        txtStatus.setText("Network Optimizer\nStatus: Normal");
        txtStatus.setTextColor(Color.WHITE);
        txtStatus.setTextSize(20);
        txtStatus.setGravity(Gravity.CENTER);
        txtStatus.setPadding(0, 0, 0, 80);
        layout.addView(txtStatus);

        btnToggle = new Button(this);
        btnToggle.setText("BẬT NÚT NỔI (CẦN QUYỀN)");
        btnToggle.setTextSize(18);
        btnToggle.setBackgroundColor(Color.parseColor("#2196F3"));
        btnToggle.setTextColor(Color.WHITE);
        layout.addView(btnToggle);

        setContentView(layout);

        // === SỰ KIỆN NÚT CHÍNH ===
        btnToggle.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(MainActivity.this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_REQUEST_CODE);
            } else {
                showFloatingButton();
                Toast.makeText(this, "Đã bật nút nổi!", Toast.LENGTH_SHORT).show();
                moveTaskToBack(true); 
            }
        });
    }

    // === ANTI-BAN (ẨN APP) ===
    private void applyAntiBan() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                PackageManager pm = getPackageManager();
                ComponentName stealthComponent = new ComponentName(this, MainActivity.class);
                // Ẩn icon app khỏi màn hình chính và danh sách ứng dụng
                pm.setComponentEnabledSetting(stealthComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == OVERLAY_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                showFloatingButton();
                Toast.makeText(this, "Cấp quyền thành công! Nút nổi đã bật.", Toast.LENGTH_SHORT).show();
                moveTaskToBack(true); 
            } else {
                Toast.makeText(this, "Chưa cấp quyền hiển thị!", Toast.LENGTH_SHORT).show();
            }
        } 
        else if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startLag();
            updateFloatingBtnUI(true);
        }
    }

    // === NÚT NỔI (FLOATING BUTTON) ===
    private void showFloatingButton() {
        if (isFloatingAdded) return;

        floatingBtn = new Button(this);
        floatingBtn.setText("NET OFF");
        floatingBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        floatingBtn.setTextColor(Color.WHITE);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        floatingParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        floatingParams.gravity = Gravity.TOP | Gravity.START;
        floatingParams.x = 100;
        floatingParams.y = 200;

        // Kéo thả nút
        floatingBtn.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = floatingParams.x;
                        initialY = floatingParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                        
                        floatingParams.x = initialX + dx;
                        floatingParams.y = initialY + dy;
                        windowManager.updateViewLayout(floatingBtn, floatingParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            if (!isLagging) {
                                Intent intent = VpnService.prepare(MainActivity.this);
                                if (intent != null) {
                                    startActivityForResult(intent, VPN_REQUEST_CODE);
                                } else {
                                    onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
                                }
                            } else {
                                stopLag();
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingBtn, floatingParams);
        isFloatingAdded = true;
        finish(); 
    }

    private void updateFloatingBtnUI(boolean lagging) {
        if (floatingBtn != null) {
            floatingBtn.setText(lagging ? "NET ON" : "NET OFF");
            floatingBtn.setBackgroundColor(lagging ? Color.RED : Color.GREEN);
        }
    }

    // === BẬT/TẮT MẠNG ===
    private void startLag() {
        Intent intent = new Intent(this, LagVpnService.class);
        startService(intent);
        isLagging = true;
        if (txtStatus != null) {
            txtStatus.setText("Network Optimizer\nStatus: LAGGING");
            txtStatus.setTextColor(Color.YELLOW);
        }
        updateFloatingBtnUI(true);
    }

    private void stopLag() {
        Intent intent = new Intent(this, LagVpnService.class);
        stopService(intent);
        isLagging = false;
        if (txtStatus != null) {
            txtStatus.setText("Network Optimizer\nStatus: Normal");
            txtStatus.setTextColor(Color.WHITE);
        }
        updateFloatingBtnUI(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFloatingAdded && floatingBtn != null) {
            windowManager.removeView(floatingBtn);
            isFloatingAdded = false;
        }
    }

    // ==========================================
    // VPN SERVICE (BỌC ANTI-BAN TÊN VPN)
    // ==========================================
    public static class LagVpnService extends VpnService implements Runnable {
        private Thread mThread;
        private ParcelFileDescriptor mInterface;
        private boolean running = true;

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (mThread != null) return START_STICKY;
            mThread = new Thread(this, "LagSwitchThread");
            mThread.start();
            return START_STICKY;
        }

        @Override
        public void run() {
            try {
                Builder builder = new Builder();
                // Đổi tên VPN thành "Network Optimizer" để lừa Anti-cheat
                builder.setSession("Network Optimizer");
                builder.addAddress("10.0.0.2", 32);
                builder.addRoute("0.0.0.0", 0); 

                mInterface = builder.establish();
                if (mInterface == null) return;

                FileInputStream in = new FileInputStream(mInterface.getFileDescriptor());
                byte[] packet = new byte[32767];

                while (running) {
                    if (in.read(packet) <= 0) break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDestroy() {
            running = false;
            try {
                if (mThread != null) mThread.interrupt();
                if (mInterface != null) mInterface.close();
            } catch (Exception ignored) {}
            super.onDestroy();
        }
    }
}
