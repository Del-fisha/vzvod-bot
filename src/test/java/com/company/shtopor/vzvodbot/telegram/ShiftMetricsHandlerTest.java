package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.core.BotShiftItemDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftMetricsHandlerTest {

    @Test
    void formatShiftUpdatedMessage_includesMigrant() {
        BotShiftItemDto shift = new BotShiftItemDto(
                UUID.randomUUID(),
                LocalDate.of(2026, 8, 9),
                "МП 31",
                "Маршрут",
                "VZVOD_ROUTE",
                "1",
                null,
                null,
                2,
                0,
                5,
                3,
                1,
                0,
                null,
                List.of()
        );

        String text = ShiftMetricsHandler.formatShiftUpdatedMessage(shift);

        assertTrue(text.contains("ИБДР: 5"));
        assertTrue(text.contains("Мигрант: 3"));
        assertTrue(text.contains("Заявок: 2"));
    }

    @Test
    void formatOpenShiftSummary_includesMigrant() {
        BotShiftItemDto shift = new BotShiftItemDto(
                UUID.randomUUID(),
                LocalDate.of(2026, 8, 9),
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                0,
                4,
                2,
                0,
                0,
                null,
                null
        );

        String text = ShiftMetricsHandler.formatOpenShiftSummary(shift);

        assertEquals(
                "Открытая смена 09.08.2026: ИБДР 4, мигрант 2, заявки 1, адм. 0, уголов. 0",
                text
        );
    }

    @Test
    void parseRouteIdFromButton_stripsPrefixAndTime() {
        assertEquals("МП 28", ShiftMetricsHandler.parseRouteIdFromButton("✓ МП 28"));
        assertEquals("МП 30", ShiftMetricsHandler.parseRouteIdFromButton("✓ МП 30 · 14:05"));
    }

    @Test
    void formatOpenShiftSummary_checking() {
        BotShiftItemDto shift = new BotShiftItemDto(
                UUID.randomUUID(),
                LocalDate.of(2026, 8, 9),
                "Другое",
                "Проверки",
                "CHECKING",
                "1",
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                List.of()
        );
        assertEquals(
                "Открытая смена проверок 09.08.2026 — кнопки маршрутов ✓",
                ShiftMetricsHandler.formatOpenShiftSummary(shift)
        );
    }
}
