package az.yusif.loanflow.application.internal;

import java.util.EnumSet;
import java.util.Set;

public enum ApplicationStatus {
    SUBMITTED,
    PENDING_APPROVAL,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    CANCELLED;

    static final Set<ApplicationStatus> AWAITING_DECISION = EnumSet.of(PENDING_APPROVAL, UNDER_REVIEW);

    public boolean isFinal() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }
}
