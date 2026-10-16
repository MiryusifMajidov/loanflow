package az.yusif.loanflow.scoring.internal;

import az.yusif.loanflow.application.ApplicationSubmitted;
import az.yusif.loanflow.application.ScoringOutcome;
import az.yusif.loanflow.application.ScoringOutcome.Decision;
import az.yusif.loanflow.shared.Annuity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ScoringPolicy {

    static final BigDecimal MAX_DTI = new BigDecimal("0.50");
    static final BigDecimal REVIEW_DTI = new BigDecimal("0.40");
    static final BigDecimal REVIEW_AMOUNT = new BigDecimal("50000");
    static final int INCOME_MULTIPLIER_LIMIT = 36;
    static final int REJECT_SCORE = 550;
    static final int REVIEW_SCORE = 650;

    public Evaluation evaluate(ApplicationSubmitted application, Optional<BureauReport> bureau) {
        Integer score = bureau.map(BureauReport::score).orElse(null);
        BigDecimal rate = rateFor(score);
        BigDecimal newPayment = Annuity.monthlyPayment(application.amount(), rate, application.termMonths());

        BigDecimal existingDebt = bureau.map(BureauReport::monthlyObligations)
                .filter(obligations -> obligations.compareTo(application.monthlyDebt()) > 0)
                .orElse(application.monthlyDebt());
        BigDecimal dti = existingDebt.add(newPayment).divide(application.monthlyIncome(), 4, RoundingMode.HALF_UP);
        int delinquencies = bureau.map(BureauReport::delinquenciesLast12Months).orElse(0);

        List<String> rejectReasons = new ArrayList<>();
        if (dti.compareTo(MAX_DTI) > 0) {
            rejectReasons.add("DTI_TOO_HIGH");
        }
        if (score != null && score < REJECT_SCORE) {
            rejectReasons.add("LOW_CREDIT_SCORE");
        }
        if (delinquencies > 2) {
            rejectReasons.add("RECENT_DELINQUENCIES");
        }
        if (application.amount().compareTo(application.monthlyIncome().multiply(BigDecimal.valueOf(INCOME_MULTIPLIER_LIMIT))) > 0) {
            rejectReasons.add("AMOUNT_EXCEEDS_INCOME_LIMIT");
        }
        if (!rejectReasons.isEmpty()) {
            return new Evaluation(new ScoringOutcome(score, dti, Decision.REJECT, rate, rejectReasons), newPayment, existingDebt, delinquencies);
        }

        List<String> reviewReasons = new ArrayList<>();
        if (score == null) {
            reviewReasons.add("NO_CREDIT_HISTORY");
        } else if (score < REVIEW_SCORE) {
            reviewReasons.add("BORDERLINE_CREDIT_SCORE");
        }
        if (dti.compareTo(REVIEW_DTI) > 0) {
            reviewReasons.add("HIGH_DTI");
        }
        if (delinquencies > 0) {
            reviewReasons.add("PAST_DELINQUENCY");
        }
        if (application.amount().compareTo(REVIEW_AMOUNT) > 0) {
            reviewReasons.add("LARGE_AMOUNT");
        }

        Decision decision = reviewReasons.isEmpty() ? Decision.APPROVE : Decision.MANUAL_REVIEW;
        return new Evaluation(new ScoringOutcome(score, dti, decision, rate, reviewReasons), newPayment, existingDebt, delinquencies);
    }

    static BigDecimal rateFor(Integer score) {
        if (score == null) {
            return new BigDecimal("22.0");
        }
        if (score >= 750) {
            return new BigDecimal("14.0");
        }
        if (score >= 680) {
            return new BigDecimal("16.5");
        }
        if (score >= 600) {
            return new BigDecimal("19.0");
        }
        return new BigDecimal("22.0");
    }

    public record Evaluation(ScoringOutcome outcome, BigDecimal newPayment, BigDecimal existingDebt, int delinquencies) {
    }
}
