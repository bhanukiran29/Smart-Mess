package com.example.smartmess;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;
    private TextView tvRegister;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Edge to Edge Full Screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        // Check if user is already logged in
        if (mAuth.getCurrentUser() != null) {
            checkUserRoleAndRedirect(mAuth.getCurrentUser().getUid());
        }

        // Initialize UI components
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        progressBar = findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> loginUser());

        btnLogin.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).setInterpolator(new OvershootInterpolator()).start();
                    break;
            }
            return false;
        });

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required.");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        // Firebase Auth login
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        // Success animation morph to green checkmark
                        btnLogin.setText("✓");
                        btnLogin.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00A34F")));
                        btnLogin.setEnabled(false);
                        
                        new android.os.Handler().postDelayed(() -> {
                            checkUserRoleAndRedirect(userId);
                        }, 500);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Error: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void checkUserRoleAndRedirect(String userId) {
        db.collection("users").document(userId).get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);

                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document != null && document.exists()) {
                            String role = document.getString("role");

                            // Redirect based on role
                            Intent intent;
                            if ("admin".equalsIgnoreCase(role)) {
                                intent = new Intent(LoginActivity.this, AdminActivity.class); // redirected to Admin
                                                                                              // Dashboard
                                Toast.makeText(LoginActivity.this, "Welcome Admin!", Toast.LENGTH_SHORT).show();
                            } else if ("staff".equalsIgnoreCase(role)) {
                                intent = new Intent(LoginActivity.this, StaffActivity.class); // change to StaffActivity
                                Toast.makeText(LoginActivity.this, "Welcome Staff!", Toast.LENGTH_SHORT).show();
                            } else {
                                intent = new Intent(LoginActivity.this, MainActivity.class); // change to
                                                                                             // StudentActivity later
                                Toast.makeText(LoginActivity.this, "Welcome Student!", Toast.LENGTH_SHORT).show();
                            }

                            startActivity(intent);
                            finish(); // Close login activity
                        } else {
                            Toast.makeText(LoginActivity.this, "User profile not found in database.", Toast.LENGTH_LONG)
                                    .show();
                            mAuth.signOut();
                        }
                    } else {
                        String errMsg = task.getException() != null ? task.getException().getMessage()
                                : "Unknown error";
                        Toast.makeText(LoginActivity.this, "Failed to fetch user role: " + errMsg, Toast.LENGTH_LONG)
                                .show();
                    }
                });
    }
}
