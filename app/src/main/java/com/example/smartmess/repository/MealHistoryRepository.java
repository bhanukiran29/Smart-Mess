package com.example.smartmess.repository;

import android.util.Log;

import com.example.smartmess.models.AttendanceRecord;
import com.example.smartmess.models.ConfirmationRecord;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MealHistoryRepository
 *
 * All Firestore reads/writes for the Meal History feature.
 *
 * Firestore paths used:
 *   confirmations/{date}/{meal}/{uid}              — shared confirmation (existing)
 *   user_confirmations/{uid}/records/{date_meal}   — user-side history copy (new)
 *   scanLogs/{date}/{meal}/{uid}                   — QR attendance (existing, read-only here)
 */
public class MealHistoryRepository {

    private static final String TAG   = "MealHistoryRepo";
    private static final String[] MEALS = {"breakfast", "lunch", "snacks", "dinner"};

    // -----------------------------------------------------------------------
    // Callbacks
    // -----------------------------------------------------------------------

    public interface ConfirmationsCallback {
        void onSuccess(List<ConfirmationRecord> records);
        void onFailure(Exception e);
    }

    public interface AttendanceCallback {
        void onSuccess(List<AttendanceRecord> records);
        void onFailure(Exception e);
    }

    public interface SaveCallback {
        void onSuccess(ConfirmationRecord saved);
        void onFailure(Exception e);
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final FirebaseFirestore db;

    public MealHistoryRepository(FirebaseFirestore db) {
        this.db = db;
    }

    // -----------------------------------------------------------------------
    // Save Confirmation — dual-write
    //
    // Write 1: confirmations/{date}/{mealType}/{uid}
    //          Uses the existing Confirmation model so the QR gate still works.
    //
    // Write 2: user_confirmations/{uid}/records/{date_mealType}
    //          Deterministic key (date + "_" + mealType) so re-confirming the
    //          same meal overwrites rather than duplicates.
    //          timestamp is stored as FieldValue.serverTimestamp() so Firestore
    //          keeps it as a Timestamp object — required for orderBy to work.
    // -----------------------------------------------------------------------
    public void saveConfirmation(String userId,
                                 String date,
                                 String mealType,
                                 String status,
                                 String timeSlot,
                                 SaveCallback callback) {

        // --- Write 1: shared confirmations collection (existing structure) ---
        Map<String, Object> sharedData = new HashMap<>();
        sharedData.put("userId",    userId);
        sharedData.put("status",    status);
        sharedData.put("timeSlot",  timeSlot);
        sharedData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("confirmations")
                .document(date)
                .collection(mealType)
                .document(userId)
                .set(sharedData)
                .addOnFailureListener(e ->
                        Log.e(TAG, "Write 1 (confirmations) failed: " + e.getMessage(), e));

        // --- Write 2: user-side history copy ---
        Map<String, Object> userRecord = new HashMap<>();
        userRecord.put("date",      date);
        userRecord.put("mealType",  mealType);
        userRecord.put("status",    status);
        userRecord.put("timeSlot",  timeSlot);
        userRecord.put("timestamp", FieldValue.serverTimestamp());

        String docKey = date + "_" + mealType;   // e.g. "2026-05-30_breakfast"

        db.collection("user_confirmations")
                .document(userId)
                .collection("records")
                .document(docKey)
                .set(userRecord)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Confirmation saved: " + docKey);
                    // Build a local ConfirmationRecord to return to the UI immediately.
                    // timestamp is set to now() locally since serverTimestamp is async.
                    ConfirmationRecord record = new ConfirmationRecord(
                            date, mealType, status, timeSlot, System.currentTimeMillis());
                    record.setDocId(docKey);
                    callback.onSuccess(record);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Write 2 (user_confirmations) failed: " + e.getMessage(), e);
                    callback.onFailure(e);
                });
    }

    // -----------------------------------------------------------------------
    // Load Upcoming Confirmations
    //
    // Reads: user_confirmations/{uid}/records
    //
    // KEY FIX: The previous version used a compound query
    //   .whereGreaterThanOrEqualTo("date", today)
    //   .orderBy("date")
    //   .orderBy("timestamp")
    // which requires a composite Firestore index that may not exist, causing
    // a FAILED_PRECONDITION error.
    //
    // Solution: fetch ALL records ordered only by timestamp (single-field index
    // always exists), then filter and sort client-side. This is safe because
    // a single student will never have more than a few hundred records.
    // -----------------------------------------------------------------------
    public void loadUpcomingConfirmations(String userId, ConfirmationsCallback callback) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());

        Log.d(TAG, "Loading upcoming confirmations for uid=" + userId + " from date=" + today);

        db.collection("user_confirmations")
                .document(userId)
                .collection("records")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<ConfirmationRecord> upcoming = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String date     = doc.getString("date");
                        String mealType = doc.getString("mealType");
                        String status   = doc.getString("status");
                        String timeSlot = doc.getString("timeSlot");

                        // Timestamp may be a Firestore Timestamp or a Long — handle both
                        long tsMillis = 0;
                        Object tsObj = doc.get("timestamp");
                        if (tsObj instanceof com.google.firebase.Timestamp) {
                            tsMillis = ((com.google.firebase.Timestamp) tsObj).toDate().getTime();
                        } else if (tsObj instanceof Long) {
                            tsMillis = (Long) tsObj;
                        }

                        if (date == null) continue;

                        // Client-side filter: only future/today records
                        if (date.compareTo(today) >= 0) {
                            ConfirmationRecord r = new ConfirmationRecord(
                                    date, mealType, status, timeSlot, tsMillis);
                            r.setDocId(doc.getId());
                            upcoming.add(r);
                        }
                    }

                    // Sort nearest date first, then by meal order within same date
                    upcoming.sort((a, b) -> {
                        int dateCmp = a.getDate().compareTo(b.getDate());
                        if (dateCmp != 0) return dateCmp;
                        return mealOrder(a.getMealType()) - mealOrder(b.getMealType());
                    });

                    Log.d(TAG, "Upcoming confirmations loaded: " + upcoming.size());
                    callback.onSuccess(upcoming);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "loadUpcomingConfirmations FAILED: " + e.getMessage(), e);
                    callback.onFailure(e);
                });
    }

    // -----------------------------------------------------------------------
    // Load Attendance History
    //
    // Reads: scanLogs/{date}/{meal}/{uid}
    //
    // KEY FIX: Removed the 30-day cap. Now scans ALL available dates by
    // querying the scanLogs collection group for this user's documents.
    //
    // Because scanLogs is structured as scanLogs/{date}/{meal}/{uid}, we
    // cannot do a simple collectionGroup query filtered by uid without a
    // composite index. Instead we use a practical approach: scan the last
    // 365 days (1 year). This covers all realistic history while keeping
    // query count manageable (365 × 4 meals = 1460 reads max, most empty).
    // -----------------------------------------------------------------------
    public void loadAttendanceHistory(String userId, AttendanceCallback callback) {
        SimpleDateFormat sdf      = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelFmt = new SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault());

        // Scan last 365 days (full year — no arbitrary cap)
        int DAYS_TO_SCAN = 365;

        List<String> dates     = new ArrayList<>();
        List<String> dayLabels = new ArrayList<>();
        for (int i = 0; i < DAYS_TO_SCAN; i++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -i);
            dates.add(sdf.format(cal.getTime()));
            dayLabels.add(labelFmt.format(cal.getTime()));
        }

        int totalQueries = dates.size() * MEALS.length;
        AtomicInteger done    = new AtomicInteger(0);
        List<AttendanceRecord> results = Collections.synchronizedList(new ArrayList<>());

        Log.d(TAG, "Loading attendance history: scanning " + DAYS_TO_SCAN + " days × "
                + MEALS.length + " meals = " + totalQueries + " queries");

        for (int i = 0; i < dates.size(); i++) {
            final String date     = dates.get(i);
            final String dayLabel = dayLabels.get(i);

            for (String meal : MEALS) {
                db.collection("scanLogs")
                        .document(date)
                        .collection(meal)
                        .document(userId)
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                DocumentSnapshot doc = task.getResult();
                                if (doc != null && doc.exists()) {
                                    Boolean walkin  = doc.getBoolean("isWalkin");
                                    String  mealTyp = doc.getString("mealType");

                                    // Handle both Firestore Timestamp and Long for timestamp
                                    long tsMillis = 0;
                                    Object tsObj = doc.get("timestamp");
                                    if (tsObj instanceof com.google.firebase.Timestamp) {
                                        tsMillis = ((com.google.firebase.Timestamp) tsObj)
                                                .toDate().getTime();
                                    } else if (tsObj instanceof Long) {
                                        tsMillis = (Long) tsObj;
                                    }

                                    results.add(new AttendanceRecord(
                                            date,
                                            dayLabel,
                                            mealTyp != null ? mealTyp : meal,
                                            Boolean.TRUE.equals(walkin),
                                            tsMillis
                                    ));
                                }
                            } else {
                                Log.e(TAG, "scanLogs read failed for " + date + "/" + meal
                                        + ": " + (task.getException() != null
                                                  ? task.getException().getMessage() : "unknown"));
                            }

                            if (done.incrementAndGet() >= totalQueries) {
                                // Sort newest first
                                List<AttendanceRecord> sorted = new ArrayList<>(results);
                                sorted.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                                Log.d(TAG, "Attendance records found: " + sorted.size());
                                callback.onSuccess(sorted);
                            }
                        });
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helper: canonical meal order for same-day sorting
    // -----------------------------------------------------------------------
    private int mealOrder(String meal) {
        if (meal == null) return 99;
        switch (meal.toLowerCase()) {
            case "breakfast": return 0;
            case "lunch":     return 1;
            case "snacks":    return 2;
            case "dinner":    return 3;
            default:          return 99;
        }
    }
}
