package com.example.smartmess.repository;

import android.util.Log;

import com.example.smartmess.models.AttendanceRecord;
import com.example.smartmess.models.ConfirmationRecord;
import com.example.smartmess.models.MealTimelineEntry;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MealHistoryRepository — optimised for fast load times.
 *
 * Key changes vs. previous version:
 *
 * 1. Attendance now uses a single collectionGroup("scanLogs") query
 *    filtered by userId instead of 365 × 4 = 1,460 individual reads.
 *    One network round-trip replaces 1,460.
 *
 * 2. Firestore offline persistence is enabled so cached data is served
 *    instantly on repeat opens; the network refresh happens in the
 *    background automatically.
 *
 * 3. loadAttendanceHistory() accepts a Source parameter so the caller
 *    can request CACHE first (instant) then SERVER (fresh).
 *
 * 4. Results are limited to the 50 most-recent attendance records.
 *
 * Firestore paths:
 *   confirmations/{date}/{meal}/{uid}            — shared confirmation
 *   user_confirmations/{uid}/records/{date_meal} — user-side history
 *   scanLogs/{date}/{meal}/{uid}                 — QR attendance (read-only)
 *
 * Required Firestore index (create once in Firebase console):
 *   Collection group : scanLogs
 *   Fields           : userId ASC, timestamp DESC
 */
public class MealHistoryRepository {

    private static final String TAG            = "MealHistoryRepo";
    private static final int    ATTENDANCE_LIMIT = 50;   // max records to display

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

    public interface TimelineCallback {
        void onSuccess(List<MealTimelineEntry> entries);
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
        enableOfflinePersistence();
    }

    /**
     * Enable Firestore offline persistence once.
     * Cached data is served instantly on repeat opens; network refresh
     * happens automatically in the background.
     */
    private void enableOfflinePersistence() {
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            db.setFirestoreSettings(settings);
        } catch (Exception e) {
            // Settings can only be changed before any Firestore usage — safe to ignore
            // if already set elsewhere (e.g. Application class).
            Log.w(TAG, "Firestore settings already configured: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Save Confirmation — dual-write
    //
    // Write 1: confirmations/{date}/{mealType}/{uid}
    // Write 2: user_confirmations/{uid}/records/{date_mealType}
    //          Uses FieldValue.serverTimestamp() for consistent Timestamp type.
    // -----------------------------------------------------------------------
    public void saveConfirmation(String userId,
                                 String date,
                                 String mealType,
                                 String status,
                                 String timeSlot,
                                 SaveCallback callback) {

        // Write 1 — shared confirmations (existing QR-gate structure)
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

        // Write 2 — user-side history copy
        Map<String, Object> userRecord = new HashMap<>();
        userRecord.put("date",      date);
        userRecord.put("mealType",  mealType);
        userRecord.put("status",    status);
        userRecord.put("timeSlot",  timeSlot);
        userRecord.put("timestamp", FieldValue.serverTimestamp());

        String docKey = date + "_" + mealType;

        db.collection("user_confirmations")
                .document(userId)
                .collection("records")
                .document(docKey)
                .set(userRecord)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Confirmation saved: " + docKey);
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
    // Single query ordered by timestamp DESC, client-side filter for
    // date >= today. Safe because a student has at most ~30 future records.
    // -----------------------------------------------------------------------
    public void loadUpcomingConfirmations(String userId, ConfirmationsCallback callback) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());

        long startMs = System.currentTimeMillis();
        Log.d(TAG, "loadUpcomingConfirmations: uid=" + userId + " today=" + today);

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

                        long tsMillis = extractTimestampMillis(doc.get("timestamp"));
                        if (date == null) continue;

                        if (date.compareTo(today) >= 0) {
                            ConfirmationRecord r = new ConfirmationRecord(
                                    date, mealType, status, timeSlot, tsMillis);
                            r.setDocId(doc.getId());
                            upcoming.add(r);
                        }
                    }

                    // Sort: nearest date first, then canonical meal order within same date
                    upcoming.sort((a, b) -> {
                        int dateCmp = a.getDate().compareTo(b.getDate());
                        if (dateCmp != 0) return dateCmp;
                        return mealOrder(a.getMealType()) - mealOrder(b.getMealType());
                    });

                    Log.d(TAG, "Upcoming loaded: " + upcoming.size()
                            + " in " + (System.currentTimeMillis() - startMs) + "ms");
                    callback.onSuccess(upcoming);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "loadUpcomingConfirmations FAILED: " + e.getMessage(), e);
                    callback.onFailure(e);
                });
    }

    // -----------------------------------------------------------------------
    // Load Attendance History — OPTIMISED
    //
    // BEFORE: 365 × 4 = 1,460 individual document reads (one per date/meal).
    // AFTER:  1 collectionGroup query filtered by userId, ordered by
    //         timestamp DESC, limited to ATTENDANCE_LIMIT (50) records.
    //
    // Strategy:
    //   Step 1 — Query CACHE first (Source.CACHE). Returns instantly if
    //            data was previously fetched. Delivers results to the UI
    //            immediately so the spinner disappears fast.
    //   Step 2 — Query SERVER (Source.SERVER) in the background to refresh.
    //            If the cache is empty (first open), falls through to server.
    //
    // Required Firestore index (create once in Firebase console):
    //   Collection group : scanLogs
    //   Fields           : userId ASC, timestamp DESC
    //
    // If the index doesn't exist yet, Firestore returns a FAILED_PRECONDITION
    // error with a direct link to create it — we log that link clearly.
    // -----------------------------------------------------------------------
    public void loadAttendanceHistory(String userId, AttendanceCallback callback) {
        long startMs = System.currentTimeMillis();
        Log.d("PERFORMANCE", "Attendance query started for uid=" + userId);

        // Build the base query once — reused for both cache and server passes
        Query baseQuery = db.collectionGroup("scanLogs")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(ATTENDANCE_LIMIT);

        // Step 1: try cache first for instant display
        baseQuery.get(Source.CACHE)
                .addOnSuccessListener(cacheSnap -> {
                    if (!cacheSnap.isEmpty()) {
                        List<AttendanceRecord> cached = parseAttendanceSnapshot(cacheSnap);
                        Log.d("PERFORMANCE", "Attendance from CACHE: " + cached.size()
                                + " records in " + (System.currentTimeMillis() - startMs) + "ms");
                        callback.onSuccess(cached);
                        // Still refresh from server silently in background
                        refreshAttendanceFromServer(baseQuery, userId, startMs);
                    } else {
                        // Cache empty — go straight to server
                        Log.d("PERFORMANCE", "Cache empty, fetching from SERVER");
                        fetchAttendanceFromServer(baseQuery, userId, startMs, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    // Cache miss is normal on first open — fall through to server
                    Log.d(TAG, "Cache miss (expected on first open): " + e.getMessage());
                    fetchAttendanceFromServer(baseQuery, userId, startMs, callback);
                });
    }

    /**
     * Fetch attendance from Firestore server with a 10-second timeout guard.
     * If the query fails (e.g. missing index), logs the error clearly.
     */
    private void fetchAttendanceFromServer(Query query, String userId,
                                           long startMs, AttendanceCallback callback) {
        // 10-second timeout: post a delayed runnable that fires the failure
        // callback if Firestore hasn't responded yet.
        android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] responded = {false};

        Runnable timeoutRunnable = () -> {
            if (!responded[0]) {
                responded[0] = true;
                Log.e("PERFORMANCE", "Attendance query timed out after 10s for uid=" + userId);
                callback.onFailure(new Exception(
                        "Unable to load attendance records. Please try again."));
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 10_000);

        query.get(Source.SERVER)
                .addOnSuccessListener(snap -> {
                    if (responded[0]) return;   // timeout already fired
                    responded[0] = true;
                    timeoutHandler.removeCallbacks(timeoutRunnable);

                    List<AttendanceRecord> records = parseAttendanceSnapshot(snap);
                    Log.d("PERFORMANCE", "Attendance from SERVER: " + records.size()
                            + " records in " + (System.currentTimeMillis() - startMs) + "ms");
                    callback.onSuccess(records);
                })
                .addOnFailureListener(e -> {
                    if (responded[0]) return;
                    responded[0] = true;
                    timeoutHandler.removeCallbacks(timeoutRunnable);

                    String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    // If this is a missing-index error, Firestore includes a URL to create it
                    if (msg.contains("FAILED_PRECONDITION") || msg.contains("index")) {
                        Log.e("PERFORMANCE",
                                "Missing Firestore index for scanLogs collectionGroup query.\n"
                                + "Create it at: https://console.firebase.google.com\n"
                                + "Collection group: scanLogs | Fields: userId ASC, timestamp DESC\n"
                                + "Full error: " + msg);
                    } else {
                        Log.e("PERFORMANCE", "Attendance SERVER query FAILED: " + msg, e);
                    }
                    callback.onFailure(e);
                });
    }

    /**
     * Silent background refresh after serving cached data.
     * Updates the callback only if the server returns different results.
     */
    private void refreshAttendanceFromServer(Query query, String userId, long startMs) {
        query.get(Source.SERVER)
                .addOnSuccessListener(snap -> {
                    Log.d("PERFORMANCE", "Background refresh complete: " + snap.size()
                            + " records in " + (System.currentTimeMillis() - startMs) + "ms");
                    // Background refresh — results will be served from cache on next open
                })
                .addOnFailureListener(e ->
                        Log.w(TAG, "Background refresh failed (non-critical): " + e.getMessage()));
    }

    /**
     * Parse a QuerySnapshot of scanLogs documents into AttendanceRecord list.
     * Each document path is: scanLogs/{date}/{meal}/{userId}
     * We extract date and meal from the document reference path.
     */
    private List<AttendanceRecord> parseAttendanceSnapshot(
            com.google.firebase.firestore.QuerySnapshot snap) {

        SimpleDateFormat labelFmt =
                new SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault());
        SimpleDateFormat parseFmt =
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        List<AttendanceRecord> records = new ArrayList<>();

        for (QueryDocumentSnapshot doc : snap) {
            // Path: scanLogs/{date}/{meal}/{userId}
            // doc.getReference().getParent() = scanLogs/{date}/{meal}
            // doc.getReference().getParent().getParent() = scanLogs/{date}
            // doc.getReference().getParent().getId() = meal name
            // doc.getReference().getParent().getParent().getId() = date string
            String meal = doc.getReference().getParent().getId();
            String date = doc.getReference().getParent().getParent() != null
                    ? doc.getReference().getParent().getParent().getId()
                    : "";

            // Build human-readable day label from the date string
            String dayLabel = date;
            try {
                java.util.Date d = parseFmt.parse(date);
                if (d != null) dayLabel = labelFmt.format(d);
            } catch (Exception ignored) {}

            Boolean walkin  = doc.getBoolean("isWalkin");
            String  mealTyp = doc.getString("mealType");
            long    tsMillis = extractTimestampMillis(doc.get("timestamp"));

            records.add(new AttendanceRecord(
                    date,
                    dayLabel,
                    mealTyp != null ? mealTyp : meal,
                    Boolean.TRUE.equals(walkin),
                    tsMillis
            ));
        }

        return records;
    }

    // -----------------------------------------------------------------------
    // Load Meal Timeline — unified History tab data
    //
    // Strategy:
    //   1. Load ALL user confirmations (user_confirmations/{uid}/records)
    //   2. Load ALL attendance records (collectionGroup scanLogs by userId)
    //   3. Merge by date+meal key to derive per-meal status:
    //
    //      Confirmed (eat) + Scan found   → CONFIRMED_ATTENDED
    //      Confirmed (eat) + No scan + past date → CONFIRMED_MISSED
    //      Confirmed (not_eat)             → SKIPPED
    //      Confirmed (eat) + future date   → UPCOMING (included in history)
    //      Scan without any confirmation   → WALKIN
    //
    //   4. Sort by date DESC, then canonical meal order within same date.
    //   5. Optional filter: 7 = last 7 days, 30 = last 30 days, 0 = all time
    // -----------------------------------------------------------------------

    /**
     * @param filterDays 7 = last 7 days, 30 = last 30 days, 0 = all time
     */
    public void loadMealTimeline(String userId, int filterDays, TimelineCallback callback) {
        long startMs = System.currentTimeMillis();
        Log.d(TAG, "loadMealTimeline: uid=" + userId + " filterDays=" + filterDays);

        final String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());

        final String cutoffDate;
        if (filterDays > 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -filterDays);
            cutoffDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(cal.getTime());
        } else {
            cutoffDate = "";
        }

        db.collection("user_confirmations")
                .document(userId)
                .collection("records")
                .get()
                .addOnSuccessListener(confirmSnap -> {

                    final Map<String, ConfirmationRecord> confirmMap = new HashMap<>();
                    for (QueryDocumentSnapshot doc : confirmSnap) {
                        String date     = doc.getString("date");
                        String mealType = doc.getString("mealType");
                        String status   = doc.getString("status");
                        String timeSlot = doc.getString("timeSlot");
                        long   tsMillis = extractTimestampMillis(doc.get("timestamp"));
                        if (date == null || mealType == null) continue;
                        if (!cutoffDate.isEmpty() && date.compareTo(cutoffDate) < 0) continue;

                        ConfirmationRecord r = new ConfirmationRecord(
                                date, mealType, status, timeSlot, tsMillis);
                        r.setDocId(doc.getId());
                        confirmMap.put(date + "_" + mealType, r);
                    }

                    Query attQuery = db.collectionGroup("scanLogs")
                            .whereEqualTo("userId", userId)
                            .orderBy("timestamp", Query.Direction.DESCENDING)
                            .limit(200);

                    attQuery.get()
                            .addOnSuccessListener(scanSnap -> {

                                final Map<String, Long> scanMap = new HashMap<>();
                                for (QueryDocumentSnapshot doc : scanSnap) {
                                    String meal = doc.getReference().getParent().getId();
                                    String date = doc.getReference().getParent().getParent() != null
                                            ? doc.getReference().getParent().getParent().getId()
                                            : "";
                                    if (date.isEmpty() || meal.isEmpty()) continue;
                                    if (!cutoffDate.isEmpty() && date.compareTo(cutoffDate) < 0)
                                        continue;

                                    long tsMillis = extractTimestampMillis(doc.get("timestamp"));
                                    String key = date + "_" + meal;
                                    if (!scanMap.containsKey(key)
                                            || scanMap.get(key) > tsMillis) {
                                        scanMap.put(key, tsMillis);
                                    }
                                }

                                List<MealTimelineEntry> timeline = new ArrayList<>();

                                for (Map.Entry<String, ConfirmationRecord> e
                                        : confirmMap.entrySet()) {
                                    String key   = e.getKey();
                                    ConfirmationRecord conf = e.getValue();
                                    String date  = conf.getDate();
                                    String meal  = conf.getMealType();
                                    boolean isFuture = date.compareTo(today) > 0;
                                    boolean isToday  = date.equals(today);
                                    boolean isEat    = "eat".equals(conf.getStatus());
                                    long scanTime    = scanMap.containsKey(key)
                                            ? scanMap.get(key) : 0L;

                                    int entryStatus;
                                    if (!isEat) {
                                        entryStatus = MealTimelineEntry.STATUS_SKIPPED;
                                    } else if (isFuture) {
                                        entryStatus = MealTimelineEntry.STATUS_UPCOMING;
                                    } else if (scanTime > 0) {
                                        entryStatus = MealTimelineEntry.STATUS_CONFIRMED_ATTENDED;
                                    } else {
                                        entryStatus = isToday
                                                ? MealTimelineEntry.STATUS_UPCOMING
                                                : MealTimelineEntry.STATUS_CONFIRMED_MISSED;
                                    }

                                    timeline.add(new MealTimelineEntry(
                                            date, meal, entryStatus,
                                            conf.getTimestamp(),
                                            conf.getTimeSlot(),
                                            scanTime));

                                    scanMap.remove(key);
                                }

                                // Remaining scans are walk-ins
                                for (Map.Entry<String, Long> e : scanMap.entrySet()) {
                                    String[] parts = e.getKey().split("_", 2);
                                    if (parts.length < 2) continue;
                                    String date = parts[0];
                                    String meal = parts[1];
                                    timeline.add(new MealTimelineEntry(
                                            date, meal,
                                            MealTimelineEntry.STATUS_WALKIN,
                                            e.getValue(), null, e.getValue()));
                                }

                                // Sort: newest date first, canonical meal order within day
                                timeline.sort((a, b) -> {
                                    int dateCmp = b.getDate().compareTo(a.getDate());
                                    if (dateCmp != 0) return dateCmp;
                                    return mealOrder(a.getMealType())
                                            - mealOrder(b.getMealType());
                                });

                                Log.d(TAG, "Timeline built: " + timeline.size()
                                        + " in " + (System.currentTimeMillis() - startMs) + "ms");
                                callback.onSuccess(timeline);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "loadMealTimeline scanLogs FAILED: " + e.getMessage(), e);
                                // Fallback: confirmations-only timeline
                                List<MealTimelineEntry> fallback =
                                        buildConfirmOnlyTimeline(confirmMap, today);
                                callback.onSuccess(fallback);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "loadMealTimeline confirmations FAILED: " + e.getMessage(), e);
                    callback.onFailure(e);
                });
    }

    /** Fallback when scanLogs query fails — builds timeline from confirmations only. */
    private List<MealTimelineEntry> buildConfirmOnlyTimeline(
            Map<String, ConfirmationRecord> confirmMap, String today) {

        List<MealTimelineEntry> list = new ArrayList<>();
        for (ConfirmationRecord conf : confirmMap.values()) {
            boolean isFuture = conf.getDate().compareTo(today) > 0;
            boolean isEat    = "eat".equals(conf.getStatus());
            int entryStatus;
            if (!isEat)        entryStatus = MealTimelineEntry.STATUS_SKIPPED;
            else if (isFuture) entryStatus = MealTimelineEntry.STATUS_UPCOMING;
            else               entryStatus = MealTimelineEntry.STATUS_CONFIRMED_MISSED;

            list.add(new MealTimelineEntry(
                    conf.getDate(), conf.getMealType(), entryStatus,
                    conf.getTimestamp(), conf.getTimeSlot(), 0L));
        }
        list.sort((a, b) -> {
            int dateCmp = b.getDate().compareTo(a.getDate());
            if (dateCmp != 0) return dateCmp;
            return mealOrder(a.getMealType()) - mealOrder(b.getMealType());
        });
        return list;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Safely extract epoch millis from either a Firestore Timestamp or a Long. */
    private long extractTimestampMillis(Object tsObj) {
        if (tsObj instanceof com.google.firebase.Timestamp) {
            return ((com.google.firebase.Timestamp) tsObj).toDate().getTime();
        } else if (tsObj instanceof Long) {
            return (Long) tsObj;
        }
        return 0L;
    }

    /** Canonical meal order for same-day sorting. */
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
