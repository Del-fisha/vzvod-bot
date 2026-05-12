package com.company.shtopor.vzvodbot.core;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BotProfilePatchDto(
        String breastplate,
        Boolean medicalExamination,
        BotAddressPatchDto registration,
        BotAddressPatchDto habitation
) {
}
