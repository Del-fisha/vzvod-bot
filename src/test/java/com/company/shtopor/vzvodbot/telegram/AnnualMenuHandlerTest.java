package com.company.shtopor.vzvodbot.telegram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnualMenuHandlerTest {

    @Test
    void escapesHtmlInColleagueNames() {
        assertEquals("A &amp; B", AnnualMenuHandler.escape("A & B"));
        assertEquals("", AnnualMenuHandler.escape(null));
    }
}
