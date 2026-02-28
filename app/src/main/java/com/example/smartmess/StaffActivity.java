package com.example.smartmess;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartmess.models.WasteData;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

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

    private ImageView ivQrCode;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentDate;

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

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(StaffActivity.this, LoginActivity.class));
            finish();
        });

        // Generate dynamic QR Code for the specific date
        btnGenerateQR.setOnClickListener(v -> generateQRCode());

        btnSubmitWaste.setOnClickListener(v -> submitWasteData());
    }

    private void generateQRCode() {
        try {
            // Encode the current date as the QR data (you could make this more
            // secure/complex later)
            String qrData = "SMART_MESS_" + currentDate;

            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(qrData, BarcodeFormat.QR_CODE, 600, 600);

            ivQrCode.setImageBitmap(bitmap);
            ivQrCode.setVisibility(View.VISIBLE);

            Toast.makeText(this, "Session QR Code Generated!", Toast.LENGTH_SHORT).show();
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
