package com.example.smartmess;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MenuActivity extends AppCompatActivity {

    private EditText etBreakfast, etLunch, etDinner;
    private MaterialButton btnSaveMenu;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private String targetDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        db = FirebaseFirestore.getInstance();

        etBreakfast = findViewById(R.id.etBreakfast);
        etLunch = findViewById(R.id.etLunch);
        etDinner = findViewById(R.id.etDinner);
        btnSaveMenu = findViewById(R.id.btnSaveMenu);
        progressBar = findViewById(R.id.progressBar);

        // Uploading menu for tomorrow usually, or let's say "today's" menu
        Calendar calendar = Calendar.getInstance();
        targetDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());

        loadExistingMenu();

        btnSaveMenu.setOnClickListener(v -> saveMenu());
    }

    private void loadExistingMenu() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("weekly_menu").document(targetDate).get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBar.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        etBreakfast.setText(documentSnapshot.getString("breakfast"));
                        etLunch.setText(documentSnapshot.getString("lunch"));
                        etDinner.setText(documentSnapshot.getString("dinner"));
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load menu", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveMenu() {
        String breakfast = etBreakfast.getText().toString().trim();
        String lunch = etLunch.getText().toString().trim();
        String dinner = etDinner.getText().toString().trim();

        if (breakfast.isEmpty() || lunch.isEmpty() || dinner.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSaveMenu.setEnabled(false);

        Map<String, Object> menuData = new HashMap<>();
        menuData.put("breakfast", breakfast);
        menuData.put("lunch", lunch);
        menuData.put("dinner", dinner);
        menuData.put("timestamp", System.currentTimeMillis());

        db.collection("weekly_menu").document(targetDate)
                .set(menuData)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    btnSaveMenu.setEnabled(true);
                    Toast.makeText(MenuActivity.this, "Menu Saved Successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSaveMenu.setEnabled(true);
                    Toast.makeText(MenuActivity.this, "Failed to save menu", Toast.LENGTH_SHORT).show();
                });
    }
}
