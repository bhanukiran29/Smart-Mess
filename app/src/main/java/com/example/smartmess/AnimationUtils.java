package com.example.smartmess;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.recyclerview.widget.RecyclerView;

/**
 * AnimationUtils — drop-in animation helpers for the whole Smart Mess app.
 *
 * Usage examples:
 *   AnimationUtils.staggerRecyclerView(rvUsers, 60);
 *   AnimationUtils.scaleIn(btnSubmit, 0);
 *   AnimationUtils.successPulse(btnSubmit);
 *   AnimationUtils.countUpTo(tvBalance, 0, 350, 800);
 *   AnimationUtils.flipNumber(tvCounter, "46");
 *   AnimationUtils.scanLineSweep(viewScanLine, scanAreaHeight);
 *   AnimationUtils.errorShake(view);
 */
public class AnimationUtils {

    // -------------------------------------------------------------------------
    // 1. STAGGER — slide RecyclerView items in from bottom, one after another
    //    Usage: call after adapter.notifyDataSetChanged() or on first load
    // -------------------------------------------------------------------------
    public static void staggerRecyclerView(RecyclerView rv, int delayBetweenItemsMs) {
        rv.post(() -> {
            int childCount = rv.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = rv.getChildAt(i);
                if (child == null) continue;
                child.setAlpha(0f);
                child.setTranslationY(60f);
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .setStartDelay((long) i * delayBetweenItemsMs)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
            }
        });
    }

    // -------------------------------------------------------------------------
    // 2. SCALE IN — a card or button scales in from 0.85 with overshoot
    //    Usage: call in onViewCreated or after adding a card dynamically
    // -------------------------------------------------------------------------
    public static void scaleIn(View view, long delayMs) {
        view.setAlpha(0f);
        view.setScaleX(0.85f);
        view.setScaleY(0.85f);
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(350)
            .setStartDelay(delayMs)
            .setInterpolator(new OvershootInterpolator(1.5f))
            .start();
    }

    // -------------------------------------------------------------------------
    // 3. STAGGER CARDS — pass an array of views (e.g. KPI cards), each slides up
    //    Usage: AnimationUtils.staggerViews(new View[]{card1, card2, card3}, 80);
    // -------------------------------------------------------------------------
    public static void staggerViews(View[] views, int delayBetweenMs) {
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            v.setAlpha(0f);
            v.setTranslationY(48f);
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(380)
                .setStartDelay((long) i * delayBetweenMs)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
        }
    }

    // -------------------------------------------------------------------------
    // 4. SUCCESS PULSE — button turns green and pulses once, then reverts
    //    Usage: call after a successful Firestore write
    // -------------------------------------------------------------------------
    public static void successPulse(View view, int originalBgResId) {
        // Scale up
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(120)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() ->
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(new OvershootInterpolator(2f))
                    .start()
            )
            .start();
    }

    // -------------------------------------------------------------------------
    // 5. COUNT UP — animates a number from start to end in a TextView
    //    Usage: AnimationUtils.countUpTo(tvBalance, 0, 350, 800);
    //    Formats with "₹" prefix — adjust formatValue() below if needed
    // -------------------------------------------------------------------------
    public static void countUpTo(android.widget.TextView tv, int from, int to, int durationMs) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(durationMs);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(anim -> {
            int value = (int) anim.getAnimatedValue();
            tv.setText("₹" + value);
        });
        animator.start();
    }

    // Variant without currency symbol
    public static void countUpPlain(android.widget.TextView tv, int from, int to, int durationMs) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(durationMs);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(anim -> tv.setText(String.valueOf((int) anim.getAnimatedValue())));
        animator.start();
    }

    // -------------------------------------------------------------------------
    // 6. FLIP NUMBER — the staff live counter flip animation
    //    Old number slides up and fades out; new number comes up from below
    //    Usage: AnimationUtils.flipNumber(tvBreakfastCount, "46");
    // -------------------------------------------------------------------------
    public static void flipNumber(android.widget.TextView tv, String newValue) {
        // Slide old value out upward
        tv.animate()
            .translationY(-tv.getHeight() * 0.6f)
            .alpha(0f)
            .setDuration(180)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                tv.setText(newValue);
                tv.setTranslationY(tv.getHeight() * 0.6f);
                tv.setAlpha(0f);
                // Slide new value in from below
                tv.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
            })
            .start();
    }

    // -------------------------------------------------------------------------
    // 7. SCAN LINE SWEEP — the animated line in the QR scanner viewfinder
    //    Call this once; it loops indefinitely until you cancel the animator
    //    Usage:
    //      ValueAnimator anim = AnimationUtils.scanLineSweep(viewScanLine, 240);
    //      // store anim reference, call anim.cancel() when leaving the screen
    // -------------------------------------------------------------------------
    public static ValueAnimator scanLineSweep(View scanLine, int scanAreaHeightDp) {
        float heightPx = scanAreaHeightDp * scanLine.getContext().getResources().getDisplayMetrics().density;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, heightPx);
        animator.setDuration(1600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(anim ->
            scanLine.setTranslationY((float) anim.getAnimatedValue())
        );
        animator.start();
        return animator;
    }

    // -------------------------------------------------------------------------
    // 8. CORNER BRACKET PULSE — the scanner corner brackets pulse
    //    Usage: pass the root ViewGroup containing corner bracket views
    // -------------------------------------------------------------------------
    public static void cornerBracketPulse(View... corners) {
        for (View corner : corners) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(corner, View.SCALE_X, 1f, 1.08f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(corner, View.SCALE_Y, 1f, 1.08f, 1f);
            AnimatorSet pulse = new AnimatorSet();
            pulse.playTogether(scaleX, scaleY);
            pulse.setDuration(1200);
            pulse.setInterpolator(new DecelerateInterpolator(2f));
            pulse.setStartDelay(200);
            // Repeat manually so it can be cancelled cleanly
            pulse.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    pulse.start();
                }
            });
            pulse.start();
        }
    }

    // -------------------------------------------------------------------------
    // 9. ERROR SHAKE — horizontal shake for invalid QR / insufficient balance
    //    Usage: AnimationUtils.errorShake(view);
    // -------------------------------------------------------------------------
    public static void errorShake(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(
            view, View.TRANSLATION_X,
            0f, -16f, 16f, -12f, 12f, -8f, 8f, -4f, 4f, 0f
        );
        shake.setDuration(500);
        shake.setInterpolator(new DecelerateInterpolator(2f));
        shake.start();
    }

    // -------------------------------------------------------------------------
    // 10. BUTTON PRESS — scale-down on press, spring back on release
    //     Usage in XML: use StateListAnimator OR call these in onTouch listener
    // -------------------------------------------------------------------------
    public static void buttonPressDown(View view) {
        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(80)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    public static void buttonPressRelease(View view) {
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(new OvershootInterpolator(2f))
            .start();
    }

    // -------------------------------------------------------------------------
    // 11. FADE IN VIEW — simple fade, used for empty-state and progress bars
    // -------------------------------------------------------------------------
    public static void fadeIn(View view, int durationMs) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    public static void fadeOut(View view, int durationMs) {
        view.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> view.setVisibility(View.GONE))
            .start();
    }

    // -------------------------------------------------------------------------
    // 12. SLIDE UP BOTTOM SHEET (manual, for custom confirmation overlays)
    //     Usage: AnimationUtils.slideUpReveal(bottomSheetView, parentHeight);
    // -------------------------------------------------------------------------
    public static void slideUpReveal(View view, int parentHeightPx) {
        view.setTranslationY(parentHeightPx);
        view.setVisibility(View.VISIBLE);
        view.animate()
            .translationY(0f)
            .setDuration(380)
            .setInterpolator(new DecelerateInterpolator(2f))
            .start();
    }

    public static void slideDownDismiss(View view, int parentHeightPx) {
        view.animate()
            .translationY(parentHeightPx)
            .setDuration(280)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> view.setVisibility(View.GONE))
            .start();
    }
}
