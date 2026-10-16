package az.yusif.loanflow.loan;

import java.math.BigDecimal;

public record LoanDefaulted(long loanId, String borrowerId, BigDecimal outstanding) {
}
