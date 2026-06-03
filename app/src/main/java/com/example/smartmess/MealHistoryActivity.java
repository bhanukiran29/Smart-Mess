package com.example.smartmess;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartmess.adapters.MealAttendanceAdapter;
import com.example.smartmess.adapters.MealConfirmationAdapter;
import com.example.smartmess.adapters.MealTimelineAdapter;
import com.example.smartmess.models.AttendanceRecord;
import com.example.smartmess.models.ConfirmationRecord;
import com.example.smartmess.models.MealTimelineEntry;
import com.example.smartmess.repository.MealHistoryRepository;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * MealHistoryActivity — complete meal activity dashboard.
 *
 * Tab 0 — Upcoming     Future confirmations (eating / skipping)
 * Tab 1 — History      Unified chronological timeline:
 *                         ✅ Confirmed & Attended
 *                         ❌ Confirmed but Missed
 *                         🚶 Walk-in
 *                         ⏭ Skipped
 *                         📝 Confirmed (Future)
 * Tab 2 — Attendance   QR scan records only (with scan time + Pre-confirmed / Walk-in)
 *
 * Header shows 5 stats: Confirmed · Attended · Skipped · Walk-ins · Missed
 * History tab has filter chips: Last 7 Days | Last 30 Days | All Time
 *
 * Data sources:
 *   user_confirmations/{uid}/records — confirmations
 *   collectionGroup("scanLogs")      — QR attendance
 */
public class MealHistoryActivity extends AppCompatActivity {

    private static final String TAG = "MealHistoryActivity";

    // ---- Filter constants ----
    private static final int FILTER_7   = 7;
    private static final int FILTER_30  = 30;
    private static final int FILTER_ALL = 0;

    // ---- Views ----
    private TabLayout    tabLayout;

    // Tab containers
    private LinearLayout layoutUpcoming, layoutHistory, layoutAttendance;

    // RecyclerViews
    private RecyclerView rvUpcoming, rvHistory, rvAttendance;

    // Empty states
    private LinearLayout llEmptyUpcoming, llEmptyHistory, llEmptyAttendance;

    // Stat cards
    private TextView tvStatConfirmed, tvStatAttended, tvStatSkipped,
                     tvStatWalkins, tvStatMissed;

    // Filter chips (History tab)
    private TextView chipFilter7, chipFilter30, chipFilterAll;

    // Retry buttons
    private Button btnRetryHistory, btnRetryAttendance;

    private ProgressBar progressBar;
    private ImageView   ivBack;

    // ---- Adapters ----
    private MealConfirmationAdapter confirmationAdapter;
    private MealTimelineAdapter     timelineAdapter;
    private MealAttendanceAdapter   attendanceAdapter;

    // ---- Data lists ----
    private final List<ConfirmationRecord> confirmationList = new ArrayList<>();
    private final List<MealTimelineEntry>  historyList      = new ArrayList<>();
    private final List<AttendanceRecord>   attendanceList   = new ArrayList<>();

    // ---- Firebase ----
    private FirebaseAuth          mAuth;
    private MealHistoryRepository repository;

    // ---- State ----
    private boolean historyLoaded    = false;
    private boolean attendanceLoaded = false;
    private int     activeFilter     = FILTER_ALL;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_history);

        mAuth      = FirebaseAuth.getInstance();
        repository = new MealHistoryRepository(FirebaseFirestore.getInstance());

        if (mAuth.getCurrentUser() == null) { finish(); return; }

        bindViews();
        setupRecyclerViews();
        setupTabs();
        setupFilterChips();

        ivBack.setOnClickListener(v -> finish());

        // Tab 0 is the default — load upcoming confirmations immediately
        loadUpcomingConfirmations();
    }

    // -----------------------------------------------------------------------
    // View binding
    // -----------------------------------------------------------------------

    private void bindViews() {
        tabLayout          = findViewById(R.id.tabLayout);
        ivBack             = findViewById(R.id.ivBack);
        progressBar        = findViewById(R.id.progressBar);

        layoutUpcoming     = findViewById(R.id.layoutUpcoming);
        layoutHistory      = findViewById(R.id.layoutHistory);
        layoutAttendance   = findViewById(R.id.layoutAttendance);

        rvUpcoming         = findViewById(R.id.rvUpcoming);
        rvHistory          = findViewById(R.id.rvHistory);
        rvAttendance       = findViewById(R.id.rvAttendance);

        llEmptyUpcoming    = findViewById(R.id.llEmptyUpcoming);
        llEmptyHistory     = findViewById(R.id.llEmptyHistory);
        llEmptyAttendance  = findViewById(R.id.llEmptyAttendance);

        tvStatConfirmed    = findViewById(R.id.tvStatConfirmed);
        tvStatAttended     = findViewById(R.id.tvStatAttended);
        tvStatSkipped      = findViewById(R.id.tvStatSkipped);
        tvStatWalkins      = findViewById(R.id.tvStatWalkins);
        tvStatMissed       = findViewById(R.id.tvStatMissed);

        chipFilter7        = findViewById(R.id.chipFilter7);
        chipFilter30       = findViewById(R.id.chipFilter30);
        chipFilterAll      = findViewById(R.id.chipFilterAll);

        btnRetryHistory    = findViewById(R.id.btnRetryHistory);
        btnRetryAttendance = findViewById(R.id.btnRetryAttendance);

        if (btnRetryHistory != null) {
            btnRetryHistory.setVisibility(View.GONE);
            btnRetryHistory.setOnClickListener(v -> {
                btnRetryHistory.setVisibility(View.GONE);
                historyLoaded = false;
                loadMealHistory(activeFilter);
            });
        }
        if (btnRetryAttendance != null) {
            btnRetryAttendance.setVisibility(View.GONE);
            btnRetryAttendance.setOnClickListener(v -> {
                btnRetryAttendance.setVisibility(View.GONE);
                attendanceLoaded = false;
                loadAttendanceHistory();
            });
        }
    }

    // -----------------------------------------------------------------------
    // RecyclerViews
    // -----------------------------------------------------------------------

    private void setupRecyclerViews() {
        // Tab 0 — Upcoming
        confirmationAdapter = new MealConfirmationAdapter(this, confirmationList);
        rvUpcoming.setLayoutManager(new LinearLayoutManager(this));
        rvUpcoming.setAdapter(confirmationAdapter);
        rvUpcoming.setHasFixedSize(true);
        rvUpcoming.setNestedScrollingEnabled(false);

        // Tab 1 — History timeline
        timelineAdapter = new MealTimelineAdapter();
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(timelineAdapter);
        rvHistory.setHasFixedSize(true);
        rvHistory.setNestedScrollingEnabled(false);

        // Tab 2 — Attendance
        attendanceAdapter = new MealAttendanceAdapter(attendanceList);
        rvAttendance.setLayoutManager(new LinearLayoutManager(this));
        rvAttendance.setAdapter(attendanceAdapter);
        rvAttendance.setHasFixedSize(true);
        rvAttendance.setNestedScrollingEnabled(false);
    }

    // -----------------------------------------------------------------------
    // Tab switching
    // -----------------------------------------------------------------------

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: showTab(0); break;
                    case 1: showTab(1); break;
                    case 2: showTab(2); break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void showTab(int index) {
        layoutUpcoming.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        layoutHistory.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        layoutAttendance.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        if (index == 1 && !historyLoaded) {
            loadMealHistory(activeFilter);
        }
        if (index == 2 && !attendanceLoaded) {
            loadAttendanceHistory();
        }
    }

    // -----------------------------------------------------------------------
    // Filter chips (History tab)
    // -----------------------------------------------------------------------

    private void setupFilterChips() {
        // "All Time" starts selected
        setChipSelected(chipFilterAll, true);
        setChipSelected(chipFilter7,   false);
        setChipSelected(chipFilter30,  false);

        chipFilter7.setOnClickListener(v -> {
            if (activeFilter == FILTER_7) return;
            activeFilter = FILTER_7;
            setChipSelected(chipFilter7, true);
            setChipSelected(chipFilter30, false);
            setChipSelected(chipFilterAll, false);
            historyLoaded = false;
            loadMealHistory(activeFilter);
        });

        chipFilter30.setOnClickListener(v -> {
            if (activeFilter == FILTER_30) return;
            activeFilter = FILTER_30;
            setChipSelected(chipFilter7, false);
            setChipSelected(chipFilter30, true);
            setChipSelected(chipFilterAll, false);
            historyLoaded = false;
            loadMealHistory(activeFilter);
        });

        chipFilterAll.setOnClickListener(v -> {
            if (activeFilter == FILTER_ALL) return;
            activeFilter = FILTER_ALL;
            setChipSelected(chipFilter7, false);
            setChipSelected(chipFilter30, false);
            setChipSelected(chipFilterAll, true);
            historyLoaded = false;
            loadMealHistory(activeFilter);
        });
    }

    private void setChipSelected(TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setTextColor(selected
                ? Color.WHITE
                : Color.parseColor("#64748B"));
    }

    // -----------------------------------------------------------------------
    // Load: Upcoming Confirmations (Tab 0)
    // -----------------------------------------------------------------------

    private void loadUpcomingConfirmations() {
        progressBar.setVisibility(View.VISIBLE);
        rvUpcoming.setVisibility(View.GONE);
        llEmptyUpcoming.setVisibility(View.GONE);

        String uid = mAuth.getCurrentUser().getUid();

        repository.loadUpcomingConfirmations(uid, new MealHistoryRepository.ConfirmationsCallback() {
            @Override
            public void onSuccess(List<ConfirmationRecord> records) {
                progressBar.setVisibility(View.GONE);

                confirmationList.clear();
                confirmationList.addAll(records);
                confirmationAdapter.notifyDataSetChanged();

                // Update "Confirmed" stat (upcoming count)
                tvStatConfirmed.setText(String.valueOf(records.size()));

                if (records.isEmpty()) {
                    rvUpcoming.setVisibility(View.GONE);
                    llEmptyUpcoming.setVisibility(View.VISIBLE);
                } else {
                    llEmptyUpcoming.setVisibility(View.GONE);
                    rvUpcoming.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Exception e) {
                progressBar.setVisibility(View.GONE);
                llEmptyUpcoming.setVisibility(View.VISIBLE);
                rvUpcoming.setVisibility(View.GONE);
                String reason = e != null ? e.getMessage() : "Unknown error";
                Log.e(TAG, "loadUpcomingConfirmations FAILED: " + reason, e);
                Toast.makeText(MealHistoryActivity.this,
                        "Could not load confirmations.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Load: Unified Meal History Timeline (Tab 1)
    // -----------------------------------------------------------------------

    private void loadMealHistory(int filterDays) {
        boolean hasExistingData = !historyList.isEmpty();
        if (!hasExistingData) {
            progressBar.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
            llEmptyHistory.setVisibility(View.GONE);
        }
        if (btnRetryHistory != null) btnRetryHistory.setVisibility(View.GONE);

        String uid = mAuth.getCurrentUser().getUid();

        repository.loadMealTimeline(uid, filterDays,
                new MealHistoryRepository.TimelineCallback() {
                    @Override
                    public void onSuccess(List<MealTimelineEntry> entries) {
                        historyLoaded = true;
                        progressBar.setVisibility(View.GONE);

                        historyList.clear();
                        historyList.addAll(entries);
                        timelineAdapter.submitList(entries);

                        // Compute stats from timeline
                        computeAndUpdateStats(entries);

                        if (entries.isEmpty()) {
                            rvHistory.setVisibility(View.GONE);
                            llEmptyHistory.setVisibility(View.VISIBLE);
                        } else {
                            llEmptyHistory.setVisibility(View.GONE);
                            rvHistory.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        historyLoaded = true;
                        progressBar.setVisibility(View.GONE);
                        String reason = e != null ? e.getMessage() : "Unknown error";
                        Log.e(TAG, "loadMealHistory FAILED: " + reason, e);

                        if (historyList.isEmpty()) {
                            rvHistory.setVisibility(View.GONE);
                            llEmptyHistory.setVisibility(View.VISIBLE);
                        }
                        if (btnRetryHistory != null) {
                            btnRetryHistory.setVisibility(View.VISIBLE);
                        }
                        Toast.makeText(MealHistoryActivity.this,
                                "Unable to load meal history. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // -----------------------------------------------------------------------
    // Load: QR Attendance History (Tab 2)
    // -----------------------------------------------------------------------

    private void loadAttendanceHistory() {
        boolean hasExistingData = !attendanceList.isEmpty();
        if (!hasExistingData) {
            progressBar.setVisibility(View.VISIBLE);
            rvAttendance.setVisibility(View.GONE);
            llEmptyAttendance.setVisibility(View.GONE);
        }
        if (btnRetryAttendance != null) btnRetryAttendance.setVisibility(View.GONE);

        String uid = mAuth.getCurrentUser().getUid();

        repository.loadAttendanceHistory(uid, new MealHistoryRepository.AttendanceCallback() {
            @Override
            public void onSuccess(List<AttendanceRecord> records) {
                attendanceLoaded = true;
                progressBar.setVisibility(View.GONE);

                attendanceList.clear();
                attendanceList.addAll(records);
                attendanceAdapter.submitList(records);

                // Update walk-in stat from attendance data
                int walkins = 0;
                for (AttendanceRecord r : records) {
                    if (r.isWalkin()) walkins++;
                }
                tvStatWalkins.setText(String.valueOf(walkins));

                if (records.isEmpty()) {
                    rvAttendance.setVisibility(View.GONE);
                    llEmptyAttendance.setVisibility(View.VISIBLE);
                } else {
                    llEmptyAttendance.setVisibility(View.GONE);
                    rvAttendance.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Exception e) {
                attendanceLoaded = true;
                progressBar.setVisibility(View.GONE);
                String reason = e != null ? e.getMessage() : "Unknown error";
                Log.e(TAG, "loadAttendanceHistory FAILED: " + reason, e);

                if (attendanceList.isEmpty()) {
                    rvAttendance.setVisibility(View.GONE);
                    llEmptyAttendance.setVisibility(View.VISIBLE);
                }
                if (btnRetryAttendance != null) {
                    btnRetryAttendance.setVisibility(View.VISIBLE);
                }
                Toast.makeText(MealHistoryActivity.this,
                        "Unable to load attendance records. Please try again.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Stats computation
    // -----------------------------------------------------------------------

    /**
     * Compute stats from the unified timeline and update all 5 header cards.
     * Called whenever History tab data is freshly loaded.
     */
    private void computeAndUpdateStats(List<MealTimelineEntry> entries) {
        int confirmed = 0, attended = 0, skipped = 0, walkins = 0, missed = 0;

        for (MealTimelineEntry e : entries) {
            switch (e.getStatus()) {
                case MealTimelineEntry.STATUS_CONFIRMED_ATTENDED:
                    confirmed++;
                    attended++;
                    break;
                case MealTimelineEntry.STATUS_CONFIRMED_MISSED:
                    confirmed++;
                    missed++;
                    break;
                case MealTimelineEntry.STATUS_UPCOMING:
                    confirmed++;
                    break;
                case MealTimelineEntry.STATUS_SKIPPED:
                    skipped++;
                    break;
                case MealTimelineEntry.STATUS_WALKIN:
                    walkins++;
                    attended++;
                    break;
            }
        }

        tvStatConfirmed.setText(String.valueOf(confirmed));
        tvStatAttended.setText(String.valueOf(attended));
        tvStatSkipped.setText(String.valueOf(skipped));
        tvStatWalkins.setText(String.valueOf(walkins));
        tvStatMissed.setText(String.valueOf(missed));
    }

    // -----------------------------------------------------------------------
    // Real-time update — called from MainActivity after a confirmation is saved
    // -----------------------------------------------------------------------

    public void onNewConfirmationSaved(ConfirmationRecord record) {
        confirmationAdapter.addToTop(record);
        tvStatConfirmed.setText(String.valueOf(confirmationList.size()));
        llEmptyUpcoming.setVisibility(View.GONE);
        rvUpcoming.setVisibility(View.VISIBLE);
        // Invalidate history so it reloads with the new record next time
        historyLoaded = false;
        Log.d(TAG, "Real-time update: added " + record.getMealType() + " on " + record.getDate());
    }
}
