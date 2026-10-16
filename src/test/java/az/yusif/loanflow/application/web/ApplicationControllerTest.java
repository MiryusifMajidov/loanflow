package az.yusif.loanflow.application.web;

import az.yusif.loanflow.application.internal.ApplicationHistory;
import az.yusif.loanflow.application.internal.ApplicationService;
import az.yusif.loanflow.shared.security.SecurityConfig;
import az.yusif.loanflow.shared.web.LoanflowException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicationController.class)
@Import(SecurityConfig.class)
class ApplicationControllerTest {

    private static final String VALID_BODY = """
            {"finCode": "5ZK7M2P", "amount": 5000.00, "termMonths": 12, "purpose": "CONSUMER",
             "repaymentType": "ANNUITY", "monthlyIncome": 2500.00, "monthlyDebt": 0}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService applicationService;

    @MockitoBean
    private ApplicationHistory history;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mockMvc.perform(get("/api/applications/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void validationErrorsAreProblemDetails() throws Exception {
        mockMvc.perform(post("/api/applications").with(user("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finCode": "12", "amount": 50, "termMonths": 120, "purpose": "CONSUMER",
                                 "repaymentType": "ANNUITY", "monthlyIncome": 2500, "monthlyDebt": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://loanflow.local/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));

        verifyNoInteractions(applicationService);
    }

    @Test
    void officersCannotApplyForLoans() throws Exception {
        mockMvc.perform(post("/api/applications").with(user("LOAN_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void customersCannotSeeWorkQueue() throws Exception {
        mockMvc.perform(get("/api/applications").with(user("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessErrorsCarryTheirCode() throws Exception {
        when(applicationService.approve(eq(7L), any()))
                .thenThrow(LoanflowException.ruleViolation("INVALID_STATUS", "Application is REJECTED"));

        mockMvc.perform(post("/api/applications/7/approve").with(user("LOAN_OFFICER")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://loanflow.local/problems/invalid-status"))
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.detail").value("Application is REJECTED"));
    }

    private static RequestPostProcessor user(String role) {
        return jwt().jwt(token -> token.subject("user-1").claim("name", "Test User"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
