package az.yusif.loanflow.scoring.internal;

import az.yusif.loanflow.application.ApplicationSubmitted;
import az.yusif.loanflow.application.LoanApplications;
import az.yusif.loanflow.scoring.internal.ScoringPolicy.Evaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final CreditBureauClient bureau;
    private final ScoringPolicy policy;
    private final ScoringReportRepository reports;
    private final LoanApplications applications;
    private final Clock clock;

    public ScoringService(CreditBureauClient bureau, ScoringPolicy policy, ScoringReportRepository reports,
                          LoanApplications applications, Clock clock) {
        this.bureau = bureau;
        this.policy = policy;
        this.reports = reports;
        this.applications = applications;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void onApplicationSubmitted(ApplicationSubmitted application) {
        Optional<BureauReport> report = bureau.findReport(application.finCode());
        Evaluation evaluation = policy.evaluate(application, report);

        reports.save(new ScoringReport(application.applicationId(), evaluation, clock.instant()));
        applications.applyScoring(application.applicationId(), evaluation.outcome());
        log.info("Application {} scored: {} (score {}, DTI {})", application.applicationId(),
                evaluation.outcome().decision(), evaluation.outcome().creditScore(), evaluation.outcome().debtToIncome());
    }
}
