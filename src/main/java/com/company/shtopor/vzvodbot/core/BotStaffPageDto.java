package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotStaffPageDto(
        List<BotStaffItemDto> items,
        int page,
        boolean hasMore
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BotStaffItemDto(
            UUID userId,
            String displayName,
            String post,
            String department,
            String rank
    ) {
    }
}
