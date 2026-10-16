package az.yusif.loanflow.application.internal;

import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.application.ScoringOutcome;
import az.yusif.loanflow.shared.security.CurrentUser;
import az.yusif.loanflow.shared.security.Roles;
import az.yusif.loanflow.shared.web.LoanflowException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Audited
@Table(name = "loan_application")
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loan_application_seq")
    @SequenceGenerator(name = "loan_application_seq", sequenceName = "loan_application_seq", allocationSize = 20)
    private Long id;

    @Column(name = "applicant_id", nullable = false, updatable = false, length = 36)
    private String applicantId;

    @Column(name = "applicant_name", nullable = false, updatable = false)
    private String applicantName;

    @Column(name = "fin_code", nullable = false, updatable = false, length = 7)
    private String finCode;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "term_months", nullable = false, updatable = false)
    private int termMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private LoanPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_type", nullable = false, updatable = false, length = 20)
    private RepaymentType repaymentType;

    @NotAudited
    @Column(name = "monthly_income", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal monthlyIncome;

    @NotAudited
    @Column(name = "monthly_debt", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal monthlyDebt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "debt_to_income", precision = 6, scale = 4)
    private BigDecimal debtToIncome;

    @Column(name = "annual_rate", precision = 7, scale = 4)
    private BigDecimal annualRate;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @NotAudited
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotAudited
    @Version
    private Long version;

    protected LoanApplication() {
    }

    public LoanApplication(String applicantId, String applicantName, String finCode, BigDecimal amount, int termMonths,
                           LoanPurpose purpose, RepaymentType repaymentType, BigDecimal monthlyIncome,
                           BigDecimal monthlyDebt, Instant createdAt) {
        this.applicantId = applicantId;
        this.applicantName = applicantName;
        this.finCode = finCode;
        this.amount = amount;
        this.termMonths = termMonths;
        this.purpose = purpose;
        this.repaymentType = repaymentType;
        this.monthlyIncome = monthlyIncome;
        this.monthlyDebt = monthlyDebt;
        this.createdAt = createdAt;
    }

    public void applyScoring(ScoringOutcome outcome, Instant now) {
        if (status != ApplicationStatus.SUBMITTED) {
            throw LoanflowException.conflict("ALREADY_SCORED", "Application " + id + " is already " + status);
        }
        creditScore = outcome.creditScore();
        debtToIncome = outcome.debtToIncome();
        annualRate = outcome.annualRate();
        switch (outcome.decision()) {
            case APPROVE -> status = ApplicationStatus.PENDING_APPROVAL;
            case MANUAL_REVIEW -> {
                status = ApplicationStatus.UNDER_REVIEW;
                decisionReason = String.join(", ", outcome.reasons());
            }
            case REJECT -> {
                status = ApplicationStatus.REJECTED;
                decisionReason = String.join(", ", outcome.reasons());
                decidedBy = "scoring";
                decidedAt = now;
            }
        }
    }

    public void approve(CurrentUser user, Instant now) {
        String requiredRole = switch (status) {
            case PENDING_APPROVAL -> Roles.LOAN_OFFICER;
            case UNDER_REVIEW -> Roles.RISK_MANAGER;
            default -> throw LoanflowException.ruleViolation("INVALID_STATUS", "Application is " + status);
        };
        if (!user.hasRole(requiredRole) && !user.hasRole(Roles.ADMIN)) {
            throw LoanflowException.forbidden("Only " + requiredRole + " can approve an application that is " + status);
        }
        decide(ApplicationStatus.APPROVED, null, user, now);
    }

    public void reject(CurrentUser user, String reason, Instant now) {
        if (!ApplicationStatus.AWAITING_DECISION.contains(status)) {
            throw LoanflowException.ruleViolation("INVALID_STATUS", "Application is " + status);
        }
        decide(ApplicationStatus.REJECTED, reason, user, now);
    }

    public void cancel(CurrentUser user, Instant now) {
        if (!applicantId.equals(user.id())) {
            throw LoanflowException.forbidden("Only the applicant can cancel the application");
        }
        if (status.isFinal()) {
            throw LoanflowException.ruleViolation("INVALID_STATUS", "Application is already " + status);
        }
        decide(ApplicationStatus.CANCELLED, "Cancelled by applicant", user, now);
    }

    public boolean isVisibleTo(CurrentUser user) {
        return applicantId.equals(user.id()) || user.isStaff();
    }

    private void decide(ApplicationStatus newStatus, String reason, CurrentUser user, Instant now) {
        status = newStatus;
        decisionReason = reason;
        decidedBy = user.name();
        decidedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getApplicantId() {
        return applicantId;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public String getFinCode() {
        return finCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public LoanPurpose getPurpose() {
        return purpose;
    }

    public RepaymentType getRepaymentType() {
        return repaymentType;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public BigDecimal getMonthlyDebt() {
        return monthlyDebt;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public BigDecimal getDebtToIncome() {
        return debtToIncome;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
