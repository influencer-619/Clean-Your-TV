package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** PIN-protected watch limit: after the chosen minutes Home locks and apps are sent back. Off until config. */
final class WatchTimer {
    static final String PREFS = "watch_timer";
    static final String PIN = "pin";
    static final String RECOVERY = "recovery";
    static final String UNTIL = "until";
    /** Off, then 30 minutes through 4 hours. */
    static final int[] MINUTES = {0, 30, 60, 120, 180, 240};

    private WatchTimer() {
    }

    static boolean on(Context ctx) {
        return ctx.getResources().getBoolean(R.bool.watch_timer);
    }

    static boolean locked(long now, long until) {
        return until > 0 && now >= until;
    }

    static boolean running(long now, long until) {
        return until > now;
    }

    static long start(long now, int minutes) {
        return minutes <= 0 ? 0L : now + minutes * 60_000L;
    }

    static long leftMinutes(long now, long until) {
        if (until <= now) return 0;
        return (until - now + 59_999L) / 60_000L;
    }

    static boolean pinOk(String stored, String typed) {
        return stored != null && !stored.isEmpty() && stored.equals(hash(typed));
    }

    static boolean pinReady(String stored) {
        return stored != null && !stored.isEmpty();
    }

    static boolean fourDigits(String pin) {
        return pin != null && pin.length() == 4 && pin.chars().allMatch(Character::isDigit);
    }

    static boolean sixDigits(String pin) {
        return pin != null && pin.length() == 6 && pin.chars().allMatch(Character::isDigit);
    }

    static String hash(String pin) {
        if (pin == null) return "";
        try {
            byte[] raw = MessageDigest.getInstance("SHA-256").digest(pin.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    static boolean allowedWhileLocked(String pkg, String self) {
        return self.equals(pkg) || "com.android.systemui".equals(pkg) || "android".equals(pkg);
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static long until(Context ctx) {
        return prefs(ctx).getLong(UNTIL, 0L);
    }

    static boolean locked(Context ctx) {
        return locked(System.currentTimeMillis(), until(ctx));
    }

    static boolean running(Context ctx) {
        return running(System.currentTimeMillis(), until(ctx));
    }

    static boolean needsWindows(Context ctx) {
        return locked(ctx) || running(ctx);
    }

    static void setUntil(Context ctx, long until) {
        prefs(ctx).edit().putLong(UNTIL, until).apply();
    }

    static void setPin(Context ctx, String pin) {
        prefs(ctx).edit().putString(PIN, hash(pin)).apply();
    }

    static void setRecovery(Context ctx, String pin) {
        prefs(ctx).edit().putString(RECOVERY, hash(pin)).apply();
    }

    static String storedPin(Context ctx) {
        return prefs(ctx).getString(PIN, "");
    }

    static String storedRecovery(Context ctx) {
        return prefs(ctx).getString(RECOVERY, "");
    }

    static void clearSecrets(Context ctx) {
        prefs(ctx).edit().remove(PIN).remove(RECOVERY).putLong(UNTIL, 0L).apply();
    }

    static void arm(Context ctx, Handler handler, Runnable lock) {
        handler.removeCallbacks(lock);
        long left = until(ctx) - System.currentTimeMillis();
        if (left > 0) handler.postDelayed(lock, left);
    }
}
