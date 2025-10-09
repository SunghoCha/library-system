package msa.bookcatalog.adapter.in.batch.aladin.dto;

import msa.bookcatalog.domain.model.BookCatalog;
import msa.common.events.EventType;

public record BatchItem(BookCatalog bookCatalog, EventType eventType) {}