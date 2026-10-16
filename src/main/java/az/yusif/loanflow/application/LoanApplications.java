package az.yusif.loanflow.application;

public interface LoanApplications {

    void applyScoring(long applicationId, ScoringOutcome outcome);
}
