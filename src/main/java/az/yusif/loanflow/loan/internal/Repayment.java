package az.yusif.loanflow.loan.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "repayment")
public class Repayment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "repayment_seq")
    @SequenceGenerator(name = "repayment_seq", sequenceName = "repayment_seq", allocationSize = 20)
    private Long id;

    @Column(name = "loan_id", nullable = false, updatable = false)
    private Long loanId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "principal_part", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal principalPart;

    @Column(name = "interest_part", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal interestPart;

    @Column(name = "penalty_part", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal penaltyPart;

    @Column(name = "paid_by", nullable = false, updatable = false, length = 100)
    private String paidBy;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    protected Repayment() {
    }

    Repayment(Long loanId, Installment.Allocation allocation, String paidBy, Instant paidAt) {
        this.loanId = loanId;
        this.amount = allocation.total();
        this.principalPart = allocation.principal();
        this.interestPart = allocation.interest();
        this.penaltyPart = allocation.penalty();
        this.paidBy = paidBy;
        this.paidAt = paidAt;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPrincipalPart() {
        return principalPart;
    }

    public BigDecimal getInterestPart() {
        return interestPart;
    }

    public BigDecimal getPenaltyPart() {
        return penaltyPart;
    }

    public String getPaidBy() {
        return paidBy;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
