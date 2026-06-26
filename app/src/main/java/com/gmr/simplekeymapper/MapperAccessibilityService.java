package com.gmr.simplekeymapper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class MapperAccessibilityService extends AccessibilityService {

    public static MapperAccessibilityService instance = null;
    private WindowManager windowManager;
    
    public boolean isUiRunning = false;
    private LinearLayout menuLayout;
    private Button mouseLockButton;
    private boolean isMenuExpanded = false;
    private boolean isMouseLocked = false;

    private HashMap<String, int[]> mappedCoordinates = new HashMap<>();
    private HashMap<String, View> activeMappedButtons = new HashMap<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    public void startKeymapperUI() {
        if (isUiRunning) return; // অলরেডি চললে ডুপ্লিকেট ভিউ অ্যাড হবে না
        isUiRunning = true;
        createMainFloatingMenu();
        createMouseLockTrigger();
    }

    public void stopKeymapperUI() {
        if (!isUiRunning) return;
        isUiRunning = false;

        if (menuLayout != null) {
            windowManager.removeView(menuLayout);
            menuLayout = null;
        }
        if (mouseLockButton != null) {
            windowManager.removeView(mouseLockButton);
            mouseLockButton = null;
        }
        for (View v : activeMappedButtons.values()) {
            if (v != null) windowManager.removeView(v);
        }
        activeMappedButtons.clear();
        mappedCoordinates.clear();
        isMenuExpanded = false;
        isMouseLocked = false;
    }

    private void createMainFloatingMenu() {
        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setBackgroundColor(Color.parseColor("#DD000000"));
        menuLayout.setPadding(10, 10, 10, 10);

        Button mainBtn = new Button(this);
        mainBtn.setText("MENU ☰");
        mainBtn.setBackgroundColor(Color.BLUE);
        mainBtn.setTextColor(Color.WHITE);
        menuLayout.addView(mainBtn);

        LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setVisibility(View.GONE);

        rowsContainer.addView(createMenuRow("Left Click"));
        rowsContainer.addView(createMenuRow("Right Click"));
        rowsContainer.addView(createMenuRow("Scroll Click"));
        rowsContainer.addView(createMenuRow("Scroll Up"));
        rowsContainer.addView(createMenuRow("Scroll Down"));

        menuLayout.addView(rowsContainer);

        mainBtn.setOnClickListener(v -> {
            if (isMenuExpanded) {
                rowsContainer.setVisibility(View.GONE);
                isMenuExpanded = false;
            } else {
                rowsContainer.setVisibility(View.VISIBLE);
                isMenuExpanded = true;
            }
        });

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 200;

        windowManager.addView(menuLayout, params);
    }

    private Button createMenuRow(String actionName) {
        Button rowBtn = new Button(this);
        rowBtn.setText("+ " + actionName);
        rowBtn.setTextColor(Color.GREEN);
        rowBtn.setBackgroundColor(Color.TRANSPARENT);
        rowBtn.setOnClickListener(v -> createTransparentMappedButton(actionName));
        return rowBtn;
    }

    // কাস্টম বাটন তৈরি এবং লং-প্রেস করে রিমুভ করার লজিক
    private void createTransparentMappedButton(final String actionName) {
        if (activeMappedButtons.containsKey(actionName)) return;

        final TextView btnView = new TextView(this);
        btnView.setText(actionName);
        btnView.setTextColor(Color.WHITE);
        btnView.setGravity(Gravity.CENTER);
        btnView.setPadding(20, 20, 20, 20);
        btnView.setBackgroundColor(Color.parseColor("#80FF0000")); // ৫০% ট্রান্সপারেন্ট লাল

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 400;
        params.y = 400;

        // ড্র্যাগ অ্যান্ড ড্রপ লজিক
        btnView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return false; // Long Click সচল রাখার জন্য false দিতে হবে
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(btnView, params);
                        mappedCoordinates.put(actionName, new int[]{params.x + 60, params.y + 60});
                        return true;
                }
                return false;
            }
        });

        // ফিক্সড: বাটনটি রিমুভ করার জন্য লং-প্রেস (চেপে ধরে রাখুন) লজিক
        btnView.setOnLongClickListener(v -> {
            windowManager.removeView(btnView);
            activeMappedButtons.remove(actionName);
            mappedCoordinates.remove(actionName);
            Toast.makeText(this, actionName + " Removed Successfully", Toast.LENGTH_SHORT).show();
            return true;
        });

        windowManager.addView(btnView, params);
        activeMappedButtons.put(actionName, btnView);
        mappedCoordinates.put(actionName, new int[]{params.x + 60, params.y + 60});
        Toast.makeText(this, "Long-press button to remove it", Toast.LENGTH_SHORT).show();
    }

    // ফিক্সড: মাউস লক করার ক্ষুদ্র বাটন (যা গেমের টাচ ব্লক করবে না)
    private void createMouseLockTrigger() {
        mouseLockButton = new Button(this);
        mouseLockButton.setText("LOCK MOUSE");
        mouseLockButton.setBackgroundColor(Color.RED);
        mouseLockButton.setTextColor(Color.WHITE);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                0, // প্রথমে ফোকাসড থাকবে যাতে মাউস ক্লিক ধরতে পারে
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 20;

        // মাউস লক মোড (Pointer Capture Engine)
        mouseLockButton.setOnClickListener(v -> {
            if (!isMouseLocked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.A_ROOT || Build.VERSION.SDK_INT >= 26) {
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                    windowManager.updateViewLayout(mouseLockButton, params);
                    mouseLockButton.requestFocus();
                    mouseLockButton.requestPointerCapture(); // মাউস কার্সার উধাও এবং লক হবে
                    mouseLockButton.setText("LOCKED (Right-Click to Unlock)");
                    mouseLockButton.setBackgroundColor(Color.getHtmlColor("green"));
                    isMouseLocked = true;
                }
            }
        });

        // লকড অবস্থায় মাউস মুভমেন্ট এবং কাস্টম ক্লিকের আসল ট্র্যাকিং
        mouseLockButton.setOnCapturedPointerListener((v, event) -> {
            // ১. মাউস নড়াচড়া (Look Around Camera)
            float x = event.getX();
            float y = event.getY();
            if (x != 0 || y != 0) {
                simulateMouseLook(x * 8, y * 8);
                return true;
            }

            // ২. মাউস হুইল স্ক্রল ট্র্যাকিং
            float scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (scroll > 0) { triggerMappedClick("Scroll Up"); return true; }
            else if (scroll < 0) { triggerMappedClick("Scroll Down"); return true; }

            // ৩. মাউস বাটন ক্লিক ট্র্যাকিং
            int buttonState = event.getButtonState();
            if ((buttonState & MotionEvent.BUTTON_PRIMARY) != 0) {
                triggerMappedClick("Left Click");
                return true;
            } else if ((buttonState & MotionEvent.BUTTON_SECONDARY) != 0) {
                // রাইট ক্লিক করলে মাউস আনলক হয়ে যাবে এবং গেম নরমাল হয়ে যাবে
                if (Build.VERSION.SDK_INT >= 26) {
                    mouseLockButton.releasePointerCapture();
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(mouseLockButton, params);
                    mouseLockButton.setText("LOCK MOUSE");
                    mouseLockButton.setBackgroundColor(Color.RED);
                    isMouseLocked = false;
                }
                return true;
            } else if ((buttonState & MotionEvent.BUTTON_TERTIARY) != 0) {
                triggerMappedClick("Scroll Click");
                return true;
            }
            return false;
        });

        windowManager.addView(mouseLockButton, params);
    }

    private void triggerMappedClick(String actionName) {
        if (!mappedCoordinates.containsKey(actionName)) return;
        int[] coords = mappedCoordinates.get(actionName);
        if (coords != null) {
            Path clickPath = new Path();
            clickPath.moveTo(coords[0], coords[1]);
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 15));
            dispatchGesture(gestureBuilder.build(), null, null);
        }
    }

    private void simulateMouseLook(float deltaX, float deltaY) {
        Path swipePath = new Path();
        swipePath.moveTo(800f, 1000f); // স্ক্রিনের সেন্টার পয়েন্ট
        swipePath.lineTo(800f + deltaX, 1000f + deltaY);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 25));
        dispatchGesture(gestureBuilder.build(), null, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopKeymapperUI();
        instance = null;
    }
}
