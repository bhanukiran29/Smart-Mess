package com.example.smartmess;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Color;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartmess.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPassword, etConfirmPassword, etId;
    private MaterialButton btnRegister;
    private ImageView ivBack;
    private ProgressBar progressBar, progressForm;
    private MaterialCardView cardRoleStudent, cardRoleStaff, cardRoleAdmin;
    private String selectedRole = "student"; // Default role

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etId = findViewById(R.id.etId);
        btnRegister = findViewById(R.id.btnRegister);
        ivBack = findViewById(R.id.ivBack);
        progressBar = findViewById(R.id.progressBar);
        progressForm = findViewById(R.id.progressForm);
        
        cardRoleStudent = findViewById(R.id.cardRoleStudent);
        cardRoleStaff = findViewById(R.id.cardRoleStaff);
        cardRoleAdmin = findViewById(R.id.cardRoleAdmin);

        btnRegister.setOnClickListener(v -> registerUser());

        ivBack.setOnClickListener(v -> finish());
        
        setupRoleCards();
        setupFormProgress();
    }

    private void setupRoleCards() {
        View.OnClickListener roleClickListener = v -> {
            // Animate click
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).withEndAction(() -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).setInterpolator(new OvershootInterpolator()).start();
                }).start();
            }).start();
            
            // Reset all cards
            resetCard(cardRoleStudent);
            resetCard(cardRoleStaff);
            resetCard(cardRoleAdmin);
            
            // Highlight selected
            MaterialCardView selected = (MaterialCardView) v;
            selected.setStrokeColor(Color.parseColor("#1E3A8A"));
            selected.setStrokeWidth(Math.round(2 * getResources().getDisplayMetrics().density));
            selected.setCardBackgroundColor(Color.parseColor("#EFF6FF"));
            
            if (v.getId() == R.id.cardRoleStudent) selectedRole = "student";
            else if (v.getId() == R.id.cardRoleStaff) selectedRole = "staff";
            else if (v.getId() == R.id.cardRoleAdmin) selectedRole = "admin";
        };

        cardRoleStudent.setOnClickListener(roleClickListener);
        cardRoleStaff.setOnClickListener(roleClickListener);
        cardRoleAdmin.setOnClickListener(roleClickListener);
    }
    
    private void resetCard(MaterialCardView card) {
        card.setStrokeColor(Color.parseColor("#E2E8F0"));
        card.setStrokeWidth(Math.round(1 * getResources().getDisplayMetrics().density));
        card.setCardBackgroundColor(Color.WHITE);
    }

    private void setupFormProgress() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                int score = 0;
                if (!TextUtils.isEmpty(etName.getText())) score++;
                if (!TextUtils.isEmpty(etEmail.getText())) score++;
                if (!TextUtils.isEmpty(etId.getText())) score++;
                if (!TextUtils.isEmpty(etPassword.getText())) score++;
                if (!TextUtils.isEmpty(etConfirmPassword.getText())) score++;
                progressForm.setProgress(score);
            }
        };
        etName.addTextChangedListener(watcher);
        etEmail.addTextChangedListener(watcher);
        etId.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);
        etConfirmPassword.addTextChangedListener(watcher);
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        String idStr = etId.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required.");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required.");
            return;
        }
        if (TextUtils.isEmpty(idStr)) {
            etId.setError("ID is required.");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required.");
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be >= 6 Characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        // 1. Create User in Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        // Passing idStr into the 'block' field for Firebase persistence backward compatibility
                        saveUserToFirestore(userId, name, email, selectedRole, idStr);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnRegister.setEnabled(true);
                        Toast.makeText(RegisterActivity.this, "Error: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String userId, String name, String email, String role, String block) {
        try {
            // 2. Save user details to Firestore database using our Model class
            User newUser = new User(userId, name, email, role, block);

            // Set the document locally, it will automatically sync to cloud in background!
            db.collection("users").document(userId).set(newUser);

            progressBar.setVisibility(View.GONE);
            Toast.makeText(RegisterActivity.this, "Account Created Successfully! Please login.", Toast.LENGTH_LONG).show();

            // Firebase Auth automatically signs in the created user. 
            // We must sign them out so they hit the login screen properly.
            mAuth.signOut();

            // Send them to login page
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            btnRegister.setEnabled(true);
            Toast.makeText(RegisterActivity.this, "Crash saving data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
