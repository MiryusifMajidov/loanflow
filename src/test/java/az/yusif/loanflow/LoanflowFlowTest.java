package az.yusif.loanflow;

import az.yusif.loanflow.loan.internal.OverdueProcessor;
import az.yusif.loanflow.notification.RabbitConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:loanflow;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/realms/test",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/realms/test/certs"
})
@AutoConfigureMockMvc
class LoanflowFlowTest {

    private static final WireMockServer bureau = new WireMockServer(options().dynamicPort());

    static {
        bureau.start();
    }

    private static final RequestPostProcessor OFFICER = staff("officer", "LOAN_OFFICER");
    private static final RequestPostProcessor RISK_MANAGER = staff("risk", "RISK_MANAGER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OverdueProcessor overdueProcessor;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @DynamicPropertySource
    static void bureauUrl(DynamicPropertyRegistry registry) {
        registry.add("loanflow.bureau.base-url", bureau::baseUrl);
    }

    @AfterAll
    static void stopBureau() {
        bureau.stop();
    }

    @Test
    void applicationBecomesLoanAndGetsRepaid() throws Exception {
        bureau.stubFor(get(urlEqualTo("/reports/5ZK7M2P")).willReturn(okJson(report("5ZK7M2P", 770, 0, 0))));
        RequestPostProcessor customer = customer("Leyla Mammadova");

        long applicationId = submit(customer, "5ZK7M2P", "1200.00", 3);
        JsonNode scored = awaitStatus(customer, applicationId, "PENDING_APPROVAL");
        assertThat(scored.get("creditScore").asInt()).isEqualTo(770);
        assertThat(scored.get("annualRate").decimalValue()).isEqualByComparingTo("14.0");

        JsonNode reports = json(mockMvc.perform(getRequest("/api/applications/" + applicationId + "/scoring").with(OFFICER)));
        assertThat(reports.get(0).get("decision").asText()).isEqualTo("APPROVE");

        json(mockMvc.perform(post("/api/applications/{id}/approve", applicationId).with(OFFICER)));

        long loanId = await().atMost(Duration.ofSeconds(10)).until(
                () -> json(mockMvc.perform(getRequest("/api/loans/mine").with(customer))),
                loans -> loans.size() == 1).get(0).get("id").asLong();

        JsonNode disbursed = json(mockMvc.perform(post("/api/loans/{id}/disburse", loanId).with(OFFICER)));
        assertThat(disbursed.get("status").asText()).isEqualTo("ACTIVE");

        JsonNode details = json(mockMvc.perform(getRequest("/api/loans/" + loanId).with(customer)));
        assertThat(details.get("schedule")).hasSize(3);
        String outstanding = details.get("outstanding").decimalValue().toPlainString();

        JsonNode repayment = json(mockMvc.perform(post("/api/loans/{id}/repayments", loanId).with(customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + outstanding + "}")).andExpect(status().isCreated()));
        assertThat(repayment.get("principal").decimalValue()).isEqualByComparingTo("1200.00");

        JsonNode closed = json(mockMvc.perform(getRequest("/api/loans/" + loanId).with(customer)));
        assertThat(closed.get("loan").get("status").asText()).isEqualTo("CLOSED");

        JsonNode history = json(mockMvc.perform(getRequest("/api/applications/" + applicationId + "/history").with(OFFICER)));
        assertThat(history.findValuesAsText("status")).containsExactly("SUBMITTED", "PENDING_APPROVAL", "APPROVED");
        assertThat(history.get(1).get("changedBy").asText()).isEqualTo("system");
        assertThat(history.get(2).get("changedBy").asText()).isEqualTo("officer");

        verify(rabbitTemplate, timeout(5000)).convertAndSend(eq(RabbitConfig.EXCHANGE), eq("application.approved"), any(Object.class));
        verify(rabbitTemplate, timeout(5000)).convertAndSend(eq(RabbitConfig.EXCHANGE), eq("loan.disbursed"), any(Object.class));
        verify(rabbitTemplate, timeout(5000)).convertAndSend(eq(RabbitConfig.EXCHANGE), eq("loan.closed"), any(Object.class));
    }

    @Test
    void applicantWithoutHistoryNeedsRiskManager() throws Exception {
        bureau.stubFor(get(urlEqualTo("/reports/1AB2CD3")).willReturn(aResponse().withStatus(404)));
        RequestPostProcessor customer = customer("Tural Guliyev");

        long applicationId = submit(customer, "1AB2CD3", "3000.00", 12);
        JsonNode review = awaitStatus(customer, applicationId, "UNDER_REVIEW");
        assertThat(review.get("decisionReason").asText()).isEqualTo("NO_CREDIT_HISTORY");

        mockMvc.perform(post("/api/applications/{id}/approve", applicationId).with(OFFICER))
                .andExpect(status().isForbidden());
        JsonNode approved = json(mockMvc.perform(post("/api/applications/{id}/approve", applicationId).with(RISK_MANAGER)));
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void bureauOutageCanBeRetried() throws Exception {
        bureau.stubFor(get(urlEqualTo("/reports/9QW8E7R")).willReturn(aResponse().withStatus(503)));
        RequestPostProcessor customer = customer("Nigar Huseynova");

        long applicationId = submit(customer, "9QW8E7R", "2000.00", 6);
        Thread.sleep(1500);
        assertThat(application(customer, applicationId).get("status").asText()).isEqualTo("SUBMITTED");

        bureau.stubFor(get(urlEqualTo("/reports/9QW8E7R")).willReturn(okJson(report("9QW8E7R", 710, 0, 0))));
        mockMvc.perform(post("/api/applications/{id}/rescore", applicationId).with(OFFICER)).andExpect(status().isOk());

        assertThat(awaitStatus(customer, applicationId, "PENDING_APPROVAL").get("annualRate").decimalValue())
                .isEqualByComparingTo("16.5");
    }

    @Test
    void lateInstallmentsAreMarkedOverdue() throws Exception {
        bureau.stubFor(get(urlEqualTo("/reports/4RT5YU6")).willReturn(okJson(report("4RT5YU6", 760, 0, 0))));
        RequestPostProcessor customer = customer("Kamran Rzayev");

        long applicationId = submit(customer, "4RT5YU6", "900.00", 3);
        awaitStatus(customer, applicationId, "PENDING_APPROVAL");
        json(mockMvc.perform(post("/api/applications/{id}/approve", applicationId).with(OFFICER)));
        long loanId = await().atMost(Duration.ofSeconds(10)).until(
                () -> json(mockMvc.perform(getRequest("/api/loans/mine").with(customer))),
                loans -> loans.size() == 1).get(0).get("id").asLong();
        json(mockMvc.perform(post("/api/loans/{id}/disburse", loanId).with(OFFICER)));

        overdueProcessor.process(LocalDate.now().plusMonths(1).plusDays(3));

        JsonNode loan = json(mockMvc.perform(getRequest("/api/loans/" + loanId).with(customer)));
        assertThat(loan.get("loan").get("status").asText()).isEqualTo("OVERDUE");
        assertThat(loan.get("schedule").get(0).get("status").asText()).isEqualTo("OVERDUE");
        assertThat(loan.get("schedule").get(0).get("penalty").decimalValue()).isPositive();
        verify(rabbitTemplate, timeout(5000)).convertAndSend(eq(RabbitConfig.EXCHANGE), eq("loan.installment-overdue"), any(Object.class));
    }

    private long submit(RequestPostProcessor customer, String fin, String amount, int months) throws Exception {
        String body = """
                {"finCode": "%s", "amount": %s, "termMonths": %d, "purpose": "CONSUMER",
                 "repaymentType": "ANNUITY", "monthlyIncome": 3000.00, "monthlyDebt": 0}
                """.formatted(fin, amount, months);
        return json(mockMvc.perform(post("/api/applications").with(customer)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())).get("id").asLong();
    }

    private JsonNode awaitStatus(RequestPostProcessor customer, long applicationId, String expected) {
        return await().atMost(Duration.ofSeconds(10))
                .until(() -> application(customer, applicationId), node -> expected.equals(node.get("status").asText()));
    }

    private JsonNode application(RequestPostProcessor customer, long applicationId) throws Exception {
        return json(mockMvc.perform(getRequest("/api/applications/" + applicationId).with(customer)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder getRequest(String url) {
        return MockMvcRequestBuilders.get(url);
    }

    private static RequestPostProcessor customer(String name) {
        String id = UUID.randomUUID().toString();
        return jwt().jwt(token -> token.subject(id).claim("name", name).claim("preferred_username", name))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor staff(String username, String role) {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("name", username)
                        .claim("preferred_username", username))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static String report(String fin, int score, int obligations, int delinquencies) {
        return """
                {"fin": "%s", "score": %d, "activeLoans": 0, "monthlyObligations": %d, "delinquenciesLast12Months": %d}
                """.formatted(fin, score, obligations, delinquencies);
    }
}
