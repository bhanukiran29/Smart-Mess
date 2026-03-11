package com.example.smartmess;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * WalletActivity — Manages the student's digital wallet balance.
 *
 * Firestore paths:
 *   wallets/{userId}                          → { balance, lastUpdated }
 *   wallet_transactions/{userId}/txns/{auto}  → { type, amount, description, timestamp }
 */
public class WalletActivity extends AppCompatActivity {

    private TextView tvWalletBalance, tvNoTransactions;
    private LinearLayout llTransactions;
    private MaterialButton btn50, btn100, btn200;
    private Button btnBack;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DocumentReference walletRef;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() == null) { finish(); return; }

        userId    = mAuth.getCurrentUser().getUid();
        walletRef = db.collection("wallets").document(userId);

        initViews();
        loadBalance();
        loadTransactionHistory();

        btnBack.setOnClickListener(v -> finish());
        btn50.setOnClickListener(v  -> showPaymentDialog(50));
        btn100.setOnClickListener(v -> showPaymentDialog(100));
        btn200.setOnClickListener(v -> showPaymentDialog(200));
    }

    // -------------------------------------------------------------------------
    // UI Init
    // -------------------------------------------------------------------------
    private void initViews() {
        tvWalletBalance  = findViewById(R.id.tvWalletBalance);
        llTransactions   = findViewById(R.id.llTransactions);
        tvNoTransactions = findViewById(R.id.tvNoTransactions);
        btn50   = findViewById(R.id.btn50);
        btn100  = findViewById(R.id.btn100);
        btn200  = findViewById(R.id.btn200);
        btnBack = findViewById(R.id.btnWalletBack);
    }

    // -------------------------------------------------------------------------
    // Balance
    // -------------------------------------------------------------------------
    private void loadBalance() {
        tvWalletBalance.setText("Loading…");
        walletRef.get().addOnSuccessListener(snapshot -> {
            double balance = 0;
            if (snapshot.exists() && snapshot.getDouble("balance") != null) {
                balance = snapshot.getDouble("balance");
            } else {
                Map<String, Object> init = new HashMap<>();
                init.put("balance", 0.0);
                init.put("lastUpdated", System.currentTimeMillis());
                walletRef.set(init);
            }
            tvWalletBalance.setText(String.format("₹%.2f", balance));
        }).addOnFailureListener(e -> tvWalletBalance.setText("Error loading balance"));
    }

    // -------------------------------------------------------------------------
    // Transaction History
    // Firestore path: wallet_transactions/{userId}/txns  ordered by timestamp desc
    // -------------------------------------------------------------------------
    private void loadTransactionHistory() {
        db.collection("wallet_transactions")
                .document(userId)
                .collection("txns")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(15)
                .get()
                .addOnSuccessListener(snapshots -> {
                    // Remove placeholder text
                    llTransactions.removeAllViews();

                    if (snapshots.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("No transactions yet");
                        empty.setTextColor(Color.parseColor("#999999"));
                        empty.setTextSize(14);
                        llTransactions.addView(empty);
                        return;
                    }

                    SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String type   = doc.getString("type");        // "topup" or "deduction"
                        Double amount = doc.getDouble("amount");
                        String desc   = doc.getString("description");
                        Long   ts     = doc.getLong("timestamp");

                        // Build a row view
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams rowParams =
                                new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT);
                        rowParams.setMargins(0, 0, 0, 16);
                        row.setLayoutParams(rowParams);

                        // Left: description + date
                        LinearLayout textCol = new LinearLayout(this);
                        textCol.setOrientation(LinearLayout.VERTICAL);
                        textCol.setLayoutParams(
                                new LinearLayout.LayoutParams(0,
                                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                        TextView tvDesc = new TextView(this);
                        tvDesc.setText(desc != null ? desc : "Transaction");
                        tvDesc.setTextSize(14);
                        tvDesc.setTextColor(Color.parseColor("#1A1A2E"));

                        TextView tvDate = new TextView(this);
                        tvDate.setText(ts != null ? fmt.format(new Date(ts)) : "");
                        tvDate.setTextSize(11);
                        tvDate.setTextColor(Color.parseColor("#999999"));

                        textCol.addView(tvDesc);
                        textCol.addView(tvDate);

                        // Right: amount
                        TextView tvAmt = new TextView(this);
                        boolean isTopup = "topup".equals(type);
                        tvAmt.setText((isTopup ? "+" : "−") + "₹" + String.format("%.0f", Math.abs(amount != null ? amount : 0)));
                        tvAmt.setTextSize(15);
                        tvAmt.setTypeface(null, Typeface.BOLD);
                        tvAmt.setTextColor(isTopup
                                ? Color.parseColor("#2E7D32")  // green for top-up
                                : Color.parseColor("#C62828")); // red for deduction
                        tvAmt.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);

                        row.addView(textCol);
                        row.addView(tvAmt);
                        llTransactions.addView(row);

                        // Divider
                        View divider = new View(this);
                        LinearLayout.LayoutParams dp =
                                new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                        dp.setMargins(0, 0, 0, 16);
                        divider.setLayoutParams(dp);
                        divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
                        llTransactions.addView(divider);
                    }
                })
                .addOnFailureListener(e -> {
                    // silently fail — not critical
                });
    }

    // -------------------------------------------------------------------------
    // Simulated Payment Dialog
    // -------------------------------------------------------------------------
    private void showPaymentDialog(int amount) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_mock_payment, null);

        TextView tvAmount          = dialogView.findViewById(R.id.tvPaymentAmount);
        TextInputEditText etCard   = dialogView.findViewById(R.id.etCardNumber);
        TextInputEditText etExpiry = dialogView.findViewById(R.id.etExpiry);
        TextInputEditText etCvv    = dialogView.findViewById(R.id.etCvv);
        TextInputEditText etName   = dialogView.findViewById(R.id.etCardName);
        MaterialButton btnPay      = dialogView.findViewById(R.id.btnConfirmPayment);
        ProgressBar spinner        = dialogView.findViewById(R.id.pbPaymentProcessing);
        TextView tvProcessing      = dialogView.findViewById(R.id.tvProcessingLabel);

        tvAmount.setText("Adding ₹" + amount + " to wallet");
        btnPay.setText("Pay ₹" + amount);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView).setCancelable(true).create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
        }

        btnPay.setOnClickListener(v -> {
            String card   = etCard.getText()   != null ? etCard.getText().toString().trim()   : "";
            String expiry = etExpiry.getText()  != null ? etExpiry.getText().toString().trim()  : "";
            String cvv    = etCvv.getText()     != null ? etCvv.getText().toString().trim()     : "";
            String name   = etName.getText()    != null ? etName.getText().toString().trim()    : "";

            if (card.length() < 12)          { etCard.setError("Enter a valid card number"); return; }
            if (expiry.length() < 4)         { etExpiry.setError("Enter expiry (MM/YY)"); return; }
            if (cvv.length() < 3)            { etCvv.setError("Enter 3-digit CVV"); return; }
            if (TextUtils.isEmpty(name))     { etName.setError("Enter name on card"); return; }

            btnPay.setEnabled(false);
            etCard.setEnabled(false); etExpiry.setEnabled(false);
            etCvv.setEnabled(false);  etName.setEnabled(false);
            spinner.setVisibility(View.VISIBLE);
            tvProcessing.setVisibility(View.VISIBLE);
            btnPay.setText("Processing…");

            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    completeTopUp(amount, dialog), 2000);
        });

        dialog.show();
    }

    /** Called after the simulated 2-second "processing" delay */
    private void completeTopUp(int amount, AlertDialog dialog) {
        walletRef.update(
                "balance",     FieldValue.increment(amount),
                "lastUpdated", System.currentTimeMillis()
        ).addOnSuccessListener(aVoid -> {
            // Log the transaction
            logTransaction("topup", amount, "Wallet Top-up (Card Payment)");

            dialog.dismiss();
            Toast.makeText(this, "✅ ₹" + amount + " added successfully!", Toast.LENGTH_LONG).show();
            loadBalance();
            loadTransactionHistory(); // Refresh list
        }).addOnFailureListener(e -> {
            dialog.dismiss();
            Toast.makeText(this, "Payment processed but balance update failed. Try again.", Toast.LENGTH_LONG).show();
        });
    }

    // -------------------------------------------------------------------------
    // Transaction Logger — called by WalletActivity AND MainActivity
    // Firestore path: wallet_transactions/{userId}/txns/{autoId}
    // -------------------------------------------------------------------------
    public static void logTransaction(FirebaseFirestore db, String userId,
                                      String type, double amount, String description) {
        Map<String, Object> tx = new HashMap<>();
        tx.put("type",        type);          // "topup" or "deduction"
        tx.put("amount",      amount);
        tx.put("description", description);
        tx.put("timestamp",   System.currentTimeMillis());

        db.collection("wallet_transactions")
                .document(userId)
                .collection("txns")
                .add(tx);
    }

    /** Convenience overload for use inside WalletActivity itself */
    private void logTransaction(String type, double amount, String description) {
        logTransaction(db, userId, type, amount, description);
    }
}
