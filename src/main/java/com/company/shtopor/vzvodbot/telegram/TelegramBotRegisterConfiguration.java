package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.config.CoreClientProperties;
import com.company.shtopor.vzvodbot.config.TelegramBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.methods.updates.GetWebhookInfo;
import org.telegram.telegrambots.meta.api.objects.WebhookInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Регистрация long polling после {@link ApplicationReadyEvent}: в Spring Boot строка «Started …»
 * пишется <em>до</em> {@code ApplicationRunner}, из‑за чего логи бота оказываются ниже и их легко не заметить.
 * <p>
 * В Docker (особенно Windows) DNS к api.telegram.org иногда недоступен в момент старта — повторяем попытки в фоне.
 */
@Configuration
public class TelegramBotRegisterConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotRegisterConfiguration.class);
    private static final long RETRY_DELAY_MS = 15_000L;

    private final AtomicBoolean registrationStarted = new AtomicBoolean(false);
    private final AtomicBoolean registrationDone = new AtomicBoolean(false);

    private final VzvodTelegramBot bot;
    private final TelegramBotProperties props;
    private final CoreClientProperties core;

    public TelegramBotRegisterConfiguration(VzvodTelegramBot bot, TelegramBotProperties props, CoreClientProperties core) {
        this.bot = bot;
        this.props = props;
        this.core = core;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerTelegramBotOnReady() {
        if (!registrationStarted.compareAndSet(false, true)) {
            return;
        }
        if (props.getToken() == null || props.getToken().isBlank()) {
            log.error("telegram.bot.token пуст — Telegram не запущен. Задайте TELEGRAM_BOT_TOKEN в Run Configuration "
                    + "или создайте src/main/resources/application-local.yml (см. application-local.example.yml). "
                    + "Если файл в .gitignore, выполните mvn process-resources или включите копирование ресурсов в IDE.");
            return;
        }
        Thread registerThread = new Thread(this::registerWithRetries, "telegram-bot-register");
        registerThread.setDaemon(true);
        registerThread.start();
    }

    private void registerWithRetries() {
        int attempt = 0;
        while (!registrationDone.get() && !Thread.currentThread().isInterrupted()) {
            attempt++;
            try {
                registerOnce();
                registrationDone.set(true);
                return;
            } catch (TelegramApiException e) {
                if (attempt == 1) {
                    log.error(registrationFailureHint(), e);
                } else if (attempt % 4 == 0) {
                    log.warn("Повторная регистрация Telegram (попытка {}): {}", attempt, rootCauseMessage(e));
                } else {
                    log.debug("Повторная регистрация Telegram (попытка {}): {}", attempt, rootCauseMessage(e));
                }
                sleepQuietly(RETRY_DELAY_MS);
            }
        }
    }

    private void registerOnce() throws TelegramApiException {
        log.info("Регистрация Telegram long polling (@{})…", props.getUsername());
        logJvmAndAppProxyContext();
        WebhookInfo wh = bot.execute(new GetWebhookInfo());
        if (wh.getUrl() != null && !wh.getUrl().isBlank()) {
            log.warn("У бота был включён webhook URL={} — отключаем до старта long polling, иначе апдейты не придут", wh.getUrl());
            bot.execute(DeleteWebhook.builder().dropPendingUpdates(true).build());
        }

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);

        log.info("Telegram-бот в long polling: @{}, Core: {}", bot.getBotUsername(), core.getBaseUrl().replaceAll("/+$", ""));
    }

    private static String registrationFailureHint() {
        return "Не удалось подключиться к Telegram Bot API (getWebhookInfo / register). "
                + "Без успешной регистрации /start в Telegram не обрабатывается. "
                + "Проверьте исходящий HTTPS к api.telegram.org:443. "
                + "В Docker на Windows при UnknownHostException часто помогает перезапуск compose или явный DNS; "
                + "бот будет повторять попытки каждые 15 с. "
                + "Если Telegram доступен только через VPN на хосте: в .env задайте TELEGRAM_BOT_PROXY_HOST=host.docker.internal "
                + "(не 127.0.0.1), TELEGRAM_BOT_PROXY_PORT и TELEGRAM_BOT_PROXY_TYPE=SOCKS5.";
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logJvmAndAppProxyContext() {
        boolean appProxy = props.getProxyHost() != null && !props.getProxyHost().isBlank() && props.getProxyPort() > 0;
        String appProxyStr = appProxy
                ? props.getProxyHost().trim() + ":" + props.getProxyPort() + " (" + props.getProxyType() + ")"
                : "нет (прямое подключение к api.telegram.org)";
        String socks = System.getProperty("socksProxyHost");
        String https = System.getProperty("https.proxyHost");
        log.info(
                "Контекст сети для JVM: telegram.bot прокси={} | https.proxyHost={} | socksProxyHost={} | java.net.useSystemProxies={}",
                appProxyStr,
                https != null && !https.isBlank() ? https : "—",
                socks != null && !socks.isBlank() ? socks : "—",
                System.getProperty("java.net.useSystemProxies", "—"));
        if (socks != null && !socks.isBlank()) {
            log.warn("У JVM задан socksProxyHost — исходящие соединения Java (в т.ч. к Telegram) идут через SOCKS. "
                    + "Если это старый/мертвый прокси, будет таймаут; уберите из VM options или поднимите SOCKS.");
        }
    }
}
