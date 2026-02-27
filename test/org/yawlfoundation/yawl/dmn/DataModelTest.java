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

package org.yawlfoundation.yawl.dmn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DataModel}, {@link DmnTable}, {@link DmnColumn},
 * and {@link DmnRelationship}.
 */
@DisplayName("DataModel and schema types")
class DataModelTest {

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private static DmnTable applicantTable() {
        return DmnTable.builder("Applicant")
                .column(DmnColumn.of("age", "integer").build())
                .column(DmnColumn.of("income", "double").build())
                .column(DmnColumn.of("status", "string").required(false).build())
                .build();
    }

    private static DmnTable productTable() {
        return DmnTable.builder("Product")
                .column(DmnColumn.of("productType", "string").build())
                .column(DmnColumn.of("riskBand", "string").build())
                .build();
    }

    private static DmnRelationship applicantToProduct() {
        return DmnRelationship.builder("applicant-to-product")
                .fromTable("Applicant")
                .toTable("Product")
                .sourceCardinality(EndpointCardinality.ONE_ONE)
                .targetCardinality(EndpointCardinality.ZERO_MANY)
                .build();
    }

    private static DataModel loanModel() {
        return DataModel.builder("LoanEligibility")
                .table(applicantTable())
                .table(productTable())
                .relationship(applicantToProduct())
                .build();
    }

    // -----------------------------------------------------------------------
    // DmnColumn
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("DmnColumn")
    class ColumnTests {

        @Test
        void of_createsColumn() {
            DmnColumn col = DmnColumn.of("age", "integer").build();
            assertThat(col.getName(), is("age"));
            assertThat(col.getTypeRef(), is("integer"));
            assertTrue(col.isRequired());
        }

        @Test
        void required_false_overrides() {
            DmnColumn col = DmnColumn.of("notes", "string").required(false).build();
            assertFalse(col.isRequired());
        }

        @Test
        void description_isStoredAndReturned() {
            DmnColumn col = DmnColumn.of("score", "double")
                    .description("Risk score 0-100").build();
            assertThat(col.getDescription(), is("Risk score 0-100"));
        }

        @Test
        void blankName_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> DmnColumn.of("", "string").build());
        }
    }

    // -----------------------------------------------------------------------
    // DmnTable
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("DmnTable")
    class TableTests {

        @Test
        void builder_storesNameAndColumns() {
            DmnTable t = applicantTable();
            assertThat(t.getName(), is("Applicant"));
            assertThat(t.columnCount(), is(3));
        }

        @Test
        void getColumn_findsExisting() {
            DmnTable t = applicantTable();
            assertTrue(t.getColumn("age").isPresent());
            assertThat(t.getColumn("age").get().getTypeRef(), is("integer"));
        }

        @Test
        void getColumn_returnsEmptyForMissing() {
            DmnTable t = applicantTable();
            assertTrue(t.getColumn("nonExistent").isEmpty());
        }

        @Test
        void hasColumn_returnsCorrectly() {
            DmnTable t = applicantTable();
            assertTrue(t.hasColumn("income"));
            assertFalse(t.hasColumn("zipCode"));
        }

        @Test
        void validateRow_passesForValidRow() {
            DmnTable t = applicantTable();
            Map<String, Object> row = Map.of("age", 30, "income", 55000.0);
            assertThat(t.validateRow(row), is(empty()));
        }

        @Test
        void validateRow_reportsRequiredMissing() {
            DmnTable t = applicantTable();
            Map<String, Object> row = Map.of("age", 30); // income missing
            List<String> errors = t.validateRow(row);
            assertThat(errors, hasSize(1));
            assertThat(errors.get(0), containsString("income"));
        }

        @Test
        void validateRow_optionalColumnNotRequired() {
            DmnTable t = applicantTable();
            // status is optional — should not appear in errors
            Map<String, Object> row = Map.of("age", 30, "income", 50000.0);
            assertThat(t.validateRow(row), is(empty()));
        }

        @Test
        void blankName_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> DmnTable.builder("").build());
        }
    }

    // -----------------------------------------------------------------------
    // DmnRelationship
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("DmnRelationship")
    class RelationshipTests {

        @Test
        void builder_storesAllFields() {
            DmnRelationship rel = applicantToProduct();
            assertThat(rel.getName(), is("applicant-to-product"));
            assertThat(rel.getFromTable(), is("Applicant"));
            assertThat(rel.getToTable(), is("Product"));
            assertThat(rel.getSourceCardinality(), is(EndpointCardinality.ONE_ONE));
            assertThat(rel.getTargetCardinality(), is(EndpointCardinality.ZERO_MANY));
        }

        @Test
        void sourceMinMax_delegatesToCardinality() {
            DmnRelationship rel = applicantToProduct();
            assertThat(rel.sourceMin(), is(1));
            assertThat(rel.sourceMax(), is(1));
            assertThat(rel.targetMin(), is(0));
            assertThat(rel.targetMax(), is(EndpointCardinality.UNBOUNDED));
        }

        @Test
        void columnAnnotations_optionalByDefault() {
            DmnRelationship rel = applicantToProduct();
            assertNull(rel.getFromColumn());
            assertNull(rel.getToColumn());
        }

        @Test
        void withColumns_storesColumnNames() {
            DmnRelationship rel = DmnRelationship.builder("fk-rel")
                    .fromTable("A")
                    .toTable("B")
                    .fromColumn("a_id")
                    .toColumn("id")
                    .build();
            assertThat(rel.getFromColumn(), is("a_id"));
            assertThat(rel.getToColumn(), is("id"));
        }

        @Test
        void blankName_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> DmnRelationship.builder("").build());
        }

        @Test
        void missingFromTable_throwsIllegalState() {
            assertThrows(IllegalStateException.class, () ->
                    DmnRelationship.builder("r").toTable("B").build());
        }

        @Test
        void missingToTable_throwsIllegalState() {
            assertThrows(IllegalStateException.class, () ->
                    DmnRelationship.builder("r").fromTable("A").build());
        }

        @Test
        void toString_includesCardinalityNotation() {
            String s = applicantToProduct().toString();
            assertThat(s, containsString("1..1"));
            assertThat(s, containsString("0..*"));
        }
    }

    // -----------------------------------------------------------------------
    // DataModel
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("DataModel")
    class DataModelTests {

        @Test
        void builder_storesNameTablesRelationships() {
            DataModel model = loanModel();
            assertThat(model.getName(), is("LoanEligibility"));
            assertThat(model.tableCount(), is(2));
            assertThat(model.relationshipCount(), is(1));
        }

        @Test
        void getTable_findsExisting() {
            DataModel model = loanModel();
            assertTrue(model.getTable("Applicant").isPresent());
        }

        @Test
        void hasTable_correctResults() {
            DataModel model = loanModel();
            assertTrue(model.hasTable("Product"));
            assertFalse(model.hasTable("Unknown"));
        }

        @Test
        void getRelationshipsFrom_returnsOutbound() {
            DataModel model = loanModel();
            List<DmnRelationship> rels = model.getRelationshipsFrom("Applicant");
            assertThat(rels, hasSize(1));
            assertThat(rels.get(0).getName(), is("applicant-to-product"));
        }

        @Test
        void getRelationshipsTo_returnsInbound() {
            DataModel model = loanModel();
            List<DmnRelationship> rels = model.getRelationshipsTo("Product");
            assertThat(rels, hasSize(1));
        }

        @Test
        void getRelationshipsFrom_emptyForUnrelatedTable() {
            DataModel model = loanModel();
            assertThat(model.getRelationshipsFrom("Product"), is(empty()));
        }

        @Test
        void getRelationship_byName() {
            DataModel model = loanModel();
            assertTrue(model.getRelationship("applicant-to-product").isPresent());
            assertTrue(model.getRelationship("nonExistent").isEmpty());
        }

        @Test
        void validateIntegrity_passesForConsistentModel() {
            DataModel model = loanModel();
            assertThat(model.validateIntegrity(), is(empty()));
        }

        @Test
        void validateIntegrity_failsForDanglingRelationship() {
            DataModel model = DataModel.builder("Broken")
                    .table(applicantTable())
                    // Product table is missing but relationship references it
                    .relationship(applicantToProduct())
                    .build();
            List<String> errors = model.validateIntegrity();
            assertThat(errors, not(empty()));
            assertThat(errors.get(0), containsString("Product"));
        }

        @Test
        void duplicateTableName_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () ->
                    DataModel.builder("M")
                            .table(applicantTable())
                            .table(applicantTable()) // duplicate
                            .build());
        }

        @Test
        void duplicateRelationshipName_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () ->
                    DataModel.builder("M")
                            .table(applicantTable())
                            .table(productTable())
                            .relationship(applicantToProduct())
                            .relationship(applicantToProduct()) // duplicate
                            .build());
        }

        @Test
        void toString_containsNameAndCounts() {
            String s = loanModel().toString();
            assertThat(s, containsString("LoanEligibility"));
            assertThat(s, containsString("tables=2"));
            assertThat(s, containsString("relationships=1"));
        }
    }
}
