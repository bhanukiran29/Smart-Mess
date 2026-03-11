package com.example.smartmess;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MealHistoryActivity extends AppCompatActivity {

    private ListView lvHistory;
    private TextView tvEmpty;
    private Button btnBack;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_history);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        lvHistory = findViewById(R.id.lvMealHistory);
        tvEmpty   = findViewById(R.id.tvEmptyHistory);
        btnBack   = findViewById(R.id.btnHistoryBack);

        btnBack.setOnClickListener(v -> finish());
        loadHistory();
    }

    private void loadHistory() {
        String userId = mAuth.getCurrentUser().getUid();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String[] meals = {"breakfast", "lunch", "dinner"};
        List<String> entries = new ArrayList<>();

        // Check last 7 days
        int totalQueries = 7 * 3;
        final int[] done = {0};

        for (int i = 0; i < 7; i++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -i);
            String date = sdf.format(cal.getTime());
            SimpleDateFormat labelFmt = new SimpleDateFormat("EEE, MMM dd", Locale.getDefault());
            String dayLabel = labelFmt.format(cal.getTime());

            for (String meal : meals) {
                db.collection("scanLogs").document(date).collection(meal)
                        .document(userId).get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                DocumentSnapshot doc = task.getResult();
                                if (doc.exists()) {
                                    Boolean isWalkin = doc.getBoolean("isWalkin");
                                    String mealName = doc.getString("mealType");
                                    String tag = Boolean.TRUE.equals(isWalkin) ? "💳 Walk-in (₹50)" : "✅ Pre-confirmed";
                                    entries.add(dayLabel + "  •  " + capitalize(mealName) + "\n" + tag);
                                }
                            }
                            done[0]++;
                            if (done[0] >= totalQueries) {
                                runOnUiThread(() -> {
                                    if (entries.isEmpty()) {
                                        tvEmpty.setVisibility(View.VISIBLE);
                                        lvHistory.setVisibility(View.GONE);
                                    } else {
                                        tvEmpty.setVisibility(View.GONE);
                                        lvHistory.setVisibility(View.VISIBLE);
                                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                                                android.R.layout.simple_list_item_1, entries);
                                        lvHistory.setAdapter(adapter);
                                    }
                                });
                            }
                        });
            }
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
