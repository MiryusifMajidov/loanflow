package az.yusif.loanflow;

import az.yusif.loanflow.application.RepaymentType;
import az.yusif.loanflow.application.internal.ApplicationHistory;
import az.yusif.loanflow.application.internal.ApplicationService;
import az.yusif.loanflow.application.internal.ApplicationService.NewApplication;
import az.yusif.loanflow.application.internal.ApplicationStatus;
import az.yusif.loanflow.application.internal.LoanApplication;
import az.yusif.loanflow.application.internal.LoanPurpose;
import az.yusif.loanflow.loan.internal.Loan;
import az.yusif.loanflow.loan.internal.LoanService;
import az.yusif.loanflow.loan.internal.LoanStatus;
import az.yusif.loanflow.scoring.internal.BureauReport;
import az.yusif.loanflow.scoring.internal.CreditBureauClient;
import az.yusif.loanflow.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/realms/test",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/realms/test/certs"
})
@Testcontainers(disabledWithoutDocker = true)
class OracleIntegrationTest {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationHistory history;

    @Autowired
    private LoanService loanService;

    @Autowired
    private Scheduler scheduler;

    @MockitoBean
    private CreditBureauClient bureau;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void fullLifecycleWorksOnOracle() throws Exception {
        when(bureau.findReport(anyString()))
                .thenReturn(Optional.of(new BureauReport("5ZK7M2P", 765, 0, BigDecimal.ZERO, 0)));
        CurrentUser customer = new CurrentUser("c0ffee00-0000-4000-8000-000000000001", "Leyla Mammadova", Set.of("CUSTOMER"));
        CurrentUser officer = new CurrentUser("c0ffee00-0000-4000-8000-000000000002", "officer", Set.of("LOAN_OFFICER"));

        LoanApplication submitted = applicationService.submit(customer, new NewApplication("5ZK7M2P",
                new BigDecimal("2400.00"), 6, LoanPurpose.EDUCATION, RepaymentType.DIFFERENTIATED,
                new BigDecimal("2000.00"), BigDecimal.ZERO));

        await().atMost(Duration.ofSeconds(20)).until(
                () -> applicationService.get(submitted.getId(), customer).getStatus() == ApplicationStatus.PENDING_APPROVAL);
        applicationService.approve(submitted.getId(), officer);

        List<Loan> loans = await().atMost(Duration.ofSeconds(20)).until(() -> loanService.mine(customer), list -> !list.isEmpty());
        Loan disbursed = loanService.disburse(loans.get(0).getId());
        loanService.repay(disbursed.getId(), loanService.get(disbursed.getId(), customer).outstanding(), customer);

        assertThat(loanService.get(disbursed.getId(), customer).getStatus()).isEqualTo(LoanStatus.CLOSED);
        assertThat(history.of(submitted.getId())).hasSize(3);
        assertThat(scheduler.getTrigger(TriggerKey.triggerKey("overdue-installments-daily", "loans"))).isNotNull();
    }
}
