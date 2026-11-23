package com.company.shtopor.vzvodbot.core;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AnnualDtos {
    private AnnualDtos() {
    }

    public record AnnualItemDto(
            String kind,
            String title,
            String group,
            boolean checked,
            boolean counts,
            boolean extendable,
            boolean dateAllowed,
            LocalDate performedOn,
            LocalDate validUntil
    ) {
    }

    public record AnnualBoardDto(
            boolean female,
            boolean medicalPassed,
            int percent,
            List<AnnualItemDto> items
    ) {
    }

    public record AnnualFlagsRequest(
            Boolean medicalExamination,
            Boolean cpd,
            Boolean psychologist
    ) {
    }

    public record AnnualLineDto(String title, String text) {
    }

    public record AnnualPersonDto(
            UUID userId,
            String shortFio,
            Integer departmentNumber,
            int percent,
            List<AnnualLineDto> lines
    ) {
    }

    public record AnnualRosterDto(List<AnnualPersonDto> people) {
    }
}
