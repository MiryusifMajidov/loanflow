package az.yusif.loanflow.application.internal;

import az.yusif.loanflow.application.ApplicationApproved;
import az.yusif.loanflow.application.ApplicationRejected;
import az.yusif.loanflow.application.ApplicationSubmitted;
import az.yusif.loanflow.application.LoanApplications;
import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.application.ScoringOutcome;
import az.yusif.loanflow.shared.Amounts;
import az.yusif.loanflow.shared.security.CurrentUser;
import az.yusif.loanflow.shared.web.LoanflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;

@Service
public class ApplicationService implements LoanApplications {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final List<ApplicationStatus> OPEN = List.of(
            ApplicationStatus.SUBMITTED, ApplicationStatus.PENDING_APPROVAL, ApplicationStatus.UNDER_REVIEW);

    private final LoanApplicationRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ApplicationService(LoanApplicationRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public LoanApplication submit(CurrentUser applicant, NewApplication request) {
        if (repository.existsByApplicantIdAndStatusIn(applicant.id(), OPEN)) {
            throw LoanflowException.conflict("OPEN_APPLICATION_EXISTS", "You already have an application in progress");
        }

        LoanApplication application = repository.save(new LoanApplication(applicant.id(), applicant.name(),
                request.finCode().toUpperCase(Locale.ROOT), Amounts.money(request.amount()), request.termMonths(),
                request.purpose(), request.repaymentType(), Amounts.money(request.monthlyIncome()),
                Amounts.money(request.monthlyDebt()), clock.instant()));

        events.publishEvent(submittedEvent(application));
        log.info("Application {} submitted for {} AZN / {} months", application.getId(), application.getAmount(),
                application.getTermMonths());
        return application;
    }

    @Override
    @Transactional
    public void applyScoring(long applicationId, ScoringOutcome outcome) {
        LoanApplication application = find(applicationId);
        application.applyScoring(outcome, clock.instant());
        if (application.getStatus() == ApplicationStatus.REJECTED) {
            events.publishEvent(new ApplicationRejected(application.getId(), application.getApplicantId(),
                    application.getDecisionReason()));
        }
    }

    @Transactional
    public LoanApplication approve(long id, CurrentUser user) {
        LoanApplication application = find(id);
        application.approve(user, clock.instant());
        events.publishEvent(new ApplicationApproved(application.getId(), application.getApplicantId(),
                application.getApplicantName(), application.getAmount(), application.getTermMonths(),
                application.getAnnualRate(), application.getRepaymentType()));
        return application;
    }

    @Transactional
    public LoanApplication reject(long id, String reason, CurrentUser user) {
        LoanApplication application = find(id);
        application.reject(user, reason, clock.instant());
        events.publishEvent(new ApplicationRejected(application.getId(), application.getApplicantId(), reason));
        return application;
    }

    @Transactional
    public LoanApplication cancel(long id, CurrentUser user) {
        LoanApplication application = find(id);
        application.cancel(user, clock.instant());
        return application;
    }

    @Transactional
    public LoanApplication rescore(long id) {
        LoanApplication application = find(id);
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw LoanflowException.ruleViolation("INVALID_STATUS", "Only applications waiting for scoring can be rescored");
        }
        events.publishEvent(submittedEvent(application));
        return application;
    }

    @Transactional(readOnly = true)
    public LoanApplication get(long id, CurrentUser user) {
        LoanApplication application = find(id);
        if (!application.isVisibleTo(user)) {
            throw notFound(id);
        }
        return application;
    }

    @Transactional(readOnly = true)
    public List<LoanApplication> mine(CurrentUser user) {
        return repository.findByApplicantIdOrderByCreatedAtDesc(user.id());
    }

    @Transactional(readOnly = true)
    public Page<LoanApplication> search(ApplicationStatus status, Pageable pageable) {
        return status != null
                ? repository.findByStatusOrderByCreatedAtAsc(status, pageable)
                : repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private LoanApplication find(long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private static ApplicationSubmitted submittedEvent(LoanApplication application) {
        return new ApplicationSubmitted(application.getId(), application.getApplicantId(), application.getFinCode(),
                application.getAmount(), application.getTermMonths(), application.getMonthlyIncome(),
                application.getMonthlyDebt());
    }

    private static LoanflowException notFound(long id) {
        return LoanflowException.notFound("APPLICATION_NOT_FOUND", "Application " + id + " not found");
    }

    public record NewApplication(String finCode,
                                 BigDecimal amount,
                                 int termMonths,
                                 LoanPurpose purpose,
                                 RepaymentType repaymentType,
                                 BigDecimal monthlyIncome,
                                 BigDecimal monthlyDebt) {
    }
}
