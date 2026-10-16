package az.yusif.loanflow.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentOverdue(long loanId,
                                 String borrowerId,
                                 int installmentNumber,
                                 LocalDate dueDate,
                                 BigDecimal overdueAmount) {
}
