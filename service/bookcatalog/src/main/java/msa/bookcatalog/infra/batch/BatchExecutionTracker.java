package msa.bookcatalog.infra.batch;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
public class BatchExecutionTracker {

    @Id
    private String jobName;

    private LocalDate lastExecutionDate;

    public BatchExecutionTracker(String jobName, LocalDate lastExecutionDate) {
        this.jobName = jobName;
        this.lastExecutionDate = lastExecutionDate;
    }

    public void updateExecutionDate(LocalDate date) {
        this.lastExecutionDate = date;
    }
}
