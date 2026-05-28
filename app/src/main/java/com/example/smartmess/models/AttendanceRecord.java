package com.example.smartmess.models;

/**
 * AttendanceRecord — a single row in the Attendance History tab.
 * Built locally from scanLogs/{date}/{meal}/{uid} documents.
 */
public class AttendanceRecord {

    private String date;        // "yyyy-MM-dd"
    private String dayLabel;    // "Mon, May 26"
    private String mealType;    // "breakfast" | "lunch" | "dinner"
    private boolean isWalkin;   // true → walk-in (₹50 charged), false → pre-confirmed
    private long   timestamp;

    public AttendanceRecord() {}

    public AttendanceRecord(String date, String dayLabel,
                            String mealType, boolean isWalkin, long timestamp) {
        this.date      = date;
        this.dayLabel  = dayLabel;
        this.mealType  = mealType;
        this.isWalkin  = isWalkin;
        this.timestamp = timestamp;
    }

    public String  getDate()                    { return date; }
    public void    setDate(String date)         { this.date = date; }

    public String  getDayLabel()                { return dayLabel; }
    public void    setDayLabel(String dayLabel) { this.dayLabel = dayLabel; }

    public String  getMealType()                { return mealType; }
    public void    setMealType(String mealType) { this.mealType = mealType; }

    public boolean isWalkin()                   { return isWalkin; }
    public void    setWalkin(boolean walkin)    { isWalkin = walkin; }

    public long    getTimestamp()               { return timestamp; }
    public void    setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
