package az.yusif.loanflow.loan.web;

import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.loan.internal.LoanService;
import az.yusif.loanflow.loan.internal.LoanStatus;
import az.yusif.loanflow.loan.web.LoanDtos.LoanDetails;
import az.yusif.loanflow.loan.web.LoanDtos.LoanSummary;
import az.yusif.loanflow.loan.web.LoanDtos.RepaymentRequest;
import az.yusif.loanflow.loan.web.LoanDtos.RepaymentView;
import az.yusif.loanflow.loan.web.LoanDtos.SchedulePreview;
import az.yusif.loanflow.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/loans")
@Tag(name = "Loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/mine")
    public List<LoanSummary> mine(CurrentUser user) {
        return loanService.mine(user).stream().map(LoanSummary::from).toList();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'RISK_MANAGER', 'ADMIN')")
    public Page<LoanSummary> byStatus(@RequestParam LoanStatus status,
                                      @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return loanService.byStatus(status, pageable).map(LoanSummary::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Loan with its repayment schedule")
    public LoanDetails get(@PathVariable long id, CurrentUser user) {
        return LoanDetails.from(loanService.get(id, user));
    }

    @PostMapping("/{id}/disburse")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
    @Operation(summary = "Pay out an approved loan and build its schedule")
    public LoanSummary disburse(@PathVariable long id) {
        return LoanSummary.from(loanService.disburse(id));
    }

    @PostMapping("/{id}/repayments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Repay a loan",
            description = "Money goes to penalties first, then interest, then principal, oldest installment first")
    public RepaymentView repay(@PathVariable long id, @Valid @RequestBody RepaymentRequest request, CurrentUser user) {
        return RepaymentView.from(loanService.repay(id, request.amount(), user));
    }

    @GetMapping("/{id}/repayments")
    public List<RepaymentView> repayments(@PathVariable long id, CurrentUser user) {
        return loanService.repayments(id, user).stream().map(RepaymentView::from).toList();
    }

    @GetMapping("/preview")
    @Operation(summary = "Calculate a schedule without applying")
    public SchedulePreview preview(@RequestParam @DecimalMin("100") @DecimalMax("100000") BigDecimal amount,
                                   @RequestParam @Min(3) @Max(60) int termMonths,
                                   @RequestParam @DecimalMin("0") @DecimalMax("60") BigDecimal annualRate,
                                   @RequestParam(defaultValue = "ANNUITY") RepaymentType type) {
        return SchedulePreview.from(loanService.preview(amount, annualRate, termMonths, type));
    }
}
