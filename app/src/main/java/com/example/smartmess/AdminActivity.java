package com.example.smartmess;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AdminActivity extends AppCompatActivity {

    private TextView tvGreeting, tvBreakfastCount, tvLunchCount, tvDinnerCount;
    private TextView tvRatingBreakfast, tvRatingLunch, tvRatingDinner;
    private MaterialButton btnGenerateReport, btnManageMenu, btnUserManagement, btnViewFeedback;
    private Button btnLogout;
    private ProgressBar progressBar;
    private BarChart wasteBarChart;

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
        fetchChartData();

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(AdminActivity.this, LoginActivity.class));
            finish();
        });

        btnGenerateReport.setOnClickListener(v -> {
            generatePdfReport();
        });

        btnManageMenu.setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, MenuActivity.class)));

        btnUserManagement.setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, UserManagementActivity.class)));

        btnViewFeedback.setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, FeedbackViewerActivity.class)));
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
        wasteBarChart = findViewById(R.id.wasteBarChart);
        btnUserManagement = findViewById(R.id.btnUserManagement);
        btnViewFeedback   = findViewById(R.id.btnViewFeedback);

        // Target Tomorrow's date for accurate counts since students log for tomorrow
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        targetDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
    }

    // -----------------------------------------------------------------------
    // 7-Day Bar Chart: Predicted Meals vs. Actual Waste
    // -----------------------------------------------------------------------
    private void fetchChartData() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelFmt = new SimpleDateFormat("EEE", Locale.getDefault()); // Mon, Tue...

        // Build ordered list of the last 7 days (oldest → newest)
        List<String> dates = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -i);
            dates.add(sdf.format(cal.getTime()));
            labels.add(labelFmt.format(cal.getTime()));
        }

        // Arrays to accumulate totals once all async calls finish
        float[] predicted = new float[7]; // total meal confirmations per day
        float[] wasted   = new float[7]; // total preparedPlates from waste_data per day

        // We fire 7 pairs of queries (14 total). Use AtomicInteger to know when all finish.
        int totalQueries = 14;
        AtomicInteger completedQueries = new AtomicInteger(0);

        for (int i = 0; i < 7; i++) {
            final int idx = i;
            String date = dates.get(i);

            // --- Query 1: count predicted meals (confirmations with status == "eat") ---
            // We sum across breakfast + lunch + dinner sub-collections for that date.
            String[] meals = {"breakfast", "lunch", "dinner"};
            AtomicInteger mealsDone = new AtomicInteger(0);
            final float[] dayPredicted = {0};

            for (String meal : meals) {
                db.collection("confirmations").document(date).collection(meal)
                        .whereEqualTo("status", "eat")
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                dayPredicted[0] += task.getResult().size();
                            }
                            // When all 3 meal sub-collections for this day are done
                            if (mealsDone.incrementAndGet() == 3) {
                                predicted[idx] = dayPredicted[0];
                                // Mark 3 of the 14 queries done
                                int done = completedQueries.addAndGet(3);
                                if (done >= totalQueries) {
                                    setupBarChart(predicted, wasted, labels);
                                }
                            }
                        });
            }

            // --- Query 2: sum preparedPlates from waste_data for that date ---
            // Firestore path: waste_data/{date}/{meal}  (each doc has preparedPlates field)
            AtomicInteger wasteMealsDone = new AtomicInteger(0);
            final float[] dayWasted = {0};

            for (String meal : meals) {
                db.collection("waste_data").document(date).collection(meal)
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                for (DocumentSnapshot doc : task.getResult()) {
                                    Long plates = doc.getLong("preparedPlates");
                                    if (plates != null) dayWasted[0] += plates;
                                }
                            }
                            if (wasteMealsDone.incrementAndGet() == 3) {
                                wasted[idx] = dayWasted[0];
                                int done = completedQueries.addAndGet(3);
                                if (done >= totalQueries) {
                                    setupBarChart(predicted, wasted, labels);
                                }
                            }
                        });
            }
        }
    }

    private void setupBarChart(float[] predicted, float[] wasted, List<String> labels) {
        runOnUiThread(() -> {
            List<BarEntry> predictedEntries = new ArrayList<>();
            List<BarEntry> wastedEntries    = new ArrayList<>();

            for (int i = 0; i < 7; i++) {
                predictedEntries.add(new BarEntry(i, predicted[i]));
                wastedEntries.add(new BarEntry(i, wasted[i]));
            }

            // Dataset 1 – Predicted Meals (teal)
            BarDataSet ds1 = new BarDataSet(predictedEntries, "Predicted Meals");
            ds1.setColor(Color.parseColor("#26A69A")); // teal
            ds1.setValueTextSize(10f);
            ds1.setValueTextColor(Color.DKGRAY);

            // Dataset 2 – Actual Waste / Prepared Plates (deep orange)
            BarDataSet ds2 = new BarDataSet(wastedEntries, "Prepared Plates (Waste Risk)");
            ds2.setColor(Color.parseColor("#EF6C00")); // deep orange
            ds2.setValueTextSize(10f);
            ds2.setValueTextColor(Color.DKGRAY);

            // Grouped bar layout
            float groupSpace = 0.3f;
            float barSpace   = 0.05f;
            float barWidth   = 0.3f; // (barWidth + barSpace) * 2 + groupSpace = 1.0

            BarData barData = new BarData(ds1, ds2);
            barData.setBarWidth(barWidth);

            wasteBarChart.setData(barData);
            wasteBarChart.groupBars(0f, groupSpace, barSpace);

            // X-axis – day labels
            XAxis xAxis = wasteBarChart.getXAxis();
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setGranularity(1f);
            xAxis.setGranularityEnabled(true);
            xAxis.setCenterAxisLabels(true);
            xAxis.setAxisMinimum(0f);
            xAxis.setAxisMaximum(7f);
            xAxis.setDrawGridLines(false);
            xAxis.setTextSize(11f);
            xAxis.setLabelRotationAngle(-45f);

            // Y-axis
            YAxis leftAxis = wasteBarChart.getAxisLeft();
            leftAxis.setAxisMinimum(0f);
            leftAxis.setGranularity(1f);
            wasteBarChart.getAxisRight().setEnabled(false);

            // Legend
            Legend legend = wasteBarChart.getLegend();
            legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
            legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
            legend.setOrientation(Legend.LegendOrientation.VERTICAL);
            legend.setDrawInside(false);

            // General chart styling
            wasteBarChart.setDescription(null);
            wasteBarChart.setDrawGridBackground(false);
            wasteBarChart.setPinchZoom(false);
            wasteBarChart.setDoubleTapToZoomEnabled(false);
            wasteBarChart.setExtraBottomOffset(20f);
            wasteBarChart.animateY(800);
            wasteBarChart.invalidate();
        });
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

    private static final double BUFFER_PERCENT = 0.10; // 10% extra plates as walk-in buffer

    private void fetchMealCounts() {
        progressBar.setVisibility(View.VISIBLE);

        // We collect all 3 counts first, then write capacity once all are known
        final int[] counts = {0, 0, 0}; // [breakfast, lunch, dinner]
        AtomicInteger done = new AtomicInteger(0);

        // Fetch Breakfast Count
        db.collection("confirmations").document(targetDate).collection("breakfast")
                .whereEqualTo("status", "eat")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        counts[0] = task.getResult().size();
                        tvBreakfastCount.setText(counts[0] + " students");
                    } else {
                        tvBreakfastCount.setText("Error");
                    }
                    if (done.incrementAndGet() == 3) writeCapacity(counts);
                });

        // Fetch Lunch Count
        db.collection("confirmations").document(targetDate).collection("lunch")
                .whereEqualTo("status", "eat")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        counts[1] = task.getResult().size();
                        tvLunchCount.setText(counts[1] + " students");
                    } else {
                        tvLunchCount.setText("Error");
                    }
                    if (done.incrementAndGet() == 3) writeCapacity(counts);
                });

        // Fetch Dinner Count
        db.collection("confirmations").document(targetDate).collection("dinner")
                .whereEqualTo("status", "eat")
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        counts[2] = task.getResult().size();
                        tvDinnerCount.setText(counts[2] + " students");
                    } else {
                        tvDinnerCount.setText("Error");
                    }
                    if (done.incrementAndGet() == 3) writeCapacity(counts);
                });
    }

    /**
     * Writes tomorrow's meal capacity to Firestore with a 10% walk-in buffer.
     * Formula: capacity = confirmed + ceil(confirmed × BUFFER_PERCENT)
     *
     * Firestore path: meal_capacity/{targetDate}
     * Fields written:
     *   breakfast_capacity, lunch_capacity, dinner_capacity  → confirmed + buffer
     *   breakfast_scanned,  lunch_scanned,  dinner_scanned   → 0 (reset for the day)
     */
    private void writeCapacity(int[] counts) {
        long bCapRaw = counts[0] + (long) Math.ceil(counts[0] * BUFFER_PERCENT);
        long lCapRaw = counts[1] + (long) Math.ceil(counts[1] * BUFFER_PERCENT);
        long dCapRaw = counts[2] + (long) Math.ceil(counts[2] * BUFFER_PERCENT);

        // Ensure at least 1 so the gate works even with 0 registrations
        final long bCap = Math.max(bCapRaw, 1);
        final long lCap = Math.max(lCapRaw, 1);
        final long dCap = Math.max(dCapRaw, 1);

        Map<String, Object> capacityDoc = new HashMap<>();
        capacityDoc.put("breakfast_capacity", bCap);
        capacityDoc.put("lunch_capacity",     lCap);
        capacityDoc.put("dinner_capacity",    dCap);
        // Only reset scanned counts if this is the first write of the day
        // (use merge so existing scanned values are preserved if admin refreshes mid-day)
        capacityDoc.put("breakfast_scanned",  0L);
        capacityDoc.put("lunch_scanned",      0L);
        capacityDoc.put("dinner_scanned",     0L);
        capacityDoc.put("bufferPercent",      (int)(BUFFER_PERCENT * 100));
        capacityDoc.put("lastUpdated",        System.currentTimeMillis());

        db.collection("meal_capacity").document(targetDate)
                .set(capacityDoc) // full overwrite keeps scanned=0 when admin opens dashboard
                .addOnSuccessListener(aVoid ->
                        Log.d("AdminActivity", "Capacity written → B:" + bCap
                                + " L:" + lCap + " D:" + dCap))
                .addOnFailureListener(e ->
                        Log.e("AdminActivity", "Capacity write failed: " + e.getMessage()));
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
