package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BotAddressPatchDto(
        String index,
        String city,
        String street,
        String houseNumber,
        String body,
        String flat,
        String typeOfHousing,
        String statusOfHousing
) {
}
