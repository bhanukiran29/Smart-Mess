package com.example.smartmess;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartmess.models.WasteData;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import android.graphics.Bitmap;
import android.widget.ImageView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StaffActivity extends AppCompatActivity {

    private TextInputEditText etPreparedPlates, etWastedKg;
    private MaterialButton btnGenerateQR, btnSubmitWaste;
    private Button btnLogout;
    private ProgressBar progressBar;
    private TextView tvGreeting;
    private Spinner spinnerMealType;
    private ImageView ivQrCode;

    // Live counter TextViews
    private TextView tvBreakfastCounter, tvLunchCounter, tvDinnerCounter;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentDate;
    private String selectedMeal = "breakfast"; // default
    private ListenerRegistration counterListener;

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
        listenToLiveCapacity();

        spinnerMealType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedMeal = parent.getItemAtPosition(pos).toString().toLowerCase();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(StaffActivity.this, LoginActivity.class));
            finish();
        });

        // Generate dynamic QR Code for the specific meal + date
        btnGenerateQR.setOnClickListener(v -> generateQRCode());

        btnSubmitWaste.setOnClickListener(v -> submitWasteData());
    }

    private void generateQRCode() {
        try {
            // QR format: SMART_MESS_{mealType}_{date}
            // e.g.  SMART_MESS_lunch_2025-03-12
            String qrData = "SMART_MESS_" + selectedMeal + "_" + currentDate;

            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(qrData, BarcodeFormat.QR_CODE, 600, 600);

            ivQrCode.setImageBitmap(bitmap);
            ivQrCode.setVisibility(View.VISIBLE);

            Toast.makeText(this, selectedMeal.toUpperCase() + " QR Generated!", Toast.LENGTH_SHORT).show();
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
        progressBar = findViewById(R.id.progressBar);
        ivQrCode = findViewById(R.id.ivQrCode);
        spinnerMealType = findViewById(R.id.spinnerMealType);
        tvBreakfastCounter = findViewById(R.id.tvBreakfastCounter);
        tvLunchCounter     = findViewById(R.id.tvLunchCounter);
        tvDinnerCounter    = findViewById(R.id.tvDinnerCounter);

        ArrayAdapter<String> mealAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Breakfast", "Lunch", "Dinner"});
        spinnerMealType.setAdapter(mealAdapter);
    }

    private void loadGreetingAndDate() {
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        tvGreeting.setText("Hi, " + name + " (Staff)");
                    }
                });

        currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    // -----------------------------------------------------------------------
    // Live Capacity Counter — real-time Firestore listener
    // Firestore path: meal_capacity/{date}  →
    //   { breakfast_scanned: 0, breakfast_capacity: 90,
    //     lunch_scanned: 0,     lunch_capacity: 120,
    //     dinner_scanned: 0,    dinner_capacity: 80 }
    // -----------------------------------------------------------------------
    private void listenToLiveCapacity() {
        counterListener = db.collection("meal_capacity").document(currentDate)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    updateCounter(tvBreakfastCounter, snapshot.getLong("breakfast_scanned"),
                            snapshot.getLong("breakfast_capacity"));
                    updateCounter(tvLunchCounter, snapshot.getLong("lunch_scanned"),
                            snapshot.getLong("lunch_capacity"));
                    updateCounter(tvDinnerCounter, snapshot.getLong("dinner_scanned"),
                            snapshot.getLong("dinner_capacity"));
                });
    }

    private void updateCounter(TextView tv, Long scanned, Long capacity) {
        if (capacity == null || capacity == 0) {
            tv.setText("Not set");
            tv.setTextColor(getColor(android.R.color.darker_gray));
            return;
        }
        long s = scanned != null ? scanned : 0;
        String label = s + " / " + capacity;
        tv.setText(label);
        // Colour code: green if plenty of space, orange if nearly full, red if full
        if (s >= capacity) {
            tv.setTextColor(getColor(android.R.color.holo_red_dark));
        } else if (s >= capacity * 0.85) {
            tv.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            tv.setTextColor(getColor(android.R.color.holo_green_dark));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (counterListener != null) counterListener.remove();
    }

    private void submitWasteData() {
        String preparedStr = etPreparedPlates.getText().toString().trim();
        String wastedStr = etWastedKg.getText().toString().trim();

        if (TextUtils.isEmpty(preparedStr)) {
            etPreparedPlates.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(wastedStr)) {
            etWastedKg.setError("Required");
            return;
        }

        int preparedPlates = Integer.parseInt(preparedStr);
        double wastedKg = Double.parseDouble(wastedStr);

        progressBar.setVisibility(View.VISIBLE);
        btnSubmitWaste.setEnabled(false);

        String userId = mAuth.getCurrentUser().getUid();
        WasteData wasteData = new WasteData(preparedPlates, wastedKg, userId);

        // Path: waste / date / general_stats
        db.collection("waste")
                .document(currentDate)
                .set(wasteData);

        progressBar.setVisibility(View.GONE);
        btnSubmitWaste.setEnabled(true);
        Toast.makeText(StaffActivity.this, "Waste Data Saved Successfully!", Toast.LENGTH_LONG).show();
        etPreparedPlates.setText("");
        etWastedKg.setText("");
    }
}
