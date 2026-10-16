package az.yusif.loanflow.loan.internal;

import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.loan.internal.Installment.Allocation;
import az.yusif.loanflow.shared.web.LoanflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanTest {

    private static final LocalDate FIRST_DUE = LocalDate.of(2026, 11, 15);
    private static final BigDecimal DAILY_PENALTY = new BigDecimal("0.001");
    private static final Instant NOW = Instant.parse("2026-10-15T08:00:00Z");

    private Loan loan;

    @BeforeEach
    void setUp() {
        loan = new Loan(1L, "user-1", "Leyla Mammadova", new BigDecimal("1200.00"), new BigDecimal("12"), 3,
                RepaymentType.DIFFERENTIATED, NOW);
        loan.disburse(new ScheduleCalculator().calculate(loan.getPrincipal(), loan.getAnnualRate(), 3,
                RepaymentType.DIFFERENTIATED, FIRST_DUE), NOW);
    }

    @Test
    void disbursementBuildsScheduleAndActivatesLoan() {
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(loan.getInstallments()).hasSize(3);
        assertThat(loan.outstanding()).isEqualByComparingTo("1224.00");
        assertThatThrownBy(() -> loan.disburse(List.of(), NOW)).isInstanceOf(LoanflowException.class);
    }

    @Test
    void repaymentPaysInterestBeforePrincipal() {
        Allocation allocation = loan.repay(new BigDecimal("100.00"), FIRST_DUE, NOW);

        assertThat(allocation.interest()).isEqualByComparingTo("12.00");
        assertThat(allocation.principal()).isEqualByComparingTo("88.00");
        assertThat(loan.getInstallments().get(0).isPaid()).isFalse();
    }

    @Test
    void repaymentSpillsOverToNextInstallments() {
        loan.repay(new BigDecimal("500.00"), FIRST_DUE, NOW);

        List<Installment> installments = loan.getInstallments();
        assertThat(installments.get(0).isPaid()).isTrue();
        assertThat(installments.get(1).getInterestPaid()).isEqualByComparingTo("8.00");
        assertThat(installments.get(1).getPrincipalPaid()).isEqualByComparingTo("80.00");
    }

    @Test
    void payingEverythingClosesLoan() {
        loan.repay(loan.outstanding(), FIRST_DUE, NOW);

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
        assertThat(loan.getClosedAt()).isEqualTo(NOW);
        assertThat(loan.outstanding()).isZero();
    }

    @Test
    void overpaymentIsRejected() {
        assertThatThrownBy(() -> loan.repay(new BigDecimal("1224.01"), FIRST_DUE, NOW))
                .isInstanceOf(LoanflowException.class)
                .extracting("code").isEqualTo("OVERPAYMENT");
    }

    @Test
    void lateInstallmentAccruesDailyPenalty() {
        List<Installment> overdue = loan.markOverdue(FIRST_DUE.plusDays(1), DAILY_PENALTY, 90);
        loan.markOverdue(FIRST_DUE.plusDays(10), DAILY_PENALTY, 90);

        assertThat(overdue).extracting(Installment::getNumber).containsExactly(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.OVERDUE);
        assertThat(loan.getInstallments().get(0).getPenaltyDue()).isEqualByComparingTo("4.12");
    }

    @Test
    void repeatedRunOnSameDayDoesNotChargeTwice() {
        loan.markOverdue(FIRST_DUE.plusDays(5), DAILY_PENALTY, 90);
        loan.markOverdue(FIRST_DUE.plusDays(5), DAILY_PENALTY, 90);

        assertThat(loan.getInstallments().get(0).getPenaltyDue()).isEqualByComparingTo("2.06");
    }

    @Test
    void payingOverdueAmountReturnsLoanToActive() {
        loan.markOverdue(FIRST_DUE.plusDays(3), DAILY_PENALTY, 90);
        BigDecimal overdue = loan.getInstallments().get(0).outstanding();

        Allocation allocation = loan.repay(overdue, FIRST_DUE.plusDays(3), NOW);

        assertThat(allocation.penalty()).isEqualByComparingTo("1.24");
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
    }

    @Test
    void loanDefaultsAfterNinetyDays() {
        loan.markOverdue(FIRST_DUE.plusDays(91), DAILY_PENALTY, 90);

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DEFAULTED);
    }
}
