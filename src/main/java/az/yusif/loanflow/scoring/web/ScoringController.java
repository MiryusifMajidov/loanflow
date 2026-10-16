package az.yusif.loanflow.scoring.web;

import az.yusif.loanflow.application.ScoringOutcome.Decision;
import az.yusif.loanflow.scoring.internal.ScoringReport;
import az.yusif.loanflow.scoring.internal.ScoringReportRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@Tag(name = "Scoring")
public class ScoringController {

    private final ScoringReportRepository reports;

    public ScoringController(ScoringReportRepository reports) {
        this.reports = reports;
    }

    @GetMapping("/api/applications/{id}/scoring")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'RISK_MANAGER', 'ADMIN')")
    @Operation(summary = "Scoring reports of an application, newest first")
    public List<ScoringReportResponse> reports(@PathVariable long id) {
        return reports.findByApplicationIdOrderByCreatedAtDesc(id).stream()
                .map(ScoringReportResponse::from)
                .toList();
    }

    public record ScoringReportResponse(Integer bureauScore,
                                        BigDecimal existingDebt,
                                        int delinquencies,
                                        BigDecimal newPayment,
                                        BigDecimal debtToIncome,
                                        Decision decision,
                                        List<String> reasons,
                                        BigDecimal annualRate,
                                        Instant createdAt) {

        static ScoringReportResponse from(ScoringReport report) {
            List<String> reasons = report.getReasons() == null || report.getReasons().isBlank()
                    ? List.of()
                    : Arrays.asList(report.getReasons().split(","));
            return new ScoringReportResponse(report.getBureauScore(), report.getExistingDebt(), report.getDelinquencies(),
                    report.getNewPayment(), report.getDebtToIncome(), report.getDecision(), reasons,
                    report.getAnnualRate(), report.getCreatedAt());
        }
    }
}
