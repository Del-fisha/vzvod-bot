package com.company.shtopor.vzvodbot.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ShiftDraftSession: смена без напарника")
class ShiftDraftSessionCheckingPartnerTest {

    @Test
    void readyWithoutPartnerWhenChecking() {
        ShiftDraftSession s = new ShiftDraftSession();
        s.setDate(LocalDate.of(2026, 8, 10));
        s.setRouteId("Другое");
        s.setTypeOfShiftId("CHECKING");
        s.setStartTime(LocalTime.of(9, 0));
        assertTrue(s.isReadyToSubmit());
    }

    @Test
    void readyWithoutPartnerWhenRegular() {
        ShiftDraftSession s = new ShiftDraftSession();
        s.setDate(LocalDate.of(2026, 8, 10));
        s.setRouteId("МП 28");
        s.setTypeOfShiftId("VZVOD_ROUTE");
        s.setStartTime(LocalTime.of(9, 0));
        assertTrue(s.isReadyToSubmit());
    }
}
