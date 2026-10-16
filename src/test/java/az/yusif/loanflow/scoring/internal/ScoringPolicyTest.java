package az.yusif.loanflow.scoring.internal;

import az.yusif.loanflow.application.ApplicationSubmitted;
import az.yusif.loanflow.application.ScoringOutcome;
import az.yusif.loanflow.application.ScoringOutcome.Decision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringPolicyTest {

    private final ScoringPolicy policy = new ScoringPolicy();

    @Test
    void goodApplicantIsApprovedWithBestRate() {
        ScoringOutcome outcome = policy.evaluate(application("10000", 24, "3000", "0"), bureau(780, "200", 0)).outcome();

        assertThat(outcome.decision()).isEqualTo(Decision.APPROVE);
        assertThat(outcome.annualRate()).isEqualByComparingTo("14.0");
        assertThat(outcome.debtToIncome()).isEqualByComparingTo("0.2267");
        assertThat(outcome.reasons()).isEmpty();
    }

    @Test
    void bureauObligationsOverrideDeclaredDebt() {
        ScoringPolicy.Evaluation evaluation = policy.evaluate(application("5000", 12, "2000", "100"), bureau(700, "450", 0));

        assertThat(evaluation.existingDebt()).isEqualByComparingTo("450");
    }

    @Test
    void highDebtToIncomeIsRejected() {
        ScoringOutcome outcome = policy.evaluate(application("20000", 12, "2500", "600"), bureau(760, "600", 0)).outcome();

        assertThat(outcome.decision()).isEqualTo(Decision.REJECT);
        assertThat(outcome.reasons()).contains("DTI_TOO_HIGH");
    }

    @Test
    void lowScoreAndDelinquenciesAreRejected() {
        ScoringOutcome outcome = policy.evaluate(application("3000", 12, "2000", "0"), bureau(520, "0", 3)).outcome();

        assertThat(outcome.decision()).isEqualTo(Decision.REJECT);
        assertThat(outcome.reasons()).containsExactly("LOW_CREDIT_SCORE", "RECENT_DELINQUENCIES");
    }

    @Test
    void applicantWithoutHistoryGoesToManualReview() {
        ScoringOutcome outcome = policy.evaluate(application("3000", 12, "2000", "0"), Optional.empty()).outcome();

        assertThat(outcome.decision()).isEqualTo(Decision.MANUAL_REVIEW);
        assertThat(outcome.creditScore()).isNull();
        assertThat(outcome.annualRate()).isEqualByComparingTo("22.0");
        assertThat(outcome.reasons()).containsExactly("NO_CREDIT_HISTORY");
    }

    @Test
    void borderlineCasesNeedRiskManager() {
        ScoringOutcome outcome = policy.evaluate(application("60000", 60, "5000", "300"), bureau(630, "300", 1)).outcome();

        assertThat(outcome.decision()).isEqualTo(Decision.MANUAL_REVIEW);
        assertThat(outcome.reasons()).contains("BORDERLINE_CREDIT_SCORE", "PAST_DELINQUENCY", "LARGE_AMOUNT");
    }

    @Test
    void amountIsLimitedByIncome() {
        ScoringOutcome outcome = policy.evaluate(application("40000", 60, "1000", "0"), bureau(800, "0", 0)).outcome();

        assertThat(outcome.reasons()).contains("AMOUNT_EXCEEDS_INCOME_LIMIT");
    }

    private static ApplicationSubmitted application(String amount, int months, String income, String debt) {
        return new ApplicationSubmitted(1L, "user-1", "5ZK7M2P", new BigDecimal(amount), months,
                new BigDecimal(income), new BigDecimal(debt));
    }

    private static Optional<BureauReport> bureau(int score, String obligations, int delinquencies) {
        return Optional.of(new BureauReport("5ZK7M2P", score, 1, new BigDecimal(obligations), delinquencies));
    }
}
