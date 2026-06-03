package com.example.smartmess.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartmess.R;
import com.example.smartmess.models.AttendanceRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MealAttendanceAdapter
 *
 * Drives the "Attendance History" RecyclerView in MealHistoryActivity.
 *
 * Uses DiffUtil so only changed rows are rebound — avoids full
 * notifyDataSetChanged() flicker on background refresh.
 */
public class MealAttendanceAdapter
        extends RecyclerView.Adapter<MealAttendanceAdapter.ViewHolder> {

    private List<AttendanceRecord> items;

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    public MealAttendanceAdapter(List<AttendanceRecord> items) {
        this.items = new ArrayList<>(items);
        setHasStableIds(true);
    }

    // -----------------------------------------------------------------------
    // RecyclerView.Adapter
    // -----------------------------------------------------------------------

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        AttendanceRecord rec = items.get(position);

        h.tvDayLabel.setText(rec.getDayLabel());
        h.tvMealType.setText(capitalize(rec.getMealType()));
        h.tvMealIcon.setText(mealIcon(rec.getMealType()));

        // Show scan time if available
        if (rec.getTimestamp() > 0) {
            h.tvScanTime.setText("🕐 Scanned at " + TIME_FMT.format(new Date(rec.getTimestamp())));
            h.tvScanTime.setVisibility(View.VISIBLE);
        } else {
            h.tvScanTime.setVisibility(View.GONE);
        }

        if (rec.isWalkin()) {
            h.tvBadge.setText("Walk-in");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_orange);
            h.viewBar.setBackgroundColor(Color.parseColor("#F97316"));
        } else {
            h.tvBadge.setText("Pre-confirmed");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_blue);
            h.viewBar.setBackgroundColor(Color.parseColor("#3B82F6"));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    /**
     * Stable IDs: use date+meal as a unique long hash so RecyclerView
     * can animate only the rows that actually changed.
     */
    @Override
    public long getItemId(int position) {
        AttendanceRecord r = items.get(position);
        String key = (r.getDate() != null ? r.getDate() : "")
                   + "_" + (r.getMealType() != null ? r.getMealType() : "");
        return key.hashCode();
    }

    // -----------------------------------------------------------------------
    // DiffUtil update — call this instead of notifyDataSetChanged()
    // -----------------------------------------------------------------------

    /**
     * Replace the dataset using DiffUtil so only changed rows are rebound.
     * Safe to call from the main thread; DiffUtil.calculateDiff() is O(N)
     * but for ≤50 items it completes in < 1ms.
     */
    public void submitList(List<AttendanceRecord> newItems) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffCallback(items, newItems));
        items = new ArrayList<>(newItems);
        diff.dispatchUpdatesTo(this);
    }

    // -----------------------------------------------------------------------
    // ViewHolder
    // -----------------------------------------------------------------------

    static class ViewHolder extends RecyclerView.ViewHolder {
        View     viewBar;
        TextView tvMealIcon, tvDayLabel, tvMealType, tvScanTime, tvBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewBar    = itemView.findViewById(R.id.viewAttendanceBar);
            tvMealIcon = itemView.findViewById(R.id.tvAttMealIcon);
            tvDayLabel = itemView.findViewById(R.id.tvAttDayLabel);
            tvMealType = itemView.findViewById(R.id.tvAttMealType);
            tvScanTime = itemView.findViewById(R.id.tvAttScanTime);
            tvBadge    = itemView.findViewById(R.id.tvAttBadge);
        }
    }

    // -----------------------------------------------------------------------
    // DiffUtil callback
    // -----------------------------------------------------------------------

    private static class DiffCallback extends DiffUtil.Callback {
        private final List<AttendanceRecord> oldList;
        private final List<AttendanceRecord> newList;

        DiffCallback(List<AttendanceRecord> oldList, List<AttendanceRecord> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            AttendanceRecord o = oldList.get(oldPos);
            AttendanceRecord n = newList.get(newPos);
            // Same record = same date + same meal
            return safeEquals(o.getDate(), n.getDate())
                    && safeEquals(o.getMealType(), n.getMealType());
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            AttendanceRecord o = oldList.get(oldPos);
            AttendanceRecord n = newList.get(newPos);
            return o.isWalkin() == n.isWalkin()
                    && safeEquals(o.getDayLabel(), n.getDayLabel());
        }

        private boolean safeEquals(String a, String b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            return a.equals(b);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String mealIcon(String meal) {
        if (meal == null) return "🍽️";
        switch (meal.toLowerCase()) {
            case "breakfast": return "🍳";
            case "lunch":     return "🍱";
            case "snacks":    return "🥪";
            case "dinner":    return "🍛";
            default:          return "🍽️";
        }
    }
}
