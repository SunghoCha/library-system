package msa.bookcatalog.infra.aladin.dto;

import java.util.List;

public record AladinBookListResponse(
        List<AladinBookItemDto> item
) {}
