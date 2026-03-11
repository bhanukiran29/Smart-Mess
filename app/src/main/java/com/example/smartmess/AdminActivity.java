package com.example.smartmess;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AdminActivity extends AppCompatActivity {

    private TextView tvGreeting, tvBreakfastCount, tvLunchCount, tvDinnerCount;
    private TextView tvRatingBreakfast, tvRatingLunch, tvRatingDinner;
    private MaterialButton btnGenerateReport, btnManageMenu;
    private Button btnLogout;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String targetDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initializeViews();
        loadGreeting();
        fetchMealCounts();
        fetchMealRatings();

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(AdminActivity.this, LoginActivity.class));
            finish();
        });

        btnGenerateReport.setOnClickListener(v -> {
            generatePdfReport();
        });

        btnManageMenu.setOnClickListener(v -> {
            startActivity(new Intent(AdminActivity.this, MenuActivity.class));
        });
    }

    private void generatePdfReport() {
        android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
        android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(400,
                600, 1).create();
        android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);

        android.graphics.Canvas canvas = page.getCanvas();
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.BLACK);
        paint.setTextSize(20f);

        int x = 20, y = 40;
        canvas.drawText("Smart Mess - Daily Analytics Report", x, y, paint);
        y += 40;

        paint.setTextSize(16f);
        canvas.drawText("Target Date: " + targetDate, x, y, paint);
        y += 30;
        canvas.drawText("Breakfast Confirmations: " + tvBreakfastCount.getText().toString(), x, y, paint);
        y += 30;
        canvas.drawText("Lunch Confirmations: " + tvLunchCount.getText().toString(), x, y, paint);
        y += 30;
        canvas.drawText("Dinner Confirmations: " + tvDinnerCount.getText().toString(), x, y, paint);

        document.finishPage(page);

        // Save the file to the public Downloads directory so it is visually accessible
        java.io.File dir = android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        java.io.File file = new java.io.File(dir, "MessReport_" + targetDate + ".pdf");

        try {
            document.writeTo(new java.io.FileOutputStream(file));
            Toast.makeText(this, "PDF Saved successfully to Documents!", Toast.LENGTH_LONG).show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        document.close();
    }

    private void initializeViews() {
        tvGreeting = findViewById(R.id.tvGreeting);
        tvBreakfastCount = findViewById(R.id.tvBreakfastCount);
        tvLunchCount = findViewById(R.id.tvLunchCount);
        tvDinnerCount = findViewById(R.id.tvDinnerCount);
        
        tvRatingBreakfast = findViewById(R.id.tvRatingBreakfast);
        tvRatingLunch = findViewById(R.id.tvRatingLunch);
        tvRatingDinner = findViewById(R.id.tvRatingDinner);
        
        btnGenerateReport = findViewById(R.id.btnGenerateReport);
        btnManageMenu = findViewById(R.id.btnManageMenu);
        btnLogout = findViewById(R.id.btnLogout);
        progressBar = findViewById(R.id.progressBar);

        // Target Tomorrow's date for accurate counts since students log for tomorrow
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        targetDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
    }

    private void loadGreeting() {
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        tvGreeting.setText("Hi, " + name + " (Admin)");
                    }
                });
    }

    private void fetchMealCounts() {
        progressBar.setVisibility(View.VISIBLE);

        // Fetch Breakfast Count
        db.collection("confirmations").document(targetDate).collection("breakfast")
                .whereEqualTo("status", "eat") // Only count those who said "eat"
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int count = task.getResult().size();
                        tvBreakfastCount.setText(count + " students");
                    } else {
                        tvBreakfastCount.setText("Error");
                    }
                });

        // Fetch Lunch Count
        db.collection("confirmations").document(targetDate).collection("lunch")
                .whereEqualTo("status", "eat")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int count = task.getResult().size();
                        tvLunchCount.setText(count + " students");
                    } else {
                        tvLunchCount.setText("Error");
                    }
                });

        // Fetch Dinner Count
        db.collection("confirmations").document(targetDate).collection("dinner")
                .whereEqualTo("status", "eat")
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE); // Hide bar after last query finishes
                    if (task.isSuccessful()) {
                        int count = task.getResult().size();
                        tvDinnerCount.setText(count + " students");
                    } else {
                        tvDinnerCount.setText("Error");
                    }
                });
    }

    private void fetchMealRatings() {
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // Fetch Breakfast Ratings
        db.collection("meal_ratings").document(todayDate).collection("breakfast").get()
                .addOnSuccessListener(queryDocumentSnapshots -> calculateAverageRating(queryDocumentSnapshots, tvRatingBreakfast));

        // Fetch Lunch Ratings
        db.collection("meal_ratings").document(todayDate).collection("lunch").get()
                .addOnSuccessListener(queryDocumentSnapshots -> calculateAverageRating(queryDocumentSnapshots, tvRatingLunch));

        // Fetch Dinner Ratings
        db.collection("meal_ratings").document(todayDate).collection("dinner").get()
                .addOnSuccessListener(queryDocumentSnapshots -> calculateAverageRating(queryDocumentSnapshots, tvRatingDinner));
    }

    private void calculateAverageRating(QuerySnapshot snapshots, TextView targetView) {
        if (snapshots.isEmpty()) {
            targetView.setText("N/A");
            return;
        }

        float totalRating = 0;
        int count = 0;

        for (DocumentSnapshot doc : snapshots) {
            Double rating = doc.getDouble("rating");
            if (rating != null) {
                totalRating += rating;
                count++;
            }
        }

        if (count > 0) {
            float average = totalRating / count;
            targetView.setText(String.format(Locale.getDefault(), "%.1f (%d votes)", average, count));
        } else {
            targetView.setText("N/A");
        }
    }
}
