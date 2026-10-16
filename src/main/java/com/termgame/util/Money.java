package com.termgame.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额与展示工具。引擎内部一律使用 double 元，边界处统一四舍五入到 2 位小数，
 * 避免累加出现浮点漂移。
 */
public final class Money {

    private Money() {
    }

    public static double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static String fmt(double v) {
        return String.format("%,.2f", round(v));
    }

    public static String fmtInt(double v) {
        return String.format("%,.0f", round(v));
    }
}
