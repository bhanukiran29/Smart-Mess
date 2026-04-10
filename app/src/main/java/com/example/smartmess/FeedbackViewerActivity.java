package com.example.smartmess;

import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FeedbackViewerActivity extends AppCompatActivity {

    private ImageView ivBack;
    private ChipGroup cgFilters;
    private RecyclerView rvFeedback;
    private ProgressBar progressBar;

    private FirebaseFirestore db;
    private FeedbackAdapter adapter;
    private List<FeedbackItem> masterList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback_viewer);

        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupFilters();
        loadFeedback();

        ivBack.setOnClickListener(v -> finish());
    }

    private void initializeViews() {
        ivBack = findViewById(R.id.ivBack);
        cgFilters = findViewById(R.id.cgFilters);
        rvFeedback = findViewById(R.id.rvFeedback);
        progressBar = findViewById(R.id.progressBar);

        rvFeedback.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FeedbackAdapter();
        rvFeedback.setAdapter(adapter);

        // Native staggered list animation mechanics
        LayoutAnimationController animation = new LayoutAnimationController(
                new AlphaAnimation(0f, 1f), 0.15f
        );
        animation.setInterpolator(new DecelerateInterpolator());
        rvFeedback.setLayoutAnimation(animation);
    }

    private void setupFilters() {
        int[] chipIds = {R.id.fAll, R.id.fQual, R.id.fPort, R.id.fHyg, R.id.fBill, R.id.fOther};
        
        for (int id : chipIds) {
            Chip chip = findViewById(id);
            chip.setChipBackgroundColorResource(android.R.color.transparent);
            
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    chip.setChipBackgroundColorResource(android.R.color.transparent);
                    chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#432C7A"))); // Purple
                    chip.setTextColor(Color.WHITE);
                    
                    applyFilter(chip.getText().toString());
                } else {
                    chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F1F5F9")));
                    chip.setTextColor(Color.parseColor("#64748B"));
                }
            });
        }
        
        ((Chip) findViewById(R.id.fAll)).setChecked(true);
    }

    private void applyFilter(String filterValue) {
        if (masterList.isEmpty()) return;
        
        rvFeedback.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            List<FeedbackItem> filtered = new ArrayList<>();
            if (filterValue.equals("All")) {
                filtered.addAll(masterList);
            } else {
                for (FeedbackItem item : masterList) {
                    if (filterValue.equals(item.category)) filtered.add(item);
                }
            }
            adapter.setData(filtered);
            rvFeedback.animate().alpha(1f).setDuration(150).start();
        }).start();
    }

    private void loadFeedback() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("feedback")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    progressBar.setVisibility(View.GONE);
                    masterList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        FeedbackItem item = new FeedbackItem();
                        item.id = doc.getId();
                        item.category = doc.getString("category") != null ? doc.getString("category") : "Other";
                        item.text = doc.getString("feedback");
                        item.rating = doc.getLong("rating") != null ? doc.getLong("rating").intValue() : 0;
                        item.ts = doc.getLong("timestamp");
                        masterList.add(item);
                    }
                    adapter.setData(masterList);
                    rvFeedback.scheduleLayoutAnimation();
                });
    }

    private static class FeedbackItem {
        String id, category, text;
        int rating;
        Long ts;
    }

    private class FeedbackAdapter extends RecyclerView.Adapter<FeedbackAdapter.ViewHolder> {
        private List<FeedbackItem> items = new ArrayList<>();

        public void setData(List<FeedbackItem> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feedback, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            FeedbackItem item = items.get(position);
            
            holder.tvFeedbackText.setText(item.text != null ? item.text : "");
            holder.tvCategoryBadge.setText(item.category);
            
            // Map Star Rating string math automatically
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                stars.append(i < item.rating ? "★" : "☆");
            }
            holder.tvStars.setText(stars.toString());

            if (item.ts != null) {
                CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(item.ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
                holder.tvTimeAgo.setText(timeAgo);
            } else {
                holder.tvTimeAgo.setText("");
            }

            // Assign structural Category Colors mapped dynamically
            int colorCode = Color.parseColor("#432C7A"); // Default purple for Other
            switch (item.category) {
                case "Food Quality": colorCode = Color.parseColor("#EF4444"); break; // Red
                case "Portion Size": colorCode = Color.parseColor("#F59E0B"); break; // Amber
                case "Hygiene":      colorCode = Color.parseColor("#06B6D4"); break; // Cyan
                case "Billing":      colorCode = Color.parseColor("#10B981"); break; // Emerald
            }
            
            holder.vLeftBorder.setBackgroundColor(colorCode);
            holder.tvCategoryBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorCode));
            
            // Expand string mechanic logic
            holder.tvReadMore.setVisibility(item.text != null && item.text.length() > 120 ? View.VISIBLE : View.GONE);
            holder.tvReadMore.setOnClickListener(v -> {
                if (holder.tvFeedbackText.getMaxLines() == 3) {
                    holder.tvFeedbackText.setMaxLines(50);
                    holder.tvReadMore.setText("Show less");
                } else {
                    holder.tvFeedbackText.setMaxLines(3);
                    holder.tvReadMore.setText("Read more");
                }
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvStudentName, tvTimeAgo, tvCategoryBadge, tvFeedbackText, tvReadMore, tvStars;
            View vLeftBorder;
            ViewHolder(View v) {
                super(v);
                tvStudentName = v.findViewById(R.id.tvStudentName);
                tvTimeAgo = v.findViewById(R.id.tvTimeAgo);
                tvCategoryBadge = v.findViewById(R.id.tvCategoryBadge);
                tvFeedbackText = v.findViewById(R.id.tvFeedbackText);
                tvReadMore = v.findViewById(R.id.tvReadMore);
                tvStars = v.findViewById(R.id.tvStars);
                vLeftBorder = v.findViewById(R.id.vLeftBorder);
            }
        }
    }
}
