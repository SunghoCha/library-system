package msa.bookloan.infra.inbox.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import msa.common.events.inbox.record.PayloadInboxEventRecord;


@Entity
@SuperBuilder
@Table(name = "book_catalog_inbox")
public class BookCatalogProjectionInboxEventRecord extends PayloadInboxEventRecord {

    public BookCatalogProjectionInboxEventRecord() {
    }




}
