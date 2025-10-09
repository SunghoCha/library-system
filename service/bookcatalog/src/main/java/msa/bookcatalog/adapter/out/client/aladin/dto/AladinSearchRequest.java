package msa.bookcatalog.adapter.out.client.aladin.dto;

import lombok.Builder;
import msa.bookcatalog.adapter.out.client.aladin.model.SearchQueryType;

@Builder
public record AladinSearchRequest(
        SearchQueryType queryType,
        String query,
        Integer maxResults,
        Integer start,
        Integer categoryId,
        String sort,
        String cover,
        String optResult
) {}
