/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable currency type for the GregVerse marketplace.
 *
 * <p>Represents credits/tokens used for service exchange between OTs and other agents.
 * Provides precise financial calculations with proper rounding and validation.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Immutable with BigDecimal precision</li>
 *   <li>Automatic validation for negative values</li>
 *   <li>Formatted currency display</li>
 *   <li>Arithmetic operations with proper rounding</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * MarketplaceCurrency price = new MarketplaceCurrency(250.00);
 * MarketplaceCurrency discount = price.multiply(0.1); // 25.00
 * MarketplaceCurrency finalPrice = price.subtract(discount);
 * String formatted = finalPrice.format(); // "$225.00"
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class MarketplaceCurrency {

    public static final MarketplaceCurrency ZERO = new MarketplaceCurrency(0.0);
    public static final MarketplaceCurrency MIN_UNIT = new MarketplaceCurrency(0.01);
    public static final MarketplaceCurrency MAX_BALANCE = new MarketplaceCurrency(1_000_000.0);

    private final BigDecimal amount;

    /**
     * Creates a new currency amount with validation.
     *
     * @param amount the currency amount
     * @throws IllegalArgumentException if amount is negative
     */
    public MarketplaceCurrency(double amount) {
        this(BigDecimal.valueOf(amount));
    }

    /**
     * Creates a new currency amount with validation.
     *
     * @param amount the currency amount
     * @throws IllegalArgumentException if amount is negative
     */
    public MarketplaceCurrency(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Currency amount cannot be negative: " + amount);
        }
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns the currency amount.
     *
     * @return the amount as BigDecimal
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returns the currency amount as double.
     *
     * @return the amount as double
     */
    public double getAmountAsDouble() {
        return amount.doubleValue();
    }

    /**
     * Adds another currency amount to this one.
     *
     * @param other the currency to add
     * @return new MarketplaceCurrency with the sum
     */
    public MarketplaceCurrency add(MarketplaceCurrency other) {
        Objects.requireNonNull(other, "Other currency must not be null");
        return new MarketplaceCurrency(this.amount.add(other.amount));
    }

    /**
     * Subtracts another currency amount from this one.
     *
     * @param other the currency to subtract
     * @return new MarketplaceCurrency with the difference
     * @throws IllegalArgumentException if result would be negative
     */
    public MarketplaceCurrency subtract(MarketplaceCurrency other) {
        Objects.requireNonNull(other, "Other currency must not be null");
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Insufficient funds: " + this + " - " + other);
        }
        return new MarketplaceCurrency(result);
    }

    /**
     * Multiplies this currency by a factor.
     *
     * @param factor the multiplication factor
     * @return new MarketplaceCurrency with the product
     */
    public MarketplaceCurrency multiply(double factor) {
        return new MarketplaceCurrency(this.amount.multiply(BigDecimal.valueOf(factor)));
    }

    /**
     * Multiplies this currency by another currency (useful for percentage calculations).
     *
     * @param other the currency multiplier
     * @return new MarketplaceCurrency with the product
     */
    public MarketplaceCurrency multiply(MarketplaceCurrency other) {
        Objects.requireNonNull(other, "Other currency must not be null");
        return new MarketplaceCurrency(this.amount.multiply(other.amount));
    }

    /**
     * Divides this currency by a divisor.
     *
     * @param divisor the divisor
     * @return new MarketplaceCurrency with the quotient
     * @throws IllegalArgumentException if divisor is zero or negative
     */
    public MarketplaceCurrency divide(double divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("Divisor must be positive: " + divisor);
        }
        return new MarketplaceCurrency(this.amount.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP));
    }

    /**
     * Divides this currency by another currency.
     *
     * @param other the divisor currency
     * @return new MarketplaceCurrency with the quotient
     * @throws IllegalArgumentException if divisor is zero or negative
     */
    public MarketplaceCurrency divide(MarketplaceCurrency other) {
        Objects.requireNonNull(other, "Other currency must not be null");
        if (other.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Divisor must be positive: " + other);
        }
        return new MarketplaceCurrency(this.amount.divide(other.amount, 2, RoundingMode.HALF_UP));
    }

    /**
     * Returns a percentage of this currency amount.
     *
     * @param percentage the percentage (e.g., 0.15 for 15%)
     * @return new MarketplaceCurrency with the percentage amount
     */
    public MarketplaceCurrency percentage(double percentage) {
        return multiply(percentage);
    }

    /**
     * Rounds this currency to the nearest whole unit.
     *
     * @return new MarketplaceCurrency rounded to whole units
     */
    public MarketplaceCurrency round() {
        return new MarketplaceCurrency(this.amount.setScale(0, RoundingMode.HALF_UP));
    }

    /**
     * Rounds this currency to a specific number of decimal places.
     *
     * @param decimalPlaces the number of decimal places
     * @return new MarketplaceCurrency rounded to specified precision
     */
    public MarketplaceCurrency round(int decimalPlaces) {
        return new MarketplaceCurrency(this.amount.setScale(decimalPlaces, RoundingMode.HALF_UP));
    }

    /**
     * Compares this currency to another.
     *
     * @param other the currency to compare to
     * @return -1 if less than, 0 if equal, 1 if greater than
     */
    public int compareTo(MarketplaceCurrency other) {
        Objects.requireNonNull(other, "Other currency must not be null");
        return this.amount.compareTo(other.amount);
    }

    /**
     * Checks if this currency is greater than another.
     *
     * @param other the currency to compare to
     * @return true if this currency is greater
     */
    public boolean isGreaterThan(MarketplaceCurrency other) {
        return compareTo(other) > 0;
    }

    /**
     * Checks if this currency is less than another.
     *
     * @param other the currency to compare to
     * @return true if this currency is less
     */
    public boolean isLessThan(MarketplaceCurrency other) {
        return compareTo(other) < 0;
    }

    /**
     * Checks if this currency is equal to or greater than another.
     *
     * @param other the currency to compare to
     * @return true if this currency is equal or greater
     */
    public boolean isGreaterThanOrEqualTo(MarketplaceCurrency other) {
        return compareTo(other) >= 0;
    }

    /**
     * Checks if this currency is equal to or less than another.
     *
     * @param other the currency to compare to
     * @return true if this currency is equal or less
     */
    public boolean isLessThanOrEqualTo(MarketplaceCurrency other) {
        return compareTo(other) <= 0;
    }

    /**
     * Checks if this currency is zero.
     *
     * @return true if amount is zero
     */
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Checks if this currency is positive.
     *
     * @return true if amount is positive
     */
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Formats the currency for display.
     *
     * @return formatted currency string (e.g., "$250.00")
     */
    public String format() {
        return String.format("$%.2f", amount);
    }

    /**
     * Formats the currency with a specific currency symbol.
     *
     * @param symbol the currency symbol
     * @return formatted currency string
     */
    public String format(String symbol) {
        return String.format("%s%.2f", symbol, amount);
    }

    /**
     * Converts to a string representation.
     *
     * @return the currency amount as string
     */
    @Override
    public String toString() {
        return format();
    }

    /**
     * Compares this currency to another object.
     *
     * @param o the object to compare to
     * @return true if equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarketplaceCurrency that = (MarketplaceCurrency) o;
        return amount.equals(that.amount);
    }

    /**
     * Returns the hash code for this currency.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return amount.hashCode();
    }

    /**
     * Creates a currency from a string.
     *
     * @param amountStr the string representation of the amount
     * @return new MarketplaceCurrency
     * @throws NumberFormatException if string is not a valid number
     */
    public static MarketplaceCurrency fromString(String amountStr) {
        Objects.requireNonNull(amountStr, "Amount string must not be null");
        return new MarketplaceCurrency(new BigDecimal(amountStr.trim()));
    }

    /**
     * Creates a currency from cents.
     *
     * @param cents the amount in cents
     * @return new MarketplaceCurrency
     */
    public static MarketplaceCurrency fromCents(long cents) {
        return new MarketplaceCurrency(BigDecimal.valueOf(cents, 2));
    }

    /**
     * Converts this currency to cents.
     *
     * @return the amount in cents as long
     */
    public long toCents() {
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }
}