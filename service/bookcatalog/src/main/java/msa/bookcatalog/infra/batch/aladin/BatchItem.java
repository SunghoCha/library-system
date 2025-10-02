package msa.bookcatalog.infra.batch.aladin;

import msa.bookcatalog.domain.model.BookCatalog;
import msa.common.events.EventType;

public record BatchItem(BookCatalog bookCatalog, EventType eventType) {}