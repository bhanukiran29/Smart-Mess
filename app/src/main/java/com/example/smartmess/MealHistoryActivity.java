package com.example.smartmess;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import com.example.smartmess.models.AttendanceRecord;
import com.example.smartmess.models.ConfirmationRecord;
import com.example.smartmess.repository.MealHistoryRepository;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * MealHistoryActivity
 *
 * Tab 0 — Upcoming Confirmations
 *   Source: user_confirmations/{uid}/records  (date >= today, sorted nearest-first)
 *
 * Tab 1 — Attendance History
 *   Source: scanLogs/{date}/{meal}/{uid}  (full history, sorted newest-first)
 *
 * The QR attendance system, scanLogs, and analytics are untouched.
 */
public class MealHistoryActivity extends AppCompatActivity {

    private static final String TAG = "MealHistoryActivity";

    // ---- Views ----
    private TabLayout     tabLayout;
    private LinearLayout  layoutUpcoming, layoutAttendance;
    private RecyclerView  rvUpcoming, rvAttendance;
    private LinearLayout  llEmptyUpcoming, llEmptyAttendance;
    private TextView      tvCountUpcoming, tvCountEaten, tvCountWalkins;
    private ProgressBar   progressBar;
    private ImageView     ivBack;

    // ---- Data ----
    private final List<ConfirmationRecord> confirmationList = new ArrayList<>();
    private final List<AttendanceRecord>   attendanceList   = new ArrayList<>();

    private MealConfirmationAdapter confirmationAdapter;
    private MealAttendanceAdapter   attendanceAdapter;

    // ---- Firebase ----
    private FirebaseAuth          mAuth;
    private MealHistoryRepository repository;

    // ---- State ----
    private boolean attendanceLoaded = false;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_history);

        mAuth      = FirebaseAuth.getInstance();
        repository = new MealHistoryRepository(FirebaseFirestore.getInstance());

        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        bindViews();
        setupRecyclerViews();
        setupTabs();

        ivBack.setOnClickListener(v -> finish());

        // Tab 0 is shown by default — load its data immediately
        loadUpcomingConfirmations();
    }

    // -----------------------------------------------------------------------
    // View binding
    // -----------------------------------------------------------------------

    private void bindViews() {
        tabLayout         = findViewById(R.id.tabLayout);
        layoutUpcoming    = findViewById(R.id.layoutUpcoming);
        layoutAttendance  = findViewById(R.id.layoutAttendance);
        rvUpcoming        = findViewById(R.id.rvUpcoming);
        rvAttendance      = findViewById(R.id.rvAttendance);
        llEmptyUpcoming   = findViewById(R.id.llEmptyUpcoming);
        llEmptyAttendance = findViewById(R.id.llEmptyAttendance);
        tvCountUpcoming   = findViewById(R.id.tvCountUpcoming);
        tvCountEaten      = findViewById(R.id.tvCountEaten);
        tvCountWalkins    = findViewById(R.id.tvCountWalkins);
        progressBar       = findViewById(R.id.progressBar);
        ivBack            = findViewById(R.id.ivBack);
    }

    // -----------------------------------------------------------------------
    // RecyclerViews
    // -----------------------------------------------------------------------

    private void setupRecyclerViews() {
        confirmationAdapter = new MealConfirmationAdapter(this, confirmationList);
        rvUpcoming.setLayoutManager(new LinearLayoutManager(this));
        rvUpcoming.setAdapter(confirmationAdapter);
        rvUpcoming.setNestedScrollingEnabled(false);

        attendanceAdapter = new MealAttendanceAdapter(attendanceList);
        rvAttendance.setLayoutManager(new LinearLayoutManager(this));
        rvAttendance.setAdapter(attendanceAdapter);
        rvAttendance.setNestedScrollingEnabled(false);
    }

    // -----------------------------------------------------------------------
    // Tab switching
    // -----------------------------------------------------------------------

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    showUpcomingTab();
                } else {
                    showAttendanceTab();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void showUpcomingTab() {
        layoutUpcoming.setVisibility(View.VISIBLE);
        layoutAttendance.setVisibility(View.GONE);
    }

    private void showAttendanceTab() {
        layoutUpcoming.setVisibility(View.GONE);
        layoutAttendance.setVisibility(View.VISIBLE);
        if (!attendanceLoaded) {
            loadAttendanceHistory();
        }
    }

    // -----------------------------------------------------------------------
    // Load: Upcoming Confirmations (Tab 0)
    // -----------------------------------------------------------------------

    private void loadUpcomingConfirmations() {
        progressBar.setVisibility(View.VISIBLE);
        // Hide both states while loading
        rvUpcoming.setVisibility(View.GONE);
        llEmptyUpcoming.setVisibility(View.GONE);

        String uid = mAuth.getCurrentUser().getUid();
        Log.d(TAG, "loadUpcomingConfirmations: uid=" + uid);

        repository.loadUpcomingConfirmations(uid, new MealHistoryRepository.ConfirmationsCallback() {
            @Override
            public void onSuccess(List<ConfirmationRecord> records) {
                progressBar.setVisibility(View.GONE);

                confirmationList.clear();
                confirmationList.addAll(records);
                confirmationAdapter.notifyDataSetChanged();

                tvCountUpcoming.setText(String.valueOf(records.size()));

                if (records.isEmpty()) {
                    rvUpcoming.setVisibility(View.GONE);
                    llEmptyUpcoming.setVisibility(View.VISIBLE);
                    Log.d(TAG, "No upcoming confirmations found.");
                } else {
                    llEmptyUpcoming.setVisibility(View.GONE);
                    rvUpcoming.setVisibility(View.VISIBLE);
                    Log.d(TAG, "Upcoming confirmations displayed: " + records.size());
                }
            }

            @Override
            public void onFailure(Exception e) {
                progressBar.setVisibility(View.GONE);
                // Show empty state so the screen isn't blank
                rvUpcoming.setVisibility(View.GONE);
                llEmptyUpcoming.setVisibility(View.VISIBLE);

                String reason = e != null ? e.getMessage() : "Unknown error";
                Log.e(TAG, "loadUpcomingConfirmations FAILED: " + reason, e);
                Toast.makeText(MealHistoryActivity.this,
                        "Could not load confirmations: " + reason,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Load: Attendance History (Tab 1)
    // -----------------------------------------------------------------------

    private void loadAttendanceHistory() {
        progressBar.setVisibility(View.VISIBLE);
        rvAttendance.setVisibility(View.GONE);
        llEmptyAttendance.setVisibility(View.GONE);

        String uid = mAuth.getCurrentUser().getUid();
        Log.d(TAG, "loadAttendanceHistory: uid=" + uid);

        repository.loadAttendanceHistory(uid, new MealHistoryRepository.AttendanceCallback() {
            @Override
            public void onSuccess(List<AttendanceRecord> records) {
                attendanceLoaded = true;
                progressBar.setVisibility(View.GONE);

                attendanceList.clear();
                attendanceList.addAll(records);
                attendanceAdapter.notifyDataSetChanged();

                // Update header stats
                int eaten   = 0;
                int walkins = 0;
                for (AttendanceRecord r : records) {
                    if (r.isWalkin()) walkins++; else eaten++;
                }
                tvCountEaten.setText(String.valueOf(eaten));
                tvCountWalkins.setText(String.valueOf(walkins));

                if (records.isEmpty()) {
                    rvAttendance.setVisibility(View.GONE);
                    llEmptyAttendance.setVisibility(View.VISIBLE);
                    Log.d(TAG, "No attendance records found.");
                } else {
                    llEmptyAttendance.setVisibility(View.GONE);
                    rvAttendance.setVisibility(View.VISIBLE);
                    Log.d(TAG, "Attendance records displayed: " + records.size());
                }
            }

            @Override
            public void onFailure(Exception e) {
                attendanceLoaded = true;
                progressBar.setVisibility(View.GONE);
                rvAttendance.setVisibility(View.GONE);
                llEmptyAttendance.setVisibility(View.VISIBLE);

                String reason = e != null ? e.getMessage() : "Unknown error";
                Log.e(TAG, "loadAttendanceHistory FAILED: " + reason, e);
                Toast.makeText(MealHistoryActivity.this,
                        "Could not load attendance: " + reason,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Real-time update — called from MainActivity after a confirmation is saved
    // so the Upcoming list refreshes without requiring the screen to reopen.
    // -----------------------------------------------------------------------

    public void onNewConfirmationSaved(ConfirmationRecord record) {
        confirmationAdapter.addToTop(record);
        tvCountUpcoming.setText(String.valueOf(confirmationList.size()));
        llEmptyUpcoming.setVisibility(View.GONE);
        rvUpcoming.setVisibility(View.VISIBLE);
        Log.d(TAG, "Real-time update: added " + record.getMealType() + " on " + record.getDate());
    }
}
