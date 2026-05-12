package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotShiftItemDto(
        UUID id,
        LocalDate date,
        String route,
        String shiftType,
        String departmentToday,
        LocalTime startTime,
        LocalTime endTime
) {
}
