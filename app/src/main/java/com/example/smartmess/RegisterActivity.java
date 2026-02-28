package com.example.smartmess;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartmess.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPassword, etBlock;
    private RadioGroup rgRole;
    private MaterialButton btnRegister;
    private TextView tvLogin;
    private ProgressBar progressBar;

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
        etBlock = findViewById(R.id.etBlock);
        rgRole = findViewById(R.id.rgRole);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
        progressBar = findViewById(R.id.progressBar);

        btnRegister.setOnClickListener(v -> registerUser());

        tvLogin.setOnClickListener(v -> {
            finish(); // Go back to login screen
        });
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String block = etBlock.getText().toString().trim();

        // Getting the selected role
        int selectedRoleId = rgRole.getCheckedRadioButtonId();
        RadioButton selectedRoleButton = findViewById(selectedRoleId);
        String role = selectedRoleButton.getText().toString().toLowerCase(); // "student", "staff", or "admin"

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required.");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required.");
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
        if (TextUtils.isEmpty(block)) {
            etBlock.setError("Hostel block is required.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        // 1. Create User in Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        saveUserToFirestore(userId, name, email, role, block);
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
            Toast.makeText(RegisterActivity.this, "Account Created Successfully!", Toast.LENGTH_LONG).show();

            // Send them to correct dashboard
            Intent intent;
            if ("admin".equals(role)) {
                intent = new Intent(RegisterActivity.this, AdminActivity.class);
            } else if ("staff".equals(role)) {
                intent = new Intent(RegisterActivity.this, StaffActivity.class);
            } else {
                intent = new Intent(RegisterActivity.this, MainActivity.class);
            }
            startActivity(intent);
            finish();

        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            btnRegister.setEnabled(true);
            Toast.makeText(RegisterActivity.this, "Crash saving data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
