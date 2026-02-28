package com.example.smartmess.models;

public class User {
    private String userId;
    private String name;
    private String email;
    private String role; // "student", "staff", "admin"
    private String hostelBlock;

    // Required empty constructor for Firebase
    public User() {}

    public User(String userId, String name, String email, String role, String hostelBlock) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.hostelBlock = hostelBlock;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getHostelBlock() { return hostelBlock; }
    public void setHostelBlock(String hostelBlock) { this.hostelBlock = hostelBlock; }
}
