package az.yusif.loanflow.scoring.internal;

import az.yusif.loanflow.application.ScoringOutcome.Decision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "scoring_report")
public class ScoringReport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "scoring_report_seq")
    @SequenceGenerator(name = "scoring_report_seq", sequenceName = "scoring_report_seq", allocationSize = 20)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "bureau_score")
    private Integer bureauScore;

    @Column(name = "existing_debt", nullable = false, precision = 19, scale = 2)
    private BigDecimal existingDebt;

    @Column(nullable = false)
    private int delinquencies;

    @Column(name = "new_payment", nullable = false, precision = 19, scale = 2)
    private BigDecimal newPayment;

    @Column(name = "debt_to_income", nullable = false, precision = 6, scale = 4)
    private BigDecimal debtToIncome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Column(length = 500)
    private String reasons;

    @Column(name = "annual_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal annualRate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ScoringReport() {
    }

    public ScoringReport(Long applicationId, ScoringPolicy.Evaluation evaluation, Instant createdAt) {
        this.applicationId = applicationId;
        this.bureauScore = evaluation.outcome().creditScore();
        this.existingDebt = evaluation.existingDebt();
        this.delinquencies = evaluation.delinquencies();
        this.newPayment = evaluation.newPayment();
        this.debtToIncome = evaluation.outcome().debtToIncome();
        this.decision = evaluation.outcome().decision();
        this.reasons = String.join(",", evaluation.outcome().reasons());
        this.annualRate = evaluation.outcome().annualRate();
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Integer getBureauScore() {
        return bureauScore;
    }

    public BigDecimal getExistingDebt() {
        return existingDebt;
    }

    public int getDelinquencies() {
        return delinquencies;
    }

    public BigDecimal getNewPayment() {
        return newPayment;
    }

    public BigDecimal getDebtToIncome() {
        return debtToIncome;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getReasons() {
        return reasons;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
