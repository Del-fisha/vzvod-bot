package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotEventItemDto(LocalDate date, String name) {
}
