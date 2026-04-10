package com.example.smartmess;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MenuActivity extends AppCompatActivity {

    private ImageView ivBack;
    private ChipGroup chipGroupDays;
    private LinearLayout llContentContainer;
    
    private TextInputEditText etBreakfast, etLunch, etDinner;
    private TextInputEditText etCapBfast, etCapLunch, etCapDinner;
    private MaterialButton btnSaveMenu;

    private FirebaseFirestore db;
    private String[] weekDates = new String[7];
    private String[] weekLabels = new String[7];
    private int selectedIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        db = FirebaseFirestore.getInstance();

        initializeViews();
        generateWeekDates();
        setupDayChips();

        ivBack.setOnClickListener(v -> finish());
        btnSaveMenu.setOnClickListener(v -> saveMenu());
    }

    private void initializeViews() {
        ivBack = findViewById(R.id.ivBack);
        chipGroupDays = findViewById(R.id.chipGroupDays);
        llContentContainer = findViewById(R.id.llContentContainer);

        etBreakfast = findViewById(R.id.etBreakfast);
        etLunch = findViewById(R.id.etLunch);
        etDinner = findViewById(R.id.etDinner);

        etCapBfast = findViewById(R.id.etCapBfast);
        etCapLunch = findViewById(R.id.etCapLunch);
        etCapDinner = findViewById(R.id.etCapDinner);

        btnSaveMenu = findViewById(R.id.btnSaveMenu);
    }

    private void generateWeekDates() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelFmt = new SimpleDateFormat("EEE", Locale.getDefault());

        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            weekDates[i] = sdf.format(cal.getTime());
            weekLabels[i] = labelFmt.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private void setupDayChips() {
        for (int i = 0; i < 7; i++) {
            Chip chip = new Chip(this);
            chip.setText(weekLabels[i]);
            chip.setCheckable(true);
            chip.setClickable(true);
            
            // Custom styling mimicking pill behavior
            chip.setChipBackgroundColorResource(android.R.color.transparent); // Default material fallback logic
            
            int finalI = i;
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    chip.setChipBackgroundColorResource(android.R.color.transparent);
                    chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#432C7A")));
                    chip.setTextColor(Color.WHITE);
                    switchTab(finalI);
                } else {
                    chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F1F5F9")));
                    chip.setTextColor(Color.parseColor("#64748B"));
                }
            });
            
            chipGroupDays.addView(chip);
        }
        
        // Select tracking start index (Today)
        ((Chip) chipGroupDays.getChildAt(0)).setChecked(true);
    }

    private void switchTab(int index) {
        if (index == selectedIndex && index != 0) return; 

        // Slide Animation logic bridging the ViewPager2-style feel
        llContentContainer.animate()
                .translationX(index > selectedIndex ? -200f : 200f)
                .alpha(0f)
                .setDuration(150)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        selectedIndex = index;
                        btnSaveMenu.setText("Save Menu for " + weekLabels[selectedIndex]);
                        clearFields();
                        loadMenuAndCapacities(weekDates[selectedIndex]);

                        llContentContainer.setTranslationX(index > selectedIndex ? 200f : -200f);
                        llContentContainer.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(200)
                                .setListener(null)
                                .start();
                    }
                }).start();
    }

    private void clearFields() {
        etBreakfast.setText("");
        etLunch.setText("");
        etDinner.setText("");
        etCapBfast.setText("0");
        etCapLunch.setText("0");
        etCapDinner.setText("0");
    }

    private void loadMenuAndCapacities(String date) {
        db.collection("weekly_menu").document(date).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        etBreakfast.setText(doc.getString("breakfast"));
                        etLunch.setText(doc.getString("lunch"));
                        etDinner.setText(doc.getString("dinner"));
                    }
                });

        db.collection("meal_capacity").document(date).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Long bCap = doc.getLong("breakfast_capacity");
                        Long lCap = doc.getLong("lunch_capacity");
                        Long dCap = doc.getLong("dinner_capacity");
                        if (bCap != null) etCapBfast.setText(String.valueOf(bCap));
                        if (lCap != null) etCapLunch.setText(String.valueOf(lCap));
                        if (dCap != null) etCapDinner.setText(String.valueOf(dCap));
                    }
                });
    }

    private void saveMenu() {
        String breakfast = etBreakfast.getText().toString().trim();
        String lunch = etLunch.getText().toString().trim();
        String dinner = etDinner.getText().toString().trim();

        String bCapStr = etCapBfast.getText().toString().trim();
        String lCapStr = etCapLunch.getText().toString().trim();
        String dCapStr = etCapDinner.getText().toString().trim();

        if (breakfast.isEmpty() || lunch.isEmpty() || dinner.isEmpty()) {
            Toast.makeText(this, "Menu fields cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveMenu.setEnabled(false);
        String targetDateStr = weekDates[selectedIndex];

        // Batch upload logic simulating the real world implementation
        Map<String, Object> menuData = new HashMap<>();
        menuData.put("breakfast", breakfast);
        menuData.put("lunch", lunch);
        menuData.put("dinner", dinner);
        menuData.put("timestamp", System.currentTimeMillis());

        Map<String, Object> capacityData = new HashMap<>();
        capacityData.put("breakfast_capacity", bCapStr.isEmpty() ? 0 : Integer.parseInt(bCapStr));
        capacityData.put("lunch_capacity", lCapStr.isEmpty() ? 0 : Integer.parseInt(lCapStr));
        capacityData.put("dinner_capacity", dCapStr.isEmpty() ? 0 : Integer.parseInt(dCapStr));

        // Setting capacities properly logic
        db.collection("weekly_menu").document(targetDateStr).set(menuData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    db.collection("meal_capacity").document(targetDateStr).set(capacityData, SetOptions.merge())
                        .addOnSuccessListener(aVoid2 -> {
                            playSuccessAnimation();
                        });
                })
                .addOnFailureListener(e -> {
                    btnSaveMenu.setEnabled(true);
                    Toast.makeText(this, "Failed to save menu", Toast.LENGTH_SHORT).show();
                });
    }

    private void playSuccessAnimation() {
        int colorFrom = Color.parseColor("#432C7A");
        int colorTo = Color.parseColor("#16A34A");

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(250); // milliseconds
        colorAnimation.addUpdateListener(animator -> btnSaveMenu.setBackgroundTintList(android.content.res.ColorStateList.valueOf((int) animator.getAnimatedValue())));
        colorAnimation.start();

        btnSaveMenu.setText("✅  Saved Successfully");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ValueAnimator revertAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorTo, colorFrom);
            revertAnimation.setDuration(250);
            revertAnimation.addUpdateListener(animator -> btnSaveMenu.setBackgroundTintList(android.content.res.ColorStateList.valueOf((int) animator.getAnimatedValue())));
            revertAnimation.start();
            
            btnSaveMenu.setText("Save Menu for " + weekLabels[selectedIndex]);
            btnSaveMenu.setEnabled(true);
        }, 1500);
    }
}
