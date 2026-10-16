package az.yusif.loanflow.application.web;

import az.yusif.loanflow.application.internal.ApplicationHistory;
import az.yusif.loanflow.application.internal.ApplicationHistory.Revision;
import az.yusif.loanflow.application.internal.ApplicationService;
import az.yusif.loanflow.application.internal.ApplicationStatus;
import az.yusif.loanflow.application.web.ApplicationDtos.ApplicationResponse;
import az.yusif.loanflow.application.web.ApplicationDtos.RejectRequest;
import az.yusif.loanflow.application.web.ApplicationDtos.SubmitApplicationRequest;
import az.yusif.loanflow.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@Tag(name = "Loan applications")
public class ApplicationController {

    private static final String STAFF = "hasAnyRole('LOAN_OFFICER', 'RISK_MANAGER', 'ADMIN')";

    private final ApplicationService applicationService;
    private final ApplicationHistory history;

    public ApplicationController(ApplicationService applicationService, ApplicationHistory history) {
        this.applicationService = applicationService;
        this.history = history;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Apply for a loan", description = "Scoring runs in the background, poll the application for the result")
    public ApplicationResponse submit(CurrentUser user, @Valid @RequestBody SubmitApplicationRequest request) {
        return ApplicationResponse.from(applicationService.submit(user, request.toCommand()));
    }

    @GetMapping("/mine")
    public List<ApplicationResponse> mine(CurrentUser user) {
        return applicationService.mine(user).stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable long id, CurrentUser user) {
        return ApplicationResponse.from(applicationService.get(id, user));
    }

    @GetMapping
    @PreAuthorize(STAFF)
    @Operation(summary = "Work queue of applications, oldest first when filtered by status")
    public Page<ApplicationResponse> search(@RequestParam(required = false) ApplicationStatus status,
                                            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return applicationService.search(status, pageable).map(ApplicationResponse::from);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(STAFF)
    @Operation(summary = "Approve an application",
            description = "Loan officers approve PENDING_APPROVAL, risk managers approve UNDER_REVIEW")
    public ApplicationResponse approve(@PathVariable long id, CurrentUser user) {
        return ApplicationResponse.from(applicationService.approve(id, user));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(STAFF)
    public ApplicationResponse reject(@PathVariable long id, @Valid @RequestBody RejectRequest request, CurrentUser user) {
        return ApplicationResponse.from(applicationService.reject(id, request.reason(), user));
    }

    @PostMapping("/{id}/cancel")
    public ApplicationResponse cancel(@PathVariable long id, CurrentUser user) {
        return ApplicationResponse.from(applicationService.cancel(id, user));
    }

    @PostMapping("/{id}/rescore")
    @PreAuthorize(STAFF)
    @Operation(summary = "Run scoring again, e.g. after the credit bureau was unavailable")
    public ApplicationResponse rescore(@PathVariable long id) {
        return ApplicationResponse.from(applicationService.rescore(id));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize(STAFF)
    @Operation(summary = "Audit trail of status changes (Hibernate Envers)")
    public List<Revision> history(@PathVariable long id) {
        return history.of(id);
    }
}
