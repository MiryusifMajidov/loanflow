package az.yusif.loanflow.loan.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    boolean existsByApplicationId(Long applicationId);

    @EntityGraph(attributePaths = "installments")
    Optional<Loan> findWithInstallmentsById(Long id);

    List<Loan> findByBorrowerIdOrderByCreatedAtDesc(String borrowerId);

    Page<Loan> findByStatus(LoanStatus status, Pageable pageable);

    @Query("select l.id from Loan l where l.status in :statuses order by l.id")
    List<Long> findIdsByStatusIn(Collection<LoanStatus> statuses);
}
