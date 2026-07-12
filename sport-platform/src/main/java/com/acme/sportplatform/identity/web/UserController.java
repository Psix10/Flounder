package com.acme.sportplatform.identity.web;

import com.acme.sportplatform.identity.api.CreateUserRequest;
import com.acme.sportplatform.identity.api.UserResponse;
import com.acme.sportplatform.identity.application.CreateUserUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final CreateUserUseCase createUserUseCase;

    public UserController(CreateUserUseCase createUserUseCase) {
        this.createUserUseCase = createUserUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return createUserUseCase.execute(request);
    }
}