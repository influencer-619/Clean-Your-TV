package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * PIN-protected daily watch limit. Choosing 30 minutes to 4 hours stores that length for every day.
 * The countdown runs only while the TV screen is on: it pauses in standby and resumes with the
 * leftover time when the screen comes back. At midnight a fresh allotment of the same length starts.
 * Unlock after lock frees the rest of today only; tomorrow the limit applies again. Off until the
 * owner picks a length.
 */
final class WatchTimer {
    static final String PREFS = "watch_timer";
    static final String PIN = "pin";
    static final String RECOVERY = "recovery";
    static final String UNTIL = "until";
    static final String DAILY_MINUTES = "daily_minutes";
    static final String DAY_KEY = "day_key";
    static final String UNLOCKED_DAY = "unlocked_day";
    /** Milliseconds of watch time left today while paused, or last synced while counting. */
    static final String REMAINING = "remaining_ms";
    /** elapsedRealtime() deadline for the current boot; ignored after a reboot. */
    static final String ELAPSED_END = "elapsed_end";
    /** elapsedRealtime() when the timer was armed; after a reboot this is higher than now. */
    static final String ELAPSED_AT_SET = "elapsed_at_set";
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

    /**
     * Same-boot sessions use elapsedRealtime so an NTP clock jump cannot end the timer early.
     * After a reboot, elapsedAtSet is in the future relative to the new elapsed clock, so wall time
     * is used instead.
     */
    static boolean locked(long wallNow, long wallUntil, long elapsedNow, long elapsedEnd, long elapsedAtSet) {
        if (wallUntil <= 0) return false;
        if (sameBoot(elapsedNow, elapsedEnd, elapsedAtSet)) return elapsedNow >= elapsedEnd;
        return wallNow >= wallUntil;
    }

    static boolean running(long now, long until) {
        return until > now;
    }

    static boolean running(long wallNow, long wallUntil, long elapsedNow, long elapsedEnd, long elapsedAtSet) {
        if (wallUntil <= 0) return false;
        if (sameBoot(elapsedNow, elapsedEnd, elapsedAtSet)) return elapsedNow < elapsedEnd;
        return wallNow < wallUntil;
    }

    static boolean sameBoot(long elapsedNow, long elapsedEnd, long elapsedAtSet) {
        return elapsedEnd > 0 && elapsedAtSet >= 0 && elapsedNow >= elapsedAtSet;
    }

    static long start(long now, int minutes) {
        return minutes <= 0 ? 0L : now + minutes * 60_000L;
    }

    static long leftMinutes(long now, long until) {
        if (until <= now) return 0;
        return (until - now + 59_999L) / 60_000L;
    }

    static long leftMinutes(long wallNow, long wallUntil, long elapsedNow, long elapsedEnd, long elapsedAtSet) {
        if (sameBoot(elapsedNow, elapsedEnd, elapsedAtSet)) {
            return leftMinutes(elapsedNow, elapsedEnd);
        }
        return leftMinutes(wallNow, wallUntil);
    }

    /** Milliseconds left: active elapsed countdown when same-boot, else the paused remaining budget. */
    static long leftMs(long remaining, long elapsedNow, long elapsedEnd, long elapsedAtSet) {
        if (sameBoot(elapsedNow, elapsedEnd, elapsedAtSet)) return Math.max(0L, elapsedEnd - elapsedNow);
        return Math.max(0L, remaining);
    }

    /** Legacy wall-clock helper kept for older call sites and tests. */
    static long leftMs(long wallNow, long wallUntil, long elapsedNow, long elapsedEnd, long elapsedAtSet) {
        if (wallUntil <= 0) return 0;
        if (sameBoot(elapsedNow, elapsedEnd, elapsedAtSet)) return Math.max(0L, elapsedEnd - elapsedNow);
        return Math.max(0L, wallUntil - wallNow);
    }

    static String dayKey(long wallNow, TimeZone zone) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setTimeZone(zone);
        return fmt.format(new Date(wallNow));
    }

    static boolean needsDayRoll(String today, String storedDay, int dailyMinutes) {
        return dailyMinutes > 0 && (storedDay == null || storedDay.isEmpty() || !today.equals(storedDay));
    }

    /** Milliseconds until the next local midnight; at least 1 so a postDelayed always wakes later. */
    static long msUntilNextDay(long wallNow, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(wallNow);
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return Math.max(1L, c.getTimeInMillis() - wallNow);
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

    /** D-pad and OK must reach the lock overlay; other keys are still eaten while locked. */
    static boolean lockAllowsKey(int keyCode) {
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_DPAD_UP:
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
            case android.view.KeyEvent.KEYCODE_NUMPAD_ENTER:
            case android.view.KeyEvent.KEYCODE_BACK:
            case android.view.KeyEvent.KEYCODE_ESCAPE:
            case android.view.KeyEvent.KEYCODE_POWER:
            case android.view.KeyEvent.KEYCODE_WAKEUP:
                return true;
            default:
                return false;
        }
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean screenOn(Context ctx) {
        PowerManager power = ctx.getSystemService(PowerManager.class);
        return power != null && power.isInteractive();
    }

    static int dailyMinutes(Context ctx) {
        return prefs(ctx).getInt(DAILY_MINUTES, 0);
    }

    static long until(Context ctx) {
        return prefs(ctx).getLong(UNTIL, 0L);
    }

    /** Remaining budget: prefs key, or a one-time migrate from the old wall-clock until. */
    static long storedRemaining(SharedPreferences p) {
        if (p.contains(REMAINING)) return Math.max(0L, p.getLong(REMAINING, 0L));
        long until = p.getLong(UNTIL, 0L);
        if (until <= 0) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    /** If the calendar day changed and a daily length is set, start a fresh allotment. */
    static void ensureDay(Context ctx) {
        SharedPreferences p = prefs(ctx);
        int daily = p.getInt(DAILY_MINUTES, 0);
        if (daily <= 0) return;
        long now = System.currentTimeMillis();
        String today = dayKey(now, TimeZone.getDefault());
        if (!needsDayRoll(today, p.getString(DAY_KEY, ""), daily)) return;
        p.edit().putString(DAY_KEY, today).remove(UNLOCKED_DAY).apply();
        writeBudget(p, daily * 60_000L, screenOn(ctx));
    }

    static boolean unlockedToday(Context ctx) {
        long now = System.currentTimeMillis();
        String today = dayKey(now, TimeZone.getDefault());
        return today.equals(prefs(ctx).getString(UNLOCKED_DAY, ""));
    }

    static long leftMs(Context ctx) {
        SharedPreferences p = prefs(ctx);
        return leftMs(storedRemaining(p), SystemClock.elapsedRealtime(),
                p.getLong(ELAPSED_END, 0L), p.getLong(ELAPSED_AT_SET, -1L));
    }

    static boolean locked(Context ctx) {
        ensureDay(ctx);
        if (unlockedToday(ctx) || dailyMinutes(ctx) <= 0) return false;
        return leftMs(ctx) <= 0;
    }

    static boolean running(Context ctx) {
        ensureDay(ctx);
        if (unlockedToday(ctx) || dailyMinutes(ctx) <= 0) return false;
        return leftMs(ctx) > 0;
    }

    static long leftMinutes(Context ctx) {
        ensureDay(ctx);
        if (unlockedToday(ctx)) return 0;
        long left = leftMs(ctx);
        if (left <= 0) return 0;
        return (left + 59_999L) / 60_000L;
    }

    static boolean needsWindows(Context ctx) {
        return locked(ctx) || running(ctx) || dailyMinutes(ctx) > 0;
    }

    /** Turns the daily limit on (minutes &gt; 0) or off (0). */
    static void setDailyMinutes(Context ctx, int minutes) {
        SharedPreferences p = prefs(ctx);
        if (minutes <= 0) {
            p.edit().putInt(DAILY_MINUTES, 0).remove(DAY_KEY).remove(UNLOCKED_DAY).apply();
            writeBudget(p, 0L, false);
            return;
        }
        long now = System.currentTimeMillis();
        String today = dayKey(now, TimeZone.getDefault());
        p.edit().putInt(DAILY_MINUTES, minutes).putString(DAY_KEY, today).remove(UNLOCKED_DAY).apply();
        writeBudget(p, minutes * 60_000L, screenOn(ctx));
    }

    static void setUntil(Context ctx, long until) {
        long left = until <= 0 ? 0L : Math.max(0L, until - System.currentTimeMillis());
        writeBudget(prefs(ctx), left, left > 0 && screenOn(ctx));
    }

    /** Freeze the leftover budget; standby must not burn watch time. */
    static void pause(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (p.getInt(DAILY_MINUTES, 0) <= 0 || unlockedToday(ctx)) return;
        long left = leftMs(ctx);
        writeBudget(p, left, false);
    }

    /** Continue from the leftover budget when the screen is on. */
    static void resume(Context ctx) {
        ensureDay(ctx);
        if (dailyMinutes(ctx) <= 0 || unlockedToday(ctx) || !screenOn(ctx)) return;
        SharedPreferences p = prefs(ctx);
        long elapsedNow = SystemClock.elapsedRealtime();
        if (sameBoot(elapsedNow, p.getLong(ELAPSED_END, 0L), p.getLong(ELAPSED_AT_SET, -1L))) return;
        writeBudget(p, storedRemaining(p), true);
    }

    /** Parent unlock after time is up: free for the rest of today; midnight starts a new day. */
    static void unlockToday(Context ctx) {
        ensureDay(ctx);
        long now = System.currentTimeMillis();
        String today = dayKey(now, TimeZone.getDefault());
        SharedPreferences p = prefs(ctx);
        SharedPreferences.Editor edit = p.edit().putString(UNLOCKED_DAY, today);
        if (p.getInt(DAILY_MINUTES, 0) <= 0) edit.putInt(DAILY_MINUTES, 0);
        edit.apply();
        writeBudget(p, 0L, false);
    }

    private static void writeBudget(SharedPreferences p, long remainingMs, boolean counting) {
        long rem = Math.max(0L, remainingMs);
        SharedPreferences.Editor edit = p.edit().putLong(REMAINING, rem);
        if (counting && rem > 0) {
            long elapsed = SystemClock.elapsedRealtime();
            edit.putLong(ELAPSED_END, elapsed + rem).putLong(ELAPSED_AT_SET, elapsed)
                    .putLong(UNTIL, System.currentTimeMillis() + rem);
        } else {
            edit.putLong(UNTIL, 0L).remove(ELAPSED_END).remove(ELAPSED_AT_SET);
        }
        edit.apply();
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
        prefs(ctx).edit().remove(PIN).remove(RECOVERY)
                .putInt(DAILY_MINUTES, 0).remove(DAY_KEY).remove(UNLOCKED_DAY)
                .putLong(REMAINING, 0L).putLong(UNTIL, 0L).remove(ELAPSED_END).remove(ELAPSED_AT_SET).apply();
    }

    /**
     * Schedules {@code tick} for lock expiry (only while the screen is on) and for local midnight.
     * While paused in standby, only midnight is scheduled so a new day can start when the TV wakes.
     */
    static void arm(Context ctx, Handler handler, Runnable tick) {
        handler.removeCallbacks(tick);
        ensureDay(ctx);
        int daily = dailyMinutes(ctx);
        if (daily <= 0) return;
        long now = System.currentTimeMillis();
        long untilMidnight = msUntilNextDay(now, TimeZone.getDefault());
        if (unlockedToday(ctx)) {
            handler.postDelayed(tick, untilMidnight);
            return;
        }
        if (!screenOn(ctx)) {
            handler.postDelayed(tick, untilMidnight);
            return;
        }
        resume(ctx);
        long left = leftMs(ctx);
        if (left > 0) handler.postDelayed(tick, Math.min(left, untilMidnight));
        else handler.postDelayed(tick, untilMidnight);
    }
}
