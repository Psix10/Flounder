package com.acme.sportplatform.protocol;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public interface ProtocolConfigurationPublication {

    record Settings(
            @NotNull
            @Min(1)
            @Max(1024)
            Integer capacity,

            @NotEmpty
            @Size(max = 1024)
            List<@NotNull @Min(0) Integer> positionOrder,

            @NotBlank
            @Size(max = 100)
            String metricKey,

            @NotBlank
            @Pattern(regexp = "FASTEST_FIRST|SLOWEST_FIRST")
            String heatOrder,

            @NotBlank
            @Pattern(regexp = "FIRST|LAST")
            String partialHeat,

            @Size(max = 256)
            List<@NotNull JsonNode> categories
    ) {
        public Settings(
                Integer capacity,
                List<Integer> positionOrder,
                String metricKey,
                String heatOrder,
                String partialHeat
        ) {
            this(
                    capacity,
                    positionOrder,
                    metricKey,
                    heatOrder,
                    partialHeat,
                    List.of()
            );
        }
    }

    UUID publish(
            UUID sportId,
            String name,
            Settings settings,
            UUID actorId
    );

    Settings read(UUID ruleVersionId);
}