package com.company.shtopor.vzvodbot.api;

import com.company.shtopor.vzvodbot.telegram.TelegramOutboundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalTelegramSendController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "internal.api.token=secret")
class InternalTelegramSendControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TelegramOutboundService telegramOutboundService;

    @Test
    void sendsMessageWhenTokenValid() throws Exception {
        mockMvc.perform(post("/internal/telegram/send")
                        .header("X-Internal-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":12345,\"text\":\"Привет\"}"))
                .andExpect(status().isOk());

        verify(telegramOutboundService).sendText(eq(12345L), eq("Привет"));
    }

    @Test
    void rejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/internal/telegram/send")
                        .header("X-Internal-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":12345,\"text\":\"Привет\"}"))
                .andExpect(status().isUnauthorized());
    }
}
