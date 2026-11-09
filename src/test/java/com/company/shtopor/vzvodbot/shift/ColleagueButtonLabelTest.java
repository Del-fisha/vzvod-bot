package com.company.shtopor.vzvodbot.shift;

import com.company.shtopor.vzvodbot.core.BotColleagueItemDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColleagueButtonLabelTest {

    @Test
    void appendsDepartmentWhenPresent() {
        BotColleagueItemDto item = new BotColleagueItemDto(UUID.randomUUID(), "Иванов И. И.", 1);
        assertEquals("Иванов И. И. · отд. 1", ColleagueButtonLabel.of(item));
    }

    @Test
    void keepsLabelWhenDepartmentMissing() {
        BotColleagueItemDto item = new BotColleagueItemDto(UUID.randomUUID(), "Петров П. П.", null);
        assertEquals("Петров П. П.", ColleagueButtonLabel.of(item));
    }
}
