package com.example.smartmess;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FeedbackViewerActivity — Admin screen to review all student complaints.
 * Reads from: feedback (collection)
 * Each doc: { userId, category, feedback, date, status, timestamp }
 */
public class FeedbackViewerActivity extends AppCompatActivity {

    private LinearLayout llFeedbackList;
    private TextView tvFeedbackCount;
    private ProgressBar progressBar;
    private Button btnBack;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback_viewer);

        db              = FirebaseFirestore.getInstance();
        llFeedbackList  = findViewById(R.id.llFeedbackList);
        tvFeedbackCount = findViewById(R.id.tvFeedbackCount);
        progressBar     = findViewById(R.id.progressBarFeedbackViewer);
        btnBack         = findViewById(R.id.btnFeedbackViewerBack);

        btnBack.setOnClickListener(v -> finish());
        loadFeedback();
    }

    private void loadFeedback() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("feedback")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    progressBar.setVisibility(View.GONE);
                    tvFeedbackCount.setText("Total complaints: " + snapshots.size());

                    llFeedbackList.removeAllViews();

                    if (snapshots.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("No feedback submitted yet. 🎉");
                        empty.setTextSize(15);
                        empty.setPadding(16, 32, 16, 32);
                        llFeedbackList.addView(empty);
                        return;
                    }

                    SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String category = doc.getString("category");
                        String text     = doc.getString("feedback");
                        String uid      = doc.getString("userId");
                        String status   = doc.getString("status");
                        Long   ts       = doc.getLong("timestamp");
                        String dateStr  = ts != null ? fmt.format(new Date(ts)) : "Unknown date";

                        // Card-like container
                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
                        LinearLayout.LayoutParams cardParams =
                                new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT);
                        cardParams.setMargins(0, 0, 0, 24);
                        card.setLayoutParams(cardParams);
                        card.setPadding(32, 24, 32, 24);

                        // Category + date row
                        TextView tvCat = new TextView(this);
                        tvCat.setText(categoryIcon(category) + " " + category + "  ·  " + dateStr);
                        tvCat.setTextSize(12);
                        tvCat.setTextColor(android.graphics.Color.parseColor("#6750A4"));

                        // Feedback body
                        TextView tvText = new TextView(this);
                        tvText.setText(text);
                        tvText.setTextSize(15);
                        tvText.setTextColor(android.graphics.Color.parseColor("#1A1A2E"));
                        tvText.setPadding(0, 8, 0, 8);

                        // Student ID (truncated for privacy)
                        String shortUid = uid != null && uid.length() > 8
                                ? uid.substring(0, 8) + "…" : uid;
                        TextView tvUser = new TextView(this);
                        tvUser.setText("Student: " + shortUid + "  |  Status: " + (status != null ? status : "open"));
                        tvUser.setTextSize(11);
                        tvUser.setTextColor(android.graphics.Color.parseColor("#888888"));

                        // Mark Resolved button
                        Button btnResolve = new Button(this);
                        btnResolve.setText("Mark Resolved ✓");
                        btnResolve.setOnClickListener(v -> {
                            doc.getReference().update("status", "resolved")
                                    .addOnSuccessListener(a -> {
                                        tvUser.setText("Status: resolved");
                                        btnResolve.setEnabled(false);
                                        Toast.makeText(this, "Marked as resolved", Toast.LENGTH_SHORT).show();
                                    });
                        });
                        if ("resolved".equals(status)) btnResolve.setEnabled(false);

                        card.addView(tvCat);
                        card.addView(tvText);
                        card.addView(tvUser);
                        card.addView(btnResolve);
                        llFeedbackList.addView(card);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load feedback", Toast.LENGTH_SHORT).show();
                });
    }

    private String categoryIcon(String category) {
        if (category == null) return "📌";
        switch (category) {
            case "Food Quality":      return "🍽";
            case "Hygiene":           return "🧹";
            case "Quantity / Portion":return "⚖";
            case "Staff Behaviour":   return "🤝";
            default:                  return "📌";
        }
    }
}
