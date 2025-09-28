package msa.bookcatalog.infra.aladin.dto;

import lombok.Builder;
import msa.bookcatalog.infra.aladin.model.SearchQueryType;

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
