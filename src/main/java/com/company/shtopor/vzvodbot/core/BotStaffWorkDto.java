package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotStaffWorkDto(
        int year,
        BotWorkTotalsDto month,
        BotWorkTotalsDto yearTotals,
        BotWorkTotalsDto allTime,
        int vacationEntitled,
        int vacationRemaining,
        List<BotVocationItemDto> vacationsThisYear
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BotWorkTotalsDto(
            int ibdr,
            int administrative,
            int criminal
    ) {
    }
}
