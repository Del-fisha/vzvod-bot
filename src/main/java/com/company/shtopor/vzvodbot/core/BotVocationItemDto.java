package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotVocationItemDto(
        LocalDate startDate,
        LocalDate endDate,
        Integer countOfDays,
        String typeLabel
) {
}
