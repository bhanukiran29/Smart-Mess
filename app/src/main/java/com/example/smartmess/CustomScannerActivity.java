package com.example.smartmess;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.google.zxing.client.android.Intents;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CustomScannerActivity extends AppCompatActivity {

    private CaptureManager captureManager;
    private DecoratedBarcodeView barcodeScannerView;
    private View clBrackets;
    private View viewScanLine;
    private View viewFlash;
    private MaterialCardView btnFlashlight;
    private MaterialCardView cardResult;
    private TextView tvResultIcon, tvResultTitle, tvResultSubtitle, tvResultMessage;
    
    private View[] brackets;
    private boolean isFlashlightOn = false;
    private boolean isScanned = false; // Prevent multiple scans

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_scanner);

        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner);
        
        // Hide default ZXing Viewfinder overlay because it conflicts with our custom brackets
        View defaultViewfinder = barcodeScannerView.findViewById(com.google.zxing.client.android.R.id.zxing_viewfinder_view);
        if (defaultViewfinder != null) {
            defaultViewfinder.setVisibility(View.GONE);
        }

        // We MUST initialize CaptureManager so ZXing inherently parses intents and cleanly mounts the camera
        captureManager = new CaptureManager(this, barcodeScannerView);
        captureManager.initializeFromIntent(getIntent(), savedInstanceState);
        // We INTENTIONALLY don't call captureManager.decode() because we want to intercept the result manually!


        clBrackets = findViewById(R.id.clBrackets);
        viewScanLine = findViewById(R.id.viewScanLine);
        viewFlash = findViewById(R.id.viewFlash);
        btnFlashlight = findViewById(R.id.btnFlashlight);
        cardResult = findViewById(R.id.cardResult);
        
        tvResultIcon = findViewById(R.id.tvResultIcon);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultSubtitle = findViewById(R.id.tvResultSubtitle);
        tvResultMessage = findViewById(R.id.tvResultMessage);

        brackets = new View[]{
            findViewById(R.id.tl_h), findViewById(R.id.tl_v),
            findViewById(R.id.tr_h), findViewById(R.id.tr_v),
            findViewById(R.id.bl_h), findViewById(R.id.bl_v),
            findViewById(R.id.br_h), findViewById(R.id.br_v)
        };

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        btnFlashlight.setOnClickListener(v -> {
            if (isFlashlightOn) {
                barcodeScannerView.setTorchOff();
                btnFlashlight.setCardBackgroundColor(Color.parseColor("#26FFFFFF"));
            } else {
                barcodeScannerView.setTorchOn();
                btnFlashlight.setCardBackgroundColor(Color.parseColor("#80FFFFFF"));
            }
            isFlashlightOn = !isFlashlightOn;
        });

        startScanAnimations();
        
        // Custom decoding so we can present the bottom sheet before finishing the activity!
        barcodeScannerView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result.getText() != null && !isScanned) {
                    isScanned = true;
                    barcodeScannerView.pause();
                    showSuccessState(result.getText());
                }
            }
        });
    }
    
    private void returnResultAndFinish(String contents) {
        Intent intent = new Intent();
        intent.putExtra(Intents.Scan.RESULT, contents);
        setResult(RESULT_OK, intent);
        finish();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        captureManager.onSaveInstanceState(outState);
    }

    private void startScanAnimations() {
        // 1. Brackets scale in from zero using an Overshoot interpolator
        clBrackets.setScaleX(0f);
        clBrackets.setScaleY(0f);
        clBrackets.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(new OvershootInterpolator())
            .withEndAction(this::pulseBrackets)
            .start();

        // 2. Sweeping glowing laser line
        float dp240 = 240f * getResources().getDisplayMetrics().density;
        ObjectAnimator sweep = ObjectAnimator.ofFloat(viewScanLine, "translationY", 0f, dp240);
        sweep.setDuration(1600);
        sweep.setRepeatCount(ValueAnimator.INFINITE);
        sweep.setInterpolator(new LinearInterpolator());
        sweep.start();
    }

    private void pulseBrackets() {
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(clBrackets, "scaleX", 1.0f, 1.04f, 0.96f, 1.0f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(clBrackets, "scaleY", 1.0f, 1.04f, 0.96f, 1.0f);
        pulseX.setDuration(1200);
        pulseY.setDuration(1200);
        pulseX.setRepeatCount(ValueAnimator.INFINITE);
        pulseY.setRepeatCount(ValueAnimator.INFINITE);
        pulseX.start();
        pulseY.start();
    }

    private void showSuccessState(String qrData) {
        // Validation format: SMART_MESS_{mealType}_{date}
        boolean isValid = qrData.startsWith("SMART_MESS_");
        
        if (isValid) {
            // Turn brackets green
            for (View v : brackets) {
                v.setBackgroundColor(Color.parseColor("#00A34F"));
            }
            
            // Flash overlay green
            viewFlash.setBackgroundColor(Color.parseColor("#00A34F"));
            viewFlash.setAlpha(0.6f);
            viewFlash.animate().alpha(0f).setDuration(200).start();
            
            String[] parts = qrData.split("_");
            String mealType = parts.length >= 3 ? parts[2] : "Meal";
            String date = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());

            tvResultIcon.setText("✅");
            tvResultTitle.setText("Meal Confirmed!");
            tvResultTitle.setTextColor(Color.parseColor("#0F172A"));
            tvResultSubtitle.setText(mealType.substring(0, 1).toUpperCase() + mealType.substring(1) + " | " + date);
            tvResultMessage.setText("Enjoy your " + mealType.toLowerCase() + "!");
            tvResultMessage.setTextColor(Color.parseColor("#00A34F"));

        } else {
            // Turn brackets red
            for (View v : brackets) {
                v.setBackgroundColor(Color.parseColor("#EF4444"));
            }
            
            // Flash overlay red
            viewFlash.setBackgroundColor(Color.parseColor("#EF4444"));
            viewFlash.setAlpha(0.6f);
            viewFlash.animate().alpha(0f).setDuration(200).start();
            
            // Shake animation
            clBrackets.animate().translationX(20f).setDuration(50).withEndAction(() -> {
                clBrackets.animate().translationX(-20f).setDuration(50).withEndAction(() -> {
                    clBrackets.animate().translationX(0f).setDuration(50).start();
                }).start();
            }).start();
            
            tvResultIcon.setText("❌");
            tvResultTitle.setText("Invalid QR Code");
            tvResultTitle.setTextColor(Color.parseColor("#EF4444"));
            tvResultSubtitle.setText("This is not a Smart Mess QR code.");
            tvResultMessage.setText("Please scan the official QR code.");
            tvResultMessage.setTextColor(Color.parseColor("#EF4444"));
        }

        // Card Slide Up Reveal
        cardResult.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(new DecelerateInterpolator())
            .start();
            
        findViewById(R.id.btnResultDismiss).setOnClickListener(v -> returnResultAndFinish(qrData));
    }

    @Override
    protected void onResume() {
        super.onResume();
        captureManager.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        captureManager.onPause();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        captureManager.onDestroy();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        captureManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
