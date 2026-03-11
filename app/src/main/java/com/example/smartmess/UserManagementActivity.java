package com.example.smartmess;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class UserManagementActivity extends AppCompatActivity {

    private ListView lvUsers;
    private TextView tvUserCount, tvEmpty;
    private Button btnBack;
    private ProgressBar progressBar;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        db          = FirebaseFirestore.getInstance();
        lvUsers     = findViewById(R.id.lvUsers);
        tvUserCount = findViewById(R.id.tvUserCount);
        tvEmpty     = findViewById(R.id.tvEmptyUsers);
        btnBack     = findViewById(R.id.btnUsersBack);
        progressBar = findViewById(R.id.progressBarUsers);

        btnBack.setOnClickListener(v -> finish());
        loadUsers();
    }

    private void loadUsers() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("users").get()
                .addOnSuccessListener((QuerySnapshot snapshot) -> {
                    progressBar.setVisibility(View.GONE);
                    List<String> rows = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String name   = doc.getString("name");
                        String email  = doc.getString("email");
                        String role   = doc.getString("role");
                        String hostel = doc.getString("hostelBlock");
                        rows.add(roleIcon(role) + " " + name
                                + "\n" + email
                                + "\nRole: " + role + "  |  Block: " + (hostel != null ? hostel : "—"));
                    }
                    tvUserCount.setText("Total Users: " + rows.size());
                    if (rows.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        lvUsers.setAdapter(new ArrayAdapter<>(this,
                                android.R.layout.simple_list_item_1, rows));
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show();
                });
    }

    private String roleIcon(String role) {
        if (role == null) return "👤";
        switch (role) {
            case "admin": return "👑";
            case "staff": return "👨‍🍳";
            default:      return "🎓";
        }
    }
}
