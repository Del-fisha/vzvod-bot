package com.company.shtopor.vzvodbot.shift;

import com.company.shtopor.vzvodbot.core.BotColleagueItemDto;

/**
 * Подпись кнопки выбора напарника: ФИО + отделение.
 */
public final class ColleagueButtonLabel {

    private ColleagueButtonLabel() {
    }

    public static String of(BotColleagueItemDto item) {
        if (item == null) {
            return "Сотрудник";
        }
        String base = item.label() == null || item.label().isBlank() ? "Сотрудник" : item.label().trim();
        Integer dep = item.department();
        if (dep != null && (dep == 1 || dep == 2)) {
            return base + " · отд. " + dep;
        }
        return base;
    }
}
