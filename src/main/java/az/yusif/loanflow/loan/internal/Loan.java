package az.yusif.loanflow.loan.internal;

import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.loan.internal.Installment.Allocation;
import az.yusif.loanflow.loan.internal.ScheduleCalculator.ScheduledPayment;
import az.yusif.loanflow.shared.web.LoanflowException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "loan")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loan_seq")
    @SequenceGenerator(name = "loan_seq", sequenceName = "loan_seq", allocationSize = 20)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true, updatable = false)
    private Long applicationId;

    @Column(name = "borrower_id", nullable = false, updatable = false, length = 36)
    private String borrowerId;

    @Column(name = "borrower_name", nullable = false, updatable = false)
    private String borrowerName;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(name = "annual_rate", nullable = false, updatable = false, precision = 7, scale = 4)
    private BigDecimal annualRate;

    @Column(name = "term_months", nullable = false, updatable = false)
    private int termMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_type", nullable = false, updatable = false, length = 20)
    private RepaymentType repaymentType;

    @Audited
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoanStatus status = LoanStatus.AWAITING_DISBURSEMENT;

    @Audited
    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "first_due_date")
    private LocalDate firstDueDate;

    @Audited
    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("number")
    private List<Installment> installments = new ArrayList<>();

    @Version
    private Long version;

    protected Loan() {
    }

    public Loan(Long applicationId, String borrowerId, String borrowerName, BigDecimal principal, BigDecimal annualRate,
                int termMonths, RepaymentType repaymentType, Instant createdAt) {
        this.applicationId = applicationId;
        this.borrowerId = borrowerId;
        this.borrowerName = borrowerName;
        this.principal = principal;
        this.annualRate = annualRate;
        this.termMonths = termMonths;
        this.repaymentType = repaymentType;
        this.createdAt = createdAt;
    }

    public void disburse(List<ScheduledPayment> schedule, Instant now) {
        if (status != LoanStatus.AWAITING_DISBURSEMENT) {
            throw LoanflowException.ruleViolation("INVALID_LOAN_STATUS", "Loan is already " + status);
        }
        schedule.forEach(payment -> installments.add(
                new Installment(this, payment.number(), payment.dueDate(), payment.principal(), payment.interest())));
        firstDueDate = schedule.get(0).dueDate();
        disbursedAt = now;
        status = LoanStatus.ACTIVE;
    }

    Allocation repay(BigDecimal amount, LocalDate today, Instant now) {
        if (!status.acceptsRepayments()) {
            throw LoanflowException.ruleViolation("INVALID_LOAN_STATUS", "Loan is " + status);
        }
        BigDecimal outstanding = outstanding();
        if (amount.compareTo(outstanding) > 0) {
            throw LoanflowException.ruleViolation("OVERPAYMENT", "Outstanding amount is only " + outstanding);
        }

        Allocation total = Allocation.NONE;
        BigDecimal left = amount;
        for (Installment installment : installments) {
            if (left.signum() == 0) {
                break;
            }
            if (installment.isPaid()) {
                continue;
            }
            Allocation used = installment.pay(left, today);
            total = total.plus(used);
            left = left.subtract(used.total());
        }

        if (installments.stream().allMatch(Installment::isPaid)) {
            status = LoanStatus.CLOSED;
            closedAt = now;
        } else if (status == LoanStatus.OVERDUE && overdueInstallments().isEmpty()) {
            status = LoanStatus.ACTIVE;
        }
        return total;
    }

    List<Installment> markOverdue(LocalDate today, BigDecimal dailyPenaltyRate, int defaultAfterDays) {
        if (status != LoanStatus.ACTIVE && status != LoanStatus.OVERDUE) {
            return List.of();
        }
        List<Installment> newlyOverdue = installments.stream()
                .filter(installment -> installment.markOverdue(today))
                .toList();
        installments.forEach(installment -> installment.accruePenalty(dailyPenaltyRate, today));

        List<Installment> overdue = overdueInstallments();
        if (!overdue.isEmpty()) {
            long worst = overdue.stream().mapToLong(installment -> installment.daysOverdue(today)).max().orElse(0);
            status = worst > defaultAfterDays ? LoanStatus.DEFAULTED : LoanStatus.OVERDUE;
        }
        return newlyOverdue;
    }

    public BigDecimal outstanding() {
        return installments.stream().map(Installment::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal regularPayment() {
        return installments.isEmpty()
                ? BigDecimal.ZERO
                : installments.get(0).getPrincipalDue().add(installments.get(0).getInterestDue());
    }

    public boolean isOwnedBy(String userId) {
        return borrowerId.equals(userId);
    }

    private List<Installment> overdueInstallments() {
        return installments.stream().filter(installment -> installment.getStatus() == InstallmentStatus.OVERDUE).toList();
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public String getBorrowerId() {
        return borrowerId;
    }

    public String getBorrowerName() {
        return borrowerName;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public RepaymentType getRepaymentType() {
        return repaymentType;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public Instant getDisbursedAt() {
        return disbursedAt;
    }

    public LocalDate getFirstDueDate() {
        return firstDueDate;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Installment> getInstallments() {
        return List.copyOf(installments);
    }
}
