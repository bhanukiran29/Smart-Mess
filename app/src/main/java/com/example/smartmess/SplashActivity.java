package com.example.smartmess;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Hide ActionBar for fullscreen effect
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Simple fade-in animation for elements
        View iconBackground = findViewById(R.id.iconBackground);
        ImageView ivLogo = findViewById(R.id.ivLogo);
        TextView tvAppName = findViewById(R.id.tvAppName);
        TextView tvTagline = findViewById(R.id.tvTagline);

        iconBackground.setAlpha(0f);
        ivLogo.setAlpha(0f);
        tvAppName.setAlpha(0f);
        tvTagline.setAlpha(0f);

        int duration = 1000;
        iconBackground.animate().alpha(1f).setDuration(duration).start();
        ivLogo.animate().alpha(1f).setDuration(duration).start();
        tvAppName.animate().alpha(1f).setDuration(duration).setStartDelay(300).start();
        tvTagline.animate().alpha(1f).setDuration(duration).setStartDelay(500).start();

        // Adding 2 second delay to show the Splash
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            // LoginActivity handles the Auth Check
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 2500); // 2.5 seconds total
    }
}
