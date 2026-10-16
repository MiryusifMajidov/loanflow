package az.yusif.loanflow.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Amounts {

    public static final int SCALE = 2;

    private Amounts() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal money(String value) {
        return money(new BigDecimal(value));
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
