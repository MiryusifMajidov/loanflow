package az.yusif.loanflow.loan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepaymentRepository extends JpaRepository<Repayment, Long> {

    List<Repayment> findByLoanIdOrderByPaidAtDesc(Long loanId);
}
