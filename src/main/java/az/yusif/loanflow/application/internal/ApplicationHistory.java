package az.yusif.loanflow.application.internal;

import az.yusif.loanflow.shared.audit.AuditRevision;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class ApplicationHistory {

    private final EntityManager entityManager;

    public ApplicationHistory(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<Revision> of(long applicationId) {
        List<?> rows = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(LoanApplication.class, false, true)
                .add(AuditEntity.id().eq(applicationId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return rows.stream()
                .map(Object[].class::cast)
                .map(row -> {
                    LoanApplication state = (LoanApplication) row[0];
                    AuditRevision revision = (AuditRevision) row[1];
                    RevisionType type = (RevisionType) row[2];
                    return new Revision(revision.getId(), Instant.ofEpochMilli(revision.getTimestamp()),
                            revision.getUsername(), type.name(), state.getStatus(), state.getDecisionReason());
                })
                .toList();
    }

    public record Revision(Long revision,
                           Instant changedAt,
                           String changedBy,
                           String change,
                           ApplicationStatus status,
                           String reason) {
    }
}
