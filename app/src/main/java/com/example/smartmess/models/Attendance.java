package com.example.smartmess.models;

public class Attendance {
    private String userId;
    private long scannedTime;

    // Required empty constructor for Firebase
    public Attendance() {
    }

    public Attendance(String userId, long scannedTime) {
        this.userId = userId;
        this.scannedTime = scannedTime;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getScannedTime() {
        return scannedTime;
    }

    public void setScannedTime(long scannedTime) {
        this.scannedTime = scannedTime;
    }
}
