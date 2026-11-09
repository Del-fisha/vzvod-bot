package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotShiftUpsertRequestDto(
        LocalDate date,
        String routeId,
        String typeOfShiftId,
        LocalTime startTime,
        LocalTime endTime,
        UUID partnerServiceInfoId,
        Integer countOfStatements,
        Integer countOfClaims,
        Integer ibdr,
        Integer migrant
) {
}
