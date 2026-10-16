package az.yusif.loanflow.loan.internal;

import az.yusif.loanflow.application.ApplicationApproved;
import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.loan.LoanClosed;
import az.yusif.loanflow.loan.LoanDisbursed;
import az.yusif.loanflow.loan.internal.Installment.Allocation;
import az.yusif.loanflow.loan.internal.ScheduleCalculator.ScheduledPayment;
import az.yusif.loanflow.shared.Amounts;
import az.yusif.loanflow.shared.security.CurrentUser;
import az.yusif.loanflow.shared.web.LoanflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LoanRepository loans;
    private final RepaymentRepository repayments;
    private final ScheduleCalculator calculator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public LoanService(LoanRepository loans, RepaymentRepository repayments, ScheduleCalculator calculator,
                       ApplicationEventPublisher events, Clock clock) {
        this.loans = loans;
        this.repayments = repayments;
        this.calculator = calculator;
        this.events = events;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void onApplicationApproved(ApplicationApproved approved) {
        if (loans.existsByApplicationId(approved.applicationId())) {
            log.info("Loan for application {} already exists", approved.applicationId());
            return;
        }
        Loan loan = loans.save(new Loan(approved.applicationId(), approved.applicantId(), approved.applicantName(),
                approved.amount(), approved.annualRate(), approved.termMonths(), approved.repaymentType(), clock.instant()));
        log.info("Loan {} created for application {}", loan.getId(), approved.applicationId());
    }

    @Transactional
    public Loan disburse(long id) {
        Loan loan = find(id);
        LocalDate firstDueDate = LocalDate.now(clock).plusMonths(1);
        List<ScheduledPayment> schedule = calculator.calculate(loan.getPrincipal(), loan.getAnnualRate(),
                loan.getTermMonths(), loan.getRepaymentType(), firstDueDate);

        loan.disburse(schedule, clock.instant());
        events.publishEvent(new LoanDisbursed(loan.getId(), loan.getApplicationId(), loan.getBorrowerId(),
                loan.getPrincipal(), schedule.get(0).total(), firstDueDate));
        return loan;
    }

    @Transactional
    public Repayment repay(long id, BigDecimal amount, CurrentUser user) {
        Loan loan = accessible(id, user);
        Allocation allocation = loan.repay(Amounts.money(amount), LocalDate.now(clock), clock.instant());
        Repayment repayment = repayments.save(new Repayment(loan.getId(), allocation, user.name(), clock.instant()));

        if (loan.getStatus() == LoanStatus.CLOSED) {
            events.publishEvent(new LoanClosed(loan.getId(), loan.getBorrowerId(), loan.getClosedAt()));
        }
        return repayment;
    }

    @Transactional(readOnly = true)
    public Loan get(long id, CurrentUser user) {
        Loan loan = loans.findWithInstallmentsById(id).orElseThrow(() -> notFound(id));
        if (!loan.isOwnedBy(user.id()) && !user.isStaff()) {
            throw notFound(id);
        }
        return loan;
    }

    @Transactional(readOnly = true)
    public List<Loan> mine(CurrentUser user) {
        return loans.findByBorrowerIdOrderByCreatedAtDesc(user.id());
    }

    @Transactional(readOnly = true)
    public Page<Loan> byStatus(LoanStatus status, Pageable pageable) {
        return loans.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public List<Repayment> repayments(long id, CurrentUser user) {
        return repayments.findByLoanIdOrderByPaidAtDesc(accessible(id, user).getId());
    }

    public List<ScheduledPayment> preview(BigDecimal amount, BigDecimal annualRate, int months,
                                          RepaymentType type) {
        return calculator.calculate(Amounts.money(amount), annualRate, months, type, LocalDate.now(clock).plusMonths(1));
    }

    private Loan accessible(long id, CurrentUser user) {
        Loan loan = find(id);
        if (!loan.isOwnedBy(user.id()) && !user.isStaff()) {
            throw notFound(id);
        }
        return loan;
    }

    private Loan find(long id) {
        return loans.findById(id).orElseThrow(() -> notFound(id));
    }

    private static LoanflowException notFound(long id) {
        return LoanflowException.notFound("LOAN_NOT_FOUND", "Loan " + id + " not found");
    }
}
