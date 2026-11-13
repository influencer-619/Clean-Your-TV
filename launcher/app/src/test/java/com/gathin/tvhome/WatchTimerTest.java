package com.gathin.tvhome;

import org.junit.Test;

import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WatchTimerTest {
    @Test public void offNeverLocks() {
        assertFalse(WatchTimer.locked(1_000L, 0L));
        assertFalse(WatchTimer.running(1_000L, 0L));
    }

    @Test public void beforeTheEndTheSessionIsOpen() {
        assertTrue(WatchTimer.running(1_000L, 2_000L));
        assertFalse(WatchTimer.locked(1_000L, 2_000L));
    }

    @Test public void atTheEndHomeLocks() {
        assertTrue(WatchTimer.locked(2_000L, 2_000L));
        assertFalse(WatchTimer.running(2_000L, 2_000L));
    }

    @Test public void startAddsMinutes() {
        assertEquals(0L, WatchTimer.start(1_000L, 0));
        assertEquals(1_000L + 30 * 60_000L, WatchTimer.start(1_000L, 30));
    }

    @Test public void leftMinutesRoundsUp() {
        assertEquals(0L, WatchTimer.leftMinutes(2_000L, 2_000L));
        assertEquals(1L, WatchTimer.leftMinutes(1_000L, 1_000L + 1));
        assertEquals(2L, WatchTimer.leftMinutes(0L, 60_001L));
    }

    @Test public void pinMustBeFourDigits() {
        assertTrue(WatchTimer.fourDigits("1234"));
        assertFalse(WatchTimer.fourDigits("12"));
        assertFalse(WatchTimer.fourDigits("12a4"));
        assertFalse(WatchTimer.fourDigits(null));
    }

    @Test public void recoveryMustBeSixDigits() {
        assertTrue(WatchTimer.sixDigits("258014"));
        assertFalse(WatchTimer.sixDigits("2580"));
        assertFalse(WatchTimer.sixDigits("25801a"));
        assertFalse(WatchTimer.sixDigits(null));
    }

    @Test public void hashMatchesOnlyTheSamePin() {
        String stored = WatchTimer.hash("2580");
        assertTrue(WatchTimer.pinReady(stored));
        assertTrue(WatchTimer.pinOk(stored, "2580"));
        assertFalse(WatchTimer.pinOk(stored, "2581"));
        assertFalse(WatchTimer.pinOk("", "2580"));
    }

    @Test public void lockAllowsHomeAndThePowerDialog() {
        assertTrue(WatchTimer.allowedWhileLocked("com.gathin.tvhome", "com.gathin.tvhome"));
        assertTrue(WatchTimer.allowedWhileLocked("com.android.systemui", "com.gathin.tvhome"));
        assertFalse(WatchTimer.allowedWhileLocked("com.google.android.youtube.tv", "com.gathin.tvhome"));
    }

    @Test public void sameBootIgnoresAWallClockJumpForward() {
        long elapsedAtSet = 1_000L;
        long elapsedEnd = elapsedAtSet + 3 * 60 * 60_000L;
        long wallUntil = 1_000L + 3 * 60 * 60_000L;
        assertFalse(WatchTimer.locked(wallUntil + 10_000_000L, wallUntil,
                elapsedAtSet + 60_000L, elapsedEnd, elapsedAtSet));
        assertTrue(WatchTimer.running(wallUntil + 10_000_000L, wallUntil,
                elapsedAtSet + 60_000L, elapsedEnd, elapsedAtSet));
        assertTrue(WatchTimer.locked(wallUntil + 10_000_000L, wallUntil,
                elapsedEnd, elapsedEnd, elapsedAtSet));
    }

    @Test public void afterRebootWallClockIsUsed() {
        long wallUntil = 5_000L;
        assertTrue(WatchTimer.locked(5_000L, wallUntil, 100L, 9_000_000L, 8_000_000L));
        assertFalse(WatchTimer.locked(4_000L, wallUntil, 100L, 9_000_000L, 8_000_000L));
    }

    @Test public void lockPassesDpadAndBlocksAppKeys() {
        assertTrue(WatchTimer.lockAllowsKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER));
        assertTrue(WatchTimer.lockAllowsKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN));
        assertTrue(WatchTimer.lockAllowsKey(android.view.KeyEvent.KEYCODE_BACK));
        assertTrue(WatchTimer.lockAllowsKey(android.view.KeyEvent.KEYCODE_POWER));
        assertFalse(WatchTimer.lockAllowsKey(android.view.KeyEvent.KEYCODE_HOME));
        assertFalse(WatchTimer.lockAllowsKey(android.view.KeyEvent.KEYCODE_ALL_APPS));
    }

    @Test public void dayRollWhenTheCalendarDayChanges() {
        assertTrue(WatchTimer.needsDayRoll("2026-11-13", "2026-11-12", 180));
        assertFalse(WatchTimer.needsDayRoll("2026-11-12", "2026-11-12", 180));
        assertFalse(WatchTimer.needsDayRoll("2026-11-13", "2026-11-12", 0));
        assertTrue(WatchTimer.needsDayRoll("2026-11-12", "", 30));
    }

    @Test public void dayKeyUsesTheGivenTimeZone() {
        java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(2026, java.util.Calendar.NOVEMBER, 12, 20, 0, 0);
        long t = c.getTimeInMillis();
        assertEquals("2026-11-12", WatchTimer.dayKey(t, TimeZone.getTimeZone("UTC")));
        // 20:00 UTC is 01:30 on the 13th in India.
        assertEquals("2026-11-13", WatchTimer.dayKey(t, TimeZone.getTimeZone("Asia/Kolkata")));
    }

    @Test public void msUntilNextDayIsTheGapToLocalMidnight() {
        java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(2026, java.util.Calendar.NOVEMBER, 12, 23, 0, 0);
        long t = c.getTimeInMillis();
        assertEquals(60 * 60_000L, WatchTimer.msUntilNextDay(t, TimeZone.getTimeZone("UTC")));
    }

    @Test public void leftMsUsesElapsedWhileCountingAndRemainingWhilePaused() {
        long remaining = 30 * 60_000L;
        long at = 1_000L;
        long end = at + remaining;
        assertEquals(remaining - 60_000L, WatchTimer.leftMs(remaining, at + 60_000L, end, at));
        // After reboot (elapsed now below at-set), the paused remaining budget is used.
        assertEquals(remaining, WatchTimer.leftMs(remaining, 50L, end, at));
        assertEquals(0L, WatchTimer.leftMs(0L, 50L, 0L, -1L));
    }
}
