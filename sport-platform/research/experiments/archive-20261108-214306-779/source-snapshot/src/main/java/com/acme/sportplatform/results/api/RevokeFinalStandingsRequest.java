package com.acme.sportplatform.results.api;
import jakarta.validation.constraints.*;
public record RevokeFinalStandingsRequest(@NotBlank @Size(max=1000) String reason) { }
