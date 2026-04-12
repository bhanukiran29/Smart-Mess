package com.example.smartmess;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class StaffActivity extends AppCompatActivity {

    private TextInputEditText etPreparedPlates, etWastedKg;
    private MaterialButton btnGenerateQR, btnSubmitWaste, btnLogout;
    private TextView tvGreeting, tvQrCaption;
    private TabLayout tabMeals;
    private MaterialCardView cvQrWrapper;
    private ImageView ivQrCode;
    private View vPulseDot;

    private TextView tvCountBfast, tvCapBfast, tvCountLunch, tvCapLunch, tvCountDinner, tvCapDinner;
    private ProgressBar pbBfast, pbLunch, pbDinner;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentDate;
    private String selectedMeal = "breakfast";
    private ListenerRegistration counterListener;

    private int lastBfast = -1, lastLunch = -1, lastDinner = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initializeViews();
        loadGreetingAndDate();
        startPulsingDot();
        listenToLiveCapacity();

        tabMeals.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: selectedMeal = "breakfast"; break;
                    case 1: selectedMeal = "lunch"; break;
                    case 2: selectedMeal = "dinner"; break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(StaffActivity.this, LoginActivity.class));
            finish();
        });

        btnGenerateQR.setOnClickListener(v -> generateQRCode());
        btnSubmitWaste.setOnClickListener(v -> submitWasteData());
    }

    private void generateQRCode() {
        try {
            String qrData = "SMART_MESS_" + selectedMeal + "_" + currentDate;
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(qrData, BarcodeFormat.QR_CODE, 600, 600);

            ivQrCode.setImageBitmap(bitmap);
            
            String mealCap = selectedMeal.substring(0,1).toUpperCase() + selectedMeal.substring(1);
            tvQrCaption.setText(mealCap + " • " + currentDate);
            
            cvQrWrapper.setVisibility(View.VISIBLE);
            tvQrCaption.setVisibility(View.VISIBLE);

            // Pop-in animation
            cvQrWrapper.setScaleX(0);
            cvQrWrapper.setScaleY(0);
            cvQrWrapper.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator())
                    .start();

            Toast.makeText(this, mealCap + " QR Generated!", Toast.LENGTH_SHORT).show();
        } catch (WriterException e) {
            Toast.makeText(this, "Error generating QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeViews() {
        tvGreeting = findViewById(R.id.tvGreeting);
        etPreparedPlates = findViewById(R.id.etPreparedPlates);
        etWastedKg = findViewById(R.id.etWastedKg);
        btnGenerateQR = findViewById(R.id.btnGenerateQR);
        btnSubmitWaste = findViewById(R.id.btnSubmitWaste);
        btnLogout = findViewById(R.id.btnLogout);
        ivQrCode = findViewById(R.id.ivQrCode);
        cvQrWrapper = findViewById(R.id.cvQrWrapper);
        tvQrCaption = findViewById(R.id.tvQrCaption);
        tabMeals = findViewById(R.id.tabMeals);
        vPulseDot = findViewById(R.id.vPulseDot);

        tvCountBfast = findViewById(R.id.tvCountBfast);
        tvCapBfast = findViewById(R.id.tvCapBfast);
        tvCountLunch = findViewById(R.id.tvCountLunch);
        tvCapLunch = findViewById(R.id.tvCapLunch);
        tvCountDinner = findViewById(R.id.tvCountDinner);
        tvCapDinner = findViewById(R.id.tvCapDinner);

        pbBfast = findViewById(R.id.pbBfast);
        pbLunch = findViewById(R.id.pbLunch);
        pbDinner = findViewById(R.id.pbDinner);

        tabMeals.addTab(tabMeals.newTab().setText("Breakfast"));
        tabMeals.addTab(tabMeals.newTab().setText("Lunch"));
        tabMeals.addTab(tabMeals.newTab().setText("Snacks"));
        tabMeals.addTab(tabMeals.newTab().setText("Dinner"));
    }

    private void loadGreetingAndDate() {
        String userId = mAuth.getCurrentUser().getUid();
        currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        tvGreeting.setText("Good morning, " + (name != null ? name.split(" ")[0] : "Staff"));
                    }
                });
    }

    private void startPulsingDot() {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(vPulseDot, "alpha", 1f, 0.3f, 1f);
        pulse.setDuration(1200);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.start();
    }

    private void listenToLiveCapacity() {
        counterListener = db.collection("meal_capacity").document(currentDate)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;

                    int[] bfastVals = getVals(doc, "breakfast");
                    int[] lunchVals = getVals(doc, "lunch");
                    int[] dinnerVals = getVals(doc, "dinner");

                    updateCounterNode(tvCountBfast, tvCapBfast, pbBfast, bfastVals[0], bfastVals[1], 1);
                    updateCounterNode(tvCountLunch, tvCapLunch, pbLunch, lunchVals[0], lunchVals[1], 2);
                    updateCounterNode(tvCountDinner, tvCapDinner, pbDinner, dinnerVals[0], dinnerVals[1], 3);
                });
    }

    private int[] getVals(DocumentSnapshot doc, String prefix) {
        Long scanned = doc.getLong(prefix + "_scanned");
        Long cap = doc.getLong(prefix + "_capacity");
        return new int[]{scanned != null ? scanned.intValue() : 0, cap != null ? cap.intValue() : 0};
    }

    private void updateCounterNode(TextView tvCount, TextView tvCap, ProgressBar pb, int scanned, int capacity, int nodeType) {
        tvCap.setText(scanned + "/" + capacity);
        int percent = capacity > 0 ? (int) (((double) scanned / capacity) * 100) : 0;
        pb.setProgress(percent);

        int colorCode = Color.parseColor("#00A34F"); // Green
        if (percent > 95) colorCode = Color.parseColor("#EF4444"); // Red
        else if (percent >= 80) colorCode = Color.parseColor("#F59E0B"); // Amber
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(colorCode));

        boolean shouldAnimate = false;
        if (nodeType == 1 && lastBfast != scanned) { shouldAnimate = lastBfast != -1; lastBfast = scanned; }
        if (nodeType == 2 && lastLunch != scanned) { shouldAnimate = lastLunch != -1; lastLunch = scanned; }
        if (nodeType == 3 && lastDinner != scanned) { shouldAnimate = lastDinner != -1; lastDinner = scanned; }

        if (shouldAnimate) {
            tvCount.animate().translationY(-20f).alpha(0f).setDuration(150).withEndAction(() -> {
                tvCount.setText(String.valueOf(scanned));
                tvCount.setTranslationY(20f);
                tvCount.animate().translationY(0f).alpha(1f).setDuration(150).setInterpolator(new OvershootInterpolator()).start();
            }).start();
        } else {
            tvCount.setText(String.valueOf(scanned));
        }
    }

    private void submitWasteData() {
        String platesStr = etPreparedPlates.getText().toString().trim();
        String wasteStr = etWastedKg.getText().toString().trim();

        if (TextUtils.isEmpty(platesStr)) { etPreparedPlates.setError("Enter plates count"); return; }
        if (TextUtils.isEmpty(wasteStr)) { etWastedKg.setError("Enter wasted kg"); return; }

        btnSubmitWaste.setEnabled(false);
        btnSubmitWaste.setText("Submitting...");

        Map<String, Object> wasteLog = new HashMap<>();
        wasteLog.put("timestamp", System.currentTimeMillis());
        wasteLog.put("mealType", selectedMeal);
        wasteLog.put("preparedPlates", Integer.parseInt(platesStr));
        wasteLog.put("wastedKg", Double.parseDouble(wasteStr));
        wasteLog.put("staffId", mAuth.getCurrentUser().getUid());

        String logId = currentDate + "_" + selectedMeal;

        db.collection("waste_logs").document(logId).set(wasteLog)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Waste logged for " + selectedMeal, Toast.LENGTH_SHORT).show();
                    etPreparedPlates.setText("");
                    etWastedKg.setText("");
                    btnSubmitWaste.setEnabled(true);
                    btnSubmitWaste.setText("Submit Waste Data");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to log data", Toast.LENGTH_SHORT).show();
                    btnSubmitWaste.setEnabled(true);
                    btnSubmitWaste.setText("Submit Waste Data");
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (counterListener != null) counterListener.remove();
    }
}
