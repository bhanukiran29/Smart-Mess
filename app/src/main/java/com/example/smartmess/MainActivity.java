package com.example.smartmess;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
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

import com.example.smartmess.models.ConfirmationRecord;
import com.example.smartmess.repository.MealHistoryRepository;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

    private TextView tvGreeting, tvDate, tvConfirmationStatus;
    private View rowBreakfast, rowLunch, rowDinner;
    private TextView tvWalletBalance;
    private ChipGroup cgMealType, cgStatus, cgTimeSlot, cgRatingMeal;
    
    // Track selected meals and time slots for multi-selection
    private Set<String> selectedMeals = new HashSet<>();
    private Map<String, String> selectedTimeSlotsByMeal = new HashMap<>(); // meal -> selected time slot
    private RatingBar ratingBarMeal;
    private MaterialButton btnSubmitConfirmation, btnScanQR;
    private TextView btnSubmitRating;
    private LinearLayout btnNavHome, btnNavHistory, btnNavWallet, btnNavProfile;
    private ProgressBar progressBar;
    private View cardTodaysMenu, cardConfirmTomorrow, cardRateMeal;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private MealHistoryRepository mealHistoryRepository;
    private String currentDate;

    // Keep a reference to MealHistoryActivity if it is open so we can push
    // new confirmations to it in real-time without requiring a screen reopen.
    private MealHistoryActivity openHistoryActivity;

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
        mealHistoryRepository = new MealHistoryRepository(db);

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

        btnSubmitConfirmation.setOnClickListener(v -> submitMealConfirmation());
        btnSubmitRating.setOnClickListener(v -> submitMealRating());

        // Set up meal type chips with multi-selection and green color
        setupMealTypeChips();
        
        // Set up time slot chips (will be populated dynamically)
        setupTimeSlotChips();
        
        // Initialization binding slots dynamically
        updateTimeSlots();

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
            case "breakfast": return hour >= 7  && hour < 11;
            case "lunch":     return hour >= 11 && hour < 16;
            case "snacks":    return hour >= 16 && hour < 19;
            case "dinner":    return hour >= 19 && hour < 22;
            default: return true;
        }
    }

    private String mealTimeWindow(String mealType) {
        switch (mealType) {
            case "breakfast": return "Breakfast entry: 7:00 AM – 11:00 AM";
            case "lunch":     return "Lunch entry: 11:00 AM – 4:00 PM";
            case "snacks":    return "Snacks entry: 4:00 PM – 7:00 PM";
            case "dinner":    return "Dinner entry: 7:00 PM – 10:00 PM";
            default: return "Unknown Entry window limit exception.";
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
        tvConfirmationStatus = findViewById(R.id.tvConfirmationStatus);
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

        // 1. Get Selected Meal Type
        int selectedChipId = cgMealType.getCheckedChipId();
        if (selectedChipId == View.NO_ID) {
            Toast.makeText(this, "Please select a meal (Breakfast, Lunch, Snacks or Dinner)", Toast.LENGTH_SHORT).show();
            return;
        }
        Chip selectedChip = findViewById(selectedChipId);
        String mealType = selectedChip.getText().toString().toLowerCase();

        // 2. Get Eat/Skip Status
        int selectedStatusId = cgStatus.getCheckedChipId();
        if (selectedStatusId == View.NO_ID) {
            Toast.makeText(this, "Please select if you will eat or skip.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean isEating = (selectedStatusId == R.id.chipEat);
        String status = isEating ? "eat" : "not_eat";

        // 3. Get Time Slot (required only when eating)
        int timeSlotId = cgTimeSlot.getCheckedChipId();
        if (isEating && timeSlotId == View.NO_ID) {
            Toast.makeText(this, "Please select an eating time slot.", Toast.LENGTH_SHORT).show();
            return;
        }
        String timeSlot = isEating
                ? ((Chip) findViewById(timeSlotId)).getText().toString()
                : "Skipping";

        progressBar.setVisibility(View.VISIBLE);
        btnSubmitConfirmation.setEnabled(false);

        String userId = mAuth.getCurrentUser().getUid();

        // Dual-write via repository:
        //   Write 1 → confirmations/{date}/{meal}/{uid}       (existing shared structure)
        //   Write 2 → user_confirmations/{uid}/records/{key}  (new user-side history)
        mealHistoryRepository.saveConfirmation(
                userId, currentDate, mealType, status, timeSlot,
                new MealHistoryRepository.SaveCallback() {
                    @Override
                    public void onSuccess(ConfirmationRecord saved) {
                        progressBar.setVisibility(View.GONE);
                        btnSubmitConfirmation.setEnabled(true);

                        // Build a human-readable success message
                        String mealLabel = capitalize(mealType);
                        String action    = isEating ? "booked" : "skipped";
                        String slotPart  = isEating ? " · " + timeSlot : "";
                        String msg = "Tomorrow's " + mealLabel + " " + action + " successfully" + slotPart;
                        Toast.makeText(MainActivity.this, "✅ " + msg, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        progressBar.setVisibility(View.GONE);
                        btnSubmitConfirmation.setEnabled(true);
                        Toast.makeText(MainActivity.this,
                                "Failed to save confirmation. Try again.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
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

    private void updateTimeSlots() {
        cgTimeSlot.removeAllViews();
        
        // Only show time slots if meals are selected
        if (selectedMeals.isEmpty()) {
            // Show helper text when no meals are selected
            TextView helperText = new TextView(MainActivity.this);
            helperText.setText("Select meals above to see available time slots");
            helperText.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
            helperText.setTextSize(14);
            helperText.setPadding(16, 24, 16, 24);
            helperText.setGravity(android.view.Gravity.CENTER);
            cgTimeSlot.addView(helperText);
            return;
        }
        
        // Group time slots by meal type for better organization
        boolean isFirstMeal = true;
        for (String mealType : selectedMeals) {
            String[] slots = getTimeSlotsForMeal(mealType);
            
            // Add spacing between meal groups (except for first)
            if (!isFirstMeal) {
                View spacer = new View(MainActivity.this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 16));
                cgTimeSlot.addView(spacer);
            }
            
            // Add meal label with better styling
            TextView mealLabel = new TextView(MainActivity.this);
            mealLabel.setText(mealType + " Time Slots");
            mealLabel.setTextColor(android.graphics.Color.parseColor("#0F172A"));
            mealLabel.setTextSize(13);
            mealLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            mealLabel.setPadding(4, 8, 4, 8);
            cgTimeSlot.addView(mealLabel);
            
            // Add time slot chips for this meal
            for (String slot : slots) {
                Chip chip = new Chip(MainActivity.this);
                chip.setText(slot);
                chip.setCheckable(true);
                chip.setTag(mealType); // Tag chip with meal type
                
                // Improve chip styling
                chip.setChipCornerRadius(20);
                chip.setChipMinHeight(40);
                chip.setPadding(12, 8, 12, 8);
                
                // Check if this slot is already selected for this meal
                String selectedSlot = selectedTimeSlotsByMeal.get(mealType);
                boolean isSelected = slot.equals(selectedSlot);
                chip.setChecked(isSelected);
                updateTimeSlotChipAppearance(chip, isSelected);
                
                // Disable other slots if one is already selected for this meal
                if (selectedSlot != null && !isSelected) {
                    chip.setEnabled(false);
                    chip.setAlpha(0.5f); // Make disabled chips semi-transparent
                } else {
                    chip.setEnabled(true);
                    chip.setAlpha(1.0f);
                }
                
                // Set up click listener with meal-specific logic
                chip.setOnClickListener(v -> {
                    Chip clickedChip = (Chip) v;
                    String chipMealType = (String) clickedChip.getTag();
                    String slotText = clickedChip.getText().toString();
                    
                    // Only allow clicks on enabled chips
                    if (!clickedChip.isEnabled()) {
                        return;
                    }
                    
                    // Check if this slot is already selected for this meal
                    String currentSelectedSlot = selectedTimeSlotsByMeal.get(chipMealType);
                    
                    if (slotText.equals(currentSelectedSlot)) {
                        // Deselect current slot and re-enable all slots for this meal
                        selectedTimeSlotsByMeal.remove(chipMealType);
                        
                        // Re-enable all chips for this meal
                        for (int i = 0; i < cgTimeSlot.getChildCount(); i++) {
                            View child = cgTimeSlot.getChildAt(i);
                            if (child instanceof Chip) {
                                Chip otherChip = (Chip) child;
                                String otherChipMealType = (String) otherChip.getTag();
                                if (chipMealType.equals(otherChipMealType)) {
                                    otherChip.setEnabled(true);
                                    otherChip.setAlpha(1.0f);
                                    otherChip.setChecked(false);
                                    updateTimeSlotChipAppearance(otherChip, false);
                                }
                            }
                        }
                    } else {
                        // Select new slot and disable others for this meal
                        selectedTimeSlotsByMeal.put(chipMealType, slotText);
                        updateTimeSlotChipsForMeal(chipMealType);
                    }
                });
                
                cgTimeSlot.addView(chip);
            }
            isFirstMeal = false;
        }
    }
    
    private void updateTimeSlotChipsForMeal(String mealType) {
        String selectedSlot = selectedTimeSlotsByMeal.get(mealType);
        
        // Update all chips in the ChipGroup
        for (int i = 0; i < cgTimeSlot.getChildCount(); i++) {
            View child = cgTimeSlot.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                String chipMealType = (String) chip.getTag();
                
                if (mealType.equals(chipMealType)) {
                    String chipSlot = chip.getText().toString();
                    boolean shouldBeSelected = chipSlot.equals(selectedSlot);
                    
                    chip.setChecked(shouldBeSelected);
                    updateTimeSlotChipAppearance(chip, shouldBeSelected);
                    
                    // Disable other slots for this meal if one is already selected
                    if (selectedSlot != null && !shouldBeSelected) {
                        chip.setEnabled(false);
                        chip.setAlpha(0.5f); // Make disabled chips semi-transparent
                    } else {
                        chip.setEnabled(true);
                        chip.setAlpha(1.0f);
                    }
                }
            }
        }
    }
    
    private String[] getTimeSlotsForMeal(String mealType) {
        switch (mealType.toLowerCase()) {
            case "breakfast":
                return new String[]{"7:00 AM - 9:00 AM", "9:00 AM - 11:00 AM"};
            case "lunch":
                return new String[]{"11:00 AM - 1:30 PM", "1:30 PM - 4:00 PM"};
            case "snacks":
                return new String[]{"4:00 PM - 7:00 PM"};
            case "dinner":
                return new String[]{"7:00 PM - 8:30 PM", "8:30 PM - 10:00 PM"};
            default:
                return new String[]{};
        }
    }
    
    private void setupMealTypeChips() {
        // Set up each meal chip with custom click behavior
        setupMealChip(R.id.chipBreakfast, "Breakfast");
        setupMealChip(R.id.chipLunch, "Lunch");
        setupMealChip(R.id.chipSnacks, "Snacks");
        setupMealChip(R.id.chipDinner, "Dinner");
    }
    
    private void setupMealChip(int chipId, String mealType) {
        Chip chip = findViewById(chipId);
        if (chip != null) {
            // Ensure chip is checkable and clear any default styling
            chip.setCheckable(true);
            chip.setChecked(false);
            
            chip.setOnClickListener(v -> {
                Chip clickedChip = (Chip) v;
                
                if (selectedMeals.contains(mealType)) {
                    // Deselect meal and clear its time slot
                    selectedMeals.remove(mealType);
                    selectedTimeSlotsByMeal.remove(mealType);
                    clickedChip.setChecked(false);
                    updateMealChipAppearance(clickedChip, false);
                } else {
                    // Select meal
                    selectedMeals.add(mealType);
                    clickedChip.setChecked(true);
                    updateMealChipAppearance(clickedChip, true);
                }
                
                // Update time slots based on new selection
                updateTimeSlots();
            });
            
            // Set initial appearance
            updateMealChipAppearance(chip, false);
        }
    }
    
    private void setupTimeSlotChips() {
        // Time slots will be set up dynamically in updateTimeSlots()
    }
    
    private void updateMealChipAppearance(Chip chip, boolean isSelected) {
        android.util.Log.d("ChipColor", "Updating meal chip: " + chip.getText() + ", selected: " + isSelected);
        if (isSelected) {
            // Set green background for selected state
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#4CAF50")));
            chip.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            chip.setChipStrokeWidth(0);
            android.util.Log.d("ChipColor", "Set green color for: " + chip.getText());
        } else {
            // Set light gray background for unselected state
            if (chip.isEnabled()) {
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F1F5F9")));
                chip.setTextColor(android.graphics.Color.parseColor("#64748B"));
            } else {
                // Disabled state - darker gray
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#E2E8F0")));
                chip.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
            }
            chip.setChipStrokeWidth(0);
            android.util.Log.d("ChipColor", "Set gray color for: " + chip.getText());
        }
    }
    
    private void updateTimeSlotChipAppearance(Chip chip, boolean isSelected) {
        android.util.Log.d("ChipColor", "Updating time slot chip: " + chip.getText() + ", selected: " + isSelected);
        if (isSelected) {
            // Set green background for selected state
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#4CAF50")));
            chip.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            chip.setChipStrokeWidth(0);
        } else {
            // Set light gray background for unselected state
            if (chip.isEnabled()) {
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F1F5F9")));
                chip.setTextColor(android.graphics.Color.parseColor("#64748B"));
            } else {
                // Disabled state - darker gray
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#E2E8F0")));
                chip.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
            }
            chip.setChipStrokeWidth(0);
        }
    }
}