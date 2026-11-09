package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.config.TelegramBotProperties;
import com.company.shtopor.vzvodbot.core.BotAddressDto;
import com.company.shtopor.vzvodbot.core.BotAddressPatchDto;
import com.company.shtopor.vzvodbot.core.BotEventItemDto;
import com.company.shtopor.vzvodbot.core.BotEventsResponseDto;
import com.company.shtopor.vzvodbot.core.BotProfileDto;
import com.company.shtopor.vzvodbot.core.BotProfilePatchDto;
import com.company.shtopor.vzvodbot.core.CoreAuthClient;
import com.company.shtopor.vzvodbot.core.BotShiftItemDto;
import com.company.shtopor.vzvodbot.core.BotShiftsResponseDto;
import com.company.shtopor.vzvodbot.core.BotVacationBalanceDto;
import com.company.shtopor.vzvodbot.core.BotVacationsResponseDto;
import com.company.shtopor.vzvodbot.core.BotVocationItemDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import com.company.shtopor.vzvodbot.session.AddressEditSession;
import com.company.shtopor.vzvodbot.session.AddressEditSessionStore;
import com.company.shtopor.vzvodbot.session.BotSession;
import com.company.shtopor.vzvodbot.session.PendingBotInput;
import com.company.shtopor.vzvodbot.session.PendingBotInputRegistry;
import com.company.shtopor.vzvodbot.session.TelegramBotSessionCleaner;
import com.company.shtopor.vzvodbot.logging.RemoteLoggingClient;
import com.company.shtopor.vzvodbot.logging.TelegramLogActorResolver;
import com.company.shtopor.vzvodbot.logging.TelegramLogMessages;
import com.company.shtopor.vzvodbot.session.TelegramSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.DefaultBotOptions.ProxyType;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class VzvodTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(VzvodTelegramBot.class);

    private static final String CB_MAIN_MENU = "main_menu";
    private static final String CB_BACK_TO_PROFILE = "back_prof";
    private static final String CB_ADDR_BACK = "addr_back";
    private static final String PREFIX_CITY_QUICK = "adq";
    private static final String CB_EDIT_BP = "edit_bp";
    private static final String CB_EDIT_ADDR_REG = "edit_addr_reg";
    private static final String CB_EDIT_ADDR_HAB = "edit_addr_hab";
    private static final String CB_MED_YES = "edit_med_y";
    private static final String CB_MED_NO = "edit_med_n";
    private static final String PREFIX_TYPE = "adt";
    private static final String PREFIX_STATUS = "ads";

    private static final Pattern INDEX_6 = Pattern.compile("^\\d{6}$");

    private static final DateTimeFormatter DATE_RU = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru-RU"));
    private static final DateTimeFormatter TIME_SHORT = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru-RU"));
    private static final int MAX_BOT_MESSAGE = 4000;

    private final TelegramBotProperties telegramProps;
    private final CoreAuthClient coreAuthClient;
    private final CoreProfileClient coreProfileClient;
    private final TelegramSessionRegistry sessions;
    private final PendingBotInputRegistry pendingInputs;
    private final AddressEditSessionStore addressSessions;
    private final ShiftCreationWizard shiftWizard;
    private final ShiftEndTimeHandler shiftEndTimeHandler;
    private final ShiftQuickEditHandler shiftQuickEditHandler;
    private final ShiftMetricsHandler shiftMetricsHandler;
    private final ViolationCreationWizard violationWizard;
    private final MainMenuKeyboardFactory mainMenuKeyboardFactory;
    private final TelegramBotSessionCleaner sessionCleaner;
    private final RemoteLoggingClient remoteLogging;
    private final TelegramLogActorResolver logActorResolver;

    public VzvodTelegramBot(
            TelegramBotProperties telegramProps,
            CoreAuthClient coreAuthClient,
            CoreProfileClient coreProfileClient,
            TelegramSessionRegistry sessions,
            PendingBotInputRegistry pendingInputs,
            AddressEditSessionStore addressSessions,
            ShiftCreationWizard shiftWizard,
            ShiftEndTimeHandler shiftEndTimeHandler,
            ShiftQuickEditHandler shiftQuickEditHandler,
            ShiftMetricsHandler shiftMetricsHandler,
            ViolationCreationWizard violationWizard,
            MainMenuKeyboardFactory mainMenuKeyboardFactory,
            TelegramBotSessionCleaner sessionCleaner,
            RemoteLoggingClient remoteLogging,
            TelegramLogActorResolver logActorResolver
    ) {
        super(buildBotOptions(telegramProps));
        this.telegramProps = telegramProps;
        this.coreAuthClient = coreAuthClient;
        this.coreProfileClient = coreProfileClient;
        this.sessions = sessions;
        this.pendingInputs = pendingInputs;
        this.addressSessions = addressSessions;
        this.shiftWizard = shiftWizard;
        this.shiftEndTimeHandler = shiftEndTimeHandler;
        this.shiftQuickEditHandler = shiftQuickEditHandler;
        this.shiftMetricsHandler = shiftMetricsHandler;
        this.violationWizard = violationWizard;
        this.mainMenuKeyboardFactory = mainMenuKeyboardFactory;
        this.sessionCleaner = sessionCleaner;
        this.remoteLogging = remoteLogging;
        this.logActorResolver = logActorResolver;
    }

    public void sendAndLog(SendMessage message) throws TelegramApiException {
        String text = message.getText();
        if (text != null && !text.isBlank()) {
            String actor = logActorResolver.resolve(Long.parseLong(message.getChatId()));
            remoteLogging.logMain(TelegramLogMessages.programReply(actor, truncateForLog(text)));
        }
        execute(message);
    }

    private static String truncateForLog(String text) {
        if (text.length() <= 500) {
            return text;
        }
        return text.substring(0, 500) + "…";
    }

    private void clearShiftFlows(long chatId) {
        shiftWizard.clear(chatId);
        shiftEndTimeHandler.clearAwaiting(chatId);
        shiftQuickEditHandler.clear(chatId);
        violationWizard.clear(chatId);
    }

    private void clearLocalSession(long chatId) {
        sessionCleaner.clear(chatId);
    }

    private static DefaultBotOptions buildBotOptions(TelegramBotProperties p) {
        DefaultBotOptions o = new DefaultBotOptions();
        String host = p.getProxyHost() == null ? "" : p.getProxyHost().trim();
        if (!host.isEmpty() && p.getProxyPort() > 0) {
            ProxyType type = parseProxyType(p.getProxyType());
            if (type == ProxyType.NO_PROXY) {
                type = ProxyType.HTTP;
                log.warn("telegram.bot.proxy-type=NO_PROXY при заданном proxy-host — используется HTTP");
            }
            o.setProxyHost(host);
            o.setProxyPort(p.getProxyPort());
            o.setProxyType(type);
            log.info("Telegram Bot API через прокси {}:{} ({})", host, p.getProxyPort(), type);
        }
        return o;
    }

    private static ProxyType parseProxyType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ProxyType.HTTP;
        }
        try {
            return ProxyType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Неизвестный telegram.bot.proxy-type={} — используется HTTP", raw);
            return ProxyType.HTTP;
        }
    }

    @Override
    public String getBotToken() {
        return telegramProps.getToken();
    }

    @Override
    public String getBotUsername() {
        return telegramProps.getUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        logIncomingUpdate(update);
        try {
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
                return;
            }
            if (!update.hasMessage()) {
                log.debug("Telegram update updateId={} (без message)", update.getUpdateId());
                return;
            }
            Message msg = update.getMessage();
            Long chatId = msg.getChatId();
            log.debug("Telegram update updateId={} chatId={} text={} hasContact={}",
                    update.getUpdateId(),
                    chatId,
                    msg.hasText() ? msg.getText() : null,
                    msg.hasContact());

            if (msg.hasContact()) {
                handleContact(chatId, msg.getContact());
                return;
            }
            if (msg.hasText()) {
                String text = msg.getText().trim();
                if (MainMenuKeyboardFactory.BTN_BACK.equals(text)) {
                    handleReplyBack(chatId);
                    return;
                }
                if (addressSessions.get(chatId).isPresent() && !text.startsWith("/")) {
                    handleAddressTextStep(chatId, text);
                    return;
                }
                if (shiftWizard.isActive(chatId) && !text.startsWith("/")) {
                    shiftWizard.onText(this, chatId, text);
                    return;
                }
                if (shiftEndTimeHandler.handleText(this, chatId, text)) {
                    return;
                }
                if (shiftQuickEditHandler.handleText(this, chatId, text)) {
                    return;
                }
                if (sessions.find(chatId).isPresent()
                        && shiftMetricsHandler.handleMainMenuButton(this, chatId, text, mainMenuKeyboardFactory.build(chatId))) {
                    return;
                }
                if (sessions.find(chatId).isPresent()
                        && violationWizard.handleMainMenuButton(this, chatId, text, mainMenuKeyboardFactory.build(chatId))) {
                    return;
                }
                PendingBotInput pending = pendingInputs.get(chatId).orElse(null);
                if (pending == PendingBotInput.BREASTPLATE && !text.startsWith("/")) {
                    handleBreastplateText(chatId, text);
                    return;
                }
                if (text.startsWith("/start")) {
                    handleStart(chatId);
                    return;
                }
                if (MainMenuKeyboardFactory.BTN_MENU.equals(text)) {
                    handleMenuDialog(chatId);
                    return;
                }
                if ("/profile".equalsIgnoreCase(text)) {
                    handleProfile(chatId);
                    return;
                }
                if ("/edit_profile".equalsIgnoreCase(text)) {
                    handleEditProfileMenu(chatId);
                    return;
                }
                if ("/logout".equalsIgnoreCase(text)) {
                    handleLogout(chatId);
                    return;
                }
                if (MainMenuKeyboardFactory.BTN_SHIFTS.equals(text)) {
                    handleMyShifts(chatId);
                    return;
                }
            }
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Нажмите /start для входа или выберите пункт меню.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (TelegramApiException e) {
            log.error("Telegram API error", e);
            remoteLogging.logError("Ошибка Telegram API: " + e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка обработки update", e);
            remoteLogging.logError("Ошибка обработки сообщения: " + e.getMessage());
        }
    }

    private void logIncomingUpdate(Update update) {
        if (update.hasCallbackQuery()) {
            CallbackQuery cq = update.getCallbackQuery();
            long chatId = cq.getMessage() != null ? cq.getMessage().getChatId() : cq.getFrom().getId();
            String actor = logActorResolver.resolve(chatId);
            remoteLogging.logMain(TelegramLogMessages.userAction(actor, cq.getData()));
            return;
        }
        if (!update.hasMessage()) {
            return;
        }
        Message msg = update.getMessage();
        Long chatId = msg.getChatId();
        if (msg.hasContact()) {
            return;
        }
        String actor = logActorResolver.resolve(chatId);
        if (msg.hasText()) {
            remoteLogging.logMain(TelegramLogMessages.userMessage(actor, msg.getText()));
        }
    }

    private void handleReplyBack(Long chatId) throws TelegramApiException {
        if (pendingInputs.get(chatId).orElse(null) == PendingBotInput.BREASTPLATE) {
            pendingInputs.clear(chatId);
            handleEditProfileMenu(chatId);
            return;
        }
        if (shiftWizard.isActive(chatId)) {
            shiftWizard.onBack(this, chatId);
            return;
        }
        if (shiftQuickEditHandler.clearIfAwaitingDate(chatId)) {
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Изменение даты отменено.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
            return;
        }
        if (shiftQuickEditHandler.clearIfPartnerPick(chatId)) {
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Выбор напарника отменён.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
            return;
        }
        if (shiftEndTimeHandler.isConfirmFlowActive(chatId)) {
            shiftEndTimeHandler.clearAwaiting(chatId);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Завершение смены отменено.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
            return;
        }
        if (shiftEndTimeHandler.clearAwaitingIfPresent(chatId)) {
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Ввод времени окончания отменён.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
            return;
        }
        if (addressSessions.get(chatId).isPresent()) {
            handleAddressBack(chatId);
            return;
        }
        if (sessions.find(chatId).isPresent()) {
            sendMainMenu(chatId, "Главное меню.");
            return;
        }
        sendShareContactPrompt(chatId);
    }

    private void goToMainMenu(Long chatId) throws TelegramApiException {
        pendingInputs.clear(chatId);
        addressSessions.clear(chatId);
        clearShiftFlows(chatId);
        if (sessions.find(chatId).isPresent()) {
            sendMainMenu(chatId, "Главное меню.");
        } else {
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Для работы с ботом нажмите /start и при необходимости отправьте контакт.")
                    .replyMarkup(mainMenuKeyboardFactory.backOnly())
                    .build());
        }
    }

    private void handleMyShifts(Long chatId) throws TelegramApiException {
        try {
            BotShiftsResponseDto r = coreProfileClient.getShifts(chatId);
            String text = truncateTelegram(formatShifts(r));
            List<BotShiftItemDto> items = r != null && r.items() != null ? r.items() : List.of();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            if (items.stream().noneMatch(it -> it.endTime() == null)) {
                rows.add(List.of(ib("➕ Новая смена", ShiftCreationWizard.CB_SHIFT_NEW)));
            }
            for (BotShiftItemDto it : items) {
                if (it.id() == null) {
                    continue;
                }
                String shortDate = it.date() != null ? it.date().format(DATE_RU) : "?";
                List<InlineKeyboardButton> row = new ArrayList<>();
                if (it.endTime() == null) {
                    row.add(ib("Завершить · " + shortDate, ShiftEndTimeHandler.PREFIX_END_PICK + "|" + it.id()));
                    row.add(ib("Изменить · " + shortDate, ShiftQuickEditHandler.PREFIX_EDIT_MENU + "|" + it.id()));
                }
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            execute(coreAccessFailureMessage(chatId, e.getStatus()));
        } catch (Exception e) {
            log.error("Смены chatId={}", chatId, e);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось загрузить смены (Core).")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        }
    }

    private void handleMenuDialog(Long chatId) throws TelegramApiException {
        sendAndLog(SendMessage.builder()
                .chatId(chatId)
                .text("Выберите раздел:")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                List.of(ib("📋 Профиль", MainMenuKeyboardFactory.CB_MENU_PROFILE)),
                                List.of(ib("🏖 Отпуска", MainMenuKeyboardFactory.CB_MENU_VACATIONS)),
                                List.of(ib("📅 Возможные мероприятия", MainMenuKeyboardFactory.CB_MENU_EVENTS))))
                        .build())
                .build());
    }

    private void handlePossibleEvents(Long chatId) throws TelegramApiException {
        try {
            BotEventsResponseDto r = coreProfileClient.getEvents(chatId);
            String text = truncateTelegram(formatEvents(r));
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            execute(coreAccessFailureMessage(chatId, e.getStatus()));
        } catch (Exception e) {
            log.error("Мероприятия chatId={}", chatId, e);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось загрузить мероприятия (Core).")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        }
    }

    private static String formatEvents(BotEventsResponseDto r) {
        List<BotEventItemDto> items = r != null && r.items() != null ? r.items() : List.of();
        StringBuilder sb = new StringBuilder();
        sb.append("📅 Возможные мероприятия\n\n");
        if (items.isEmpty()) {
            sb.append("Нет запланированных мероприятий на сегодня и ближайшее время.");
            return sb.toString();
        }
        for (BotEventItemDto it : items) {
            String dateStr = it.date() != null ? it.date().format(DATE_RU) : "—";
            sb.append("• ")
                    .append(dateStr)
                    .append(" — ")
                    .append(nnz(it.name()))
                    .append('\n');
        }
        return sb.toString();
    }

    private void handleMyVacations(Long chatId) throws TelegramApiException {
        try {
            BotVacationsResponseDto r = coreProfileClient.getVacations(chatId);
            String text = truncateTelegram(formatVacations(r));
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            execute(coreAccessFailureMessage(chatId, e.getStatus()));
        } catch (Exception e) {
            log.error("Отпуска chatId={}", chatId, e);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось загрузить отпуска (Core).")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        }
    }

    private static String formatShifts(BotShiftsResponseDto r) {
        List<BotShiftItemDto> items = r != null && r.items() != null ? r.items() : List.of();
        StringBuilder sb = new StringBuilder();
        sb.append("🔄 Мои смены (последние записи из учётной системы).\n\n");
        if (items.isEmpty()) {
            sb.append("Пока нет ни одной смены, где вы указаны участником.\n");
            return sb.toString();
        }
        for (BotShiftItemDto it : items) {
            String dateStr = it.date() != null ? it.date().format(DATE_RU) : "—";
            String t0 = it.startTime() != null ? it.startTime().format(TIME_SHORT) : "—";
            String interval;
            if (it.endTime() != null) {
                interval = t0 + "–" + it.endTime().format(TIME_SHORT);
            } else {
                interval = t0 + "–… (ожидается окончание)";
            }
            sb.append("• ")
                    .append(dateStr)
                    .append(" · ")
                    .append(nnz(it.route()))
                    .append(" · ")
                    .append(nnz(it.shiftType()))
                    .append(" · ")
                    .append(nnz(it.departmentToday()))
                    .append(" · ")
                    .append(interval)
                    .append('\n');
        }
        return sb.toString();
    }

    private static String formatVacations(BotVacationsResponseDto r) {
        StringBuilder sb = new StringBuilder();
        sb.append("🏖 Отпуска\n\n");
        BotVacationBalanceDto b = r != null ? r.balance() : null;
        if (b != null) {
            sb.append("По данным учёта:\n")
                    .append("• Положено: ")
                    .append(b.entitled())
                    .append(" ")
                    .append(daysWord(b.entitled()))
                    .append("\n• Уже использовано: ")
                    .append(b.used())
                    .append(" ")
                    .append(daysWord(b.used()))
                    .append("\n• Можно взять ещё: ")
                    .append(b.available())
                    .append(" ")
                    .append(daysWord(b.available()))
                    .append("\n\n");
        }
        List<BotVocationItemDto> items = r != null && r.items() != null ? r.items() : List.of();
        if (items.isEmpty()) {
            sb.append("Записей об отпусках пока нет.\n");
        } else {
            sb.append("Последние отпуска:\n");
            int n = 1;
            for (BotVocationItemDto v : items) {
                String sd = v.startDate() != null ? v.startDate().format(DATE_RU) : "—";
                String ed = v.endDate() != null ? v.endDate().format(DATE_RU) : "—";
                sb.append(n++).append(") ").append(sd).append(" — ").append(ed);
                if (v.countOfDays() != null) {
                    sb.append(", ").append(v.countOfDays()).append(" дн.");
                }
                if (v.typeLabel() != null && !v.typeLabel().isBlank()) {
                    sb.append(" · ").append(v.typeLabel());
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String nnz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String daysWord(int n) {
        int a = Math.abs(n) % 100;
        int b = a % 10;
        if (a > 10 && a < 20) {
            return "дней";
        }
        if (b > 1 && b < 5) {
            return "дня";
        }
        if (b == 1) {
            return "день";
        }
        return "дней";
    }

    private static String truncateTelegram(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_BOT_MESSAGE) {
            return text;
        }
        return text.substring(0, MAX_BOT_MESSAGE - 80) + "\n\n… (сообщение обрезано)";
    }

    private void handleCallbackQuery(CallbackQuery cq) throws TelegramApiException {
        String data = cq.getData();
        Long chatId = cq.getMessage().getChatId();
        String cqId = cq.getId();
        if (data == null) {
            answerCallback(cqId);
            return;
        }
        if (CB_MAIN_MENU.equals(data)) {
            answerCallback(cqId);
            goToMainMenu(chatId);
            return;
        }
        if (CB_BACK_TO_PROFILE.equals(data)) {
            answerCallback(cqId);
            handleProfile(chatId);
            return;
        }
        if (MainMenuKeyboardFactory.CB_MENU_PROFILE.equals(data)) {
            answerCallback(cqId);
            handleProfile(chatId);
            return;
        }
        if (MainMenuKeyboardFactory.CB_MENU_VACATIONS.equals(data)) {
            answerCallback(cqId);
            handleMyVacations(chatId);
            return;
        }
        if (MainMenuKeyboardFactory.CB_MENU_EVENTS.equals(data)) {
            answerCallback(cqId);
            handlePossibleEvents(chatId);
            return;
        }
        if (CB_ADDR_BACK.equals(data)) {
            answerCallback(cqId);
            if (addressSessions.get(chatId).isPresent()) {
                handleAddressBack(chatId);
            }
            return;
        }

        if (shiftWizard.handleCallback(this, chatId, data, cqId)) {
            return;
        }
        if (violationWizard.handleCallback(this, chatId, data, cqId)) {
            return;
        }
        if (shiftWizard.isActive(chatId) && shiftEndTimeHandler.isShiftEndCallback(data)) {
            answerCallback(cqId);
            return;
        }
        if (shiftEndTimeHandler.handleCallback(this, chatId, data, cqId)) {
            return;
        }
        if (shiftWizard.isActive(chatId) && shiftQuickEditHandler.isQuickEditCallback(data)) {
            answerCallback(cqId);
            return;
        }
        if (shiftQuickEditHandler.handleCallback(this, chatId, data, cqId)) {
            return;
        }

        Optional<AddressEditSession> sessionOpt = addressSessions.get(chatId);
        if (sessionOpt.isPresent()) {
            AddressEditSession s = sessionOpt.get();
            if (data.startsWith(PREFIX_CITY_QUICK + "|")) {
                if (handleCityQuickCallback(chatId, cqId, s, data)) {
                    return;
                }
            }
            if (data.startsWith(PREFIX_TYPE + "|")) {
                if (handleAddressTypeCallback(chatId, cqId, s, data)) {
                    return;
                }
            } else if (data.startsWith(PREFIX_STATUS + "|")) {
                if (handleAddressStatusCallback(chatId, cqId, s, data)) {
                    return;
                }
            }
        }

        switch (data) {
            case CB_EDIT_BP -> {
                answerCallback(cqId);
                addressSessions.clear(chatId);
                clearShiftFlows(chatId);
                pendingInputs.set(chatId, PendingBotInput.BREASTPLATE);
                sendAndLog(SendMessage.builder()
                        .chatId(chatId)
                        .text("Введите нагрудный знак ровно 8 символов одним сообщением (латиница или цифры).")
                        .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                        .build());
            }
            case CB_EDIT_ADDR_REG -> {
                answerCallback(cqId);
                pendingInputs.clear(chatId);
                clearShiftFlows(chatId);
                AddressEditSession s = new AddressEditSession(AddressEditSession.Target.REGISTRATION);
                addressSessions.put(chatId, s);
                sendAddressStepPrompt(chatId, s);
            }
            case CB_EDIT_ADDR_HAB -> {
                answerCallback(cqId);
                pendingInputs.clear(chatId);
                clearShiftFlows(chatId);
                AddressEditSession s = new AddressEditSession(AddressEditSession.Target.HABITATION);
                addressSessions.put(chatId, s);
                sendAddressStepPrompt(chatId, s);
            }
            case CB_MED_YES -> applyMedical(chatId, cqId, true);
            case CB_MED_NO -> applyMedical(chatId, cqId, false);
            default -> answerCallback(cqId);
        }
    }

    private boolean handleAddressTypeCallback(Long chatId, String cqId, AddressEditSession s, String data)
            throws TelegramApiException {
        if (s.getStep() != 6) {
            answerCallback(cqId);
            return true;
        }
        String[] p = data.split("\\|", 3);
        if (p.length != 3 || !expectedTargetLetter(s).equals(p[1])) {
            answerCallback(cqId);
            return true;
        }
        s.setTypeOfHousingId(p[2]);
        s.setStep(7);
        answerCallback(cqId);
        sendStatusKeyboard(chatId, s);
        return true;
    }

    private boolean handleAddressStatusCallback(Long chatId, String cqId, AddressEditSession s, String data)
            throws TelegramApiException {
        if (s.getStep() != 7) {
            answerCallback(cqId);
            return true;
        }
        String[] p = data.split("\\|", 3);
        if (p.length != 3 || !expectedTargetLetter(s).equals(p[1])) {
            answerCallback(cqId);
            return true;
        }
        s.setStatusOfHousingId(p[2]);
        answerCallback(cqId);
        submitAddressPatch(chatId, s);
        return true;
    }

    private static String expectedTargetLetter(AddressEditSession s) {
        return s.getTarget() == AddressEditSession.Target.REGISTRATION ? "R" : "H";
    }

    private void submitAddressPatch(Long chatId, AddressEditSession s) throws TelegramApiException {
        BotAddressPatchDto addr = new BotAddressPatchDto(
                s.getIndex(),
                s.getCity(),
                s.getStreet(),
                s.getHouseNumber(),
                s.getBody(),
                s.getFlat(),
                s.getTypeOfHousingId(),
                s.getStatusOfHousingId()
        );
        BotProfilePatchDto patch = s.getTarget() == AddressEditSession.Target.REGISTRATION
                ? new BotProfilePatchDto(null, null, addr, null)
                : new BotProfilePatchDto(null, null, null, addr);
        try {
            coreProfileClient.updateProfile(chatId, patch);
            addressSessions.clear(chatId);
            clearShiftFlows(chatId);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Адрес сохранён в карточке.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            addressSessions.clear(chatId);
            clearShiftFlows(chatId);
            execute(coreAccessFailureMessage(chatId, e.getStatus()));
        } catch (Exception e) {
            log.error("Адрес chatId={}", chatId, e);
            addressSessions.clear(chatId);
            clearShiftFlows(chatId);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось связаться с Core.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        }
    }

    private void sendTypeKeyboard(Long chatId, AddressEditSession s) throws TelegramApiException {
        String t = expectedTargetLetter(s);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                ib("Квартира", PREFIX_TYPE + "|" + t + "|A"),
                ib("Комната", PREFIX_TYPE + "|" + t + "|B"),
                ib("Частный дом", PREFIX_TYPE + "|" + t + "|C")));
        sendAndLog(SendMessage.builder()
                .chatId(chatId)
                .text(addressPromptPrefix(s.getTarget()) + "Выберите тип жилья.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void sendStatusKeyboard(Long chatId, AddressEditSession s) throws TelegramApiException {
        String t = expectedTargetLetter(s);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                ib("Собственность", PREFIX_STATUS + "|" + t + "|A"),
                ib("Субаренда", PREFIX_STATUS + "|" + t + "|B")));
        rows.add(List.of(
                ib("Аренда", PREFIX_STATUS + "|" + t + "|C"),
                ib("Совместное", PREFIX_STATUS + "|" + t + "|D")));
        sendAndLog(SendMessage.builder()
                .chatId(chatId)
                .text(addressPromptPrefix(s.getTarget()) + "Выберите статус жилья.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private static InlineKeyboardButton ib(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private static InlineKeyboardMarkup cityStepKeyboard(AddressEditSession s) {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(ib("Санкт-Петербург", PREFIX_CITY_QUICK + "|" + expectedTargetLetter(s)))))
                .build();
    }

    private void sendAddressStepPrompt(Long chatId, AddressEditSession s) throws TelegramApiException {
        String prefix = addressPromptPrefix(s.getTarget());
        switch (s.getStep()) {
            case 0 -> sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(prefix + "Индекс (6 цифр), например 190000.")
                    .build());
            case 1 -> sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(prefix + "Город (как в документе) или кнопка ниже.")
                    .replyMarkup(cityStepKeyboard(s))
                    .build());
            case 2 -> sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(prefix + "Улица.")
                    .build());
            case 3 -> sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(prefix + "Номер дома (до 5 символов).")
                    .build());
            case 4 -> sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(prefix + "Корпус / строение (или «-» если нет, до 4 символов).")
                    .build());
            case 5 -> sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(prefix + "Квартира (или «-» если нет, до 10 символов).")
                    .build());
            default -> {
            }
        }
    }

    private void handleAddressBack(Long chatId) throws TelegramApiException {
        Optional<AddressEditSession> opt = addressSessions.get(chatId);
        if (opt.isEmpty()) {
            return;
        }
        AddressEditSession s = opt.get();
        int st = s.getStep();
        if (st <= 0) {
            addressSessions.clear(chatId);
            handleEditProfileMenu(chatId);
            return;
        }
        if (st == 7) {
            s.setStatusOfHousingId(null);
            s.setStep(6);
            sendTypeKeyboard(chatId, s);
            return;
        }
        if (st == 6) {
            s.setTypeOfHousingId(null);
            s.setStep(5);
            sendAddressStepPrompt(chatId, s);
            return;
        }
        s.setStep(st - 1);
        sendAddressStepPrompt(chatId, s);
    }

    private boolean handleCityQuickCallback(Long chatId, String cqId, AddressEditSession s, String data)
            throws TelegramApiException {
        if (s.getStep() != 1) {
            answerCallback(cqId);
            return true;
        }
        String[] p = data.split("\\|", 2);
        if (p.length != 2 || !PREFIX_CITY_QUICK.equals(p[0]) || !expectedTargetLetter(s).equals(p[1])) {
            answerCallback(cqId);
            return true;
        }
        answerCallback(cqId);
        s.setCity("Санкт-Петербург");
        s.setStep(2);
        sendAddressStepPrompt(chatId, s);
        return true;
    }

    private static String addressPromptPrefix(AddressEditSession.Target target) {
        return target == AddressEditSession.Target.REGISTRATION
                ? "Адрес регистрации.\n"
                : "Адрес проживания.\n";
    }

    private void handleAddressTextStep(Long chatId, String text) throws TelegramApiException {
        AddressEditSession s = addressSessions.get(chatId).orElse(null);
        if (s == null || s.getStep() > 5) {
            return;
        }
        String v = text.trim();
        String prefix = addressPromptPrefix(s.getTarget());
        switch (s.getStep()) {
            case 0 -> {
                if (!INDEX_6.matcher(v).matches()) {
                    sendAndLog(SendMessage.builder()
                            .chatId(chatId)
                            .text(prefix + "Нужен индекс из 6 цифр. Повторите ввод.")
                            .build());
                    return;
                }
                s.setIndex(v);
                s.setStep(1);
                sendAddressStepPrompt(chatId, s);
            }
            case 1 -> {
                if (v.isEmpty() || v.length() > 50) {
                    sendAndLog(SendMessage.builder()
                            .chatId(chatId)
                            .text(prefix + "Город обязателен, до 50 символов.")
                            .replyMarkup(cityStepKeyboard(s))
                            .build());
                    return;
                }
                s.setCity(v);
                s.setStep(2);
                sendAddressStepPrompt(chatId, s);
            }
            case 2 -> {
                if (v.isEmpty() || v.length() > 255) {
                    sendAndLog(SendMessage.builder()
                            .chatId(chatId)
                            .text(prefix + "Улица обязательна, до 255 символов.")
                            .build());
                    return;
                }
                s.setStreet(v);
                s.setStep(3);
                sendAddressStepPrompt(chatId, s);
            }
            case 3 -> {
                if (v.isEmpty() || v.length() > 5) {
                    sendAndLog(SendMessage.builder()
                            .chatId(chatId)
                            .text(prefix + "Номер дома обязателен, до 5 символов.")
                            .build());
                    return;
                }
                s.setHouseNumber(v);
                s.setStep(4);
                sendAddressStepPrompt(chatId, s);
            }
            case 4 -> {
                if ("-".equals(v)) {
                    s.setBody(null);
                } else if (v.length() > 4) {
                    sendAndLog(SendMessage.builder()
                            .chatId(chatId)
                            .text(prefix + "Корпус не длиннее 4 символов или «-».")
                            .build());
                    return;
                } else {
                    s.setBody(v.isEmpty() ? null : v);
                }
                s.setStep(5);
                sendAddressStepPrompt(chatId, s);
            }
            case 5 -> {
                if ("-".equals(v)) {
                    s.setFlat(null);
                } else if (v.length() > 10) {
                    sendAndLog(SendMessage.builder()
                            .chatId(chatId)
                            .text(prefix + "Квартира не длиннее 10 символов или «-».")
                            .build());
                    return;
                } else {
                    s.setFlat(v.isEmpty() ? null : v);
                }
                s.setStep(6);
                sendTypeKeyboard(chatId, s);
            }
            default -> {
            }
        }
    }

    private void applyMedical(Long chatId, String callbackQueryId, boolean value) throws TelegramApiException {
        addressSessions.clear(chatId);
        clearShiftFlows(chatId);
        try {
            coreProfileClient.updateProfile(chatId, new BotProfilePatchDto(null, value, null, null));
            answerCallback(callbackQueryId);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(value ? "Профосмотр отмечен как пройден." : "Профосмотр отмечен как не пройден.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            answerCallback(callbackQueryId);
            execute(coreAccessFailureMessage(chatId, e.getStatus()));
        } catch (Exception e) {
            log.error("Профосмотр chatId={}", chatId, e);
            answerCallback(callbackQueryId);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось связаться с Core.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        }
    }

    private void handleBreastplateText(Long chatId, String text) throws TelegramApiException {
        String bp = text.trim();
        if (bp.length() != 8) {
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Нужно ровно 8 символов. Попробуйте ещё раз или отмените: /edit_profile.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
            return;
        }
        try {
            coreProfileClient.updateProfile(chatId, new BotProfilePatchDto(bp, null, null, null));
            pendingInputs.clear(chatId);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Нагрудный знак обновлён: " + bp)
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            execute(coreAccessFailureMessage(chatId, e.getStatus()));
        } catch (Exception e) {
            log.error("Нагрудный знак chatId={}", chatId, e);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось связаться с Core.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        }
    }

    private void handleEditProfileMenu(Long chatId) throws TelegramApiException {
        pendingInputs.clear(chatId);
        addressSessions.clear(chatId);
        clearShiftFlows(chatId);
        try {
            coreProfileClient.getProfile(chatId);
        } catch (CoreProfileClient.CoreProfileException e) {
            execute(coreAccessFailureMessage(chatId, e.getStatus()));
            return;
        } catch (Exception e) {
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось связаться с Core.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(InlineKeyboardButton.builder()
                .text("Нагрудный знак")
                .callbackData(CB_EDIT_BP)
                .build()));
        rows.add(List.of(InlineKeyboardButton.builder()
                .text("Адрес регистрации")
                .callbackData(CB_EDIT_ADDR_REG)
                .build()));
        rows.add(List.of(InlineKeyboardButton.builder()
                .text("Адрес проживания")
                .callbackData(CB_EDIT_ADDR_HAB)
                .build()));
        rows.add(List.of(
                InlineKeyboardButton.builder().text("✅ Профосмотр пройден").callbackData(CB_MED_YES).build(),
                InlineKeyboardButton.builder().text("❌ Не пройден").callbackData(CB_MED_NO).build()));
        sendAndLog(SendMessage.builder()
                .chatId(chatId)
                .text("Контакты в боте: только адреса. Телефон меняется только в учётной системе.\n\nЧто изменить?")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void answerCallback(String callbackQueryId) throws TelegramApiException {
        execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build());
    }

    private void handleStart(Long chatId) throws TelegramApiException {
        pendingInputs.clear(chatId);
        addressSessions.clear(chatId);
        clearShiftFlows(chatId);
        if (sessions.find(chatId).isPresent()) {
            sendMainMenu(chatId, "Вы уже вошли. Выберите раздел:");
            return;
        }
        try {
            BotProfileDto p = coreProfileClient.getProfile(chatId);
            sessions.put(chatId, new BotSession(p.userId(), p.displayName()));
            sendMainMenu(chatId, "С возвращением, " + p.displayName() + "!");
            return;
        } catch (CoreProfileClient.CoreProfileException e) {
            if (e.getStatus() == 403 || e.getStatus() == 404) {
                clearLocalSession(chatId);
                execute(coreAccessFailureMessage(chatId, e.getStatus()));
                return;
            }
            log.debug("Восстановление сессии по chatId не удалось: HTTP {}", e.getStatus());
        } catch (Exception e) {
            log.debug("Восстановление сессии: {}", e.toString());
        }
        sendShareContactPrompt(chatId);
    }

    private void handleProfile(Long chatId) throws TelegramApiException {
        try {
            BotProfileDto p = coreProfileClient.getProfile(chatId);
            sessions.put(chatId, new BotSession(p.userId(), p.displayName()));
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(formatProfile(p))
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            execute(coreAccessFailureMessage(chatId, e.getStatus()));
        } catch (Exception e) {
            log.error("Профиль chatId={}", chatId, e);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось связаться с Core для загрузки профиля.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        }
    }

    private SendMessage coreAccessFailureMessage(long chatId, int status) {
        if (status == 403 || status == 404) {
            clearLocalSession(chatId);
        }
        return SendMessage.builder()
                .chatId(chatId)
                .text(profileErrorHint(status))
                .replyMarkup(status == 403 || status == 404
                        ? mainMenuKeyboardFactory.shareContactLogin()
                        : mainMenuKeyboardFactory.build(chatId))
                .build();
    }

    private static String profileErrorHint(int status) {
        return switch (status) {
            case 404 -> "Доступ к боту закрыт. Для входа нажмите /start и отправьте контакт.";
            case 403 -> "Доступ к боту закрыт (сотрудник не в статусе «В строю» или привязка снята).";
            case 401 -> "Ошибка API-ключа к Core. Обратитесь к администратору.";
            default -> "Ошибка запроса к Core (код " + status + ").";
        };
    }

    private void handleLogout(Long chatId) throws TelegramApiException {
        clearLocalSession(chatId);
        sendAndLog(SendMessage.builder()
                .chatId(chatId)
                .text("Вы вышли из сессии бота. Для входа снова нажмите /start и отправьте контакт кнопкой ниже.")
                .replyMarkup(mainMenuKeyboardFactory.shareContactLogin())
                .build());
    }

    private static String formatProfile(BotProfileDto p) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Профиль\n\n");
        sb.append("ФИО: ").append(Optional.ofNullable(p.displayName()).orElse("—")).append('\n');
        sb.append("Должность: ").append(Optional.ofNullable(p.post()).orElse("—")).append('\n');
        sb.append("Звание: ").append(Optional.ofNullable(p.rank()).orElse("—")).append('\n');
        sb.append("Отделение: ").append(Optional.ofNullable(p.department()).orElse("—")).append('\n');
        sb.append("Нагрудный знак: ").append(Optional.ofNullable(p.breastplate()).orElse("—")).append('\n');
        sb.append("Телефон: ").append(Optional.ofNullable(p.mobilePhoneMasked()).orElse("не указан")).append(" (изменение только в учётной системе)\n");
        sb.append("Регистрация: ").append(addressLine(p.registration())).append('\n');
        sb.append("Проживание: ").append(addressLine(p.habitation())).append('\n');
        boolean med = p.medicalExamination() != null && p.medicalExamination();
        sb.append("Профосмотр: ").append(med ? "✅ пройден" : "❌ не отмечен").append('\n');
        if (p.idCardIssued() != null && p.idCardUntil() != null) {
            sb.append("Удостоверение: с ")
                    .append(p.idCardIssued().format(DATE_RU))
                    .append(" по ")
                    .append(p.idCardUntil().format(DATE_RU));
        } else {
            sb.append("Удостоверение: данные не заполнены");
        }
        sb.append("\n\nРедактирование адресов и служебных полей: /edit_profile");
        return sb.toString();
    }

    private static String addressLine(BotAddressDto a) {
        if (a == null) {
            return "—";
        }
        if (a.summary() != null && !a.summary().isBlank()) {
            return a.summary();
        }
        return "—";
    }

    private void sendShareContactPrompt(Long chatId) throws TelegramApiException {
        sendAndLog(SendMessage.builder()
                .chatId(chatId)
                .text("Для входа отправьте свой мобильный номер кнопкой ниже (как в Telegram «Поделиться контактом»).")
                .replyMarkup(shareContactKeyboard())
                .build());
    }

    private void sendMainMenu(Long chatId, String text) throws TelegramApiException {
        sendAndLog(SendMessage.builder()
                .chatId(chatId)
                .text(shiftMetricsHandler.appendOpenShiftSummary(chatId, text))
                .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                .build());
    }

    private void handleContact(Long chatId, Contact contact) throws TelegramApiException {
        String phone = contact.getPhoneNumber();
        if (phone == null || phone.isBlank()) {
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("В контакте нет номера телефона.")
                    .replyMarkup(shareContactKeyboard())
                    .build());
            return;
        }
        try {
            CoreAuthClient.AuthResponse r = coreAuthClient.authenticate(phone, chatId);
            pendingInputs.clear(chatId);
            addressSessions.clear(chatId);
            clearShiftFlows(chatId);
            sessions.put(chatId, new BotSession(r.userId(), r.displayName()));
            remoteLogging.logMain(TelegramLogMessages.contactShared(r.displayName()));
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Добро пожаловать, " + r.displayName() + ". Учётная запись привязана.")
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (CoreAuthClient.CoreAuthException e) {
            String userHint = switch (e.getStatus()) {
                case 401 -> "Ошибка доступа к серверу (API-ключ). Обратитесь к администратору.";
                case 403 -> "Доступ запрещён: сотрудник не в статусе «В строю».";
                case 404 -> "Номер не найден в системе. Проверьте, что в карточке сотрудника указан тот же телефон.";
                case 409 -> "Этот Telegram уже привязан к другому сотруднику.";
                default -> "Не удалось войти (код " + e.getStatus() + ").";
            };
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text(userHint)
                    .replyMarkup(shareContactKeyboard())
                    .build());
        } catch (Exception e) {
            log.error("Связь с Core при авторизации chatId={}", chatId, e);
            sendAndLog(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось связаться с сервером (Core). Убедитесь, что он запущен и адрес vzvod.core.base-url верный.")
                    .replyMarkup(shareContactKeyboard())
                    .build());
        }
    }

    private ReplyKeyboardMarkup shareContactKeyboard() {
        return mainMenuKeyboardFactory.shareContactLogin();
    }
}
