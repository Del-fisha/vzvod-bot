package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BotAddressDto(
        String index,
        String city,
        String street,
        String houseNumber,
        String body,
        String flat,
        String typeOfHousing,
        String statusOfHousing,
        String summary
) {
}
