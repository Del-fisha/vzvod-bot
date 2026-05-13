package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.core.BotAdministrativeViolationCreateRequestDto;
import com.company.shtopor.vzvodbot.core.BotCriminalViolationCreateRequestDto;
import com.company.shtopor.vzvodbot.core.BotEnumOptionDto;
import com.company.shtopor.vzvodbot.core.BotShiftItemDto;
import com.company.shtopor.vzvodbot.core.BotViolationOptionsResponseDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import com.company.shtopor.vzvodbot.session.ViolationDraftSession;
import com.company.shtopor.vzvodbot.session.ViolationDraftSessionStore;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ViolationCreationWizard {

    private static final Logger log = LoggerFactory.getLogger(ViolationCreationWizard.class);

    static final String BTN_ADMIN = "Административка";
    static final String BTN_CRIMINAL = "Уголовка";

    private static final String PREFIX_ARTICLE = "vla";
    private static final String PREFIX_TYPE = "vlt";
    private static final String PREFIX_IMPACT = "vli";
    private static final String CB_ABORT = "vab";

    private final ViolationDraftSessionStore store;
    private final CoreProfileClient coreProfileClient;
    private final ShiftMetricsHandler shiftMetricsHandler;
    private final MainMenuKeyboardFactory mainMenuKeyboardFactory;

    public ViolationCreationWizard(
            ViolationDraftSessionStore store,
            CoreProfileClient coreProfileClient,
            ShiftMetricsHandler shiftMetricsHandler,
            MainMenuKeyboardFactory mainMenuKeyboardFactory
    ) {
        this.store = store;
        this.coreProfileClient = coreProfileClient;
        this.shiftMetricsHandler = shiftMetricsHandler;
        this.mainMenuKeyboardFactory = mainMenuKeyboardFactory;
    }

    public boolean isActive(long chatId) {
        return store.get(chatId).isPresent();
    }

    public void clear(long chatId) {
        store.clear(chatId);
    }

    public boolean isViolationCallback(String data) {
        return data != null && (data.startsWith(PREFIX_ARTICLE + "|")
                || data.startsWith(PREFIX_TYPE + "|")
                || data.startsWith(PREFIX_IMPACT + "|")
                || CB_ABORT.equals(data));
    }

    public boolean handleMainMenuButton(TelegramLongPollingBot bot, long chatId, String text,
                                        ReplyKeyboardMarkup keyboard) throws TelegramApiException {
        ViolationDraftSession.Kind kind = null;
        if (BTN_ADMIN.equals(text)) {
            kind = ViolationDraftSession.Kind.ADMINISTRATIVE;
        } else if (BTN_CRIMINAL.equals(text)) {
            kind = ViolationDraftSession.Kind.CRIMINAL;
        }
        if (kind == null) {
            return false;
        }
        Optional<BotShiftItemDto> openShift = shiftMetricsHandler.findOpenShift(chatId);
        if (openShift.isEmpty()) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Нет открытой смены — нарушение можно внести только в активную смену.")
                    .replyMarkup(keyboard)
                    .build());
            return true;
        }
        start(bot, chatId, openShift.get().id(), kind);
        return true;
    }

    public boolean handleCallback(TelegramLongPollingBot bot, long chatId, String data, String cqId)
            throws TelegramApiException {
        if (!isViolationCallback(data)) {
            return false;
        }
        answer(bot, cqId);
        if (CB_ABORT.equals(data)) {
            clear(chatId);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Создание нарушения отменено.")
                    .build());
            return true;
        }
        Optional<ViolationDraftSession> opt = store.get(chatId);
        if (opt.isEmpty()) {
            return true;
        }
        ViolationDraftSession session = opt.get();
        if (data.startsWith(PREFIX_ARTICLE + "|")) {
            if (session.getKind() != ViolationDraftSession.Kind.ADMINISTRATIVE
                    || session.getStep() != ViolationDraftSession.Step.ARTICLE) {
                return true;
            }
            session.setArticleId(parseId(data, PREFIX_ARTICLE));
            session.setStep(ViolationDraftSession.Step.IMPACT);
            store.put(chatId, session);
            sendImpactStep(bot, chatId, session);
            return true;
        }
        if (data.startsWith(PREFIX_TYPE + "|")) {
            if (session.getKind() != ViolationDraftSession.Kind.CRIMINAL
                    || session.getStep() != ViolationDraftSession.Step.TYPE) {
                return true;
            }
            session.setTypeId(parseId(data, PREFIX_TYPE));
            session.setStep(ViolationDraftSession.Step.IMPACT);
            store.put(chatId, session);
            sendImpactStep(bot, chatId, session);
            return true;
        }
        if (data.startsWith(PREFIX_IMPACT + "|")) {
            if (session.getStep() != ViolationDraftSession.Step.IMPACT) {
                return true;
            }
            int impactId = parseId(data, PREFIX_IMPACT);
            submit(bot, chatId, session, impactId);
            return true;
        }
        return true;
    }

    private void start(TelegramLongPollingBot bot, long chatId, UUID shiftId, ViolationDraftSession.Kind kind)
            throws TelegramApiException {
        try {
            BotViolationOptionsResponseDto options = coreProfileClient.getViolationOptions();
            ViolationDraftSession session = new ViolationDraftSession();
            session.setShiftId(shiftId);
            session.setKind(kind);
            session.setOptions(options);
            if (kind == ViolationDraftSession.Kind.ADMINISTRATIVE) {
                session.setStep(ViolationDraftSession.Step.ARTICLE);
                store.put(chatId, session);
                sendArticleStep(bot, chatId, session);
            } else {
                session.setStep(ViolationDraftSession.Step.TYPE);
                store.put(chatId, session);
                sendTypeStep(bot, chatId, session);
            }
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(e.userHint())
                    .build());
        } catch (Exception e) {
            log.error("Создание нарушения chatId={}", chatId, e);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось начать создание нарушения (Core).")
                    .build());
        }
    }

    private void sendArticleStep(TelegramLongPollingBot bot, long chatId, ViolationDraftSession session)
            throws TelegramApiException {
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Административное нарушение.\n\nШаг 1/2: выберите статью.")
                .replyMarkup(optionKeyboard(session.getArticles(), PREFIX_ARTICLE))
                .build());
    }

    private void sendTypeStep(TelegramLongPollingBot bot, long chatId, ViolationDraftSession session)
            throws TelegramApiException {
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Уголовное нарушение.\n\nШаг 1/2: выберите тип.")
                .replyMarkup(optionKeyboard(session.getTypes(), PREFIX_TYPE))
                .build());
    }

    private void sendImpactStep(TelegramLongPollingBot bot, long chatId, ViolationDraftSession session)
            throws TelegramApiException {
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Шаг 2/2: выберите воздействие.")
                .replyMarkup(optionKeyboard(session.getImpacts(), PREFIX_IMPACT))
                .build());
    }

    private void submit(TelegramLongPollingBot bot, long chatId, ViolationDraftSession session, int impactId)
            throws TelegramApiException {
        UUID shiftId = session.getShiftId();
        try {
            BotShiftItemDto saved;
            if (session.getKind() == ViolationDraftSession.Kind.ADMINISTRATIVE) {
                saved = coreProfileClient.createAdministrativeViolation(
                        chatId,
                        shiftId,
                        new BotAdministrativeViolationCreateRequestDto(impactId, session.getArticleId()));
            } else {
                saved = coreProfileClient.createCriminalViolation(
                        chatId,
                        shiftId,
                        new BotCriminalViolationCreateRequestDto(impactId, session.getTypeId()));
            }
            clear(chatId);
            String kindLabel = session.getKind() == ViolationDraftSession.Kind.ADMINISTRATIVE
                    ? "Административное нарушение"
                    : "Уголовное нарушение";
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(kindLabel + " сохранено.\n" + ShiftMetricsHandler.formatOpenShiftSummary(saved))
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            clear(chatId);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(e.userHint())
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (Exception e) {
            clear(chatId);
            log.error("Создание нарушения chatId={}", chatId, e);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось сохранить нарушение (Core).")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        }
    }

    private static InlineKeyboardMarkup optionKeyboard(List<BotEnumOptionDto> options, String prefix) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (BotEnumOptionDto option : options) {
            row.add(ib(option.label(), prefix + "|" + option.id()));
            if (row.size() == 2) {
                rows.add(List.copyOf(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            rows.add(List.copyOf(row));
        }
        rows.add(List.of(ib("Отмена", CB_ABORT)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private static int parseId(String data, String prefix) {
        return Integer.parseInt(data.substring(prefix.length() + 1));
    }

    private static InlineKeyboardButton ib(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    private static void answer(TelegramLongPollingBot bot, String cqId) throws TelegramApiException {
        if (cqId != null) {
            bot.execute(AnswerCallbackQuery.builder().callbackQueryId(cqId).build());
        }
    }

    private static String violationErrorHint(int status) {
        return switch (status) {
            case 400 -> "Проверьте выбранные значения и повторите.";
            case 403 -> "Доступ к этой смене запрещён.";
            case 404 -> "Смена не найдена.";
            case 409 -> "Смена уже завершена — нарушение внести нельзя.";
            case 401 -> "Ошибка API-ключа к Core.";
            default -> "Ошибка запроса к Core (код " + status + ").";
        };
    }
}
