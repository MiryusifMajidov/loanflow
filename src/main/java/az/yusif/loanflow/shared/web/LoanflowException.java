package az.yusif.loanflow.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.util.Locale;

public class LoanflowException extends ErrorResponseException {

    private static final String TYPE_BASE = "https://loanflow.local/problems/";

    private final String code;

    protected LoanflowException(HttpStatus status, String code, String detail) {
        super(status, problem(status, code, detail), null);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return getBody().getDetail();
    }

    public static LoanflowException notFound(String code, String detail) {
        return new LoanflowException(HttpStatus.NOT_FOUND, code, detail);
    }

    public static LoanflowException ruleViolation(String code, String detail) {
        return new LoanflowException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }

    public static LoanflowException conflict(String code, String detail) {
        return new LoanflowException(HttpStatus.CONFLICT, code, detail);
    }

    public static LoanflowException forbidden(String detail) {
        return new LoanflowException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", detail);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setProperty("code", code);
        return problem;
    }
}
