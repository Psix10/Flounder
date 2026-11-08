package com.acme.sportplatform.protocol.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProtocolRuleSetRequest(

        @NotNull
        UUID sportId,

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[a-z][a-z0-9_-]*")
        String code,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 64)
        String competitionFormat,

        @NotBlank
        @Size(max = 64)
        String unitType,

        @NotEmpty
        @Size(max = 32)
        List<@NotNull @Valid RuleBlockRequest> blocks

) {
}