package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotShiftItemDto(
        UUID id,
        LocalDate date,
        String route,
        String shiftType,
        String typeOfShiftId,
        String departmentToday,
        LocalTime startTime,
        LocalTime endTime,
        Integer countOfStatements,
        Integer countOfClaims,
        Integer ibdr,
        Integer migrant,
        Integer administrativeViolationsCount,
        Integer criminalViolationsCount,
        UUID partnerServiceInfoId,
        List<UUID> otherParticipantServiceInfoIds
) {
}
