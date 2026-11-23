package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.core.AnnualDtos.AnnualBoardDto;
import com.company.shtopor.vzvodbot.core.AnnualDtos.AnnualFlagsRequest;
import com.company.shtopor.vzvodbot.core.AnnualDtos.AnnualItemDto;
import com.company.shtopor.vzvodbot.core.AnnualDtos.AnnualLineDto;
import com.company.shtopor.vzvodbot.core.AnnualDtos.AnnualPersonDto;
import com.company.shtopor.vzvodbot.core.AnnualDtos.AnnualRosterDto;
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

@Component
public class AnnualMenuHandler {

    static final String CB_OPEN = "ann_menu";
    private static final String PROF = "ann_prof";
    private static final String PROF_YES = "ann_prof_y";
    private static final String PROF_NO = "ann_prof_n";
    private static final String CPD = "ann_cpd";
    private static final String CPD_YES = "ann_cpd_y";
    private static final String CPD_NO = "ann_cpd_n";
    private static final String TESTS = "ann_tests";
    private static final String PSY = "ann_psy";
    private static final String PSY_YES = "ann_psy_y";
    private static final String PSY_NO = "ann_psy_n";
    private static final String OTHERS = "ann_others";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Logger log = LoggerFactory.getLogger(AnnualMenuHandler.class);

    private final CoreProfileClient coreProfileClient;

    public AnnualMenuHandler(CoreProfileClient coreProfileClient) {
        this.coreProfileClient = coreProfileClient;
    }

    public boolean matches(String data) {
        return data != null && data.startsWith("ann_");
    }

    public void showMenu(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(button("Профосмотр", PROF)),
                List.of(button("ЦПД", CPD)),
                List.of(button("Зачёты", TESTS)),
                List.of(button("Психолог", PSY)),
                List.of(button("Результаты коллег", OTHERS))
        );
        send(bot, chatId, "Ежегодно", rows);
    }

    public boolean handleCallback(TelegramLongPollingBot bot, long chatId, String data, String callbackId)
            throws TelegramApiException {
        if (!matches(data)) {
            return false;
        }
        bot.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        try {
            switch (data) {
                case CB_OPEN -> showMenu(bot, chatId);
                case PROF -> showProf(bot, chatId);
                case PROF_YES -> {
                    coreProfileClient.setAnnualFlags(chatId, new AnnualFlagsRequest(true, null, null));
                    showProf(bot, chatId);
                }
                case PROF_NO -> {
                    coreProfileClient.setAnnualFlags(chatId, new AnnualFlagsRequest(false, null, null));
                    showProf(bot, chatId);
                }
                case CPD -> showFlagSection(bot, chatId, "CPD", "ЦПД", CPD_YES, CPD_NO);
                case CPD_YES -> {
                    coreProfileClient.setAnnualFlags(chatId, new AnnualFlagsRequest(null, true, null));
                    showFlagSection(bot, chatId, "CPD", "ЦПД", CPD_YES, CPD_NO);
                }
                case CPD_NO -> {
                    coreProfileClient.setAnnualFlags(chatId, new AnnualFlagsRequest(null, false, null));
                    showFlagSection(bot, chatId, "CPD", "ЦПД", CPD_YES, CPD_NO);
                }
                case TESTS -> showTests(bot, chatId);
                case PSY -> showFlagSection(bot, chatId, "PSYCHOLOGIST", "Психолог", PSY_YES, PSY_NO);
                case PSY_YES -> {
                    coreProfileClient.setAnnualFlags(chatId, new AnnualFlagsRequest(null, null, true));
                    showFlagSection(bot, chatId, "PSYCHOLOGIST", "Психолог", PSY_YES, PSY_NO);
                }
                case PSY_NO -> {
                    coreProfileClient.setAnnualFlags(chatId, new AnnualFlagsRequest(null, null, false));
                    showFlagSection(bot, chatId, "PSYCHOLOGIST", "Психолог", PSY_YES, PSY_NO);
                }
                case OTHERS -> showOthers(bot, chatId);
                default -> {
                    return false;
                }
            }
        } catch (CoreProfileClient.CoreProfileException e) {
            send(bot, chatId, "Не удалось получить данные (" + e.getStatus() + ").", List.of());
        } catch (RuntimeException e) {
            log.error("Ежегодно chatId={}", chatId, e);
            send(bot, chatId, "Не удалось связаться с Core.", List.of());
        }
        return true;
    }

    private void showProf(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        AnnualBoardDto board = coreProfileClient.getAnnual(chatId);
        StringBuilder text = new StringBuilder();
        text.append("Профосмотр: ").append(board.medicalPassed() ? "Пройден" : "Не пройден").append("\n\n");
        text.append("Анализы\n");
        appendGroup(text, board, "ANALYSIS");
        text.append("\nВсе врачи\n");
        appendGroup(text, board, "DOCTOR");
        send(bot, chatId, text.toString().trim(), List.of(List.of(
                button("Пройден", PROF_YES),
                button("Не пройден", PROF_NO)
        ), List.of(button("Ежегодно", CB_OPEN))));
    }

    private void showTests(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        AnnualBoardDto board = coreProfileClient.getAnnual(chatId);
        StringBuilder text = new StringBuilder("Зачёты\n");
        appendGroup(text, board, "TEST");
        send(bot, chatId, text.toString().trim(), List.of(List.of(button("Ежегодно", CB_OPEN))));
    }

    private void showFlagSection(
            TelegramLongPollingBot bot,
            long chatId,
            String group,
            String title,
            String yes,
            String no
    ) throws TelegramApiException {
        AnnualBoardDto board = coreProfileClient.getAnnual(chatId);
        StringBuilder text = new StringBuilder(title).append('\n');
        appendGroup(text, board, group);
        send(bot, chatId, text.toString().trim(), List.of(
                List.of(button("Пройден", yes), button("Не пройден", no)),
                List.of(button("Ежегодно", CB_OPEN))
        ));
    }

    private void showOthers(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        AnnualRosterDto roster = coreProfileClient.getAnnualOthers(chatId);
        List<AnnualPersonDto> people = roster == null || roster.people() == null ? List.of() : roster.people();
        if (people.isEmpty()) {
            send(bot, chatId, "Нет сотрудников", List.of(List.of(button("Ежегодно", CB_OPEN))));
            return;
        }
        StringBuilder text = new StringBuilder();
        Integer previous = Integer.MIN_VALUE;
        boolean first = true;
        for (AnnualPersonDto person : people) {
            if (first || !same(previous, person.departmentNumber())) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(escape(departmentTitle(person.departmentNumber()))).append("\n\n");
                previous = person.departmentNumber();
                first = false;
            }
            text.append("<b>").append(escape(person.shortFio())).append("</b>\n");
            if (person.lines() != null) {
                for (AnnualLineDto line : person.lines()) {
                    text.append("<b><u>").append(escape(line.title())).append("</u></b>:\n");
                    text.append(escape(line.text())).append('\n');
                }
            }
            text.append("────────\n");
            if (text.length() > 3200) {
                send(bot, chatId, text.toString().trim(), List.of(), true);
                text.setLength(0);
            }
        }
        send(bot, chatId, text.length() == 0 ? "Ежегодно" : text.toString().trim(),
                List.of(List.of(button("Ежегодно", CB_OPEN))), true);
    }

    private static void appendGroup(StringBuilder text, AnnualBoardDto board, String group) {
        if (board.items() == null) {
            return;
        }
        for (AnnualItemDto item : board.items()) {
            if (!group.equals(item.group())) {
                continue;
            }
            text.append(item.title()).append(": ").append(item.counts() ? "Пройден" : "Не пройден");
            if (item.performedOn() != null) {
                text.append(", дата ").append(DATE.format(item.performedOn()));
            }
            if (item.counts() && item.validUntil() != null) {
                text.append(", актуально до ").append(DATE.format(item.validUntil()));
            }
            text.append('\n');
        }
    }

    private static boolean same(Integer previous, Integer current) {
        return previous == null ? current == null : previous.equals(current);
    }

    private static String departmentTitle(Integer number) {
        return number == null ? "Без отделения" : number + "-е отделение";
    }

    private static InlineKeyboardButton button(String title, String data) {
        return InlineKeyboardButton.builder().text(title).callbackData(data).build();
    }

    private static void send(TelegramLongPollingBot bot, long chatId, String text, List<List<InlineKeyboardButton>> rows)
            throws TelegramApiException {
        send(bot, chatId, text, rows, false);
    }

    private static void send(
            TelegramLongPollingBot bot,
            long chatId,
            String text,
            List<List<InlineKeyboardButton>> rows,
            boolean html
    ) throws TelegramApiException {
        SendMessage.SendMessageBuilder builder = SendMessage.builder().chatId(chatId).text(text);
        if (html) {
            builder.parseMode("HTML");
        }
        if (rows != null && !rows.isEmpty()) {
            builder.replyMarkup(InlineKeyboardMarkup.builder().keyboard(new ArrayList<>(rows)).build());
        }
        bot.execute(builder.build());
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
