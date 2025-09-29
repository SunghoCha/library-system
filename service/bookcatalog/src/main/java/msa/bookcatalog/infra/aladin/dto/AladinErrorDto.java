package msa.bookcatalog.infra.aladin.dto;

public record AladinErrorDto(
        int errorCode,
        String errorMessage
) {}
