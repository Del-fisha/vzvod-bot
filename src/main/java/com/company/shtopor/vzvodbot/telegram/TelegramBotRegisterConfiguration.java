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
 */
@Configuration
public class TelegramBotRegisterConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotRegisterConfiguration.class);

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
        if (!registrationDone.compareAndSet(false, true)) {
            return;
        }
        if (props.getToken() == null || props.getToken().isBlank()) {
            log.error("telegram.bot.token пуст — Telegram не запущен. Задайте TELEGRAM_BOT_TOKEN в Run Configuration "
                    + "или создайте src/main/resources/application-local.yml (см. application-local.example.yml). "
                    + "Если файл в .gitignore, выполните mvn process-resources или включите копирование ресурсов в IDE.");
            return;
        }
        try {
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
        } catch (TelegramApiException e) {
            log.error(
                    "Не удалось подключиться к Telegram Bot API (getWebhookInfo / register). "
                            + "Без успешной регистрации /start в Telegram не обрабатывается. "
                            + "Проверьте исходящий HTTPS к api.telegram.org:443. "
                            + "Если VPN включён, а Java всё равно таймаутится: часто VPN не маршрутизирует произвольные приложения (split tunnel) — в клиенте VPN включите режим «для всей системы» / TUN, "
                            + "или в настройках VPN возьмите локальный SOCKS (обычно 127.0.0.1 и порт) и задайте telegram.bot.proxy-host, proxy-port, proxy-type=SOCKS5. "
                            + "Корпоративный HTTP-прокси: telegram.bot.proxy-* или TELEGRAM_BOT_PROXY_*. "
                            + "В стеке SocksSocketImpl — проверьте socksProxyHost / socksProxyPort в VM options IDEA (лишний или неверный прокси даёт таймаут). "
                            + "Приложение остаётся без long polling — перезапустите после появления маршрута к Telegram.",
                    e);
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
