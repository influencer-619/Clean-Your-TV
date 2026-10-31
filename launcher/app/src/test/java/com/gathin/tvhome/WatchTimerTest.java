package com.gathin.tvhome;

import org.junit.Test;

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
}
