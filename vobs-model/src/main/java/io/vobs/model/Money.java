package io.vobs.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Số tiền kèm loại tiền tệ.
 *
 * <p>Số chữ số thập phân phải khớp với loại tiền: VND không có phần lẻ, USD có 2 chữ số.
 * Giá trị được chuẩn hóa về đúng số chữ số thập phân của loại tiền để so sánh {@code equals} ổn định.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public static final Currency VND = Currency.getInstance("VND");

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int digits = currency.getDefaultFractionDigits();
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > digits) {
            throw new IllegalArgumentException(
                    "%s chỉ cho phép %d chữ số thập phân: %s".formatted(currency, digits, amount.toPlainString()));
        }
        amount = amount.setScale(digits);
    }

    public static Money vnd(long amount) {
        return new Money(BigDecimal.valueOf(amount), VND);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Khác loại tiền: %s và %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
