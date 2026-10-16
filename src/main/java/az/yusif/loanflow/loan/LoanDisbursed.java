package az.yusif.loanflow.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanDisbursed(long loanId,
                            long applicationId,
                            String borrowerId,
                            BigDecimal principal,
                            BigDecimal monthlyPayment,
                            LocalDate firstDueDate) {
}
