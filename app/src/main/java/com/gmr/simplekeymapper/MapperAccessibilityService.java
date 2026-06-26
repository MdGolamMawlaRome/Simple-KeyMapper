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

import java.util.HashMap;

public class MapperAccessibilityService extends AccessibilityService {

    public static MapperAccessibilityService instance = null;
    private WindowManager windowManager;
    
    // ফ্লোটিং উইন্ডো ভিউসমূহ
    private LinearLayout menuLayout;
    private View mouseTrackerOverlay;
    private boolean isMenuExpanded = false;

    // বাটনগুলোর পজিশন সেভ রাখার জন্য ম্যাপ (X, Y)
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

    // ফ্লোটিং মেনু এবং মাউস ট্র্যাকার উইন্ডো চালু করার মেথড
    public void startKeymapperUI() {
        createMainFloatingMenu();
        createMouseTrackerOverlay();
    }

    public void stopKeymapperUI() {
        if (menuLayout != null) {
            windowManager.removeView(menuLayout);
            menuLayout = null;
        }
        if (mouseTrackerOverlay != null) {
            windowManager.removeView(mouseTrackerOverlay);
            mouseTrackerOverlay = null;
        }
        // স্ক্রিনে থাকা সমস্ত কাস্টম বাটন রিমুভ করা
        for (View v : activeMappedButtons.values()) {
            if (v != null) windowManager.removeView(v);
        }
        activeMappedButtons.clear();
        mappedCoordinates.clear();
        isMenuExpanded = false;
    }

    // ১. এক্সপ্যান্ডেবল ফ্লোটিং মেনু তৈরি
    private void createMainFloatingMenu() {
        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setBackgroundColor(Color.parseColor("#CC000000")); // হালকা কালো ব্যাকগ্রাউন্ড
        menuLayout.setPadding(10, 10, 10, 10);

        Button mainBtn = new Button(this);
        mainBtn.setText("MENU ☰");
        mainBtn.setBackgroundColor(Color.BLUE);
        mainBtn.setTextColor(Color.WHITE);
        menuLayout.addView(mainBtn);

        // মেনুর ভেতরের রো (Rows) সমূহ ধারণ করার লেআউট
        LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setVisibility(View.GONE); // প্রথমে হাইড থাকবে

        // রো বা লাইনের তালিকা যোগ করা
        rowsContainer.addView(createMenuRow("Left Click"));
        rowsContainer.addView(createMenuRow("Right Click"));
        rowsContainer.addView(createMenuRow("Scroll Click"));
        rowsContainer.addView(createMenuRow("Scroll Up"));
        rowsContainer.addView(createMenuRow("Scroll Down"));

        menuLayout.addView(rowsContainer);

        // ক্লিক করলে এক্সপ্যান্ড বা কোলাপ্স হবে
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

    // মেনুর ভেতরের প্রতিটি লাইনের (Row) ডিজাইন
    private Button createMenuRow(String actionName) {
        Button rowBtn = new Button(this);
        rowBtn.setText("+ " + actionName);
        rowBtn.setTextSize(12);
        rowBtn.setTextColor(Color.GREEN);
        rowBtn.setBackgroundColor(Color.TRANSPARENT);
        
        rowBtn.setOnClickListener(v -> {
            createTransparentMappedButton(actionName);
        });
        return rowBtn;
    }

    // ২. ৫০% ট্রান্সপারেন্ট ড্র্যাগেবল বাটন তৈরি (যা স্ক্রিনের ওপর ড্র্যাগ করে বসানো যাবে)
    private void createTransparentMappedButton(final String actionName) {
        if (activeMappedButtons.containsKey(actionName)) return; // অলরেডি স্ক্রিনে থাকলে আর তৈরি হবে না

        final TextView btnView = new TextView(this);
        btnView.setText(actionName);
        btnView.setTextColor(Color.WHITE);
        btnView.setGravity(Gravity.CENTER);
        btnView.setPadding(15, 15, 15, 15);
        
        // ৫০% ট্রান্সপারেন্ট ব্যাকগ্রাউন্ড সেটআপ
        btnView.setBackgroundColor(Color.parseColor("#80FF0000")); // ৫০% আলফা সহ লাল রঙ

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
        params.x = 300;
        params.y = 500;

        // বাটনটি যাতে স্ক্রিনের যেকোনো জায়গায় ড্র্যাগ (টেনে নিয়ে) করে বসানো যায় তার লজিক
        btnView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(btnView, params);
                        
                        // রিয়েল-টাইম কোঅর্ডিনেট সেভ রাখা হচ্ছে ক্লিক জেনারেট করার জন্য
                        mappedCoordinates.put(actionName, new int[]{params.x + 50, params.y + 50});
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(btnView, params);
        activeMappedButtons.put(actionName, btnView);
        mappedCoordinates.put(actionName, new int[]{params.x + 50, params.y + 50});
    }

    // ৩. অদৃশ্য মাউস ট্র্যাকার উইন্ডো (যা মাউস নাড়ালে ক্যামেরা ঘুরাবে এবং মাউস ক্লিকে ম্যাপ করা বাটনে ট্যাপ করবে)
    private void createMouseTrackerOverlay() {
        mouseTrackerOverlay = new View(this);
        
        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                200, 200, // স্ক্রিনের মাঝখানে ছোট ট্র্যাকিং এরিয়া
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        // মাউস নড়াচড়া (Mouse Look) এবং স্ক্রল ট্র্যাকিং লজিক
        mouseTrackerOverlay.setOnGenericMotionListener((v, event) -> {
            // ক্যামেরা মুভমেন্ট ট্র্যাকিং
            float x = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
            float y = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);
            if (x != 0 || y != 0) {
                simulateMouseLook(x * 12, y * 12);
                return true;
            }

            // মাউস স্ক্রল হুইল ট্র্যাকিং
            float scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (scroll > 0) {
                triggerMappedClick("Scroll Up");
                return true;
            } else if (scroll < 0) {
                triggerMappedClick("Scroll Down");
                return true;
            }

            // মাউসের রাইট এবং মিডল ক্লিক ট্র্যাকিং
            int buttonState = event.getButtonState();
            if ((buttonState & MotionEvent.BUTTON_SECONDARY) != 0) {
                triggerMappedClick("Right Click");
                return true;
            } else if ((buttonState & MotionEvent.BUTTON_TERTIARY) != 0) {
                triggerMappedClick("Scroll Click");
                return true;
            }

            return false;
        });

        // মাউসের লেফট (প্রাইমারি) ক্লিক ট্র্যাকিং
        mouseTrackerOverlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                triggerMappedClick("Left Click");
                return true;
            }
            return false;
        });

        windowManager.addView(mouseTrackerOverlay, params);
    }

    // নির্দিষ্ট ম্যাপ করা বাটনের পজিশনে অটোমেটিক ট্যাপ/ক্লিক পাঠানোর মেথড
    private void triggerMappedClick(String actionName) {
        if (!mappedCoordinates.containsKey(actionName)) return;

        int[] coords = mappedCoordinates.get(actionName);
        if (coords != null) {
            Path clickPath = new Path();
            clickPath.moveTo(coords[0], coords[1]);

            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 20));
            dispatchGesture(gestureBuilder.build(), null, null);
        }
    }

    // মাউস নাড়ালে ক্যামেরা স্মুথলি ঘোরানোর মেথড
    private void simulateMouseLook(float deltaX, float deltaY) {
        Path swipePath = new Path();
        swipePath.moveTo(700f, 1000f); // স্ক্রিনের স্ট্যান্ডার্ড সেন্টার পয়েন্ট
        swipePath.lineTo(700f + deltaX, 1000f + deltaY);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 30));
        dispatchGesture(gestureBuilder.build(), null, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopKeymapperUI();
        instance = null;
    }
}
