package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotCheckableRoutesResponseDto(
        List<BotCheckableRouteItemDto> routes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BotCheckableRouteItemDto(
            String routeId,
            String routeLabel,
            List<BotRouteCheckItemDto> checks
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BotRouteCheckItemDto(
            UUID id,
            LocalTime checkedAt,
            String checkerLabel,
            String display
    ) {
    }
}
