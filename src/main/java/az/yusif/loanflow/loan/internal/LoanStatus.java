package az.yusif.loanflow.loan.internal;

public enum LoanStatus {
    AWAITING_DISBURSEMENT,
    ACTIVE,
    OVERDUE,
    DEFAULTED,
    CLOSED;

    public boolean acceptsRepayments() {
        return this == ACTIVE || this == OVERDUE || this == DEFAULTED;
    }
}
