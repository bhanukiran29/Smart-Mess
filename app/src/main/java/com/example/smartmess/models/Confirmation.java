package com.example.smartmess.models;

public class Confirmation {
    private String userId;
    private String status; // "eat", "not_eat"
    private String timeSlot;
    private long timestamp;

    // Required empty constructor for Firebase
    public Confirmation() {}

    public Confirmation(String userId, String status, String timeSlot, long timestamp) {
        this.userId = userId;
        this.status = status;
        this.timeSlot = timeSlot;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
