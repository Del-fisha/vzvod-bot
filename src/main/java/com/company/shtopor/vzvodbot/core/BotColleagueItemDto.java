package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotColleagueItemDto(
        UUID serviceInfoId,
        String label,
        Integer department
) {
}
