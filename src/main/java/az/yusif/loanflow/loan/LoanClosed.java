package az.yusif.loanflow.loan;

import java.time.Instant;

public record LoanClosed(long loanId, String borrowerId, Instant closedAt) {
}
