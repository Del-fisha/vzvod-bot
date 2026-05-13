package com.company.shtopor.vzvodbot.telegram;



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

import java.util.List;

import java.util.Locale;

import java.util.Optional;

import java.util.UUID;



/**

 * Показатели открытой смены из главного меню: ИБДР, административные и уголовные нарушения.

 */

@Component

public class ShiftMetricsHandler {



    private static final Logger log = LoggerFactory.getLogger(ShiftMetricsHandler.class);



    static final String BTN_IBD_INC = "ИБДР +";

    static final String BTN_IBD_DEC = "ИБДР −";

    static final String BTN_STMT_INC = "Заявки +";

    static final String BTN_STMT_DEC = "Заявки −";



    private static final DateTimeFormatter DATE_RU = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru-RU"));



    private final CoreProfileClient coreProfileClient;



    public ShiftMetricsHandler(CoreProfileClient coreProfileClient) {

        this.coreProfileClient = coreProfileClient;

    }



    public Optional<BotShiftItemDto> findOpenShift(long chatId) {

        try {

            BotShiftsResponseDto response = coreProfileClient.getShifts(chatId);

            List<BotShiftItemDto> items = response != null && response.items() != null ? response.items() : List.of();

            return items.stream()

                    .filter(it -> it != null && it.id() != null && it.endTime() == null)

                    .findFirst();

        } catch (Exception e) {

            log.debug("Не удалось загрузить открытую смену chatId={}", chatId, e);

            return Optional.empty();

        }

    }



    public void appendOpenShiftRows(long chatId, List<KeyboardRow> rows) {

        if (findOpenShift(chatId).isEmpty()) {

            return;

        }

        rows.add(new KeyboardRow(List.of(

                KeyboardButton.builder().text(BTN_IBD_INC).build(),

                KeyboardButton.builder().text(BTN_IBD_DEC).build())));

        rows.add(new KeyboardRow(List.of(

                KeyboardButton.builder().text(BTN_STMT_INC).build(),

                KeyboardButton.builder().text(BTN_STMT_DEC).build())));

        rows.add(new KeyboardRow(List.of(KeyboardButton.builder().text(ViolationCreationWizard.BTN_ADMIN).build())));

        rows.add(new KeyboardRow(List.of(KeyboardButton.builder().text(ViolationCreationWizard.BTN_CRIMINAL).build())));

    }



    public String appendOpenShiftSummary(long chatId, String text) {

        return findOpenShift(chatId)

                .map(shift -> text + "\n\n" + formatOpenShiftSummary(shift))

                .orElse(text);

    }



    public boolean handleMainMenuButton(TelegramLongPollingBot bot, long chatId, String text,

                                        ReplyKeyboardMarkup keyboard) throws TelegramApiException {

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

        UUID shiftId = openShift.get().id();

        try {

            BotShiftItemDto saved = switch (action) {

                case IBD_INC -> coreProfileClient.adjustIbdWithMigrant(chatId, shiftId, new BotShiftMetricDeltaRequestDto(1));

                case IBD_DEC -> coreProfileClient.adjustIbdWithMigrant(chatId, shiftId, new BotShiftMetricDeltaRequestDto(-1));

                case STMT_INC -> coreProfileClient.adjustCountOfStatements(chatId, shiftId, new BotShiftMetricDeltaRequestDto(1));

                case STMT_DEC -> coreProfileClient.adjustCountOfStatements(chatId, shiftId, new BotShiftMetricDeltaRequestDto(-1));

            };

            bot.execute(SendMessage.builder()

                    .chatId(chatId)

                    .text("Обновлено.\n" + formatOpenShiftSummary(saved))

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



    static String formatOpenShiftSummary(BotShiftItemDto shift) {

        String date = shift.date() != null ? shift.date().format(DATE_RU) : "—";

        int ibd = shift.ibdWithMigrant() != null ? shift.ibdWithMigrant() : 0;

        int statements = shift.countOfStatements() != null ? shift.countOfStatements() : 0;

        int admin = shift.administrativeViolationsCount() != null ? shift.administrativeViolationsCount() : 0;

        int criminal = shift.criminalViolationsCount() != null ? shift.criminalViolationsCount() : 0;

        return "Открытая смена " + date + ": ИБДР " + ibd + ", заявки " + statements + ", адм. " + admin + ", уголов. " + criminal;

    }



    private static MetricAction metricAction(String text) {

        if (BTN_IBD_INC.equals(text)) {

            return MetricAction.IBD_INC;

        }

        if (BTN_IBD_DEC.equals(text)) {

            return MetricAction.IBD_DEC;

        }

        if (BTN_STMT_INC.equals(text)) {

            return MetricAction.STMT_INC;

        }

        if (BTN_STMT_DEC.equals(text)) {

            return MetricAction.STMT_DEC;

        }

        return null;

    }



    private static String metricErrorHint(int status) {

        return switch (status) {

            case 400 -> "Нельзя уменьшить показатель ниже нуля.";

            case 403 -> "Доступ к этой смене запрещён.";

            case 404 -> "Смена не найдена.";

            case 409 -> "Смена уже завершена — показатели недоступны.";

            case 401 -> "Ошибка API-ключа к Core.";

            default -> "Ошибка запроса к Core (код " + status + ").";

        };

    }



    private enum MetricAction {

        IBD_INC,

        IBD_DEC,

        STMT_INC,

        STMT_DEC

    }

}


