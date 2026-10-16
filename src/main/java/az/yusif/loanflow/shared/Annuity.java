package az.yusif.loanflow.shared;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class Annuity {

    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Annuity() {
    }

    public static BigDecimal monthlyRate(BigDecimal annualRatePercent) {
        return annualRatePercent.divide(HUNDRED, PRECISION).divide(MONTHS_IN_YEAR, PRECISION);
    }

    public static BigDecimal monthlyPayment(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        BigDecimal rate = monthlyRate(annualRatePercent);
        if (rate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), Amounts.SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal growth = BigDecimal.ONE.add(rate).pow(months, PRECISION);
        BigDecimal payment = principal.multiply(rate).multiply(growth)
                .divide(growth.subtract(BigDecimal.ONE), PRECISION);
        return payment.setScale(Amounts.SCALE, RoundingMode.HALF_UP);
    }
}
