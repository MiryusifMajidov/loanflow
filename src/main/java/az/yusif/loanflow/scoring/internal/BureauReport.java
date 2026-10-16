package az.yusif.loanflow.scoring.internal;

import java.math.BigDecimal;

public record BureauReport(String fin,
                           int score,
                           int activeLoans,
                           BigDecimal monthlyObligations,
                           int delinquenciesLast12Months) {
}
