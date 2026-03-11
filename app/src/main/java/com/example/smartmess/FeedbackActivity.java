package com.example.smartmess;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FeedbackActivity extends AppCompatActivity {

    private Spinner spinnerCategory;
    private TextInputEditText etFeedback;
    private MaterialButton btnSubmitFeedback;
    private Button btnBack;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        spinnerCategory   = findViewById(R.id.spinnerFeedbackCategory);
        etFeedback        = findViewById(R.id.etFeedbackText);
        btnSubmitFeedback = findViewById(R.id.btnSubmitFeedback);
        btnBack           = findViewById(R.id.btnFeedbackBack);
        progressBar       = findViewById(R.id.progressBarFeedback);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Food Quality", "Hygiene", "Quantity / Portion", "Staff Behaviour", "Other"});
        spinnerCategory.setAdapter(catAdapter);

        btnBack.setOnClickListener(v -> finish());
        btnSubmitFeedback.setOnClickListener(v -> submitFeedback());
    }

    private void submitFeedback() {
        String category = spinnerCategory.getSelectedItem().toString();
        String text = etFeedback.getText() != null ? etFeedback.getText().toString().trim() : "";

        if (text.isEmpty()) {
            etFeedback.setError("Please describe your concern");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSubmitFeedback.setEnabled(false);

        String userId  = mAuth.getCurrentUser().getUid();
        String today   = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        Map<String, Object> data = new HashMap<>();
        data.put("userId",    userId);
        data.put("category",  category);
        data.put("feedback",  text);
        data.put("date",      today);
        data.put("status",    "open");
        data.put("timestamp", System.currentTimeMillis());

        db.collection("feedback").add(data)
                .addOnSuccessListener(ref -> {
                    progressBar.setVisibility(View.GONE);
                    btnSubmitFeedback.setEnabled(true);
                    Toast.makeText(this, "✅ Feedback submitted! Thank you.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSubmitFeedback.setEnabled(true);
                    Toast.makeText(this, "Failed to submit. Try again.", Toast.LENGTH_SHORT).show();
                });
    }
}
