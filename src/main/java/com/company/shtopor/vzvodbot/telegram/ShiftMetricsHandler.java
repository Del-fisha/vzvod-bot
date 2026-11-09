package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.core.BotCheckableRoutesResponseDto;
import com.company.shtopor.vzvodbot.core.BotRouteCheckCreateRequestDto;
import com.company.shtopor.vzvodbot.core.BotRouteCheckItemDto;
import com.company.shtopor.vzvodbot.core.BotShiftItemDto;
import com.company.shtopor.vzvodbot.core.BotShiftMetricDeltaRequestDto;
import com.company.shtopor.vzvodbot.core.BotShiftsResponseDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Показатели открытой смены из главного меню:
 * ИБДР ± / проверки маршрутов (CHECKING), заявки, адм./угол. нарушения.
 */
@Component
public class ShiftMetricsHandler {

    private static final Logger log = LoggerFactory.getLogger(ShiftMetricsHandler.class);

    static final String BTN_IBD_INC = "ИБДР +";
    static final String BTN_IBD_DEC = "ИБДР −";
    static final String BTN_MIGRANT_INC = "Мигрант +";
    static final String BTN_MIGRANT_DEC = "Мигрант −";
    static final String BTN_BOTH_INC = "Мигрант+ИБДР +";
    static final String BTN_BOTH_DEC = "Мигрант+ИБДР −";
    static final String BTN_STMT_INC = "Заявки +";
    static final String BTN_STMT_DEC = "Заявки −";
    static final String ROUTE_CHECK_PREFIX = "✓ ";

    private static final DateTimeFormatter DATE_RU =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru-RU"));
    private static final DateTimeFormatter TIME_HM =
            DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru-RU"));

    private final CoreProfileClient coreProfileClient;

    public ShiftMetricsHandler(CoreProfileClient coreProfileClient) {
        this.coreProfileClient = coreProfileClient;
    }

    public Optional<BotShiftItemDto> findOpenShift(long chatId) {
        try {
            BotShiftsResponseDto response = coreProfileClient.getShifts(chatId);
            List<BotShiftItemDto> items = response != null && response.items() != null
                    ? response.items()
                    : List.of();
            return items.stream()
                    .filter(it -> it != null && it.id() != null && it.endTime() == null)
                    .findFirst();
        } catch (Exception e) {
            log.debug("Не удалось загрузить открытую смену chatId={}", chatId, e);
            return Optional.empty();
        }
    }

    public void appendOpenShiftRows(long chatId, List<KeyboardRow> rows) {
        Optional<BotShiftItemDto> open = findOpenShift(chatId);
        if (open.isEmpty()) {
            return;
        }
        BotShiftItemDto shift = open.get();
        if (isChecking(shift)) {
            appendCheckingRows(chatId, shift, rows);
            return;
        }
        rows.add(new KeyboardRow(List.of(
                KeyboardButton.builder().text(BTN_IBD_INC).build(),
                KeyboardButton.builder().text(BTN_IBD_DEC).build())));
        rows.add(new KeyboardRow(List.of(
                KeyboardButton.builder().text(BTN_MIGRANT_INC).build(),
                KeyboardButton.builder().text(BTN_MIGRANT_DEC).build())));
        rows.add(new KeyboardRow(List.of(
                KeyboardButton.builder().text(BTN_BOTH_INC).build(),
                KeyboardButton.builder().text(BTN_BOTH_DEC).build())));
        rows.add(new KeyboardRow(List.of(
                KeyboardButton.builder().text(BTN_STMT_INC).build(),
                KeyboardButton.builder().text(BTN_STMT_DEC).build())));
        rows.add(new KeyboardRow(List.of(
                KeyboardButton.builder().text(ViolationCreationWizard.BTN_ADMIN).build())));
        rows.add(new KeyboardRow(List.of(
                KeyboardButton.builder().text(ViolationCreationWizard.BTN_CRIMINAL).build())));
    }

    private void appendCheckingRows(long chatId, BotShiftItemDto shift, List<KeyboardRow> rows) {
        try {
            BotCheckableRoutesResponseDto response = coreProfileClient.getCheckableRoutes(chatId, shift.id());
            List<BotCheckableRoutesResponseDto.BotCheckableRouteItemDto> routes =
                    response != null && response.routes() != null ? response.routes() : List.of();
            List<KeyboardButton> batch = new ArrayList<>();
            for (BotCheckableRoutesResponseDto.BotCheckableRouteItemDto route : routes) {
                if (route == null || route.routeId() == null || route.routeId().isBlank()) {
                    continue;
                }
                batch.add(KeyboardButton.builder().text(routeCheckButton(route)).build());
                if (batch.size() == 2) {
                    rows.add(new KeyboardRow(batch));
                    batch = new ArrayList<>();
                }
            }
            if (!batch.isEmpty()) {
                rows.add(new KeyboardRow(batch));
            }
        } catch (Exception e) {
            log.debug("Не удалось загрузить маршруты для проверок chatId={}", chatId, e);
        }
    }

    static String routeCheckButton(BotCheckableRoutesResponseDto.BotCheckableRouteItemDto route) {
        String label = route.routeLabel() != null && !route.routeLabel().isBlank()
                ? route.routeLabel()
                : route.routeId();
        String last = null;
        if (route.checks() != null && !route.checks().isEmpty()) {
            var check = route.checks().get(route.checks().size() - 1);
            if (check != null && check.checkedAt() != null) {
                last = TIME_HM.format(check.checkedAt());
            }
        }
        return last == null ? ROUTE_CHECK_PREFIX + label : ROUTE_CHECK_PREFIX + label + " · " + last;
    }

    public String appendOpenShiftSummary(long chatId, String text) {
        return findOpenShift(chatId)
                .map(shift -> text + "\n\n" + formatOpenShiftSummary(shift))
                .orElse(text);
    }

    public boolean handleMainMenuButton(
            TelegramLongPollingBot bot,
            long chatId,
            String text,
            ReplyKeyboardMarkup keyboard
    ) throws TelegramApiException {
        if (text != null && text.startsWith(ROUTE_CHECK_PREFIX)) {
            return handleRouteCheck(bot, chatId, text, keyboard);
        }
        MetricAction action = metricAction(text);
        if (action == null) {
            return false;
        }
        Optional<BotShiftItemDto> openShift = findOpenShift(chatId);
        if (openShift.isEmpty()) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Нет открытой смены — показатели недоступны.")
                    .replyMarkup(keyboard)
                    .build());
            return true;
        }
        if (isChecking(openShift.get())) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Для смены проверок используйте кнопки маршрутов ✓.")
                    .replyMarkup(keyboard)
                    .build());
            return true;
        }
        UUID shiftId = openShift.get().id();
        try {
            BotShiftMetricDeltaRequestDto plus = new BotShiftMetricDeltaRequestDto(1);
            BotShiftMetricDeltaRequestDto minus = new BotShiftMetricDeltaRequestDto(-1);
            BotShiftItemDto saved = switch (action) {
                case IBD_INC -> coreProfileClient.adjustIbdr(chatId, shiftId, plus);
                case IBD_DEC -> coreProfileClient.adjustIbdr(chatId, shiftId, minus);
                case MIGRANT_INC -> coreProfileClient.adjustMigrant(chatId, shiftId, plus);
                case MIGRANT_DEC -> coreProfileClient.adjustMigrant(chatId, shiftId, minus);
                case BOTH_INC -> coreProfileClient.adjustMigrantAndIbdr(chatId, shiftId, plus);
                case BOTH_DEC -> coreProfileClient.adjustMigrantAndIbdr(chatId, shiftId, minus);
                case STMT_INC -> coreProfileClient.adjustCountOfStatements(chatId, shiftId, plus);
                case STMT_DEC -> coreProfileClient.adjustCountOfStatements(chatId, shiftId, minus);
            };
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(formatShiftUpdatedMessage(saved))
                    .replyMarkup(keyboard)
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(e.userHint())
                    .replyMarkup(keyboard)
                    .build());
        } catch (Exception e) {
            log.error("Показатели смены chatId={}", chatId, e);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось обновить показатели (Core).")
                    .replyMarkup(keyboard)
                    .build());
        }
        return true;
    }

    private boolean handleRouteCheck(
            TelegramLongPollingBot bot,
            long chatId,
            String text,
            ReplyKeyboardMarkup keyboard
    ) throws TelegramApiException {
        Optional<BotShiftItemDto> openShift = findOpenShift(chatId);
        if (openShift.isEmpty() || !isChecking(openShift.get())) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Нет открытой смены проверок.")
                    .replyMarkup(keyboard)
                    .build());
            return true;
        }
        String routeId = parseRouteIdFromButton(text);
        if (routeId == null || routeId.isBlank()) {
            return true;
        }
        try {
            BotRouteCheckItemDto saved = coreProfileClient.recordRouteCheck(
                    chatId,
                    openShift.get().id(),
                    new BotRouteCheckCreateRequestDto(routeId)
            );
            String time = saved != null && saved.checkedAt() != null
                    ? TIME_HM.format(saved.checkedAt())
                    : "—";
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Проверка " + routeId + ": " + time
                            + (saved != null && saved.display() != null ? "\n" + saved.display() : ""))
                    .replyMarkup(keyboard)
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(e.userHint())
                    .replyMarkup(keyboard)
                    .build());
        } catch (Exception e) {
            log.error("Проверка маршрута chatId={}", chatId, e);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось зафиксировать проверку (Core).")
                    .replyMarkup(keyboard)
                    .build());
        }
        return true;
    }

    static String parseRouteIdFromButton(String text) {
        if (text == null || !text.startsWith(ROUTE_CHECK_PREFIX)) {
            return null;
        }
        String body = text.substring(ROUTE_CHECK_PREFIX.length()).trim();
        int sep = body.indexOf(" · ");
        return sep >= 0 ? body.substring(0, sep).trim() : body;
    }

    static boolean isChecking(BotShiftItemDto shift) {
        return shift != null && "CHECKING".equals(shift.typeOfShiftId());
    }

    static String formatOpenShiftSummary(BotShiftItemDto shift) {
        String date = shift.date() != null ? shift.date().format(DATE_RU) : "—";
        if (isChecking(shift)) {
            return "Открытая смена проверок " + date + " — кнопки маршрутов ✓";
        }
        int ibd = shift.ibdr() != null ? shift.ibdr() : 0;
        int migrant = shift.migrant() != null ? shift.migrant() : 0;
        int statements = shift.countOfStatements() != null ? shift.countOfStatements() : 0;
        int admin = shift.administrativeViolationsCount() != null ? shift.administrativeViolationsCount() : 0;
        int criminal = shift.criminalViolationsCount() != null ? shift.criminalViolationsCount() : 0;
        return "Открытая смена " + date + ": ИБДР " + ibd
                + ", мигрант " + migrant
                + ", заявки " + statements
                + ", адм. " + admin
                + ", уголов. " + criminal;
    }

    static String formatShiftUpdatedMessage(BotShiftItemDto shift) {
        String date = shift.date() != null ? shift.date().format(DATE_RU) : "—";
        int ibd = shift.ibdr() != null ? shift.ibdr() : 0;
        int migrant = shift.migrant() != null ? shift.migrant() : 0;
        int statements = shift.countOfStatements() != null ? shift.countOfStatements() : 0;
        int admin = shift.administrativeViolationsCount() != null ? shift.administrativeViolationsCount() : 0;
        int criminal = shift.criminalViolationsCount() != null ? shift.criminalViolationsCount() : 0;
        return "Обновлена смена (" + date + "):\n"
                + "\tИБДР: " + ibd + "\n"
                + "\tМигрант: " + migrant + "\n"
                + "\tЗаявок: " + statements + "\n"
                + "\tАдминистративка: " + admin + "\n"
                + "\tУголовка: " + criminal;
    }

    private static MetricAction metricAction(String text) {
        if (BTN_IBD_INC.equals(text)) {
            return MetricAction.IBD_INC;
        }
        if (BTN_IBD_DEC.equals(text)) {
            return MetricAction.IBD_DEC;
        }
        if (BTN_MIGRANT_INC.equals(text)) {
            return MetricAction.MIGRANT_INC;
        }
        if (BTN_MIGRANT_DEC.equals(text)) {
            return MetricAction.MIGRANT_DEC;
        }
        if (BTN_BOTH_INC.equals(text)) {
            return MetricAction.BOTH_INC;
        }
        if (BTN_BOTH_DEC.equals(text)) {
            return MetricAction.BOTH_DEC;
        }
        if (BTN_STMT_INC.equals(text)) {
            return MetricAction.STMT_INC;
        }
        if (BTN_STMT_DEC.equals(text)) {
            return MetricAction.STMT_DEC;
        }
        return null;
    }

    private enum MetricAction {
        IBD_INC, IBD_DEC, MIGRANT_INC, MIGRANT_DEC, BOTH_INC, BOTH_DEC, STMT_INC, STMT_DEC
    }
}
