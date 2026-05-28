package com.example.smartmess.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartmess.R;
import com.example.smartmess.models.AttendanceRecord;

import java.util.List;

/**
 * MealAttendanceAdapter
 * Drives the "Attendance History" RecyclerView in MealHistoryActivity.
 */
public class MealAttendanceAdapter
        extends RecyclerView.Adapter<MealAttendanceAdapter.ViewHolder> {

    private final List<AttendanceRecord> items;

    public MealAttendanceAdapter(List<AttendanceRecord> items) {
        this.items = items;
    }

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

        if (rec.isWalkin()) {
            // Orange — walk-in (paid ₹50)
            h.tvBadge.setText("Walk-in ₹50");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_orange);
            h.viewBar.setBackgroundColor(Color.parseColor("#F97316"));
        } else {
            // Blue — pre-confirmed attendance
            h.tvBadge.setText("Attended");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_blue);
            h.viewBar.setBackgroundColor(Color.parseColor("#3B82F6"));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ---- ViewHolder ----

    static class ViewHolder extends RecyclerView.ViewHolder {
        View     viewBar;
        TextView tvMealIcon, tvDayLabel, tvMealType, tvBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewBar    = itemView.findViewById(R.id.viewAttendanceBar);
            tvMealIcon = itemView.findViewById(R.id.tvAttMealIcon);
            tvDayLabel = itemView.findViewById(R.id.tvAttDayLabel);
            tvMealType = itemView.findViewById(R.id.tvAttMealType);
            tvBadge    = itemView.findViewById(R.id.tvAttBadge);
        }
    }

    // ---- Helpers ----

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
