package com.company.shtopor.vzvodbot.logging;

public final class TelegramLogMessages {

    private TelegramLogMessages() {
    }

    public static String userAction(String actor, String action) {
        return "%s: %s".formatted(actor, action);
    }

    public static String userMessage(String actor, String text) {
        return "%s: %s".formatted(actor, text);
    }

    public static String programReply(String actor, String text) {
        return "Ответ программы (%s): %s".formatted(actor, text);
    }

    public static String contactShared(String actor) {
        return "%s отправил контакт".formatted(actor);
    }
}
