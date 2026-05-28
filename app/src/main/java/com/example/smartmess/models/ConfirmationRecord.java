package com.example.smartmess.models;

/**
 * ConfirmationRecord — stored in user_confirmations/{uid}/records/{autoId}
 *
 * This is the user-side copy of a meal confirmation, optimised for
 * querying a single student's full confirmation history without
 * touching the shared confirmations/{date}/{meal}/{uid} structure.
 */
public class ConfirmationRecord {

    private String docId;       // Firestore auto-id (set locally after write)
    private String date;        // "yyyy-MM-dd"  e.g. "2026-05-30"
    private String mealType;    // "breakfast" | "lunch" | "snacks" | "dinner"
    private String status;      // "eat" | "not_eat"
    private String timeSlot;    // e.g. "7:00 AM - 8:00 AM"
    private long   timestamp;   // epoch millis of when the confirmation was saved

    // Required empty constructor for Firebase
    public ConfirmationRecord() {}

    public ConfirmationRecord(String date, String mealType,
                              String status, String timeSlot, long timestamp) {
        this.date      = date;
        this.mealType  = mealType;
        this.status    = status;
        this.timeSlot  = timeSlot;
        this.timestamp = timestamp;
    }

    // ---- Getters & Setters ----

    public String getDocId()                    { return docId; }
    public void   setDocId(String docId)        { this.docId = docId; }

    public String getDate()                     { return date; }
    public void   setDate(String date)          { this.date = date; }

    public String getMealType()                 { return mealType; }
    public void   setMealType(String mealType)  { this.mealType = mealType; }

    public String getStatus()                   { return status; }
    public void   setStatus(String status)      { this.status = status; }

    public String getTimeSlot()                 { return timeSlot; }
    public void   setTimeSlot(String timeSlot)  { this.timeSlot = timeSlot; }

    public long   getTimestamp()                { return timestamp; }
    public void   setTimestamp(long timestamp)  { this.timestamp = timestamp; }
}
