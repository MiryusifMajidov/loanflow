package az.yusif.loanflow.application;

import java.math.BigDecimal;

public record ApplicationApproved(long applicationId,
                                  String applicantId,
                                  String applicantName,
                                  BigDecimal amount,
                                  int termMonths,
                                  BigDecimal annualRate,
                                  RepaymentType repaymentType) {
}
