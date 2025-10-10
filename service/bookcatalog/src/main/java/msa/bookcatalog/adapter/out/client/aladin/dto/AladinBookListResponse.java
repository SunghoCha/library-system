package msa.bookcatalog.adapter.out.client.aladin.dto;

import java.util.List;

public record AladinBookListResponse(
        List<AladinBookItemDto> item
) {}
