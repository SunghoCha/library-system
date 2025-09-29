package msa.common.domain.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRef(
        @NotNull Integer categoryId,
        @Size(max = 100) String categoryName
) {}
