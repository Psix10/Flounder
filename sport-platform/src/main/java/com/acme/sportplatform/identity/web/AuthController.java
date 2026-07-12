package com.acme.sportplatform.identity.web;

import com.acme.sportplatform.identity.api.AuthTokenResponse;
import com.acme.sportplatform.identity.api.CurrentUserResponse;
import com.acme.sportplatform.identity.api.LoginRequest;
import com.acme.sportplatform.identity.application.GetCurrentUserUseCase;
import com.acme.sportplatform.identity.application.LoginUseCase;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public AuthController(LoginUseCase loginUseCase,
                          GetCurrentUserUseCase getCurrentUserUseCase) {
        this.loginUseCase = loginUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return loginUseCase.execute(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal PlatformUserPrincipal principal) {
        return getCurrentUserUseCase.execute(principal);
    }
}