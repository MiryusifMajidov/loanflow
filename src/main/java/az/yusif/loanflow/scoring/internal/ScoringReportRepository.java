package az.yusif.loanflow.scoring.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoringReportRepository extends JpaRepository<ScoringReport, Long> {

    List<ScoringReport> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
