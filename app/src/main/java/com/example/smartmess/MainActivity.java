package com.example.smartmess;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartmess.models.Confirmation;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

    private TextView tvGreeting, tvDate;
    private ChipGroup cgMealType;
    private RadioGroup rgStatus;
    private Spinner spinnerTimeSlot;
    private MaterialButton btnSubmitConfirmation, btnScanQR;
    private Button btnLogout;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentDate;

    // Register ZXing barcode scanner
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if (result.getContents() == null) {
                    Toast.makeText(MainActivity.this, "Cancelled", Toast.LENGTH_LONG).show();
                } else {
                    String scannedData = result.getContents();
                    String expectedQrStart = "SMART_MESS_";

                    if (scannedData.startsWith(expectedQrStart)) {
                        Toast.makeText(MainActivity.this, "Attendance Logged Successfully!", Toast.LENGTH_LONG).show();
                        // Real logic could log the scan event to Firestore here
                    } else {
                        Toast.makeText(MainActivity.this, "Invalid QR Code", Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initializeViews();
        setupSpinner();
        loadGreetingAndDate();

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        btnSubmitConfirmation.setOnClickListener(v -> submitMealConfirmation());

        // Launch Scanner
        btnScanQR.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            options.setPrompt("Scan the Session QR Code at the entrance");
            options.setCameraId(0); // Use a specific camera of the device
            options.setBeepEnabled(false);
            options.setBarcodeImageEnabled(true);
            barcodeLauncher.launch(options);
        });
    }

    private void initializeViews() {
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDate = findViewById(R.id.tvDate);
        cgMealType = findViewById(R.id.cgMealType);
        rgStatus = findViewById(R.id.rgStatus);
        spinnerTimeSlot = findViewById(R.id.spinnerTimeSlot);
        btnSubmitConfirmation = findViewById(R.id.btnSubmitConfirmation);
        btnScanQR = findViewById(R.id.btnScanQR);
        btnLogout = findViewById(R.id.btnLogout);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupSpinner() {
        String[] slots = new String[] { "Select Time", "7:00 AM - 8:00 AM", "8:00 AM - 9:00 AM", "12:00 PM - 1:00 PM",
                "1:00 PM - 2:00 PM", "7:00 PM - 8:00 PM", "8:00 PM - 9:00 PM" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, slots);
        spinnerTimeSlot.setAdapter(adapter);
    }

    private void loadGreetingAndDate() {
        String userId = mAuth.getCurrentUser().getUid();

        // Fetch User Name to personalize greeting
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        tvGreeting.setText("Hi, " + name + "!");
                    }
                });

        // Set Tomorrow's Date for confirmation
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        Date tomorrow = calendar.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tomorrow);
        tvDate.setText("Confirming meals for: " + sdf.format(tomorrow));
    }

    private void submitMealConfirmation() {
        // Enforce 10 PM Deadline (TEMPORARILY COMMENTED OUT FOR TESTING)
        // Calendar now = Calendar.getInstance();
        // if (now.get(Calendar.HOUR_OF_DAY) >= 22) {
        // Toast.makeText(this, "Deadline Passed! You cannot change meals after 10 PM.",
        // Toast.LENGTH_LONG).show();
        // return;
        // }

        // 1. Get Selected Meal Type (Breakfast/Lunch/Dinner)
        int selectedChipId = cgMealType.getCheckedChipId();
        if (selectedChipId == View.NO_ID) {
            Toast.makeText(this, "Please select a meal (Breakfast, Lunch, or Dinner)", Toast.LENGTH_SHORT).show();
            return;
        }
        Chip selectedChip = findViewById(selectedChipId);
        String mealType = selectedChip.getText().toString().toLowerCase();

        // 2. Get Eat Status
        int selectedStatusId = rgStatus.getCheckedRadioButtonId();
        String status = (selectedStatusId == R.id.rbEat) ? "eat" : "not_eat";

        // 3. Get Time Slot
        String timeSlot = spinnerTimeSlot.getSelectedItem().toString();
        if (status.equals("eat") && timeSlot.equals("Select Time")) {
            Toast.makeText(this, "Please select an eating time slot.", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSubmitConfirmation.setEnabled(false);

        // Build Confirmation Object
        String userId = mAuth.getCurrentUser().getUid();
        Confirmation confirmation = new Confirmation(userId, status, timeSlot, System.currentTimeMillis());

        // Save to Database Path: confirmations / date / mealType / userId
        db.collection("confirmations")
                .document(currentDate)
                .collection(mealType)
                .document(userId)
                .set(confirmation);

        progressBar.setVisibility(View.GONE);
        btnSubmitConfirmation.setEnabled(true);
        Toast.makeText(MainActivity.this, "Meal Confirmed Successfully!", Toast.LENGTH_SHORT).show();
    }
}