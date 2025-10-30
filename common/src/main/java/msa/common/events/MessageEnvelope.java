package msa.common.events;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MessageEnvelope (
        @NotBlank @Pattern(regexp="^[0-9]+$") String eventId, // snowflake 기반
        @NotBlank @Pattern(regexp="^[0-9]+$") String aggregateId, // snowflake 기반
        Long aggregateVersion, // 사가면 null 허용
        @NotBlank String eventType,
        @NotNull JsonNode payload
) implements EnvelopeMeta {}
