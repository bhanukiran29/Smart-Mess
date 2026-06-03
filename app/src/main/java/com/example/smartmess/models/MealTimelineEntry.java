package com.example.smartmess.models;

/**
 * MealTimelineEntry — a single row in the unified "History" timeline tab.
 *
 * Built locally by merging confirmations + scanLogs data:
 *
 *   Confirmed + Attended  → STATUS_CONFIRMED_ATTENDED
 *   Confirmed + No Scan   → STATUS_CONFIRMED_MISSED
 *   Confirmed (future)    → STATUS_UPCOMING  (not shown in History tab)
 *   Skipping confirmation → STATUS_SKIPPED
 *   Scan without confirm  → STATUS_WALKIN
 */
public class MealTimelineEntry {

    public static final int STATUS_CONFIRMED_ATTENDED = 0;  // ✅ Confirmed & Attended
    public static final int STATUS_CONFIRMED_MISSED   = 1;  // ❌ Confirmed but Missed
    public static final int STATUS_WALKIN             = 2;  // 🚶 Walk-in
    public static final int STATUS_SKIPPED            = 3;  // ⏭ Skipped
    public static final int STATUS_UPCOMING           = 4;  // 📝 Confirmed (Future)

    private String date;        // "yyyy-MM-dd"
    private String mealType;    // "breakfast" | "lunch" | "snacks" | "dinner"
    private int    status;      // one of the STATUS_* constants above
    private long   timestamp;   // epoch millis (used for sorting)
    private String timeSlot;    // time slot from confirmation, may be null
    private long   scanTime;    // epoch millis of QR scan, 0 if no scan

    public MealTimelineEntry() {}

    public MealTimelineEntry(String date, String mealType, int status,
                              long timestamp, String timeSlot, long scanTime) {
        this.date      = date;
        this.mealType  = mealType;
        this.status    = status;
        this.timestamp = timestamp;
        this.timeSlot  = timeSlot;
        this.scanTime  = scanTime;
    }

    // ---- Getters & Setters ----

    public String getDate()                     { return date; }
    public void   setDate(String date)          { this.date = date; }

    public String getMealType()                 { return mealType; }
    public void   setMealType(String mealType)  { this.mealType = mealType; }

    public int    getStatus()                   { return status; }
    public void   setStatus(int status)         { this.status = status; }

    public long   getTimestamp()                { return timestamp; }
    public void   setTimestamp(long timestamp)  { this.timestamp = timestamp; }

    public String getTimeSlot()                 { return timeSlot; }
    public void   setTimeSlot(String timeSlot)  { this.timeSlot = timeSlot; }

    public long   getScanTime()                 { return scanTime; }
    public void   setScanTime(long scanTime)    { this.scanTime = scanTime; }

    /** Convenience: is this entry in the past (not upcoming)? */
    public boolean isPast() { return status != STATUS_UPCOMING; }
}
