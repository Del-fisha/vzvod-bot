package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.core.BotCheckableRoutesResponseDto;
import com.company.shtopor.vzvodbot.core.BotRouteCheckUpdateRequestDto;
import com.company.shtopor.vzvodbot.core.BotShiftItemDto;
import com.company.shtopor.vzvodbot.core.BotShiftsResponseDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Редактирование времени и проверяющего и удаление уже зафиксированной проверки маршрута.
 */
@Component
public class RouteCheckEditHandler {

    static final String BTN_EDIT_CHECKS = "Изменить проверки";

    private static final Logger log = LoggerFactory.getLogger(RouteCheckEditHandler.class);
    private static final Pattern TIME_HM = Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)$");
    private static final DateTimeFormatter HM =
            DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru-RU"));
    private static final String P_OPEN = "rcko|";
    private static final String P_PICK = "rckp|";
    private static final String P_TIME = "rckt";
    private static final String P_WHO = "rckw";
    private static final String P_DEL = "rckd";
    private static final String P_YES = "rcky";
    private static final String P_LIST = "rckb";
    private static final String P_CARD = "rckc";

    private final CoreProfileClient coreProfileClient;
    private final ConcurrentHashMap<Long, Session> sessions = new ConcurrentHashMap<>();

    public RouteCheckEditHandler(CoreProfileClient coreProfileClient) {
        this.coreProfileClient = coreProfileClient;
    }

    public boolean clear(long chatId) {
        return sessions.remove(chatId) != null;
    }

    public boolean matches(String data) {
        return data != null && (data.startsWith("rck"));
    }

    public static LocalTime parseCheckedAt(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = TIME_HM.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        return LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    public boolean handleMessage(
            TelegramLongPollingBot bot,
            long chatId,
            String text,
            ReplyKeyboardMarkup keyboard
    ) throws TelegramApiException {
        if (BTN_EDIT_CHECKS.equals(text)) {
            try {
                openList(bot, chatId, keyboard);
            } catch (CoreProfileClient.CoreProfileException e) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(e.userHint())
                        .replyMarkup(keyboard)
                        .build());
            } catch (Exception e) {
                log.error("Список проверок chatId={}", chatId, e);
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Не удалось загрузить проверки.")
                        .replyMarkup(keyboard)
                        .build());
            }
            return true;
        }
        Session session = sessions.get(chatId);
        if (session == null || !session.awaitingTime) {
            return false;
        }
        if (text == null || text.startsWith("/") || isForeignButton(text)) {
            session.awaitingTime = false;
            return false;
        }
        LocalTime time = parseCheckedAt(text);
        if (time == null) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Введите время в формате ЧЧ:ММ, например 09:05.")
                    .replyMarkup(keyboard)
                    .build());
            return true;
        }
        CheckRow row = session.findSelected();
        if (row == null) {
            session.awaitingTime = false;
            openList(bot, chatId, keyboard);
            return true;
        }
        try {
            coreProfileClient.updateRouteCheck(
                    chatId,
                    row.id,
                    new BotRouteCheckUpdateRequestDto(HM.format(time), row.checkerId)
            );
            session.awaitingTime = false;
            reload(chatId, session);
            showCard(bot, chatId, session, "Время проверки обновлено.");
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(e.userHint())
                    .replyMarkup(keyboard)
                    .build());
        } catch (Exception e) {
            log.error("Время проверки chatId={}", chatId, e);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось сохранить время проверки.")
                    .replyMarkup(keyboard)
                    .build());
        }
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
        answer(bot, callbackId);
        Session session = sessions.get(chatId);
        if (session == null) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Список проверок устарел. Нажмите «" + BTN_EDIT_CHECKS + "».")
                    .build());
            return true;
        }
        try {
            if (data.startsWith(P_OPEN)) {
                int index = parseIndex(data.substring(P_OPEN.length()));
                if (index < 0 || index >= session.checks.size()) {
                    showList(bot, chatId, session, "Список устарел, откройте его снова.");
                    return true;
                }
                session.selectedId = session.checks.get(index).id;
                session.confirmDelete = false;
                session.awaitingTime = false;
                showCard(bot, chatId, session, null);
                return true;
            }
            if (P_CARD.equals(data)) {
                session.confirmDelete = false;
                session.awaitingTime = false;
                showCard(bot, chatId, session, null);
                return true;
            }
            if (P_LIST.equals(data)) {
                session.selectedId = null;
                session.confirmDelete = false;
                session.awaitingTime = false;
                reload(chatId, session);
                showList(bot, chatId, session, null);
                return true;
            }
            if (P_TIME.equals(data)) {
                if (session.findSelected() == null) {
                    showList(bot, chatId, session, "Проверка уже удалена.");
                    return true;
                }
                session.awaitingTime = true;
                session.confirmDelete = false;
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Введите новое время проверки в формате ЧЧ:ММ.")
                        .build());
                return true;
            }
            if (P_WHO.equals(data)) {
                session.awaitingTime = false;
                session.confirmDelete = false;
                showCommanders(bot, chatId, session);
                return true;
            }
            if (data.startsWith(P_PICK)) {
                int index = parseIndex(data.substring(P_PICK.length()));
                applyCommander(bot, chatId, session, index);
                return true;
            }
            if (P_DEL.equals(data)) {
                session.confirmDelete = true;
                session.awaitingTime = false;
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Удалить эту проверку?")
                        .replyMarkup(InlineKeyboardMarkup.builder()
                                .keyboard(List.of(
                                        List.of(ib("Удалить", P_YES), ib("Отмена", P_CARD))
                                ))
                                .build())
                        .build());
                return true;
            }
            if (P_YES.equals(data)) {
                deleteSelected(bot, chatId, session);
                return true;
            }
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(e.userHint())
                    .build());
        } catch (Exception e) {
            log.error("Редактирование проверки chatId={} data={}", chatId, data, e);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось изменить проверку.")
                    .build());
        }
        return true;
    }

    private void openList(TelegramLongPollingBot bot, long chatId, ReplyKeyboardMarkup keyboard)
            throws TelegramApiException {
        Optional<BotShiftItemDto> shift = openCheckingShift(chatId);
        if (shift.isEmpty() || shift.get().id() == null) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Нет открытой смены проверок.")
                    .replyMarkup(keyboard)
                    .build());
            return;
        }
        Session session = new Session(shift.get().id());
        reload(chatId, session);
        sessions.put(chatId, session);
        showList(bot, chatId, session, null);
    }

    private void showList(TelegramLongPollingBot bot, long chatId, Session session, String prefix)
            throws TelegramApiException {
        if (session.checks.isEmpty()) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(prefix == null ? "Проверок пока нет." : prefix)
                    .build());
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < session.checks.size(); i++) {
            CheckRow row = session.checks.get(i);
            rows.add(List.of(ib(truncate(row.buttonLabel()), P_OPEN + i)));
        }
        String text = "Выберите проверку: можно изменить время, проверяющего или удалить запись.";
        if (prefix != null && !prefix.isBlank()) {
            text = prefix + "\n\n" + text;
        }
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void showCard(TelegramLongPollingBot bot, long chatId, Session session, String prefix)
            throws TelegramApiException {
        CheckRow row = session.findSelected();
        if (row == null) {
            showList(bot, chatId, session, "Проверка уже удалена.");
            return;
        }
        String text = (prefix == null || prefix.isBlank() ? "" : prefix + "\n\n")
                + "Маршрут " + row.route + "\n"
                + "Время: " + (row.checkedAt == null ? "—" : HM.format(row.checkedAt)) + "\n"
                + "Проверил: " + (row.checkerLabel == null || row.checkerLabel.isBlank() ? "—" : row.checkerLabel);
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                List.of(ib("Время", P_TIME), ib("Кто проверил", P_WHO)),
                                List.of(ib("Удалить", P_DEL), ib("К списку", P_LIST))
                        ))
                        .build())
                .build());
    }

    private void showCommanders(TelegramLongPollingBot bot, long chatId, Session session)
            throws TelegramApiException {
        if (session.commanders.isEmpty()) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("На сегодняшней смене проверок нет командиров для выбора.")
                    .build());
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < session.commanders.size(); i++) {
            String label = session.commanders.get(i).label;
            rows.add(List.of(ib(truncate(label == null || label.isBlank() ? "—" : label), P_PICK + i)));
        }
        rows.add(List.of(ib("Назад", P_CARD)));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Кто проверил маршрут?")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void applyCommander(TelegramLongPollingBot bot, long chatId, Session session, int index)
            throws TelegramApiException {
        CheckRow row = session.findSelected();
        if (row == null || index < 0 || index >= session.commanders.size()) {
            showList(bot, chatId, session, "Список устарел.");
            return;
        }
        Commander commander = session.commanders.get(index);
        String time = row.checkedAt == null ? null : HM.format(row.checkedAt);
        coreProfileClient.updateRouteCheck(
                chatId,
                row.id,
                new BotRouteCheckUpdateRequestDto(time, commander.id)
        );
        reload(chatId, session);
        showCard(bot, chatId, session, "Проверяющий обновлён.");
    }

    private void deleteSelected(TelegramLongPollingBot bot, long chatId, Session session)
            throws TelegramApiException {
        CheckRow row = session.findSelected();
        if (row == null) {
            showList(bot, chatId, session, "Проверка уже удалена.");
            return;
        }
        coreProfileClient.deleteRouteCheck(chatId, row.id);
        session.selectedId = null;
        session.confirmDelete = false;
        reload(chatId, session);
        showList(bot, chatId, session, "Проверка удалена.");
    }

    private void reload(long chatId, Session session) {
        BotCheckableRoutesResponseDto response = coreProfileClient.getCheckableRoutes(chatId, session.shiftId);
        List<CheckRow> checks = new ArrayList<>();
        List<BotCheckableRoutesResponseDto.BotCheckableRouteItemDto> routes =
                response != null && response.routes() != null ? response.routes() : List.of();
        for (BotCheckableRoutesResponseDto.BotCheckableRouteItemDto route : routes) {
            if (route == null || route.checks() == null) {
                continue;
            }
            String routeLabel = route.routeLabel() != null && !route.routeLabel().isBlank()
                    ? route.routeLabel()
                    : route.routeId();
            for (BotCheckableRoutesResponseDto.BotRouteCheckItemDto check : route.checks()) {
                if (check == null || check.id() == null) {
                    continue;
                }
                checks.add(new CheckRow(
                        check.id(),
                        routeLabel == null ? "—" : routeLabel,
                        check.checkedAt(),
                        check.checkerLabel(),
                        check.checkerServiceInfoId()
                ));
            }
        }
        session.checks = checks;
        List<Commander> commanders = new ArrayList<>();
        List<BotCheckableRoutesResponseDto.BotRouteCheckerOptionDto> options =
                response != null && response.commanders() != null ? response.commanders() : List.of();
        for (BotCheckableRoutesResponseDto.BotRouteCheckerOptionDto option : options) {
            if (option == null || option.serviceInfoId() == null) {
                continue;
            }
            commanders.add(new Commander(option.serviceInfoId(), option.label()));
        }
        session.commanders = commanders;
    }

    private Optional<BotShiftItemDto> openCheckingShift(long chatId) {
        BotShiftsResponseDto response = coreProfileClient.getShifts(chatId);
        List<BotShiftItemDto> items = response != null && response.items() != null ? response.items() : List.of();
        return items.stream()
                .filter(it -> it != null && it.id() != null && it.endTime() == null)
                .filter(it -> "CHECKING".equals(it.typeOfShiftId()))
                .findFirst();
    }

    private static boolean isForeignButton(String text) {
        return MainMenuKeyboardFactory.BTN_MENU.equals(text)
                || MainMenuKeyboardFactory.BTN_SHIFTS.equals(text)
                || text.startsWith(ShiftMetricsHandler.ROUTE_CHECK_PREFIX);
    }

    private static int parseIndex(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "—";
        }
        return text.length() <= 60 ? text : text.substring(0, 57) + "…";
    }

    private static InlineKeyboardButton ib(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private static void answer(TelegramLongPollingBot bot, String callbackId) throws TelegramApiException {
        bot.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
    }

    private static final class Session {
        private final UUID shiftId;
        private List<CheckRow> checks = List.of();
        private List<Commander> commanders = List.of();
        private UUID selectedId;
        private boolean awaitingTime;
        private boolean confirmDelete;

        private Session(UUID shiftId) {
            this.shiftId = shiftId;
        }

        private CheckRow findSelected() {
            if (selectedId == null) {
                return null;
            }
            for (CheckRow row : checks) {
                if (selectedId.equals(row.id)) {
                    return row;
                }
            }
            return null;
        }
    }

    private record CheckRow(
            UUID id,
            String route,
            LocalTime checkedAt,
            String checkerLabel,
            UUID checkerId
    ) {
        private String buttonLabel() {
            String time = checkedAt == null ? "—" : HM.format(checkedAt);
            String who = checkerLabel == null || checkerLabel.isBlank() ? "" : " · " + checkerLabel;
            return route + " · " + time + who;
        }
    }

    private record Commander(UUID id, String label) {
    }
}
