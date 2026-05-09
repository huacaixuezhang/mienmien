package com.mienmien.business.management.user.interfaces.rest;

import com.mienmien.business.management.application.dto.UserAuthResponse;
import com.mienmien.business.management.application.service.UserAuthApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business/auth")
public class UserAuthController {
    private final UserAuthApplicationService userAuthApplicationService;

    public UserAuthController(UserAuthApplicationService userAuthApplicationService) {
        this.userAuthApplicationService = userAuthApplicationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAuthResponse register(@Valid @RequestBody UserAuthRequest req) {
        return userAuthApplicationService.register(req.phone(), req.password());
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public UserAuthResponse login(@Valid @RequestBody UserAuthRequest req) {
        return userAuthApplicationService.login(req.phone(), req.password());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        userAuthApplicationService.logout(extractBearer(authorization));
    }

    private static String extractBearer(String authorization) {
        if (authorization == null) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return null;
    }

    public record UserAuthRequest(@NotBlank String phone, @NotBlank String password) {
    }
}
