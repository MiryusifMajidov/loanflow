package az.yusif.loanflow.loan.internal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "loanflow.loans")
public record LoanProperties(@NotNull @DecimalMin("0") BigDecimal dailyPenaltyRate,
                             @Min(1) int defaultAfterDays,
                             @NotBlank String overdueCron) {
}
