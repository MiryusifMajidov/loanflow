package az.yusif.loanflow.application;

import java.math.BigDecimal;
import java.util.List;

public record ScoringOutcome(Integer creditScore,
                             BigDecimal debtToIncome,
                             Decision decision,
                             BigDecimal annualRate,
                             List<String> reasons) {

    public enum Decision {
        APPROVE,
        MANUAL_REVIEW,
        REJECT
    }
}
