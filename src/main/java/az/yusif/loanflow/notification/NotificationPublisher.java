package az.yusif.loanflow.notification;

import az.yusif.loanflow.application.ApplicationApproved;
import az.yusif.loanflow.application.ApplicationRejected;
import az.yusif.loanflow.loan.InstallmentOverdue;
import az.yusif.loanflow.loan.LoanClosed;
import az.yusif.loanflow.loan.LoanDefaulted;
import az.yusif.loanflow.loan.LoanDisbursed;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

@Component
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public NotificationPublisher(RabbitTemplate rabbitTemplate, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(ApplicationApproved event) {
        send("application.approved", event.applicantId(), "APPLICATION_APPROVED",
                "Your loan application for %s AZN is approved at %s%% per year.".formatted(event.amount(), event.annualRate()),
                Map.of("applicationId", event.applicationId(), "amount", event.amount()));
    }

    @ApplicationModuleListener
    void on(ApplicationRejected event) {
        send("application.rejected", event.applicantId(), "APPLICATION_REJECTED",
                "Unfortunately your loan application was not approved.",
                Map.of("applicationId", event.applicationId(), "reason", String.valueOf(event.reason())));
    }

    @ApplicationModuleListener
    void on(LoanDisbursed event) {
        send("loan.disbursed", event.borrowerId(), "LOAN_DISBURSED",
                "%s AZN was paid out. First payment of %s AZN is due on %s."
                        .formatted(event.principal(), event.monthlyPayment(), event.firstDueDate()),
                Map.of("loanId", event.loanId(), "firstDueDate", event.firstDueDate().toString()));
    }

    @ApplicationModuleListener
    void on(InstallmentOverdue event) {
        send("loan.installment-overdue", event.borrowerId(), "INSTALLMENT_OVERDUE",
                "Payment #%d of %s AZN due on %s is overdue. Penalties apply every day."
                        .formatted(event.installmentNumber(), event.overdueAmount(), event.dueDate()),
                Map.of("loanId", event.loanId(), "installment", event.installmentNumber()));
    }

    @ApplicationModuleListener
    void on(LoanDefaulted event) {
        send("loan.defaulted", event.borrowerId(), "LOAN_DEFAULTED",
                "Your loan is in default. Outstanding amount: %s AZN.".formatted(event.outstanding()),
                Map.of("loanId", event.loanId()));
    }

    @ApplicationModuleListener
    void on(LoanClosed event) {
        send("loan.closed", event.borrowerId(), "LOAN_CLOSED", "Your loan is fully repaid. Thank you!",
                Map.of("loanId", event.loanId()));
    }

    private void send(String routingKey, String recipient, String type, String text, Map<String, Object> data) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey,
                new NotificationMessage(recipient, type, text, data, clock.instant()));
    }
}
