package az.yusif.loanflow.application.web;

import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.application.internal.ApplicationService.NewApplication;
import az.yusif.loanflow.application.internal.ApplicationStatus;
import az.yusif.loanflow.application.internal.LoanApplication;
import az.yusif.loanflow.application.internal.LoanPurpose;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record SubmitApplicationRequest(
            @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{7}$", message = "must be 7 letters or digits") String finCode,
            @NotNull @DecimalMin("300.00") @DecimalMax("100000.00") @Digits(integer = 6, fraction = 2) BigDecimal amount,
            @Min(3) @Max(60) int termMonths,
            @NotNull LoanPurpose purpose,
            @NotNull RepaymentType repaymentType,
            @NotNull @DecimalMin("1.00") @Digits(integer = 9, fraction = 2) BigDecimal monthlyIncome,
            @NotNull @DecimalMin("0.00") @Digits(integer = 9, fraction = 2) BigDecimal monthlyDebt) {

        NewApplication toCommand() {
            return new NewApplication(finCode, amount, termMonths, purpose, repaymentType, monthlyIncome, monthlyDebt);
        }
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ApplicationResponse(Long id,
                                      String applicantId,
                                      String applicantName,
                                      BigDecimal amount,
                                      int termMonths,
                                      LoanPurpose purpose,
                                      RepaymentType repaymentType,
                                      ApplicationStatus status,
                                      Integer creditScore,
                                      BigDecimal debtToIncome,
                                      BigDecimal annualRate,
                                      String decisionReason,
                                      String decidedBy,
                                      Instant decidedAt,
                                      Instant createdAt) {

        static ApplicationResponse from(LoanApplication application) {
            return new ApplicationResponse(application.getId(), application.getApplicantId(),
                    application.getApplicantName(), application.getAmount(), application.getTermMonths(),
                    application.getPurpose(), application.getRepaymentType(), application.getStatus(),
                    application.getCreditScore(), application.getDebtToIncome(), application.getAnnualRate(),
                    application.getDecisionReason(), application.getDecidedBy(), application.getDecidedAt(),
                    application.getCreatedAt());
        }
    }
}
