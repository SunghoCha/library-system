package msa.bookloan.adapter.out.persistence.inbox.repository;

import msa.common.events.inbox.record.InboxDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxDeadLetterRepository extends JpaRepository<InboxDeadLetter, Long> {

}
