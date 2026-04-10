package com.example.smartmess;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MealHistoryActivity extends AppCompatActivity {

    private RecyclerView rvMealHistory;
    private LinearLayout llEmptyState;
    private ChipGroup chipGroupFilters;
    private TextView tvCountEaten, tvCountSkipped, tvCountWalkins;
    private ImageView ivBack;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private HistoryAdapter adapter;

    enum Filter { WEEK, MONTH, ALL_TIME }
    private Filter currentFilter = Filter.MONTH;

    class MealItem {
        Date dateObj;
        String dateHeader;
        String mealType;
        boolean showHeader;
        
        boolean attended;
        boolean isWalkin;
        boolean isSkipped; // Either explicitly "skip" or missed
    }

    private List<MealItem> masterList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_history);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        loadHistoryData(30); // Default to This Month = 30 days
    }

    private void initViews() {
        rvMealHistory = findViewById(R.id.rvMealHistory);
        llEmptyState = findViewById(R.id.llEmptyState);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
        tvCountEaten = findViewById(R.id.tvCountEaten);
        tvCountSkipped = findViewById(R.id.tvCountSkipped);
        tvCountWalkins = findViewById(R.id.tvCountWalkins);
        ivBack = findViewById(R.id.ivBack);

        ivBack.setOnClickListener(v -> finish());

        rvMealHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(new ArrayList<>());
        rvMealHistory.setAdapter(adapter);

        chipGroupFilters.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipThisWeek && currentFilter != Filter.WEEK) {
                currentFilter = Filter.WEEK;
                loadHistoryData(7);
            } else if (checkedId == R.id.chipThisMonth && currentFilter != Filter.MONTH) {
                currentFilter = Filter.MONTH;
                loadHistoryData(30);
            } else if (checkedId == R.id.chipAllTime && currentFilter != Filter.ALL_TIME) {
                currentFilter = Filter.ALL_TIME;
                loadHistoryData(60); // 60 days arbitrarily to prevent massive reads
            }
        });
    }

    private void loadHistoryData(int days) {
        masterList.clear();
        adapter.updateList(masterList);
        llEmptyState.setVisibility(View.GONE);
        
        tvCountEaten.setText("0");
        tvCountSkipped.setText("0");
        tvCountWalkins.setText("0");

        String userId = mAuth.getCurrentUser().getUid();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat headerFmt = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String[] meals = {"breakfast", "lunch", "dinner"};

        final int totalQueries = days * meals.length;
        final int[] done = {0};

        for (int i = 0; i < days; i++) {
            final int dayIndex = i;
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -dayIndex);
            Date dateObj = cal.getTime();
            String dateStr = sdf.format(dateObj);
            String headerStr = headerFmt.format(dateObj);
            
            if (dayIndex == 0) headerStr = "TODAY";
            else if (dayIndex == 1) headerStr = "YESTERDAY";

            for (String meal : meals) {
                final String finalHeaderStr = headerStr;
                // Check scanLogs for actual attendance!
                db.collection("scanLogs").document(dateStr).collection(meal)
                        .document(userId).get()
                        .addOnCompleteListener(task -> {
                            MealItem item = new MealItem();
                            item.dateObj = dateObj;
                            item.dateHeader = finalHeaderStr;
                            item.mealType = meal;
                            
                            if (task.isSuccessful() && task.getResult().exists()) {
                                DocumentSnapshot doc = task.getResult();
                                item.attended = true;
                                item.isWalkin = Boolean.TRUE.equals(doc.getBoolean("isWalkin"));
                                masterList.add(item);
                            } else {
                                // If not attended, did they explicitly skip?
                                db.collection("confirmations").document(dateStr).collection(meal)
                                        .document(userId).get().addOnCompleteListener(confTask -> {
                                            if (confTask.isSuccessful() && confTask.getResult().exists()) {
                                                String status = confTask.getResult().getString("status");
                                                if ("skip".equals(status)) {
                                                    item.isSkipped = true;
                                                    masterList.add(item);
                                                } else if ("eat".equals(status) && dayIndex > 0) {
                                                    // They said eat, but didn't attend and the day is over
                                                    item.isSkipped = true;
                                                    masterList.add(item);
                                                }
                                            }
                                            checkQueries(++done[0], totalQueries);
                                        });
                                return;
                            }
                            checkQueries(++done[0], totalQueries);
                        });
            }
        }
    }

    private void checkQueries(int finished, int total) {
        if (finished >= total) {
            runOnUiThread(() -> {
                processAndShowData();
            });
        }
    }

    private void processAndShowData() {
        // Sort descending by date, then meal order (breakfast -> lunch -> dinner)
        Collections.sort(masterList, (a, b) -> {
            int dateCmp = b.dateObj.compareTo(a.dateObj);
            if (dateCmp != 0) return dateCmp;
            int aVal = a.mealType.equals("breakfast") ? 1 : a.mealType.equals("lunch") ? 2 : 3;
            int bVal = b.mealType.equals("breakfast") ? 1 : b.mealType.equals("lunch") ? 2 : 3;
            return aVal - bVal;
        });

        int eaten = 0, skipped = 0, walkins = 0;
        String lastHeader = "";
        
        for (MealItem item : masterList) {
            if (item.attended) eaten++;
            if (item.isWalkin) walkins++;
            if (item.isSkipped) skipped++;
            
            if (!item.dateHeader.equals(lastHeader)) {
                item.showHeader = true;
                lastHeader = item.dateHeader;
            } else {
                item.showHeader = false;
            }
        }

        // Animated metric counting
        animateMetric(tvCountEaten, eaten);
        animateMetric(tvCountSkipped, skipped);
        animateMetric(tvCountWalkins, walkins);

        if (masterList.isEmpty()) {
            llEmptyState.setVisibility(View.VISIBLE);
            rvMealHistory.setVisibility(View.GONE);
        } else {
            llEmptyState.setVisibility(View.GONE);
            rvMealHistory.setVisibility(View.VISIBLE);
            
            // Crossfade RecyclerView wrapper
            rvMealHistory.setAlpha(0f);
            adapter.updateList(masterList);
            rvMealHistory.animate().alpha(1f).setDuration(400).start();
        }
    }
    
    private void animateMetric(TextView tv, int target) {
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(0, target);
        anim.setDuration(800);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> tv.setText(a.getAnimatedValue().toString()));
        anim.start();
    }

    // --- RECYCLER VIEW ADAPTER ---
    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<MealItem> items;
        private int lastPos = -1;

        public HistoryAdapter(List<MealItem> items) {
            this.items = items;
        }

        public void updateList(List<MealItem> newList) {
            this.items = new ArrayList<>(newList);
            lastPos = -1;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_meal_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MealItem item = items.get(position);

            if (item.showHeader) {
                holder.tvDateHeader.setVisibility(View.VISIBLE);
                holder.tvDateHeader.setText(item.dateHeader);
            } else {
                holder.tvDateHeader.setVisibility(View.GONE);
            }

            String mealCap = item.mealType.substring(0, 1).toUpperCase() + item.mealType.substring(1);
            holder.tvMealName.setText(mealCap);

            // Icon handling
            if (item.mealType.equals("breakfast")) holder.tvMealIconText.setText("🥪");
            else if (item.mealType.equals("lunch")) holder.tvMealIconText.setText("🍛");
            else holder.tvMealIconText.setText("🍲");

            if (item.isSkipped) {
                holder.viewColorStrip.setBackgroundColor(Color.parseColor("#EF4444")); // Red
                holder.cvMealIcon.setCardBackgroundColor(Color.parseColor("#FEE2E2"));
                holder.tvBadge.setText("Skipped");
                holder.tvBadge.setBackgroundResource(R.drawable.bg_history_badge); 
                holder.tvBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#94A3B8")));
                holder.tvMealTime.setText("Missed / Opted Out");
            } else if (item.isWalkin) {
                holder.viewColorStrip.setBackgroundColor(Color.parseColor("#F59E0B")); // Amber
                holder.cvMealIcon.setCardBackgroundColor(Color.parseColor("#FEF3C7"));
                holder.tvBadge.setText("₹50 charged");
                holder.tvBadge.setBackgroundResource(R.drawable.bg_history_badge);
                holder.tvBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F59E0B")));
                holder.tvMealTime.setText("Walk-in Entry");
            } else {
                holder.viewColorStrip.setBackgroundColor(Color.parseColor("#16A34A")); // Green
                holder.cvMealIcon.setCardBackgroundColor(Color.parseColor("#DCFCE7"));
                holder.tvBadge.setText("Free");
                holder.tvBadge.setBackgroundResource(R.drawable.bg_history_badge);
                holder.tvBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#16A34A")));
                holder.tvMealTime.setText("Pre-confirmed Entry");
            }

            // Stagger slide animation
            if (position > lastPos) {
                holder.itemView.setTranslationY(80f);
                holder.itemView.setAlpha(0f);
                holder.itemView.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(400)
                        .setStartDelay(Math.min(position * 40L, 400L))
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                lastPos = position;
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDateHeader, tvMealName, tvMealTime, tvBadge, tvMealIconText;
            View viewColorStrip;
            MaterialCardView cvMealIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
                tvMealName = itemView.findViewById(R.id.tvMealName);
                tvMealTime = itemView.findViewById(R.id.tvMealTime);
                tvBadge = itemView.findViewById(R.id.tvBadge);
                tvMealIconText = itemView.findViewById(R.id.tvMealIconText);
                viewColorStrip = itemView.findViewById(R.id.viewColorStrip);
                cvMealIcon = itemView.findViewById(R.id.cvMealIcon);
            }
        }
    }
}
