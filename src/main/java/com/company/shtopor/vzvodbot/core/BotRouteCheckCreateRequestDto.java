package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BotRouteCheckCreateRequestDto(
        String routeId
) {
}
