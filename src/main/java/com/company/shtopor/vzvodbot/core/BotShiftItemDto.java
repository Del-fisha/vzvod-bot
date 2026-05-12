package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotShiftItemDto(
        LocalDate date,
        String route,
        String shiftType,
        String departmentToday,
        LocalTime startTime,
        LocalTime endTime
) {
}
