package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotRouteCheckItemDto(
        UUID id,
        LocalTime checkedAt,
        String checkerLabel,
        String display
) {
}
