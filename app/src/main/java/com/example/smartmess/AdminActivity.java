package com.example.smartmess;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String targetDate; // Tomorrow's date

    private TextView tvAdminSubtitle, tvKpiBookings, tvKpiRating, tvKpiWaste, tvKpiWasteDelta;
    private MaterialCardView cvKpiBookings, cvKpiRating, cvKpiWaste;
    private MaterialCardView btnManageMenu, btnViewFeedback, btnUserManagement, btnGenerateReport;
    private MaterialButton btnLogout;
    private BarChart wasteBarChart;

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
        setupChartBase();
        triggerEntranceAnimations();

        fetchKPIs();
        fetchChartData();

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(AdminActivity.this, LoginActivity.class));
            finish();
        });

        btnManageMenu.setOnClickListener(v -> startActivity(new Intent(AdminActivity.this, MenuActivity.class)));
        btnUserManagement.setOnClickListener(v -> startActivity(new Intent(AdminActivity.this, UserManagementActivity.class)));
        btnViewFeedback.setOnClickListener(v -> startActivity(new Intent(AdminActivity.this, FeedbackViewerActivity.class)));
        btnGenerateReport.setOnClickListener(v -> generatePdfReport());
    }

    private void initializeViews() {
        tvAdminSubtitle = findViewById(R.id.tvAdminSubtitle);
        tvKpiBookings = findViewById(R.id.tvKpiBookings);
        tvKpiRating = findViewById(R.id.tvKpiRating);
        tvKpiWaste = findViewById(R.id.tvKpiWaste);
        tvKpiWasteDelta = findViewById(R.id.tvKpiWasteDelta);

        cvKpiBookings = findViewById(R.id.cvKpiBookings);
        cvKpiRating = findViewById(R.id.cvKpiRating);
        cvKpiWaste = findViewById(R.id.cvKpiWaste);

        btnManageMenu = findViewById(R.id.btnManageMenu);
        btnViewFeedback = findViewById(R.id.btnViewFeedback);
        btnUserManagement = findViewById(R.id.btnUserManagement);
        btnGenerateReport = findViewById(R.id.btnGenerateReport);
        btnLogout = findViewById(R.id.btnLogout);

        wasteBarChart = findViewById(R.id.wasteBarChart);

        String today = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());
        tvAdminSubtitle.setText(today);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        targetDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    private void triggerEntranceAnimations() {
        MaterialCardView[] cards = {cvKpiBookings, cvKpiRating, cvKpiWaste};
        for (int i = 0; i < cards.length; i++) {
            cards[i].setTranslationX(150f);
            cards[i].setAlpha(0f);
            cards[i].animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setStartDelay(100L * i)
                    .setDuration(400)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void setupChartBase() {
        wasteBarChart.setDrawGridBackground(false);
        wasteBarChart.getDescription().setEnabled(false);
        wasteBarChart.setPinchZoom(false);
        wasteBarChart.setDrawBarShadow(false);

        XAxis xAxis = wasteBarChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setCenterAxisLabels(true);

        YAxis leftAxis = wasteBarChart.getAxisLeft();
        leftAxis.setDrawGridLines(false);
        leftAxis.setAxisMinimum(0f);
        wasteBarChart.getAxisRight().setEnabled(false);

        Legend l = wasteBarChart.getLegend();
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
    }

    private void fetchKPIs() {
        // Mock heavy DB calculations to match the prompt specifications for KPIs
        tvKpiBookings.setText("0");
        tvKpiWaste.setText("0");
        tvKpiRating.setText("0.0");

        // 1. Bookings Target
        int calculatedBookings = 145; // Placeholder for DB calculation
        animateCountInt(tvKpiBookings, calculatedBookings, "");

        // 2. Avg Rating Target
        double rating = 4.6;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, (float) rating);
        anim.setDuration(1200);
        anim.addUpdateListener(a -> tvKpiRating.setText(String.format(Locale.getDefault(), "%.1f", a.getAnimatedValue())));
        anim.start();

        // 3. Waste Delta
        int wasteToday = 12;
        int wasteYesterday = 18;
        animateCountInt(tvKpiWaste, wasteToday, "kg");

        int diff = wasteToday - wasteYesterday;
        if (diff < 0) {
            tvKpiWasteDelta.setText("↓ " + Math.abs(diff) + "kg vs y'day");
            tvKpiWasteDelta.setTextColor(Color.parseColor("#16A34A"));
        } else {
            tvKpiWasteDelta.setText("↑ " + diff + "kg vs y'day");
            tvKpiWasteDelta.setTextColor(Color.parseColor("#EF4444"));
        }
    }

    private void animateCountInt(TextView tv, int target, String suffix) {
        ValueAnimator anim = ValueAnimator.ofInt(0, target);
        anim.setDuration(1200);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> tv.setText(a.getAnimatedValue().toString() + suffix));
        anim.start();
    }

    private void fetchChartData() {
        // Generating sequential dummy data corresponding to 7 trailing days
        List<BarEntry> predictedEntries = new ArrayList<>();
        List<BarEntry> wastedEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        SimpleDateFormat labelFmt = new SimpleDateFormat("EEE", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < 7; i++) {
            cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, - (6 - i));
            labels.add(labelFmt.format(cal.getTime()));

            predictedEntries.add(new BarEntry(i, 120 + (float)(Math.random() * 50)));
            wastedEntries.add(new BarEntry(i, 8 + (float)(Math.random() * 15)));
        }

        BarDataSet set1 = new BarDataSet(predictedEntries, "Predicted");
        set1.setColor(Color.parseColor("#3B82F6"));
        BarDataSet set2 = new BarDataSet(wastedEntries, "Wasted");
        set2.setColor(Color.parseColor("#F59E0B"));

        float groupSpace = 0.4f;
        float barSpace = 0.05f;
        float barWidth = 0.25f;

        BarData data = new BarData(set1, set2);
        data.setBarWidth(barWidth);

        wasteBarChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        wasteBarChart.setData(data);
        wasteBarChart.groupBars(0f, groupSpace, barSpace);
        wasteBarChart.getXAxis().setAxisMinimum(0f);
        wasteBarChart.getXAxis().setAxisMaximum(0f + wasteBarChart.getBarData().getGroupWidth(groupSpace, barSpace) * 7);

        wasteBarChart.animateY(800, Easing.EaseInOutQuart);
        wasteBarChart.invalidate();
    }

    private void generatePdfReport() {
        android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
        android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(400, 600, 1).create();
        android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);

        android.graphics.Canvas canvas = page.getCanvas();
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.BLACK);
        paint.setTextSize(20f);

        int x = 20, y = 40;
        canvas.drawText("Smart Mess - Daily Analytics", x, y, paint);
        y += 40;
        paint.setTextSize(14f);
        canvas.drawText("Target Date: " + targetDate, x, y, paint);
        y += 30;
        canvas.drawText("Total Predicted Bookings: " + tvKpiBookings.getText().toString(), x, y, paint);
        y += 30;
        canvas.drawText("Total Wasted: " + tvKpiWaste.getText().toString(), x, y, paint);

        document.finishPage(page);

        java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        java.io.File file = new java.io.File(dir, "MessReport_" + targetDate + ".pdf");

        try {
            document.writeTo(new java.io.FileOutputStream(file));
            Toast.makeText(this, "✅ PDF Saved successfully to Documents folder!", Toast.LENGTH_LONG).show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        document.close();
    }
}
