package az.yusif.loanflow.loan.web;

import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.loan.internal.Installment;
import az.yusif.loanflow.loan.internal.InstallmentStatus;
import az.yusif.loanflow.loan.internal.Loan;
import az.yusif.loanflow.loan.internal.LoanStatus;
import az.yusif.loanflow.loan.internal.Repayment;
import az.yusif.loanflow.loan.internal.ScheduleCalculator.ScheduledPayment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class LoanDtos {

    private LoanDtos() {
    }

    public record RepaymentRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {
    }

    public record LoanSummary(Long id,
                              Long applicationId,
                              BigDecimal principal,
                              BigDecimal annualRate,
                              int termMonths,
                              RepaymentType repaymentType,
                              LoanStatus status,
                              Instant disbursedAt,
                              Instant closedAt) {

        static LoanSummary from(Loan loan) {
            return new LoanSummary(loan.getId(), loan.getApplicationId(), loan.getPrincipal(), loan.getAnnualRate(),
                    loan.getTermMonths(), loan.getRepaymentType(), loan.getStatus(), loan.getDisbursedAt(),
                    loan.getClosedAt());
        }
    }

    public record LoanDetails(LoanSummary loan,
                              String borrowerName,
                              BigDecimal regularPayment,
                              BigDecimal outstanding,
                              List<InstallmentView> schedule) {

        static LoanDetails from(Loan loan) {
            List<InstallmentView> schedule = loan.getInstallments().stream().map(InstallmentView::from).toList();
            return new LoanDetails(LoanSummary.from(loan), loan.getBorrowerName(), loan.regularPayment(),
                    loan.outstanding(), schedule);
        }
    }

    public record InstallmentView(int number,
                                  LocalDate dueDate,
                                  BigDecimal principal,
                                  BigDecimal interest,
                                  BigDecimal penalty,
                                  BigDecimal paid,
                                  InstallmentStatus status,
                                  LocalDate paidOn) {

        static InstallmentView from(Installment installment) {
            BigDecimal paid = installment.getPrincipalPaid().add(installment.getInterestPaid()).add(installment.getPenaltyPaid());
            return new InstallmentView(installment.getNumber(), installment.getDueDate(), installment.getPrincipalDue(),
                    installment.getInterestDue(), installment.getPenaltyDue(), paid, installment.getStatus(),
                    installment.getPaidOn());
        }
    }

    public record RepaymentView(Long id, BigDecimal amount, BigDecimal principal, BigDecimal interest,
                                BigDecimal penalty, String paidBy, Instant paidAt) {

        static RepaymentView from(Repayment repayment) {
            return new RepaymentView(repayment.getId(), repayment.getAmount(), repayment.getPrincipalPart(),
                    repayment.getInterestPart(), repayment.getPenaltyPart(), repayment.getPaidBy(), repayment.getPaidAt());
        }
    }

    public record SchedulePreview(BigDecimal totalPayment, BigDecimal totalInterest, List<ScheduledPayment> payments) {

        static SchedulePreview from(List<ScheduledPayment> payments) {
            BigDecimal interest = payments.stream().map(ScheduledPayment::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal total = payments.stream().map(ScheduledPayment::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new SchedulePreview(total, interest, payments);
        }
    }
}
