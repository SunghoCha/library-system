package msa.bookcatalog.adapter.in.batch.aladin.dto;

import msa.bookcatalog.domain.model.BookCatalog;
import msa.common.events.EventTypeV1;

public record BatchItem(BookCatalog bookCatalog, EventTypeV1 eventTypeV1) {}