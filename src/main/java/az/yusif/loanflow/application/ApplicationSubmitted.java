package az.yusif.loanflow.application;

import java.math.BigDecimal;

public record ApplicationSubmitted(long applicationId,
                                   String applicantId,
                                   String finCode,
                                   BigDecimal amount,
                                   int termMonths,
                                   BigDecimal monthlyIncome,
                                   BigDecimal monthlyDebt) {
}
