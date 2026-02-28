package com.example.smartmess.models;

public class WasteData {
    private int preparedPlates;
    private double wastedKg;
    private String recordedBy; // userId of staff

    // Required empty constructor for Firebase
    public WasteData() {
    }

    public WasteData(int preparedPlates, double wastedKg, String recordedBy) {
        this.preparedPlates = preparedPlates;
        this.wastedKg = wastedKg;
        this.recordedBy = recordedBy;
    }

    // Getters and Setters
    public int getPreparedPlates() {
        return preparedPlates;
    }

    public void setPreparedPlates(int preparedPlates) {
        this.preparedPlates = preparedPlates;
    }

    public double getWastedKg() {
        return wastedKg;
    }

    public void setWastedKg(double wastedKg) {
        this.wastedKg = wastedKg;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }
}
