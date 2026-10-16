package az.yusif.loanflow.application;

public record ApplicationRejected(long applicationId, String applicantId, String reason) {
}
