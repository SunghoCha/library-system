package msa.bookcatalog.adapter.out.client.aladin.dto;

public record AladinErrorDto(
        int errorCode,
        String errorMessage
) {}
