package com.example.smartmess;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    private static final int TOTAL_DURATION_MS = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw behind system bars for true edge-to-edge navy bg
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_splash);

        View iconCircle   = findViewById(R.id.iconBackground);
        ImageView ivLogo  = findViewById(R.id.ivLogo);
        TextView tvSmart  = findViewById(R.id.tvSmart);
        TextView tvMess   = findViewById(R.id.tvMess);
        TextView tvTagline = findViewById(R.id.tvTagline);
        View loadingDots  = findViewById(R.id.loadingDots);

        // --- Initial state: everything invisible & shifted ---
        iconCircle.setAlpha(0f);
        iconCircle.setScaleX(0.3f);
        iconCircle.setScaleY(0.3f);

        ivLogo.setAlpha(0f);
        ivLogo.setScaleX(0.5f);
        ivLogo.setScaleY(0.5f);

        tvSmart.setAlpha(0f);
        tvSmart.setTranslationY(32f);
        tvMess.setAlpha(0f);
        tvMess.setTranslationY(32f);
        tvTagline.setAlpha(0f);
        tvTagline.setTranslationY(20f);
        loadingDots.setAlpha(0f);

        // --- Step 1: Circle scales in with overshoot (0ms) ---
        AnimatorSet circleIn = new AnimatorSet();
        circleIn.playTogether(
            ObjectAnimator.ofFloat(iconCircle, View.ALPHA, 0f, 1f).setDuration(400),
            ObjectAnimator.ofFloat(iconCircle, View.SCALE_X, 0.3f, 1f).setDuration(500),
            ObjectAnimator.ofFloat(iconCircle, View.SCALE_Y, 0.3f, 1f).setDuration(500)
        );
        circleIn.setInterpolator(new OvershootInterpolator(1.8f));
        circleIn.setStartDelay(100);

        // --- Step 2: Logo icon fades in with slight overshoot (200ms) ---
        AnimatorSet logoIn = new AnimatorSet();
        logoIn.playTogether(
            ObjectAnimator.ofFloat(ivLogo, View.ALPHA, 0f, 1f).setDuration(400),
            ObjectAnimator.ofFloat(ivLogo, View.SCALE_X, 0.5f, 1f).setDuration(450),
            ObjectAnimator.ofFloat(ivLogo, View.SCALE_Y, 0.5f, 1f).setDuration(450)
        );
        logoIn.setInterpolator(new OvershootInterpolator(1.4f));
        logoIn.setStartDelay(250);

        // --- Step 3: "Smart" slides up (550ms) ---
        AnimatorSet smartIn = new AnimatorSet();
        smartIn.playTogether(
            ObjectAnimator.ofFloat(tvSmart, View.ALPHA, 0f, 1f).setDuration(400),
            ObjectAnimator.ofFloat(tvSmart, View.TRANSLATION_Y, 32f, 0f).setDuration(400)
        );
        smartIn.setInterpolator(new DecelerateInterpolator(2f));
        smartIn.setStartDelay(550);

        // --- Step 4: "Mess" slides up (650ms) ---
        AnimatorSet messIn = new AnimatorSet();
        messIn.playTogether(
            ObjectAnimator.ofFloat(tvMess, View.ALPHA, 0f, 1f).setDuration(400),
            ObjectAnimator.ofFloat(tvMess, View.TRANSLATION_Y, 32f, 0f).setDuration(400)
        );
        messIn.setInterpolator(new DecelerateInterpolator(2f));
        messIn.setStartDelay(700);

        // --- Step 5: Tagline fades in (850ms) ---
        AnimatorSet taglineIn = new AnimatorSet();
        taglineIn.playTogether(
            ObjectAnimator.ofFloat(tvTagline, View.ALPHA, 0f, 1f).setDuration(500),
            ObjectAnimator.ofFloat(tvTagline, View.TRANSLATION_Y, 20f, 0f).setDuration(500)
        );
        taglineIn.setInterpolator(new DecelerateInterpolator(2f));
        taglineIn.setStartDelay(900);

        // --- Step 6: Loading dots fade in (1100ms) ---
        ObjectAnimator dotsIn = ObjectAnimator.ofFloat(loadingDots, View.ALPHA, 0f, 1f);
        dotsIn.setDuration(400);
        dotsIn.setStartDelay(1200);

        // --- Step 7: Subtle logo idle pulse after everything is visible ---
        ObjectAnimator idlePulse = ObjectAnimator.ofFloat(iconCircle, View.SCALE_X, 1f, 1.04f, 1f);
        idlePulse.setDuration(1200);
        idlePulse.setRepeatCount(ValueAnimator.INFINITE);
        idlePulse.setRepeatMode(ValueAnimator.RESTART);
        idlePulse.setStartDelay(1500);

        ObjectAnimator idlePulseY = ObjectAnimator.ofFloat(iconCircle, View.SCALE_Y, 1f, 1.04f, 1f);
        idlePulseY.setDuration(1200);
        idlePulseY.setRepeatCount(ValueAnimator.INFINITE);
        idlePulseY.setRepeatMode(ValueAnimator.RESTART);
        idlePulseY.setStartDelay(1500);

        // Fire everything
        circleIn.start();
        logoIn.start();
        smartIn.start();
        messIn.start();
        taglineIn.start();
        dotsIn.start();
        idlePulse.start();
        idlePulseY.start();

        // Navigate after TOTAL_DURATION_MS
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            idlePulse.cancel();
            idlePulseY.cancel();
            navigateToNext();
        }, TOTAL_DURATION_MS);
    }

    private void navigateToNext() {
        // Whole screen fades out before transitioning
        View root = getWindow().getDecorView().getRootView();
        root.animate()
            .alpha(0f)
            .setDuration(350)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    FirebaseAuth auth = FirebaseAuth.getInstance();
                    Intent intent;
                    if (auth.getCurrentUser() != null) {
                        // Already logged in — skip straight to MainActivity
                        intent = new Intent(SplashActivity.this, MainActivity.class);
                    } else {
                        intent = new Intent(SplashActivity.this, LoginActivity.class);
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    // Slide-up transition — new screen rises from below
                    overridePendingTransition(R.anim.slide_up_enter, R.anim.fade_out_exit);
                    finish();
                }
            })
            .start();
    }
}
