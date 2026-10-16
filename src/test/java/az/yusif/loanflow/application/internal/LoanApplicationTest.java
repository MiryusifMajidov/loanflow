package az.yusif.loanflow.application.internal;

import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.application.ScoringOutcome;
import az.yusif.loanflow.application.ScoringOutcome.Decision;
import az.yusif.loanflow.shared.security.CurrentUser;
import az.yusif.loanflow.shared.web.LoanflowException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanApplicationTest {

    private static final Instant NOW = Instant.parse("2026-10-15T08:00:00Z");
    private static final CurrentUser APPLICANT = new CurrentUser("user-1", "Leyla Mammadova", Set.of("CUSTOMER"));
    private static final CurrentUser OFFICER = new CurrentUser("officer-1", "Rashad Aliyev", Set.of("LOAN_OFFICER"));
    private static final CurrentUser RISK = new CurrentUser("risk-1", "Aysel Karimova", Set.of("RISK_MANAGER"));

    @Test
    void approvedScoringWaitsForOfficer() {
        LoanApplication application = scored(Decision.APPROVE);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING_APPROVAL);
        application.approve(OFFICER, NOW);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(application.getDecidedBy()).isEqualTo("Rashad Aliyev");
    }

    @Test
    void manualReviewNeedsRiskManager() {
        LoanApplication application = scored(Decision.MANUAL_REVIEW);

        assertThatThrownBy(() -> application.approve(OFFICER, NOW))
                .isInstanceOf(LoanflowException.class)
                .extracting(e -> ((LoanflowException) e).getStatusCode().value())
                .isEqualTo(HttpStatus.FORBIDDEN.value());

        application.approve(RISK, NOW);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void rejectedScoringIsFinal() {
        LoanApplication application = scored(Decision.REJECT);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(application.getDecisionReason()).isEqualTo("DTI_TOO_HIGH");
        assertThatThrownBy(() -> application.approve(OFFICER, NOW)).extracting("code").isEqualTo("INVALID_STATUS");
    }

    @Test
    void scoringIsAppliedOnlyOnce() {
        LoanApplication application = scored(Decision.APPROVE);

        assertThatThrownBy(() -> application.applyScoring(outcome(Decision.APPROVE), NOW))
                .extracting("code").isEqualTo("ALREADY_SCORED");
    }

    @Test
    void onlyApplicantCanCancel() {
        LoanApplication application = scored(Decision.APPROVE);

        assertThatThrownBy(() -> application.cancel(OFFICER, NOW)).extracting("code").isEqualTo("ACCESS_DENIED");
        application.cancel(APPLICANT, NOW);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
    }

    private static LoanApplication scored(Decision decision) {
        LoanApplication application = new LoanApplication(APPLICANT.id(), APPLICANT.name(), "5ZK7M2P",
                new BigDecimal("5000.00"), 12, LoanPurpose.CONSUMER, RepaymentType.ANNUITY,
                new BigDecimal("2500.00"), BigDecimal.ZERO, NOW);
        application.applyScoring(outcome(decision), NOW);
        return application;
    }

    private static ScoringOutcome outcome(Decision decision) {
        List<String> reasons = decision == Decision.APPROVE ? List.of()
                : decision == Decision.REJECT ? List.of("DTI_TOO_HIGH") : List.of("BORDERLINE_CREDIT_SCORE");
        return new ScoringOutcome(700, new BigDecimal("0.25"), decision, new BigDecimal("16.5"), reasons);
    }
}
