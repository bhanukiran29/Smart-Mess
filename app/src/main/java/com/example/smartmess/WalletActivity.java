package com.example.smartmess;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WalletActivity extends AppCompatActivity {

    private TextView tvWalletBalance;
    private RecyclerView rvTransactions;
    private LinearLayout llEmptyState;
    private FrameLayout flConfettiContainer;

    private MaterialCardView btn50, btn100, btn200;
    private TextView tv50, tv100, tv200;
    private ImageView ivBack;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DocumentReference walletRef;
    private String userId;

    private TransactionAdapter adapter;
    private List<Map<String, Object>> txnList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }
        userId = mAuth.getCurrentUser().getUid();
        walletRef = db.collection("wallets").document(userId);

        initViews();
        setupInteractions();

        loadBalance();
        loadTransactionHistory();
    }

    private void initViews() {
        tvWalletBalance = findViewById(R.id.tvWalletBalance);
        rvTransactions = findViewById(R.id.rvTransactions);
        llEmptyState = findViewById(R.id.llEmptyState);
        flConfettiContainer = findViewById(R.id.flConfettiContainer);
        ivBack = findViewById(R.id.ivBack);

        btn50 = findViewById(R.id.btnAmount50);
        btn100 = findViewById(R.id.btnAmount100);
        btn200 = findViewById(R.id.btnAmount200);

        tv50 = findViewById(R.id.tvAmount50);
        tv100 = findViewById(R.id.tvAmount100);
        tv200 = findViewById(R.id.tvAmount200);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(txnList);
        rvTransactions.setAdapter(adapter);
    }

    private void setupInteractions() {
        ivBack.setOnClickListener(v -> finish());

        View.OnTouchListener bounceListener = (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).setInterpolator(new OvershootInterpolator()).start();
                    break;
            }
            return false;
        };

        btn50.setOnTouchListener(bounceListener);
        btn100.setOnTouchListener(bounceListener);
        btn200.setOnTouchListener(bounceListener);

        btn50.setOnClickListener(v -> activateAmountButton(btn50, tv50, 50));
        btn100.setOnClickListener(v -> activateAmountButton(btn100, tv100, 100));
        btn200.setOnClickListener(v -> activateAmountButton(btn200, tv200, 200));
    }

    private void activateAmountButton(MaterialCardView activeBtn, TextView activeTv, int amount) {
        // Reset all
        btn50.setCardBackgroundColor(Color.WHITE); tv50.setTextColor(Color.parseColor("#1E3A8A"));
        btn100.setCardBackgroundColor(Color.WHITE); tv100.setTextColor(Color.parseColor("#1E3A8A"));
        btn200.setCardBackgroundColor(Color.WHITE); tv200.setTextColor(Color.parseColor("#1E3A8A"));

        // Set active
        activeBtn.setCardBackgroundColor(Color.parseColor("#1E3A8A"));
        activeTv.setTextColor(Color.WHITE);

        // Show mock payment sheet
        showPaymentSheet(amount, activeBtn, activeTv);
    }

    private void loadBalance() {
        walletRef.get().addOnSuccessListener(snapshot -> {
            int targetBal = 0;
            if (snapshot.exists() && snapshot.getDouble("balance") != null) {
                targetBal = snapshot.getDouble("balance").intValue();
            } else {
                Map<String, Object> init = new HashMap<>();
                init.put("balance", 0.0);
                init.put("lastUpdated", System.currentTimeMillis());
                walletRef.set(init);
            }

            AnimationUtils.countUpTo(tvWalletBalance, 0, targetBal, 800);
        });
    }

    private void loadTransactionHistory() {
        db.collection("wallet_transactions")
                .document(userId)
                .collection("txns")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(snapshots -> {
                    txnList.clear();
                    if (snapshots.isEmpty()) {
                        rvTransactions.setVisibility(View.GONE);
                        llEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        rvTransactions.setVisibility(View.VISIBLE);
                        llEmptyState.setVisibility(View.GONE);
                        for (QueryDocumentSnapshot doc : snapshots) {
                            txnList.add(doc.getData());
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void showPaymentSheet(int amount, MaterialCardView btn, TextView tv) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.layout_mock_payment_sheet, null);
        
        TextView tvSub = sheet.findViewById(R.id.tvPaymentSubtitle);
        tvSub.setText("Adding ₹" + amount + " to wallet");

        View.OnClickListener clickListener = v -> {
            dialog.dismiss();
            completeTopUp(amount);
            // Reset button style
            btn.setCardBackgroundColor(Color.WHITE);
            tv.setTextColor(Color.parseColor("#1E3A8A"));
        };

        sheet.findViewById(R.id.btnPayUpi).setOnClickListener(clickListener);
        sheet.findViewById(R.id.btnPayCard).setOnClickListener(clickListener);

        dialog.setOnCancelListener(di -> {
            btn.setCardBackgroundColor(Color.WHITE);
            tv.setTextColor(Color.parseColor("#1E3A8A"));
        });

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void completeTopUp(int amount) {
        walletRef.update("balance", FieldValue.increment(amount), "lastUpdated", System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> {
                    logTransaction("topup", amount, "Wallet Top-up");
                    fireConfetti();
                    loadBalance();
                    loadTransactionHistory();
                });
    }

    private void fireConfetti() {
        int colors[] = {Color.parseColor("#1E3A8A"), Color.parseColor("#FF6600"), Color.parseColor("#00A34F"), Color.parseColor("#FBBF24")};
        for (int i = 0; i < 20; i++) {
            View particle = new View(this);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(24, 24);
            lp.gravity = android.view.Gravity.CENTER;
            particle.setLayoutParams(lp);
            
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(colors[i % colors.length]);
            particle.setBackground(gd);
            
            flConfettiContainer.addView(particle);

            float angle = (float) (Math.random() * Math.PI * 2);
            float radius = 100f + (float) Math.random() * 200f;
            float destX = (float) Math.cos(angle) * radius;
            float destY = (float) Math.sin(angle) * radius;

            particle.animate()
                    .translationX(destX)
                    .translationY(destY)
                    .alpha(0f)
                    .setDuration(600 + (long)(Math.random() * 400))
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> flConfettiContainer.removeView(particle))
                    .start();
        }
    }

    // Called natively by MainActivity for QR deductions!
    public static void logTransaction(FirebaseFirestore db, String userId, String type, double amount, String description) {
        Map<String, Object> tx = new HashMap<>();
        tx.put("type", type);
        tx.put("amount", amount);
        tx.put("description", description);
        tx.put("timestamp", System.currentTimeMillis());
        db.collection("wallet_transactions").document(userId).collection("txns").add(tx);
    }

    private void logTransaction(String type, double amount, String description) {
        logTransaction(db, userId, type, amount, description);
    }

    // RECYCLER ADAPTER
    class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TxnViewHolder> {
        private List<Map<String, Object>> mList;
        private int lastPosition = -1;

        public TransactionAdapter(List<Map<String, Object>> list) {
            this.mList = list;
        }

        @NonNull
        @Override
        public TxnViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
            return new TxnViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull TxnViewHolder holder, int position) {
            Map<String, Object> tx = mList.get(position);
            String type = (String) tx.get("type");
            Double amt = (Double) tx.get("amount");
            String desc = (String) tx.get("description");
            Long ts = (Long) tx.get("timestamp");

            boolean isTopup = "topup".equals(type);
            holder.tvAmount.setText((isTopup ? "+" : "-") + "₹" + Math.round(amt != null ? amt : 0));
            holder.tvAmount.setTextColor(Color.parseColor(isTopup ? "#16A34A" : "#EF4444"));

            holder.cvIconBg.setCardBackgroundColor(Color.parseColor(isTopup ? "#DCFCE7" : "#FEE2E2"));
            holder.tvIcon.setText(isTopup ? "↓" : "↑");
            holder.tvIcon.setTextColor(Color.parseColor(isTopup ? "#16A34A" : "#EF4444"));

            holder.tvDescription.setText(desc);
            holder.tvDate.setText(ts != null ? new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(new Date(ts)) : "");

            // Stagger slide-in animation
            if (position > lastPosition) {
                holder.itemView.setTranslationX(150f);
                holder.itemView.setAlpha(0f);
                holder.itemView.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setStartDelay(50L * position)
                        .setDuration(300)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                lastPosition = position;
            }
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }

        class TxnViewHolder extends RecyclerView.ViewHolder {
            TextView tvIcon, tvDescription, tvDate, tvAmount;
            MaterialCardView cvIconBg;

            public TxnViewHolder(@NonNull View itemView) {
                super(itemView);
                tvIcon = itemView.findViewById(R.id.tvIcon);
                tvDescription = itemView.findViewById(R.id.tvDescription);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvAmount = itemView.findViewById(R.id.tvAmount);
                cvIconBg = itemView.findViewById(R.id.cvIconBg);
            }
        }
    }
}
