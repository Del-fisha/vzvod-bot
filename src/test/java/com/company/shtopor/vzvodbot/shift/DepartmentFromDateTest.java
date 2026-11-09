package com.company.shtopor.vzvodbot.shift;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DepartmentFromDateTest {

    @Test
    void matchesCoreCycleAroundAnchor() {
        assertEquals(1, DepartmentFromDate.toInt(LocalDate.of(2026, 2, 22)));
        assertEquals(1, DepartmentFromDate.toInt(LocalDate.of(2026, 2, 23)));
        assertEquals(2, DepartmentFromDate.toInt(LocalDate.of(2026, 2, 24)));
        assertEquals(2, DepartmentFromDate.toInt(LocalDate.of(2026, 2, 25)));
        assertEquals(1, DepartmentFromDate.toInt(LocalDate.of(2026, 2, 26)));
    }
}
