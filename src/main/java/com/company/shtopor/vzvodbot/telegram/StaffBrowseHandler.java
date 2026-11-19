package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.core.BotProfileDto;
import com.company.shtopor.vzvodbot.core.BotStaffPageDto;
import com.company.shtopor.vzvodbot.core.BotStaffWorkDto;
import com.company.shtopor.vzvodbot.core.BotVocationItemDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Просмотр карточки любого сотрудника командиром отделения, взвода или заместителем командира взвода.
 */
@Component
public class StaffBrowseHandler {

    static final String CB_OPEN = "menu_staff";
    private static final int PAGE_SIZE = 6;
    private static final String P_PAGE = "stp|";
    private static final String P_USER = "stu|";
    private static final String P_SEARCH = "stq";
    private static final String P_CLEAR = "stx";

    private static final Logger log = LoggerFactory.getLogger(StaffBrowseHandler.class);
    private static final DateTimeFormatter DATE_RU =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru-RU"));

    private final CoreProfileClient coreProfileClient;
    private final ConcurrentHashMap<Long, Session> sessions = new ConcurrentHashMap<>();

    public StaffBrowseHandler(CoreProfileClient coreProfileClient) {
        this.coreProfileClient = coreProfileClient;
    }

    public boolean clear(long chatId) {
        return sessions.remove(chatId) != null;
    }

    public boolean matches(String data) {
        if (data == null) {
            return false;
        }
        return CB_OPEN.equals(data)
                || P_SEARCH.equals(data)
                || P_CLEAR.equals(data)
                || data.startsWith(P_PAGE)
                || data.startsWith(P_USER);
    }

    public boolean handleMessage(TelegramLongPollingBot bot, long chatId, String text) throws TelegramApiException {
        Session session = sessions.get(chatId);
        if (session == null || !session.awaitingQuery) {
            return false;
        }
        if (text == null || text.startsWith("/")
                || MainMenuKeyboardFactory.BTN_MENU.equals(text)
                || MainMenuKeyboardFactory.BTN_SHIFTS.equals(text)
                || RouteCheckEditHandler.BTN_EDIT_CHECKS.equals(text)
                || text.startsWith(ShiftMetricsHandler.ROUTE_CHECK_PREFIX)) {
            session.awaitingQuery = false;
            return false;
        }
        session.query = text.trim();
        session.page = 0;
        session.awaitingQuery = false;
        showPage(bot, chatId, session);
        return true;
    }

    public boolean handleCallback(
            TelegramLongPollingBot bot,
            long chatId,
            String data,
            String callbackId
    ) throws TelegramApiException {
        if (!matches(data)) {
            return false;
        }
        bot.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        Session session = sessions.computeIfAbsent(chatId, id -> new Session());
        try {
            if (CB_OPEN.equals(data)) {
                session.query = "";
                session.page = 0;
                session.awaitingQuery = false;
                showPage(bot, chatId, session);
                return true;
            }
            if (P_SEARCH.equals(data)) {
                session.awaitingQuery = true;
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Введите фамилию или часть ФИО сотрудника.")
                        .build());
                return true;
            }
            if (P_CLEAR.equals(data)) {
                session.query = "";
                session.page = 0;
                session.awaitingQuery = false;
                showPage(bot, chatId, session);
                return true;
            }
            if (data.startsWith(P_PAGE)) {
                session.page = parsePage(data.substring(P_PAGE.length()));
                session.awaitingQuery = false;
                showPage(bot, chatId, session);
                return true;
            }
            if (data.startsWith(P_USER)) {
                UUID userId = parseUserId(data.substring(P_USER.length()));
                if (userId == null) {
                    showPage(bot, chatId, session);
                    return true;
                }
                BotProfileDto profile = coreProfileClient.getStaffProfile(chatId, userId);
                String back = P_PAGE + session.page;
                InlineKeyboardMarkup backKeyboard = InlineKeyboardMarkup.builder()
                        .keyboard(List.of(List.of(ib("К списку", back))))
                        .build();
                String workText = null;
                try {
                    workText = formatWork(coreProfileClient.getStaffWork(chatId, userId));
                } catch (CoreProfileClient.CoreProfileException e) {
                    log.debug("Результаты сотрудника chatId={} user={}", chatId, userId, e);
                }
                if (workText == null) {
                    bot.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text(VzvodTelegramBot.formatProfile(profile, "📋 Карточка сотрудника", false))
                            .replyMarkup(backKeyboard)
                            .build());
                } else {
                    bot.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text(VzvodTelegramBot.formatProfile(profile, "📋 Карточка сотрудника", false))
                            .build());
                    bot.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text(workText)
                            .replyMarkup(backKeyboard)
                            .build());
                }
                return true;
            }
        } catch (CoreProfileClient.CoreProfileException e) {
            String hint = e.getStatus() == 403
                    ? "Карточки сотрудников доступны командиру отделения, командиру взвода и заместителю командира взвода."
                    : e.userHint();
            bot.execute(SendMessage.builder().chatId(chatId).text(hint).build());
        } catch (Exception e) {
            log.error("Карточка сотрудника chatId={} data={}", chatId, data, e);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось открыть карточку сотрудника.")
                    .build());
        }
        return true;
    }

    static String formatWork(BotStaffWorkDto work) {
        if (work == null) {
            return "Результаты не загружены.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Результаты\n");
        sb.append("Месяц считается с 1-го числа, год — с 1 января.\n\n");
        appendTotals(sb, "Этот месяц", work.month());
        appendTotals(sb, "Этот год", work.yearTotals());
        appendTotals(sb, "Всего", work.allTime());
        sb.append("\nОтпуск ").append(work.year()).append('\n');
        sb.append("Положено: ").append(work.vacationEntitled()).append('\n');
        sb.append("Осталось: ").append(work.vacationRemaining()).append('\n');
        List<BotVocationItemDto> vacations = work.vacationsThisYear() == null ? List.of() : work.vacationsThisYear();
        if (vacations.isEmpty()) {
            sb.append("В этом году отпусков нет.");
        } else {
            sb.append("В этом году:\n");
            for (BotVocationItemDto item : vacations) {
                if (item == null) {
                    continue;
                }
                String start = item.startDate() == null ? "—" : item.startDate().format(DATE_RU);
                String end = item.endDate() == null ? "—" : item.endDate().format(DATE_RU);
                sb.append("• ").append(start).append("–").append(end);
                if (item.countOfDays() != null) {
                    sb.append(", ").append(item.countOfDays()).append(" дн.");
                }
                if (item.typeLabel() != null && !item.typeLabel().isBlank()) {
                    sb.append(", ").append(item.typeLabel());
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static void appendTotals(StringBuilder sb, String title, BotStaffWorkDto.BotWorkTotalsDto totals) {
        int ibdr = totals == null ? 0 : totals.ibdr();
        int admin = totals == null ? 0 : totals.administrative();
        int criminal = totals == null ? 0 : totals.criminal();
        sb.append(title).append(": ИБДР ").append(ibdr)
                .append(", АП ").append(admin)
                .append(", УП ").append(criminal)
                .append('\n');
    }

    private void showPage(TelegramLongPollingBot bot, long chatId, Session session) throws TelegramApiException {
        BotStaffPageDto page = coreProfileClient.getStaff(chatId, session.query, session.page, PAGE_SIZE);
        List<BotStaffPageDto.BotStaffItemDto> items =
                page != null && page.items() != null ? page.items() : List.of();
        int current = page != null ? page.page() : session.page;
        session.page = current;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (BotStaffPageDto.BotStaffItemDto item : items) {
            if (item == null || item.userId() == null) {
                continue;
            }
            rows.add(List.of(ib(truncate(personLabel(item)), P_USER + item.userId())));
        }
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (current > 0) {
            nav.add(ib("←", P_PAGE + (current - 1)));
        }
        if (page != null && page.hasMore()) {
            nav.add(ib("→", P_PAGE + (current + 1)));
        }
        if (!nav.isEmpty()) {
            rows.add(nav);
        }
        rows.add(List.of(ib("Поиск", P_SEARCH), ib("Все", P_CLEAR)));
        String title = session.query == null || session.query.isBlank()
                ? "Сотрудники. Выберите карточку."
                : "Сотрудники по запросу «" + session.query + "».";
        if (items.isEmpty()) {
            title = title + "\nНикого не найдено.";
        }
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text(title)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private static String personLabel(BotStaffPageDto.BotStaffItemDto item) {
        String name = item.displayName() == null || item.displayName().isBlank() ? "Сотрудник" : item.displayName();
        if (item.post() != null && !item.post().isBlank()) {
            return name + " · " + item.post();
        }
        return name;
    }

    private static String truncate(String text) {
        return text.length() <= 60 ? text : text.substring(0, 57) + "…";
    }

    private static int parsePage(String raw) {
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static UUID parseUserId(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static InlineKeyboardButton ib(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private static final class Session {
        private String query = "";
        private int page;
        private boolean awaitingQuery;
    }
}
