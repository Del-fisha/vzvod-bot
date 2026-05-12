package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotColleaguesResponseDto(
        List<BotColleagueItemDto> items,
        boolean hasMore
) {
}
