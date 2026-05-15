package com.company.shtopor.vzvodbot.api;

import com.company.shtopor.vzvodbot.session.TelegramAccessRevokeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/telegram")
public class InternalTelegramRevokeController {

    private final TelegramAccessRevokeService accessRevokeService;
    private final String internalToken;

    public InternalTelegramRevokeController(
            TelegramAccessRevokeService accessRevokeService,
            @Value("${internal.api.token:}") String internalToken
    ) {
        this.accessRevokeService = accessRevokeService;
        this.internalToken = internalToken;
    }

    @PostMapping("/revoke-access")
    public void revokeAccess(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestBody @Valid TelegramRevokeAccessRequest request
    ) {
        requireInternalToken(token);
        accessRevokeService.revoke(request.chatId(), request.message());
    }

    private void requireInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
