package com.vslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public final class TimeQueryActionsTest {
    @Test public void parsesCompactAndCompoundTimers() {
        TimeQueryActions.TimerSpec ten = TimeQueryActions.timer("timer 10m");
        assertNotNull(ten);
        assertEquals(600, ten.seconds);
        assertEquals("10m", ten.display);

        TimeQueryActions.TimerSpec compound = TimeQueryActions.timer("timer 1h 30m 5s");
        assertNotNull(compound);
        assertEquals(5405, compound.seconds);
        assertEquals("1h 30m 5s", compound.display);
    }

    @Test public void rejectsUnsafeOrAmbiguousTimers() {
        assertNull(TimeQueryActions.timer("10m"));
        assertNull(TimeQueryActions.timer("timer tomorrow"));
        assertNull(TimeQueryActions.timer("timer 25h"));
        assertNull(TimeQueryActions.timer("timer 0m"));
    }

    @Test public void parsesExplicitTwentyFourHourAlarm() {
        TimeQueryActions.AlarmSpec alarm = TimeQueryActions.alarm("alarm 07:30");
        assertNotNull(alarm);
        assertEquals(7, alarm.hour);
        assertEquals(30, alarm.minute);
        assertEquals("07:30", alarm.display);

        TimeQueryActions.AlarmSpec setAlarm = TimeQueryActions.alarm("set alarm 18.45");
        assertNotNull(setAlarm);
        assertEquals(18, setAlarm.hour);
        assertEquals(45, setAlarm.minute);
    }

    @Test public void rejectsIncompleteOrInvalidAlarms() {
        assertNull(TimeQueryActions.alarm("alarm 7"));
        assertNull(TimeQueryActions.alarm("alarm 24:00"));
        assertNull(TimeQueryActions.alarm("alarm 12:60"));
        assertNull(TimeQueryActions.alarm("07:30"));
    }
}
