package com.example.smartmess;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private TextInputEditText etName, etHostel, etEmail;
    private MaterialButton btnSave;
    private Button btnBack;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        etName     = findViewById(R.id.etProfileName);
        etHostel   = findViewById(R.id.etProfileHostel);
        etEmail    = findViewById(R.id.etProfileEmail);
        btnSave    = findViewById(R.id.btnSaveProfile);
        btnBack    = findViewById(R.id.btnProfileBack);
        progressBar = findViewById(R.id.progressBarProfile);

        loadProfile();
        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveProfile());
    }

    private void loadProfile() {
        String uid = mAuth.getCurrentUser().getUid();
        progressBar.setVisibility(View.VISIBLE);
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    progressBar.setVisibility(View.GONE);
                    if (doc.exists()) {
                        etName.setText(doc.getString("name"));
                        etHostel.setText(doc.getString("hostelBlock"));
                        etEmail.setText(doc.getString("email"));
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveProfile() {
        String name   = etName.getText() != null ? etName.getText().toString().trim() : "";
        String hostel = etHostel.getText() != null ? etHostel.getText().toString().trim() : "";

        if (name.isEmpty()) { etName.setError("Name cannot be empty"); return; }

        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("name",        name);
        updates.put("hostelBlock", hostel);

        db.collection("users").document(mAuth.getCurrentUser().getUid())
                .update(updates)
                .addOnSuccessListener(v -> {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "✅ Profile updated!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show();
                });
    }
}
