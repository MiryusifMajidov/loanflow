package az.yusif.loanflow.loan.internal;

import az.yusif.loanflow.shared.Amounts;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "installment")
public class Installment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "installment_seq")
    @SequenceGenerator(name = "installment_seq", sequenceName = "installment_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id")
    private Loan loan;

    @Column(nullable = false)
    private int number;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestDue;

    @Column(name = "penalty_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal penaltyDue = BigDecimal.ZERO;

    @Column(name = "principal_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "penalty_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal penaltyPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentStatus status = InstallmentStatus.SCHEDULED;

    @Column(name = "penalty_accrued_until")
    private LocalDate penaltyAccruedUntil;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    protected Installment() {
    }

    Installment(Loan loan, int number, LocalDate dueDate, BigDecimal principalDue, BigDecimal interestDue) {
        this.loan = loan;
        this.number = number;
        this.dueDate = dueDate;
        this.principalDue = principalDue;
        this.interestDue = interestDue;
    }

    Allocation pay(BigDecimal available, LocalDate today) {
        BigDecimal penalty = available.min(outstandingPenalty());
        BigDecimal interest = available.subtract(penalty).min(outstandingInterest());
        BigDecimal principal = available.subtract(penalty).subtract(interest).min(outstandingPrincipal());

        penaltyPaid = penaltyPaid.add(penalty);
        interestPaid = interestPaid.add(interest);
        principalPaid = principalPaid.add(principal);
        if (outstanding().signum() == 0) {
            status = InstallmentStatus.PAID;
            paidOn = today;
        }
        return new Allocation(principal, interest, penalty);
    }

    boolean markOverdue(LocalDate today) {
        if (status != InstallmentStatus.SCHEDULED || !dueDate.isBefore(today)) {
            return false;
        }
        status = InstallmentStatus.OVERDUE;
        penaltyAccruedUntil = dueDate;
        return true;
    }

    void accruePenalty(BigDecimal dailyRate, LocalDate today) {
        if (status != InstallmentStatus.OVERDUE || penaltyAccruedUntil == null || !penaltyAccruedUntil.isBefore(today)) {
            return;
        }
        long days = ChronoUnit.DAYS.between(penaltyAccruedUntil, today);
        BigDecimal base = outstandingPrincipal().add(outstandingInterest());
        penaltyDue = penaltyDue.add(Amounts.money(base.multiply(dailyRate).multiply(BigDecimal.valueOf(days))));
        penaltyAccruedUntil = today;
    }

    long daysOverdue(LocalDate today) {
        return status == InstallmentStatus.OVERDUE ? ChronoUnit.DAYS.between(dueDate, today) : 0;
    }

    BigDecimal outstanding() {
        return outstandingPrincipal().add(outstandingInterest()).add(outstandingPenalty());
    }

    BigDecimal outstandingPrincipal() {
        return principalDue.subtract(principalPaid);
    }

    BigDecimal outstandingInterest() {
        return interestDue.subtract(interestPaid);
    }

    BigDecimal outstandingPenalty() {
        return penaltyDue.subtract(penaltyPaid);
    }

    public boolean isPaid() {
        return status == InstallmentStatus.PAID;
    }

    public int getNumber() {
        return number;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getPrincipalDue() {
        return principalDue;
    }

    public BigDecimal getInterestDue() {
        return interestDue;
    }

    public BigDecimal getPenaltyDue() {
        return penaltyDue;
    }

    public BigDecimal getPrincipalPaid() {
        return principalPaid;
    }

    public BigDecimal getInterestPaid() {
        return interestPaid;
    }

    public BigDecimal getPenaltyPaid() {
        return penaltyPaid;
    }

    public InstallmentStatus getStatus() {
        return status;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    record Allocation(BigDecimal principal, BigDecimal interest, BigDecimal penalty) {

        static final Allocation NONE = new Allocation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal total() {
            return principal.add(interest).add(penalty);
        }

        Allocation plus(Allocation other) {
            return new Allocation(principal.add(other.principal), interest.add(other.interest), penalty.add(other.penalty));
        }
    }
}
