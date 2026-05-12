package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotProfileDto(
        UUID userId,
        String displayName,
        String rank,
        String post,
        String department,
        String breastplate,
        Boolean medicalExamination,
        String mobilePhoneMasked,
        BotAddressDto registration,
        BotAddressDto habitation,
        LocalDate idCardIssued,
        LocalDate idCardUntil
) {
}
