package com.acme.sportplatform.results.api;
import java.util.UUID;
import jakarta.validation.constraints.*;
public record ConfirmFinalStandingsRequest(@NotNull UUID requestId,
        @NotNull @AssertTrue Boolean programmeComplete, @Size(max=500) String note) { }
