package az.yusif.loanflow.loan.internal;

import az.yusif.loanflow.loan.InstallmentOverdue;
import az.yusif.loanflow.loan.LoanDefaulted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

@Component
public class OverdueProcessor {

    private static final Logger log = LoggerFactory.getLogger(OverdueProcessor.class);

    private final LoanRepository loans;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final LoanProperties properties;

    public OverdueProcessor(LoanRepository loans, ApplicationEventPublisher events,
                            TransactionTemplate transactionTemplate, LoanProperties properties) {
        this.loans = loans;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    public int process(LocalDate today) {
        List<Long> ids = loans.findIdsByStatusIn(EnumSet.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE));
        int changed = 0;
        for (Long id : ids) {
            Boolean updated = transactionTemplate.execute(status -> processLoan(id, today));
            if (Boolean.TRUE.equals(updated)) {
                changed++;
            }
        }
        log.info("Overdue check for {}: {} of {} loans changed", today, changed, ids.size());
        return changed;
    }

    private boolean processLoan(Long id, LocalDate today) {
        Loan loan = loans.findById(id).orElseThrow();
        LoanStatus before = loan.getStatus();
        List<Installment> newlyOverdue = loan.markOverdue(today, properties.dailyPenaltyRate(), properties.defaultAfterDays());

        newlyOverdue.forEach(installment -> events.publishEvent(new InstallmentOverdue(loan.getId(), loan.getBorrowerId(),
                installment.getNumber(), installment.getDueDate(), installment.outstanding())));
        if (before != LoanStatus.DEFAULTED && loan.getStatus() == LoanStatus.DEFAULTED) {
            events.publishEvent(new LoanDefaulted(loan.getId(), loan.getBorrowerId(), loan.outstanding()));
        }
        return before != loan.getStatus() || !newlyOverdue.isEmpty();
    }
}
