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
import com.example.smartmess.models.MealTimelineEntry;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MealTimelineAdapter
 *
 * Drives the "History" tab RecyclerView in MealHistoryActivity.
 * Displays a unified chronological meal timeline with status badges.
 *
 * Status   → Badge label      → Badge colour   → Bar colour
 * ─────────────────────────────────────────────────────────
 * CONFIRMED_ATTENDED → ✅ Attended       → Green   → #22C55E
 * CONFIRMED_MISSED   → ❌ Missed         → Red     → #EF4444
 * WALKIN             → 🚶 Walk-in        → Orange  → #F97316
 * SKIPPED            → ⏭ Skipped        → Slate   → #64748B
 * UPCOMING           → 📝 Confirmed      → Blue    → #3B82F6
 */
public class MealTimelineAdapter
        extends RecyclerView.Adapter<MealTimelineAdapter.ViewHolder> {

    private List<MealTimelineEntry> items = new ArrayList<>();

    private static final SimpleDateFormat PARSE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat DISPLAY_FMT =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    // -----------------------------------------------------------------------
    // RecyclerView.Adapter
    // -----------------------------------------------------------------------

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timeline_entry, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        MealTimelineEntry entry = items.get(position);

        // Date
        try {
            Date d = PARSE_FMT.parse(entry.getDate());
            h.tvDate.setText(d != null ? DISPLAY_FMT.format(d) : entry.getDate());
        } catch (ParseException e) {
            h.tvDate.setText(entry.getDate());
        }

        // Meal type + icon
        h.tvMealType.setText(capitalize(entry.getMealType()));
        h.tvMealIcon.setText(mealIcon(entry.getMealType()));

        // Sub-line (scan time or time slot)
        applySubLine(h, entry);

        // Badge + colour bar
        applyStatus(h, entry.getStatus());
    }

    @Override
    public int getItemCount() { return items.size(); }

    // -----------------------------------------------------------------------
    // DiffUtil update
    // -----------------------------------------------------------------------

    /** Replace dataset with DiffUtil — only changed rows are rebound. */
    public void submitList(List<MealTimelineEntry> newItems) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffCallback(items, newItems));
        items = new ArrayList<>(newItems);
        diff.dispatchUpdatesTo(this);
    }

    // -----------------------------------------------------------------------
    // Bind helpers
    // -----------------------------------------------------------------------

    private void applySubLine(ViewHolder h, MealTimelineEntry entry) {
        int status = entry.getStatus();

        if (status == MealTimelineEntry.STATUS_CONFIRMED_ATTENDED
                || status == MealTimelineEntry.STATUS_WALKIN) {
            // Show scan time if available
            if (entry.getScanTime() > 0) {
                h.tvSubLine.setText("🕐 Scanned at " + TIME_FMT.format(new Date(entry.getScanTime())));
                h.tvSubLine.setVisibility(View.VISIBLE);
                return;
            }
        }

        if (status == MealTimelineEntry.STATUS_UPCOMING
                || status == MealTimelineEntry.STATUS_CONFIRMED_MISSED) {
            // Show time slot if available
            if (entry.getTimeSlot() != null && !entry.getTimeSlot().isEmpty()
                    && !"Select Time".equals(entry.getTimeSlot())) {
                h.tvSubLine.setText("🕐 " + entry.getTimeSlot());
                h.tvSubLine.setVisibility(View.VISIBLE);
                return;
            }
        }

        h.tvSubLine.setVisibility(View.GONE);
    }

    private void applyStatus(ViewHolder h, int status) {
        switch (status) {
            case MealTimelineEntry.STATUS_CONFIRMED_ATTENDED:
                h.tvBadge.setText("✅ Attended");
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_green);
                h.viewBar.setBackgroundColor(Color.parseColor("#22C55E"));
                break;

            case MealTimelineEntry.STATUS_CONFIRMED_MISSED:
                h.tvBadge.setText("❌ Missed");
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_red);
                h.viewBar.setBackgroundColor(Color.parseColor("#EF4444"));
                break;

            case MealTimelineEntry.STATUS_WALKIN:
                h.tvBadge.setText("🚶 Walk-in");
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_orange);
                h.viewBar.setBackgroundColor(Color.parseColor("#F97316"));
                break;

            case MealTimelineEntry.STATUS_SKIPPED:
                h.tvBadge.setText("⏭ Skipped");
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_purple);
                h.viewBar.setBackgroundColor(Color.parseColor("#64748B"));
                break;

            case MealTimelineEntry.STATUS_UPCOMING:
            default:
                h.tvBadge.setText("📝 Confirmed");
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_blue);
                h.viewBar.setBackgroundColor(Color.parseColor("#3B82F6"));
                break;
        }
    }

    // -----------------------------------------------------------------------
    // ViewHolder
    // -----------------------------------------------------------------------

    static class ViewHolder extends RecyclerView.ViewHolder {
        View     viewBar;
        TextView tvMealIcon, tvDate, tvMealType, tvSubLine, tvBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewBar    = itemView.findViewById(R.id.viewTimelineBar);
            tvMealIcon = itemView.findViewById(R.id.tvTimelineMealIcon);
            tvDate     = itemView.findViewById(R.id.tvTimelineDate);
            tvMealType = itemView.findViewById(R.id.tvTimelineMealType);
            tvSubLine  = itemView.findViewById(R.id.tvTimelineSubLine);
            tvBadge    = itemView.findViewById(R.id.tvTimelineStatusBadge);
        }
    }

    // -----------------------------------------------------------------------
    // DiffUtil callback
    // -----------------------------------------------------------------------

    private static class DiffCallback extends DiffUtil.Callback {
        private final List<MealTimelineEntry> oldList;
        private final List<MealTimelineEntry> newList;

        DiffCallback(List<MealTimelineEntry> o, List<MealTimelineEntry> n) {
            this.oldList = o;
            this.newList = n;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int op, int np) {
            MealTimelineEntry o = oldList.get(op);
            MealTimelineEntry n = newList.get(np);
            return safeEquals(o.getDate(), n.getDate())
                    && safeEquals(o.getMealType(), n.getMealType());
        }

        @Override
        public boolean areContentsTheSame(int op, int np) {
            MealTimelineEntry o = oldList.get(op);
            MealTimelineEntry n = newList.get(np);
            return o.getStatus() == n.getStatus()
                    && o.getScanTime() == n.getScanTime();
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
