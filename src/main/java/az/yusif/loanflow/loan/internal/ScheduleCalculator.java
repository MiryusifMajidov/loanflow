package az.yusif.loanflow.loan.internal;

import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.shared.Amounts;
import az.yusif.loanflow.shared.Annuity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class ScheduleCalculator {

    public List<ScheduledPayment> calculate(BigDecimal principal, BigDecimal annualRate, int months,
                                            RepaymentType type, LocalDate firstDueDate) {
        BigDecimal monthlyRate = Annuity.monthlyRate(annualRate);
        BigDecimal annuityPayment = Annuity.monthlyPayment(principal, annualRate, months);
        BigDecimal equalPrincipal = principal.divide(BigDecimal.valueOf(months), Amounts.SCALE, RoundingMode.HALF_UP);

        List<ScheduledPayment> schedule = new ArrayList<>(months);
        BigDecimal remaining = principal;
        for (int number = 1; number <= months; number++) {
            BigDecimal interest = Amounts.money(remaining.multiply(monthlyRate));
            BigDecimal principalPart;
            if (number == months) {
                principalPart = remaining;
            } else if (type == RepaymentType.ANNUITY) {
                principalPart = annuityPayment.subtract(interest);
            } else {
                principalPart = equalPrincipal;
            }
            schedule.add(new ScheduledPayment(number, firstDueDate.plusMonths(number - 1L), principalPart, interest));
            remaining = remaining.subtract(principalPart);
        }
        return schedule;
    }

    public record ScheduledPayment(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest) {

        public BigDecimal total() {
            return principal.add(interest);
        }
    }
}
