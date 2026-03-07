/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.verification.ltl;

import java.util.*;
import java.util.function.Predicate;

/**
 * Represents an LTL (Linear Temporal Logic) formula for workflow verification.
 *
 * <p>LTL formulas are used to express temporal properties of workflow executions.
 * This implementation supports the standard LTL operators plus workflow-specific
 * extensions for Petri net verification.</p>
 *
 * <h2>Supported Operators</h2>
 * <table border="1">
 *   <tr><th>Operator</th><th>Syntax</th><th>Description</th></tr>
 *   <tr><td>Next</td><td>X(p)</td><td>p holds in the next state</td></tr>
 *   <tr><td>Finally</td><td>F(p) or ◇p</td><td>p holds eventually</td></tr>
 *   <tr><td>Globally</td><td>G(p) or □p</td><td>p holds in all states</td></tr>
 *   <tr><td>Until</td><td>p U q</td><td>p holds until q holds</td></tr>
 *   <tr><td>Release</td><td>p R q</td><td>q holds until p holds (dual of Until)</td></tr>
 *   <tr><td>Implies</td><td>p → q</td><td>Logical implication</td></tr>
 *   <tr><td>Weak Until</td><td>p W q</td><td>Until without requiring q</td></tr>
 * </table>
 *
 * <h2>Workflow-Specific Extensions</h2>
 * <ul>
 *   <li>{@code output_condition} - Case reached output condition</li>
 *   <li>{@code enabled(task)} - Task is enabled</li>
 *   <li>{@code executing(task)} - Task is executing</li>
 *   <li>{@code completed(task)} - Task has completed</li>
 *   <li>{@code tokens(place)} - Token count at place</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Eventually reaches output condition
 * LtlFormula eventuallyTerminates = LtlFormula.finally_(
 *     LtlFormula.atomic("output_condition")
 * );
 *
 * // No deadlock: always progress
 * LtlFormula noDeadlock = LtlFormula.globally(
 *     LtlFormula.implies(
 *         LtlFormula.atomic("has_enabled_items"),
 *         LtlFormula.finally_(LtlFormula.atomic("progress"))
 *     )
 * );
 *
 * // Soundness: always can complete
 * LtlFormula soundness = LtlFormula.globally(
 *     LtlFormula.finally_(LtlFormula.atomic("output_condition"))
 * );
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see LtlModelChecker
 * @see TemporalProperty
 */
public sealed interface LtlFormula permits
    LtlFormula.Atomic,
    LtlFormula.Not,
    LtlFormula.And,
    LtlFormula.Or,
    LtlFormula.Implies,
    LtlFormula.Next,
    LtlFormula.Finally,
    LtlFormula.Globally,
    LtlFormula.Until,
    LtlFormula.Release,
    LtlFormula.WeakUntil {

    /**
     * Accepts a visitor to process this formula.
     *
     * @param visitor The visitor to accept
     * @param <T> The return type
     * @return Result of visiting this formula
     */
    <T> T accept(Visitor<T> visitor);

    /**
     * Visitor interface for LTL formula processing.
     *
     * @param <T> The return type
     */
    interface Visitor<T> {
        T visitAtomic(Atomic formula);
        T visitNot(Not formula);
        T visitAnd(And formula);
        T visitOr(Or formula);
        T visitImplies(Implies formula);
        T visitNext(Next formula);
        T visitFinally(Finally formula);
        T visitGlobally(Globally formula);
        T visitUntil(Until formula);
        T visitRelease(Release formula);
        T visitWeakUntil(WeakUntil formula);
    }

    // =========================================================================
    // Factory Methods
    // =========================================================================

    /**
     * Creates an atomic proposition formula.
     *
     * @param proposition The proposition name
     * @return Atomic formula
     */
    static Atomic atomic(String proposition) {
        return new Atomic(proposition);
    }

    /**
     * Creates a negation formula.
     *
     * @param formula The formula to negate
     * @return Negated formula
     */
    static Not not(LtlFormula formula) {
        return new Not(formula);
    }

    /**
     * Creates a conjunction formula.
     *
     * @param left Left operand
     * @param right Right operand
     * @return Conjunction formula
     */
    static And and(LtlFormula left, LtlFormula right) {
        return new And(left, right);
    }

    /**
     * Creates a disjunction formula.
     *
     * @param left Left operand
     * @param right Right operand
     * @return Disjunction formula
     */
    static Or or(LtlFormula left, LtlFormula right) {
        return new Or(left, right);
    }

    /**
     * Creates an implication formula.
     *
     * @param antecedent The antecedent (if)
     * @param consequent The consequent (then)
     * @return Implication formula
     */
    static Implies implies(LtlFormula antecedent, LtlFormula consequent) {
        return new Implies(antecedent, consequent);
    }

    /**
     * Creates a Next (X) formula.
     *
     * @param formula The formula that must hold in next state
     * @return Next formula
     */
    static Next next(LtlFormula formula) {
        return new Next(formula);
    }

    /**
     * Creates a Finally (F) formula.
     *
     * @param formula The formula that must eventually hold
     * @return Finally formula
     */
    static Finally finally_(LtlFormula formula) {
        return new Finally(formula);
    }

    /**
     * Creates a Globally (G) formula.
     *
     * @param formula The formula that must always hold
     * @return Globally formula
     */
    static Globally globally(LtlFormula formula) {
        return new Globally(formula);
    }

    /**
     * Creates an Until (U) formula.
     *
     * @param left The formula that must hold until right holds
     * @param right The formula that must eventually hold
     * @return Until formula
     */
    static Until until(LtlFormula left, LtlFormula right) {
        return new Until(left, right);
    }

    /**
     * Creates a Release (R) formula.
     *
     * @param left The release condition
     * @param right The released formula
     * @return Release formula
     */
    static Release release(LtlFormula left, LtlFormula right) {
        return new Release(left, right);
    }

    /**
     * Creates a Weak Until (W) formula.
     *
     * @param left The formula that holds while right doesn't
     * @param right The formula that may or may not hold
     * @return Weak Until formula
     */
    static WeakUntil weakUntil(LtlFormula left, LtlFormula right) {
        return new WeakUntil(left, right);
    }

    // =========================================================================
    // Formula Types (Sealed Subtypes)
    // =========================================================================

    /**
     * Atomic proposition (e.g., "output_condition", "enabled(task_A)").
     *
     * @param proposition The proposition name
     */
    record Atomic(String proposition) implements LtlFormula {
        public Atomic {
            Objects.requireNonNull(proposition, "proposition must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitAtomic(this);
        }

        @Override
        public String toString() {
            return proposition;
        }
    }

    /**
     * Negation: ¬p.
     *
     * @param operand The formula to negate
     */
    record Not(LtlFormula operand) implements LtlFormula {
        public Not {
            Objects.requireNonNull(operand, "operand must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitNot(this);
        }

        @Override
        public String toString() {
            return "¬" + operand;
        }
    }

    /**
     * Conjunction: p ∧ q.
     *
     * @param left Left operand
     * @param right Right operand
     */
    record And(LtlFormula left, LtlFormula right) implements LtlFormula {
        public And {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitAnd(this);
        }

        @Override
        public String toString() {
            return "(%s ∧ %s)".formatted(left, right);
        }
    }

    /**
     * Disjunction: p ∨ q.
     *
     * @param left Left operand
     * @param right Right operand
     */
    record Or(LtlFormula left, LtlFormula right) implements LtlFormula {
        public Or {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitOr(this);
        }

        @Override
        public String toString() {
            return "(%s ∨ %s)".formatted(left, right);
        }
    }

    /**
     * Implication: p → q.
     *
     * @param antecedent The antecedent (if)
     * @param consequent The consequent (then)
     */
    record Implies(LtlFormula antecedent, LtlFormula consequent) implements LtlFormula {
        public Implies {
            Objects.requireNonNull(antecedent, "antecedent must not be null");
            Objects.requireNonNull(consequent, "consequent must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitImplies(this);
        }

        @Override
        public String toString() {
            return "(%s → %s)".formatted(antecedent, consequent);
        }
    }

    /**
     * Next: X(p) - p holds in the next state.
     *
     * @param operand The formula for next state
     */
    record Next(LtlFormula operand) implements LtlFormula {
        public Next {
            Objects.requireNonNull(operand, "operand must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitNext(this);
        }

        @Override
        public String toString() {
            return "X(%s)".formatted(operand);
        }
    }

    /**
     * Finally: F(p) or ◇p - p holds eventually.
     *
     * @param operand The formula that must eventually hold
     */
    record Finally(LtlFormula operand) implements LtlFormula {
        public Finally {
            Objects.requireNonNull(operand, "operand must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitFinally(this);
        }

        @Override
        public String toString() {
            return "◇(%s)".formatted(operand);
        }
    }

    /**
     * Globally: G(p) or □p - p holds in all states.
     *
     * @param operand The formula that must always hold
     */
    record Globally(LtlFormula operand) implements LtlFormula {
        public Globally {
            Objects.requireNonNull(operand, "operand must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitGlobally(this);
        }

        @Override
        public String toString() {
            return "□(%s)".formatted(operand);
        }
    }

    /**
     * Until: p U q - p holds until q holds.
     *
     * @param left The formula that holds until right holds
     * @param right The formula that must eventually hold
     */
    record Until(LtlFormula left, LtlFormula right) implements LtlFormula {
        public Until {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitUntil(this);
        }

        @Override
        public String toString() {
            return "(%s U %s)".formatted(left, right);
        }
    }

    /**
     * Release: p R q - q holds until p holds (dual of Until).
     *
     * @param left The release condition
     * @param right The released formula
     */
    record Release(LtlFormula left, LtlFormula right) implements LtlFormula {
        public Release {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitRelease(this);
        }

        @Override
        public String toString() {
            return "(%s R %s)".formatted(left, right);
        }
    }

    /**
     * Weak Until: p W q - p holds while q doesn't (q may never hold).
     *
     * @param left The formula that holds while right doesn't
     * @param right The formula that may or may not hold
     */
    record WeakUntil(LtlFormula left, LtlFormula right) implements LtlFormula {
        public WeakUntil {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitWeakUntil(this);
        }

        @Override
        public String toString() {
            return "(%s W %s)".formatted(left, right);
        }
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Converts this formula to Negation Normal Form (NNF).
     *
     * <p>NNF pushes negations inward to atomic propositions only.</p>
     *
     * @return Formula in NNF
     */
    default LtlFormula toNNF() {
        return accept(new NNFConverter());
    }

    /**
     * Converts this formula to a string representation suitable for parsing.
     *
     * @return Parseable string
     */
    default String toParseableString() {
        return toString();
    }

    /**
     * Visitor that converts formulas to Negation Normal Form.
     */
    class NNFConverter implements Visitor<LtlFormula> {
        @Override
        public LtlFormula visitAtomic(Atomic formula) {
            return formula;
        }

        @Override
        public LtlFormula visitNot(Not formula) {
            return formula.operand().accept(new NotPusher());
        }

        @Override
        public LtlFormula visitAnd(And formula) {
            return new And(formula.left().accept(this), formula.right().accept(this));
        }

        @Override
        public LtlFormula visitOr(Or formula) {
            return new Or(formula.left().accept(this), formula.right().accept(this));
        }

        @Override
        public LtlFormula visitImplies(Implies formula) {
            // p → q ≡ ¬p ∨ q
            return new Or(
                new Not(formula.antecedent()).accept(this),
                formula.consequent().accept(this)
            );
        }

        @Override
        public LtlFormula visitNext(Next formula) {
            return new Next(formula.operand().accept(this));
        }

        @Override
        public LtlFormula visitFinally(Finally formula) {
            return new Finally(formula.operand().accept(this));
        }

        @Override
        public LtlFormula visitGlobally(Globally formula) {
            return new Globally(formula.operand().accept(this));
        }

        @Override
        public LtlFormula visitUntil(Until formula) {
            return new Until(formula.left().accept(this), formula.right().accept(this));
        }

        @Override
        public LtlFormula visitRelease(Release formula) {
            return new Release(formula.left().accept(this), formula.right().accept(this));
        }

        @Override
        public LtlFormula visitWeakUntil(WeakUntil formula) {
            return new WeakUntil(formula.left().accept(this), formula.right().accept(this));
        }
    }

    /**
     * Pushes negation through formula structure.
     */
    class NotPusher implements Visitor<LtlFormula> {
        @Override
        public LtlFormula visitAtomic(Atomic formula) {
            return new Not(formula);
        }

        @Override
        public LtlFormula visitNot(Not formula) {
            // ¬¬p ≡ p
            return formula.operand().accept(new NNFConverter());
        }

        @Override
        public LtlFormula visitAnd(And formula) {
            // ¬(p ∧ q) ≡ ¬p ∨ ¬q
            return new Or(
                new Not(formula.left()).accept(new NNFConverter()),
                new Not(formula.right()).accept(new NNFConverter())
            );
        }

        @Override
        public LtlFormula visitOr(Or formula) {
            // ¬(p ∨ q) ≡ ¬p ∧ ¬q
            return new And(
                new Not(formula.left()).accept(new NNFConverter()),
                new Not(formula.right()).accept(new NNFConverter())
            );
        }

        @Override
        public LtlFormula visitImplies(Implies formula) {
            // ¬(p → q) ≡ p ∧ ¬q
            return new And(
                formula.antecedent().accept(new NNFConverter()),
                new Not(formula.consequent()).accept(new NNFConverter())
            );
        }

        @Override
        public LtlFormula visitNext(Next formula) {
            // ¬X(p) ≡ X(¬p)
            return new Next(new Not(formula.operand()).accept(new NNFConverter()));
        }

        @Override
        public LtlFormula visitFinally(Finally formula) {
            // ¬◇p ≡ □¬p
            return new Globally(new Not(formula.operand()).accept(new NNFConverter()));
        }

        @Override
        public LtlFormula visitGlobally(Globally formula) {
            // ¬□p ≡ ◇¬p
            return new Finally(new Not(formula.operand()).accept(new NNFConverter()));
        }

        @Override
        public LtlFormula visitUntil(Until formula) {
            // ¬(p U q) ≡ (¬q) R (¬p)
            return new Release(
                new Not(formula.right()).accept(new NNFConverter()),
                new Not(formula.left()).accept(new NNFConverter())
            );
        }

        @Override
        public LtlFormula visitRelease(Release formula) {
            // ¬(p R q) ≡ (¬q) U (¬p)
            return new Until(
                new Not(formula.right()).accept(new NNFConverter()),
                new Not(formula.left()).accept(new NNFConverter())
            );
        }

        @Override
        public LtlFormula visitWeakUntil(WeakUntil formula) {
            // ¬(p W q) ≡ (¬q) U (¬p) (weak until negation)
            return new Until(
                new Not(formula.right()).accept(new NNFConverter()),
                new Not(formula.left()).accept(new NNFConverter())
            );
        }
    }
}
