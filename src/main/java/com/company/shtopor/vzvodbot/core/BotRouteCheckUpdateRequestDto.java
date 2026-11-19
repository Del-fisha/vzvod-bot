package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BotRouteCheckUpdateRequestDto(
        String checkedAt,
        UUID checkerServiceInfoId
) {
}
