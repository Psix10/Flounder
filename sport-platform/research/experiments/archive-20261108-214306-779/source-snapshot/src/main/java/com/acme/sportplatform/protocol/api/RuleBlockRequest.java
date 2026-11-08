package com.acme.sportplatform.protocol.api;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RuleBlockRequest(

        @NotNull
        @Min(0)
        Integer orderIndex,

        @NotBlank
        @Size(max = 100)
        String executorCode,

        @NotNull
        JsonNode config

) {
}