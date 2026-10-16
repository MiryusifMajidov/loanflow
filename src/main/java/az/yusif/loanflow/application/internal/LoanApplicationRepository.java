package az.yusif.loanflow.application.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    List<LoanApplication> findByApplicantIdOrderByCreatedAtDesc(String applicantId);

    Page<LoanApplication> findByStatusOrderByCreatedAtAsc(ApplicationStatus status, Pageable pageable);

    Page<LoanApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByApplicantIdAndStatusIn(String applicantId, List<ApplicationStatus> statuses);
}
