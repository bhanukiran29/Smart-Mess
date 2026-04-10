package com.example.smartmess;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FeedbackActivity extends AppCompatActivity {

    private ImageView ivBack, ivSuccessCheck;
    private ChipGroup cgCategory;
    private TextInputEditText etFeedback;
    private MaterialButton btnSubmit;
    private LinearLayout llSuccess, llFormContainer, btnAttach;

    private ImageView[] stars = new ImageView[5];
    private String selectedCategory = "Other";
    private int selectedRating = 0;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupChips();
        setupStars();

        ivBack.setOnClickListener(v -> finish());
        btnSubmit.setOnClickListener(v -> submitFeedback());
    }

    private void initializeViews() {
        ivBack = findViewById(R.id.ivBack);
        cgCategory = findViewById(R.id.cgCategory);
        etFeedback = findViewById(R.id.etFeedback);
        btnSubmit = findViewById(R.id.btnSubmit);
        llSuccess = findViewById(R.id.llSuccess);
        llFormContainer = findViewById(R.id.llFormContainer);
        ivSuccessCheck = findViewById(R.id.ivSuccessCheck);
        btnAttach = findViewById(R.id.btnAttach);

        stars[0] = findViewById(R.id.star1);
        stars[1] = findViewById(R.id.star2);
        stars[2] = findViewById(R.id.star3);
        stars[3] = findViewById(R.id.star4);
        stars[4] = findViewById(R.id.star5);
    }

    private void setupChips() {
        int[] chipIds = {R.id.chip1, R.id.chip2, R.id.chip3, R.id.chip4, R.id.chip5};
        
        for (int id : chipIds) {
            Chip chip = findViewById(id);
            chip.setChipBackgroundColorResource(android.R.color.transparent);
            
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedCategory = chip.getText().toString();
                    chip.setChipBackgroundColorResource(android.R.color.transparent);
                    chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1E3A8A"))); // Navy
                    chip.setTextColor(android.graphics.Color.WHITE);
                    
                    // Bounce animation mapping to user prompt
                    chip.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150)
                            .withEndAction(() -> chip.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(new OvershootInterpolator()).start())
                            .start();
                } else {
                    chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F1F5F9")));
                    chip.setTextColor(android.graphics.Color.parseColor("#64748B"));
                }
            });
        }
        
        // Select first by default
        ((Chip) findViewById(R.id.chip1)).setChecked(true);
    }

    private void setupStars() {
        for (int i = 0; i < 5; i++) {
            int finalI = i;
            stars[i].setOnClickListener(v -> {
                selectedRating = finalI + 1;
                for (int j = 0; j < 5; j++) {
                    ImageView targetStar = stars[j];
                    if (j <= finalI) {
                        targetStar.setImageResource(android.R.drawable.star_on);
                    } else {
                        targetStar.setImageResource(android.R.drawable.star_off);
                    }
                    targetStar.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).withEndAction(() -> 
                        targetStar.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    ).start();
                }
            });
        }
    }

    private void submitFeedback() {
        String fbText = etFeedback.getText().toString().trim();
        if (fbText.isEmpty()) {
            etFeedback.setError("Please describe the issue");
            return;
        }

        btnSubmit.setEnabled(false);

        Map<String, Object> data = new HashMap<>();
        data.put("category", selectedCategory);
        data.put("feedback", fbText);
        data.put("rating", selectedRating);
        data.put("timestamp", System.currentTimeMillis());
        data.put("status", "open");

        if (mAuth.getCurrentUser() != null) {
            data.put("userId", mAuth.getCurrentUser().getUid());
        }

        db.collection("feedback").add(data)
                .addOnSuccessListener(doc -> playSuccessAnimation())
                .addOnFailureListener(e -> btnSubmit.setEnabled(true));
    }

    private void playSuccessAnimation() {
        llFormContainer.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            llSuccess.setVisibility(View.VISIBLE);
            llSuccess.setAlpha(0f);
            
            llSuccess.animate().alpha(1f).setDuration(300).withEndAction(() -> {
                ivSuccessCheck.animate()
                        .scaleX(1f).scaleY(1f)
                        .setInterpolator(new OvershootInterpolator())
                        .setDuration(600)
                        .start();
            }).start();
            
            // Auto close activity
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 2500);
        }).start();
    }
}
