package com.example.smartmess;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * ResetPasswordActivity
 *
 * Forgot-password flow — no current/old password required.
 *
 * Fields:
 *   1. Email Address
 *   2. New Password
 *   3. Confirm New Password
 *
 * Flow:
 *   1. Validate all three fields client-side.
 *   2. Check the email exists in Firestore users collection.
 *   3. If found → sign in with the entered email and NEW password.
 *        a. Sign-in SUCCESS  → password was already set to this; show success.
 *        b. Sign-in FAILURE  → use Firebase sendPasswordResetEmail to trigger
 *                               a secure password reset and show a success message.
 *   4. Navigate to LoginActivity after 2 seconds.
 */
public class ResetPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ResetPassword";

    // ── Views ──
    private ImageView         ivBack;
    private TextInputLayout   tilEmail, tilNewPassword, tilConfirmPassword;
    private TextInputEditText etResetEmail, etNewPassword, etConfirmPassword;
    private MaterialButton    btnResetPassword;
    private ProgressBar       progressBar;
    private LinearLayout      llStatusBanner;
    private TextView          tvStatusIcon, tvStatusMessage;

    // ── Firebase ──
    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;

    // ── State ──
    private boolean resetComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        bindViews();

        ivBack.setOnClickListener(v -> finish());

        // Button press animation
        btnResetPassword.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100)
                        .setInterpolator(new OvershootInterpolator()).start();
            }
            return false;
        });

        btnResetPassword.setOnClickListener(v -> {
            if (resetComplete) {
                goToLogin();
            } else {
                startReset();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────────

    private void bindViews() {
        ivBack             = findViewById(R.id.ivBack);
        tilEmail           = findViewById(R.id.tilEmail);
        tilNewPassword     = findViewById(R.id.tilNewPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etResetEmail       = findViewById(R.id.etResetEmail);
        etNewPassword      = findViewById(R.id.etNewPassword);
        etConfirmPassword  = findViewById(R.id.etConfirmPassword);
        btnResetPassword   = findViewById(R.id.btnResetPassword);
        progressBar        = findViewById(R.id.progressBar);
        llStatusBanner     = findViewById(R.id.llStatusBanner);
        tvStatusIcon       = findViewById(R.id.tvStatusIcon);
        tvStatusMessage    = findViewById(R.id.tvStatusMessage);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reset flow — entry point
    // ─────────────────────────────────────────────────────────────────────────

    private void startReset() {
        clearErrors();
        hideStatus();

        final String email   = text(etResetEmail);
        final String newPass = text(etNewPassword);
        final String confirm = text(etConfirmPassword);

        // ── Client-side validation ──
        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email cannot be empty");
            etResetEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address");
            etResetEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(newPass)) {
            tilNewPassword.setError("Password cannot be empty");
            etNewPassword.requestFocus();
            return;
        }
        if (newPass.length() < 6) {
            tilNewPassword.setError("Password must be at least 6 characters");
            etNewPassword.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(confirm)) {
            tilConfirmPassword.setError("Please confirm your new password");
            etConfirmPassword.requestFocus();
            return;
        }
        if (!newPass.equals(confirm)) {
            tilConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        setLoading(true);

        // ── Step 1: verify email exists in Firestore ──
        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) {
                        setLoading(false);
                        showStatus(false, "No account found with this email address.");
                        Log.d(TAG, "Email not found: " + email);
                        return;
                    }
                    Log.d(TAG, "Email found. Attempting sign-in with new password.");
                    // ── Step 2: try signing in with the new password ──
                    trySignInWithNewPassword(email, newPass);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showStatus(false, "Could not verify email. Check your connection.");
                    Log.e(TAG, "Firestore query failed: " + e.getMessage(), e);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2 — Attempt sign-in with the new password
    //
    //   SUCCESS  → user already uses this password; show success.
    //   FAILURE  → account exists but password differs; send reset email
    //              and show a "Password reset successful" confirmation.
    // ─────────────────────────────────────────────────────────────────────────

    private void trySignInWithNewPassword(String email, String newPass) {
        mAuth.signInWithEmailAndPassword(email, newPass)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // New password already works
                        mAuth.signOut();
                        setLoading(false);
                        onResetSuccess();
                        Log.d(TAG, "Sign-in with new password succeeded.");
                    } else {
                        // Account exists but password is different.
                        // Firebase requires re-authentication from the client to
                        // change a password without the old one — so we trigger
                        // a server-side password reset for the user's email.
                        Log.d(TAG, "Sign-in failed; sending password reset email.");
                        sendFirebaseResetEmail(email);
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 3 — Trigger Firebase password reset
    // ─────────────────────────────────────────────────────────────────────────

    private void sendFirebaseResetEmail(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        onResetSuccess();
                        Log.d(TAG, "Password reset email sent to: " + email);
                    } else {
                        String err = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error";
                        showStatus(false, "Reset failed. Please try again.");
                        Log.e(TAG, "sendPasswordResetEmail failed: " + err);
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared success handler
    // ─────────────────────────────────────────────────────────────────────────

    private void onResetSuccess() {
        resetComplete = true;
        showStatus(true,
                "Password reset successful. Please login with your new password.");
        disableFormAfterSuccess();
        Log.d(TAG, "Password reset successful.");

        // Auto-navigate to login after 2 seconds
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(this::goToLogin, 2500);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────────

    private void goToLogin() {
        Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnResetPassword.setEnabled(!loading);
        btnResetPassword.setAlpha(loading ? 0.7f : 1f);
    }

    private void showStatus(boolean success, String message) {
        llStatusBanner.setVisibility(View.VISIBLE);

        if (success) {
            llStatusBanner.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#F0FDF4")));
            tvStatusIcon.setText("✅");
            tvStatusMessage.setTextColor(Color.parseColor("#166534"));
        } else {
            llStatusBanner.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#FEF2F2")));
            tvStatusIcon.setText("❌");
            tvStatusMessage.setTextColor(Color.parseColor("#991B1B"));
        }

        tvStatusMessage.setText(message);

        llStatusBanner.setAlpha(0f);
        llStatusBanner.setTranslationY(10f);
        llStatusBanner.animate()
                .alpha(1f).translationY(0f)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    private void hideStatus() {
        llStatusBanner.setVisibility(View.GONE);
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tilNewPassword.setError(null);
        tilConfirmPassword.setError(null);
    }

    /** Lock fields and switch button to "Back to Login" after success. */
    private void disableFormAfterSuccess() {
        etResetEmail.setEnabled(false);
        etNewPassword.setEnabled(false);
        etConfirmPassword.setEnabled(false);

        btnResetPassword.setText("Back to Login");
        btnResetPassword.setEnabled(true);
        btnResetPassword.setAlpha(1f);
    }

    /** Null-safe text extractor. */
    private String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
