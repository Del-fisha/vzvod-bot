package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotVacationBalanceDto(int entitled, int available, int used) {
}
