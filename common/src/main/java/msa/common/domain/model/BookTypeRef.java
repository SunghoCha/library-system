package msa.common.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookTypeRef(
        @NotBlank String bookType,
        @Size(max = 32) String bookTypeName) {}