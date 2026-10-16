package az.yusif.loanflow.loan.internal;

import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.loan.internal.ScheduleCalculator.ScheduledPayment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleCalculatorTest {

    private static final LocalDate FIRST_DUE = LocalDate.of(2026, 11, 15);

    private final ScheduleCalculator calculator = new ScheduleCalculator();

    @Test
    void annuityHasEqualPayments() {
        List<ScheduledPayment> schedule = calculator.calculate(new BigDecimal("10000.00"), new BigDecimal("18"), 12,
                RepaymentType.ANNUITY, FIRST_DUE);

        assertThat(schedule).hasSize(12);
        ScheduledPayment first = schedule.get(0);
        assertThat(first.interest()).isEqualByComparingTo("150.00");
        assertThat(first.principal()).isEqualByComparingTo("766.80");
        assertThat(first.total()).isEqualByComparingTo("916.80");
        assertThat(schedule.subList(0, 11)).allSatisfy(payment -> assertThat(payment.total()).isEqualByComparingTo("916.80"));
        assertThat(schedule.get(11).total()).isBetween(new BigDecimal("916.70"), new BigDecimal("916.90"));
    }

    @Test
    void differentiatedPaymentsDecrease() {
        List<ScheduledPayment> schedule = calculator.calculate(new BigDecimal("10000.00"), new BigDecimal("18"), 12,
                RepaymentType.DIFFERENTIATED, FIRST_DUE);

        assertThat(schedule.get(0).principal()).isEqualByComparingTo("833.33");
        assertThat(schedule.get(0).total()).isEqualByComparingTo("983.33");
        assertThat(schedule.get(11).total()).isLessThan(schedule.get(0).total());
    }

    @ParameterizedTest
    @EnumSource(RepaymentType.class)
    void principalIsRepaidExactly(RepaymentType type) {
        List<ScheduledPayment> schedule = calculator.calculate(new BigDecimal("7350.00"), new BigDecimal("21.5"), 37,
                type, FIRST_DUE);

        BigDecimal principal = schedule.stream().map(ScheduledPayment::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(principal).isEqualByComparingTo("7350.00");
        assertThat(schedule).allSatisfy(payment -> assertThat(payment.principal().signum()).isPositive());
    }

    @Test
    void dueDatesAreMonthly() {
        List<ScheduledPayment> schedule = calculator.calculate(new BigDecimal("1200"), new BigDecimal("14"), 3,
                RepaymentType.ANNUITY, LocalDate.of(2027, 1, 31));

        assertThat(schedule).extracting(ScheduledPayment::dueDate)
                .containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31));
    }
}
