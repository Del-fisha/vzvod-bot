package com.company.shtopor.vzvodbot.api;

import com.company.shtopor.vzvodbot.session.TelegramAccessRevokeService;
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

@WebMvcTest(InternalTelegramRevokeController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "internal.api.token=secret")
class InternalTelegramRevokeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TelegramAccessRevokeService accessRevokeService;

    @Test
    void revokesAccessWhenTokenValid() throws Exception {
        mockMvc.perform(post("/internal/telegram/revoke-access")
                        .header("X-Internal-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":12345,\"message\":\"Доступ закрыт\"}"))
                .andExpect(status().isOk());

        verify(accessRevokeService).revoke(eq(12345L), eq("Доступ закрыт"));
    }

    @Test
    void rejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/internal/telegram/revoke-access")
                        .header("X-Internal-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":12345,\"message\":\"Доступ закрыт\"}"))
                .andExpect(status().isUnauthorized());
    }
}
