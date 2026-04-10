package com.example.smartmess;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.smartmess.models.Confirmation;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.google.firebase.firestore.FieldValue;

public class MainActivity extends AppCompatActivity {

    private TextView tvGreeting, tvDate;
    private View rowBreakfast, rowLunch, rowDinner;
    private TextView tvWalletBalance;
    private ChipGroup cgMealType, cgStatus, cgTimeSlot, cgRatingMeal;
    private RatingBar ratingBarMeal;
    private MaterialButton btnSubmitConfirmation, btnScanQR;
    private TextView btnSubmitRating;
    private LinearLayout btnNavHome, btnNavHistory, btnNavWallet, btnNavProfile;
    private Button btnLogout;
    private ProgressBar progressBar;
    private View cardTodaysMenu, cardConfirmTomorrow, cardRateMeal;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentDate;

    // Register ZXing barcode scanner
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if (result.getContents() == null) {
                    Toast.makeText(MainActivity.this, "Cancelled", Toast.LENGTH_SHORT).show();
                } else {
                    processQrScan(result.getContents());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initializeViews();
        loadGreetingAndDate();
        loadTodaysMenu();
        loadWalletBalance();
        checkNotificationPermission();
        scheduleMealReminder();
        runEntryAnimations();

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        btnSubmitConfirmation.setOnClickListener(v -> submitMealConfirmation());
        btnSubmitRating.setOnClickListener(v -> submitMealRating());

        // Bottom Navigation
        btnNavWallet.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, WalletActivity.class)));
        btnNavHistory.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, MealHistoryActivity.class)));
        btnNavProfile.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
        
        // For Feedback, we can bind it to the top avatar if User clicks it (optional):
        findViewById(R.id.cvAvatar).setOnClickListener(v -> startActivity(new Intent(MainActivity.this, FeedbackActivity.class)));

        // Top up pill routing logic
        View btnTopUpPill = findViewById(R.id.btnTopUpPill);
        if (btnTopUpPill != null) {
            btnTopUpPill.setOnClickListener(v -> {
                AnimationUtils.buttonPressDown(v);
                startActivity(new Intent(MainActivity.this, WalletActivity.class));
            });
        }

        // Launch Scanner
        btnScanQR.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setCaptureActivity(CustomScannerActivity.class);
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            options.setPrompt("Scan the Session QR Code at the entrance");
            options.setCameraId(0);
            options.setBeepEnabled(false);
            options.setBarcodeImageEnabled(true);
            barcodeLauncher.launch(options);
        });
    }

    // -----------------------------------------------------------------------
    // Walk-in Payment Logic
    // QR format expected:  SMART_MESS_{mealType}_{date}
    //   e.g.  SMART_MESS_lunch_2025-03-12
    //
    // Time windows (enforced):
    //   Breakfast  06:00 – 10:00
    //   Lunch      11:00 – 15:00
    //   Dinner     18:00 – 22:00
    // -----------------------------------------------------------------------
    private void processQrScan(String rawData) {
        if (!rawData.startsWith("SMART_MESS_")) {
            Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show();
            return;
        }

        String[] parts = rawData.split("_");
        if (parts.length < 4) {
            Toast.makeText(this, "QR format error", Toast.LENGTH_SHORT).show();
            return;
        }
        String mealType = parts[2].toLowerCase();
        String mealDate = parts[3];
        String userId   = mAuth.getCurrentUser().getUid();

        // ---- QR Time-Window Lock ----
        if (!isMealTimeValid(mealType)) {
            AnimationUtils.errorShake(btnScanQR);
            Toast.makeText(this,
                    "🕐 " + capitalize(mealType) + " entry is not open right now.\n"
                    + mealTimeWindow(mealType),
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Step 1 – Check live capacity FIRST before anything else
        db.collection("meal_capacity").document(mealDate).get()
                .addOnSuccessListener(capSnap -> {
                    Long capacity = capSnap.getLong(mealType + "_capacity");
                    Long scanned  = capSnap.getLong(mealType + "_scanned");
                    long scannedIn = scanned != null ? scanned : 0;

                    if (capacity != null && capacity > 0 && scannedIn >= capacity) {
                        // 🚫 Food is full — reject immediately
                        AnimationUtils.errorShake(btnScanQR);
                        Toast.makeText(this,
                                "🚫 Sorry! " + mealType.toUpperCase() + " is full. " +
                                "(" + scannedIn + "/" + capacity + " plates served). " +
                                "No food remaining.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Step 2 – Capacity OK. Check if student pre-confirmed this meal
                    db.collection("confirmations")
                            .document(mealDate)
                            .collection(mealType)
                            .document(userId)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot.exists() && "eat".equals(snapshot.getString("status"))) {
                                    // ✅ Pre-confirmed – free entry
                                    logAttendanceAndIncrement(userId, mealType, mealDate, false);
                                    Toast.makeText(this,
                                            "✅ Meal Confirmed! Enjoy your " + mealType + "!",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    // ❌ Walk-in – try wallet deduction
                                    attemptWalletDeduction(userId, mealType, mealDate);
                                }
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Network error. Try again.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not check capacity. Try again.", Toast.LENGTH_SHORT).show());
    }

    private static final double WALKIN_CHARGE = 50.0; // ₹50 per unconfirmed walk-in

    /** Returns true if the current time is within the allowed entry window for this meal */
    private boolean isMealTimeValid(String mealType) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        switch (mealType) {
            case "breakfast": return hour >= 6  && hour < 10;
            case "lunch":     return hour >= 11 && hour < 15;
            case "dinner":    return hour >= 18 && hour < 22;
            default: return true;
        }
    }

    private String mealTimeWindow(String mealType) {
        switch (mealType) {
            case "breakfast": return "Breakfast entry: 6:00 AM – 10:00 AM";
            case "lunch":     return "Lunch entry: 11:00 AM – 3:00 PM";
            case "dinner":    return "Dinner entry: 6:00 PM – 10:00 PM";
            default: return "";
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void attemptWalletDeduction(String userId, String mealType, String mealDate) {
        db.collection("wallets").document(userId).get()
                .addOnSuccessListener(snapshot -> {
                    double balance = 0;
                    if (snapshot.exists() && snapshot.getDouble("balance") != null) {
                        balance = snapshot.getDouble("balance");
                    }

                    if (balance >= WALKIN_CHARGE) {
                        // Sufficient balance – deduct atomically
                        db.collection("wallets").document(userId)
                                .update(
                                        "balance", FieldValue.increment(-WALKIN_CHARGE),
                                        "lastUpdated", System.currentTimeMillis()
                                ).addOnSuccessListener(aVoid -> {
                                    logAttendanceAndIncrement(userId, mealType, mealDate, true);
                                    // Log deduction in transaction history
                                    WalletActivity.logTransaction(db, userId,
                                            "deduction", WALKIN_CHARGE,
                                            "Walk-in " + capitalize(mealType) + " • " + mealDate);
                                    Toast.makeText(this,
                                            "💳 Walk-in: ₹50 deducted. Enjoy your " + mealType + "!",
                                            Toast.LENGTH_LONG).show();
                                });
                    } else {
                        // Insufficient balance – deny entry
                        AnimationUtils.errorShake(btnScanQR);
                        Toast.makeText(this,
                                "❌ Insufficient wallet balance (₹" + String.format("%.0f", balance)
                                        + "). Please top up in My Wallet.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not verify wallet. Try again.", Toast.LENGTH_SHORT).show());
    }

    /** Logs attendance AND atomically increments the scanned-in counter */
    private void logAttendanceAndIncrement(String userId, String mealType, String mealDate, boolean isWalkin) {
        // Log the individual attendance record
        Map<String, Object> log = new HashMap<>();
        log.put("userId",    userId);
        log.put("mealType",  mealType);
        log.put("date",      mealDate);
        log.put("isWalkin",  isWalkin);
        log.put("timestamp", System.currentTimeMillis());

        db.collection("scanLogs")
                .document(mealDate)
                .collection(mealType)
                .document(userId)
                .set(log);

        // Atomically increment the capacity counter so the live counter updates in real-time
        // We use SetOptions.merge() so it works even if Admin didn't manually set capacity for today.
        Map<String, Object> incrementData = new HashMap<>();
        incrementData.put(mealType + "_scanned", FieldValue.increment(1));
        incrementData.put("lastUpdated", System.currentTimeMillis());

        db.collection("meal_capacity").document(mealDate)
                .set(incrementData, SetOptions.merge());
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void scheduleMealReminder() {
        Calendar currentDate = Calendar.getInstance();
        Calendar dueDate = Calendar.getInstance();

        // Set Execution around 08:00:00 PM
        dueDate.set(Calendar.HOUR_OF_DAY, 20);
        dueDate.set(Calendar.MINUTE, 0);
        dueDate.set(Calendar.SECOND, 0);

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24);
        }

        long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

        PeriodicWorkRequest dailyWorkRequest = new PeriodicWorkRequest.Builder(
                MealReminderWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork("MealReminderWork", 
                ExistingPeriodicWorkPolicy.KEEP, 
                dailyWorkRequest);
    }

    private void runEntryAnimations() {
        View[] viewsToAnimate = {cardTodaysMenu, cardConfirmTomorrow, btnScanQR, cardRateMeal};
        AnimationUtils.staggerViews(viewsToAnimate, 80);
    }

    private void loadWalletBalance() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("wallets").document(userId).addSnapshotListener((snapshot, error) -> {
            if (snapshot != null && snapshot.exists()) {
                Double bal = snapshot.getDouble("balance");
                int targetBal = (bal != null) ? bal.intValue() : 0;
                
                // Animate balance counting up utilizing AnimationUtils logically
                AnimationUtils.countUpTo(tvWalletBalance, 0, targetBal, 800);
            }
        });
    }

    private void initializeViews() {
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDate = findViewById(R.id.tvDate);
        tvWalletBalance = findViewById(R.id.tvWalletBalance);
        
        rowBreakfast = findViewById(R.id.rowBreakfast);
        rowLunch = findViewById(R.id.rowLunch);
        rowDinner = findViewById(R.id.rowDinner);
        
        cardTodaysMenu = findViewById(R.id.cardTodaysMenu);
        cardConfirmTomorrow = findViewById(R.id.cardConfirmTomorrow);
        cardRateMeal = findViewById(R.id.cardRateMeal);
        
        cgMealType = findViewById(R.id.cgMealType);
        cgStatus = findViewById(R.id.cgStatus);
        
        cgTimeSlot = findViewById(R.id.cgTimeSlot);
        cgRatingMeal = findViewById(R.id.cgRatingMeal);
        ratingBarMeal = findViewById(R.id.ratingBarMeal);
        
        btnSubmitConfirmation = findViewById(R.id.btnSubmitConfirmation);
        btnScanQR = findViewById(R.id.btnScanQR);
        btnSubmitRating = findViewById(R.id.btnSubmitRating);
        
        btnNavHome = findViewById(R.id.btnNavHome);
        btnNavHistory = findViewById(R.id.btnNavHistory);
        btnNavWallet = findViewById(R.id.btnNavWallet);
        btnNavProfile = findViewById(R.id.btnNavProfile);
        
        btnLogout = findViewById(R.id.btnLogout);
        progressBar = findViewById(R.id.progressBar);
        
        if(rowBreakfast != null) {
            ((TextView) rowBreakfast.findViewById(R.id.tvMealName)).setText("Breakfast: Loading...");
            ((TextView) rowBreakfast.findViewById(R.id.tvMealTime)).setText("7 AM - 10 AM");
            ((com.google.android.material.card.MaterialCardView) rowBreakfast.findViewById(R.id.cvMealIcon)).setCardBackgroundColor(android.graphics.Color.parseColor("#F59E0B"));
            ((TextView) rowBreakfast.findViewById(R.id.tvMealEmoji)).setText("☀️");
        }
        if(rowLunch != null) {
            ((TextView) rowLunch.findViewById(R.id.tvMealName)).setText("Lunch: Loading...");
            ((TextView) rowLunch.findViewById(R.id.tvMealTime)).setText("12 PM - 3 PM");
            ((com.google.android.material.card.MaterialCardView) rowLunch.findViewById(R.id.cvMealIcon)).setCardBackgroundColor(android.graphics.Color.parseColor("#10B981"));
            ((TextView) rowLunch.findViewById(R.id.tvMealEmoji)).setText("🌤️");
        }
        if(rowDinner != null) {
            ((TextView) rowDinner.findViewById(R.id.tvMealName)).setText("Dinner: Loading...");
            ((TextView) rowDinner.findViewById(R.id.tvMealTime)).setText("7 PM - 10 PM");
            ((com.google.android.material.card.MaterialCardView) rowDinner.findViewById(R.id.cvMealIcon)).setCardBackgroundColor(android.graphics.Color.parseColor("#8B5CF6"));
            ((TextView) rowDinner.findViewById(R.id.tvMealEmoji)).setText("🌙");
        }
    }



    private void loadGreetingAndDate() {
        String userId = mAuth.getCurrentUser().getUid();

        // Fetch User Name to personalize greeting
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        tvGreeting.setText("Hi, " + name + "!");
                    }
                });

        // Set Tomorrow's Date for confirmation
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        Date tomorrow = calendar.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tomorrow);
        tvDate.setText("Confirming meals for: " + sdf.format(tomorrow));
    }

    private void submitMealConfirmation() {
        // ---- 10 PM Deadline Enforcement ----
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.HOUR_OF_DAY) >= 22) {
            Toast.makeText(this,
                    "⏰ Deadline Passed! Meal confirmations lock at 10:00 PM.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // 1. Get Selected Meal Type (Breakfast/Lunch/Dinner)
        int selectedChipId = cgMealType.getCheckedChipId();
        if (selectedChipId == View.NO_ID) {
            Toast.makeText(this, "Please select a meal (Breakfast, Lunch, or Dinner)", Toast.LENGTH_SHORT).show();
            return;
        }
        Chip selectedChip = findViewById(selectedChipId);
        String mealType = selectedChip.getText().toString().toLowerCase();

        // 2. Get Eat Status
        int selectedStatusId = cgStatus.getCheckedChipId();
        if (selectedStatusId == View.NO_ID) {
            Toast.makeText(this, "Please select if you will eat or skip.", Toast.LENGTH_SHORT).show();
            return;
        }
        String status = (selectedStatusId == R.id.chipEat) ? "eat" : "not_eat";

        // 3. Get Time Slot
        int timeSlotId = cgTimeSlot.getCheckedChipId();
        if (status.equals("eat") && timeSlotId == View.NO_ID) {
            Toast.makeText(this, "Please select an eating time slot.", Toast.LENGTH_SHORT).show();
            return;
        }
        String timeSlot = status.equals("eat") ? ((Chip) findViewById(timeSlotId)).getText().toString() : "Skipped";

        progressBar.setVisibility(View.VISIBLE);
        btnSubmitConfirmation.setEnabled(false);

        // Build Confirmation Object
        String userId = mAuth.getCurrentUser().getUid();
        Confirmation confirmation = new Confirmation(userId, status, timeSlot, System.currentTimeMillis());

        // Save to Database Path: confirmations / date / mealType / userId
        db.collection("confirmations")
                .document(currentDate)
                .collection(mealType)
                .document(userId)
                .set(confirmation);

        progressBar.setVisibility(View.GONE);
        btnSubmitConfirmation.setEnabled(true);
        Toast.makeText(MainActivity.this, "Meal Confirmed Successfully!", Toast.LENGTH_SHORT).show();
    }

    private void loadTodaysMenu() {
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        
        db.collection("weekly_menu").document(todayDate).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String b = documentSnapshot.getString("breakfast");
                        String l = documentSnapshot.getString("lunch");
                        String d = documentSnapshot.getString("dinner");
                        
                        if (rowBreakfast != null) {
                            ((TextView) rowBreakfast.findViewById(R.id.tvMealName)).setText(b != null && !b.isEmpty() ? b : "Not Set");
                        }
                        if (rowLunch != null) {
                            ((TextView) rowLunch.findViewById(R.id.tvMealName)).setText(l != null && !l.isEmpty() ? l : "Not Set");
                        }
                        if (rowDinner != null) {
                            ((TextView) rowDinner.findViewById(R.id.tvMealName)).setText(d != null && !d.isEmpty() ? d : "Not Set");
                        }
                    } else {
                        if (rowBreakfast != null) ((TextView) rowBreakfast.findViewById(R.id.tvMealName)).setText("Not uploaded yet");
                        if (rowLunch != null) ((TextView) rowLunch.findViewById(R.id.tvMealName)).setText("Not uploaded yet");
                        if (rowDinner != null) ((TextView) rowDinner.findViewById(R.id.tvMealName)).setText("Not uploaded yet");
                    }
                });
    }

    private void submitMealRating() {
        int mealId = cgRatingMeal.getCheckedChipId();
        if (mealId == View.NO_ID) {
            Toast.makeText(this, "Select a meal to rate.", Toast.LENGTH_SHORT).show();
            return;
        }
        String mealType = ((Chip) findViewById(mealId)).getText().toString().toLowerCase();
        float rating = ratingBarMeal.getRating();
        String userId = mAuth.getCurrentUser().getUid();
        
        // Save rating for 'today'
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        
        progressBar.setVisibility(View.VISIBLE);
        btnSubmitRating.setEnabled(false);

        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("userId", userId);
        ratingData.put("mealType", mealType);
        ratingData.put("rating", rating);
        ratingData.put("date", todayDate);
        ratingData.put("timestamp", System.currentTimeMillis());

        db.collection("meal_ratings")
                .document(todayDate)
                .collection(mealType)
                .document(userId) // One rating per user per meal type per day
                .set(ratingData)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    btnSubmitRating.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Rating Submitted! Thank you.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSubmitRating.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Failed to submit rating.", Toast.LENGTH_SHORT).show();
                });
    }
}