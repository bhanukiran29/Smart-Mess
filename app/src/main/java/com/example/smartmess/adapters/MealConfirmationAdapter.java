package com.example.smartmess.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartmess.R;
import com.example.smartmess.models.ConfirmationRecord;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MealConfirmationAdapter
 * Drives the "Upcoming Confirmations" RecyclerView in MealHistoryActivity.
 */
public class MealConfirmationAdapter
        extends RecyclerView.Adapter<MealConfirmationAdapter.ViewHolder> {

    private final List<ConfirmationRecord> items;
    private final Context context;

    private static final SimpleDateFormat PARSE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat DISPLAY_FMT =
            new SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    public MealConfirmationAdapter(Context context, List<ConfirmationRecord> items) {
        this.context = context;
        this.items   = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_confirmation, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ConfirmationRecord rec = items.get(position);

        // --- Date label ---
        try {
            Date d = PARSE_FMT.parse(rec.getDate());
            h.tvDate.setText(d != null ? DISPLAY_FMT.format(d) : rec.getDate());
        } catch (ParseException e) {
            h.tvDate.setText(rec.getDate());
        }

        // --- Meal type + icon ---
        h.tvMealType.setText(capitalize(rec.getMealType()));
        h.tvMealIcon.setText(mealIcon(rec.getMealType()));

        // --- Time slot ---
        boolean isEating = "eat".equals(rec.getStatus());
        if (isEating && rec.getTimeSlot() != null && !rec.getTimeSlot().isEmpty()
                && !"Select Time".equals(rec.getTimeSlot())) {
            h.tvTimeSlot.setText("🕐 " + rec.getTimeSlot());
            h.tvTimeSlot.setVisibility(View.VISIBLE);
        } else {
            h.tvTimeSlot.setVisibility(View.GONE);
        }

        // --- Confirmed-at timestamp ---
        if (rec.getTimestamp() > 0) {
            h.tvConfirmedAt.setText("Confirmed at " + TIME_FMT.format(new Date(rec.getTimestamp())));
            h.tvConfirmedAt.setVisibility(View.VISIBLE);
        } else {
            h.tvConfirmedAt.setVisibility(View.GONE);
        }

        // --- Status badge + left bar colour ---
        if (isEating) {
            h.tvStatusBadge.setText("Eating");
            h.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green);
            h.viewStatusBar.setBackgroundColor(Color.parseColor("#22C55E"));
        } else {
            h.tvStatusBadge.setText("Skipping");
            h.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_red);
            h.viewStatusBar.setBackgroundColor(Color.parseColor("#EF4444"));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    /** Prepend a newly saved record and scroll to top */
    public void addToTop(ConfirmationRecord record) {
        // Replace if same date+meal already exists
        for (int i = 0; i < items.size(); i++) {
            ConfirmationRecord existing = items.get(i);
            if (existing.getDate().equals(record.getDate())
                    && existing.getMealType().equals(record.getMealType())) {
                items.set(i, record);
                notifyItemChanged(i);
                return;
            }
        }
        // Insert at correct sorted position (nearest date first)
        int insertAt = 0;
        for (int i = 0; i < items.size(); i++) {
            if (record.getDate().compareTo(items.get(i).getDate()) <= 0) {
                insertAt = i;
                break;
            } else {
                insertAt = i + 1;
            }
        }
        items.add(insertAt, record);
        notifyItemInserted(insertAt);
    }

    // ---- ViewHolder ----

    static class ViewHolder extends RecyclerView.ViewHolder {
        View     viewStatusBar;
        TextView tvMealIcon, tvDate, tvMealType, tvTimeSlot, tvConfirmedAt, tvStatusBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewStatusBar  = itemView.findViewById(R.id.viewStatusBar);
            tvMealIcon     = itemView.findViewById(R.id.tvMealIcon);
            tvDate         = itemView.findViewById(R.id.tvConfirmDate);
            tvMealType     = itemView.findViewById(R.id.tvMealType);
            tvTimeSlot     = itemView.findViewById(R.id.tvTimeSlot);
            tvConfirmedAt  = itemView.findViewById(R.id.tvConfirmedAt);
            tvStatusBadge  = itemView.findViewById(R.id.tvStatusBadge);
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
