package com.company.shtopor.vzvodbot.telegram;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RouteCheckEditHandlerTest {

    @Test
    void parseCheckedAt_acceptsHourAndMinute() {
        assertEquals(LocalTime.of(9, 5), RouteCheckEditHandler.parseCheckedAt("9:05"));
        assertEquals(LocalTime.of(21, 14), RouteCheckEditHandler.parseCheckedAt("21:14"));
        assertNull(RouteCheckEditHandler.parseCheckedAt("24:00"));
        assertNull(RouteCheckEditHandler.parseCheckedAt("10:60"));
        assertNull(RouteCheckEditHandler.parseCheckedAt("десять"));
    }
}
