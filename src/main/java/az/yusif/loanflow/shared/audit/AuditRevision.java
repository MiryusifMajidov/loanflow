package az.yusif.loanflow.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

@Entity
@Table(name = "audit_revision")
@RevisionEntity(AuditRevisionListener.class)
public class AuditRevision {

    @Id
    @RevisionNumber
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_revision_seq")
    @SequenceGenerator(name = "audit_revision_seq", sequenceName = "audit_revision_seq", allocationSize = 1)
    private Long id;

    @RevisionTimestamp
    @Column(name = "revision_time", nullable = false)
    private long timestamp;

    @Column(name = "username", length = 100)
    private String username;

    public Long getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }
}
