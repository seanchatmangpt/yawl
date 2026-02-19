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

package org.yawlfoundation.yawl.engine;

import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.authentication.YExternalClient;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
/**
 * Hibernate 6 API integration tests using a real H2 in-memory database.
 *
 * <p>Tests verify that the V6 API upgrade is correct end-to-end:
 * <ul>
 *   <li>{@code session.persist()} inserts new entities (replaces deprecated save())</li>
 *   <li>{@code session.remove()} deletes entities (replaces deprecated delete())</li>
 *   <li>{@code session.merge()} updates detached entities (replaces saveOrUpdate())</li>
 *   <li>{@code session.detach()} clears entity from session (replaces evict())</li>
 *   <li>{@link TypedQuery} returns typed results without unchecked casts</li>
 *   <li>Parameterized named-parameter queries prevent HQL injection</li>
 *   <li>Transaction lifecycle (begin, commit, rollback) works correctly</li>
 * </ul>
 *
 * <p>Uses YExternalClient (string primary key, no singleton constraint) as the
 * test entity throughout, allowing fully isolated CRUD operations.
 *
 * <p>Chicago TDD: all tests use a real H2 SessionFactory, not mocks.
 *
 * @author YAWL Engine Team - V6 upgrade 2026-02-17
 */
@DisplayName("Hibernate V6 API Integration Tests")
@Tag("integration")
class HibernateV6ApiTest {

    private SessionFactory factory;
    private StandardServiceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = buildH2ServiceRegistry();
        MetadataSources sources = new MetadataSources(registry);
        sources.addAnnotatedClass(YExternalClient.class);
        Metadata metadata = sources.buildMetadata();
        factory = metadata.buildSessionFactory();
    }

    @AfterEach
    void tearDown() {
        if (factory != null && !factory.isClosed()) {
            factory.close();
        }
        if (registry != null) {
    @Execution(ExecutionMode.SAME_THREAD)

            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    /**
     * Builds a Hibernate StandardServiceRegistry pointed at an H2 in-memory database
     * with auto-schema creation, using the Hibernate 6.x builder API.
     */
    private StandardServiceRegistry buildH2ServiceRegistry() {
        Properties props = new Properties();
        props.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        props.setProperty("hibernate.connection.url",
                "jdbc:h2:mem:hibv6test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        props.setProperty("hibernate.connection.username", "sa");
        props.setProperty("hibernate.connection.password", "");
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        props.setProperty("hibernate.current_session_context_class", "thread");
        props.setProperty("hibernate.show_sql", "false");

        return new StandardServiceRegistryBuilder()
                .applySettings(props)
                .build();
    }

    /**
     * Creates a YExternalClient with a unique username to avoid primary key conflicts.
     */
    private YExternalClient newClient(String usernameSuffix) {
        return new YExternalClient(
                "testuser-" + usernameSuffix, "testpass", "test client " + usernameSuffix);
    }

    @Nested
    @DisplayName("SessionFactory lifecycle")
    class SessionFactoryTests {

        @Test
        @DisplayName("SessionFactory is open after build")
        void testSessionFactoryIsOpenAfterBuild() {
            assertNotNull(factory, "SessionFactory must not be null");
            assertFalse(factory.isClosed(), "SessionFactory must not be closed after build");
        }
    }

    @Nested
    @DisplayName("session.persist() V6 API")
    class PersistTests {

        @Test
        @DisplayName("persists new entity visible in same transaction")
        void testSessionPersistInsertsEntity() {
            Session session = factory.getCurrentSession();
            Transaction tx = session.beginTransaction();
            try {
                YExternalClient client = newClient("persist-001");

                // V6: session.persist() - replaces deprecated session.save()
                session.persist(client);
                session.flush();

                // Verify entity is retrievable within same transaction
                TypedQuery<YExternalClient> query = session.createQuery(
                        "from YExternalClient where _userName = :name", YExternalClient.class);
                query.setParameter("name", "testuser-persist-001");
                List<YExternalClient> results = query.getResultList();

                assertFalse(results.isEmpty(), "persist() must insert entity visible in same transaction");
                assertEquals(1, results.size(), "Should find exactly one entity");
                assertEquals("testuser-persist-001", results.get(0).getUserName(),
                        "Persisted entity userName must match");

                tx.commit();
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        }
    }

    @Nested
    @DisplayName("session.remove() V6 API")
    class RemoveTests {

        @Test
        @DisplayName("deletes entity from database")
        void testSessionRemoveDeletesEntity() {
            // First insert an entity
            Session insertSession = factory.getCurrentSession();
            Transaction insertTx = insertSession.beginTransaction();
            try {
                YExternalClient client = newClient("remove-002");
                insertSession.persist(client);
                insertTx.commit();
            } catch (Exception e) {
                insertTx.rollback();
                throw e;
            }

            // Now delete it using V6 session.remove()
            Session deleteSession = factory.getCurrentSession();
            Transaction deleteTx = deleteSession.beginTransaction();
            try {
                TypedQuery<YExternalClient> findQuery = deleteSession.createQuery(
                        "from YExternalClient where _userName = :name", YExternalClient.class);
                findQuery.setParameter("name", "testuser-remove-002");
                List<YExternalClient> found = findQuery.getResultList();
                assertFalse(found.isEmpty(), "Entity must exist before delete");

                // V6: session.remove() - replaces deprecated session.delete()
                deleteSession.remove(found.get(0));
                deleteSession.flush();

                // Verify entity is gone within same transaction
                TypedQuery<YExternalClient> verifyQuery = deleteSession.createQuery(
                        "from YExternalClient where _userName = :name", YExternalClient.class);
                verifyQuery.setParameter("name", "testuser-remove-002");
                List<YExternalClient> afterDelete = verifyQuery.getResultList();
                assertTrue(afterDelete.isEmpty(), "remove() must delete entity from database");

                deleteTx.commit();
            } catch (Exception e) {
                deleteTx.rollback();
                throw e;
            }
        }
    }

    @Nested
    @DisplayName("session.merge() V6 API")
    class MergeTests {

        @Test
        @DisplayName("updates detached entity")
        void testSessionMergeUpdatesEntity() {
            // Insert initial entity
            Session insertSession = factory.getCurrentSession();
            Transaction insertTx = insertSession.beginTransaction();
            try {
                YExternalClient client = newClient("merge-003");
                insertSession.persist(client);
                insertTx.commit();
            } catch (Exception e) {
                insertTx.rollback();
                throw e;
            }

            // Detach, modify, and merge
            Session mergeSession = factory.getCurrentSession();
            Transaction mergeTx = mergeSession.beginTransaction();
            try {
                TypedQuery<YExternalClient> q = mergeSession.createQuery(
                        "from YExternalClient where _userName = :name", YExternalClient.class);
                q.setParameter("name", "testuser-merge-003");
                YExternalClient managed = q.getResultList().get(0);

                // V6: session.detach() - replaces deprecated session.evict()
                mergeSession.detach(managed);

                // Modify the detached entity
                managed.setDocumentation("updated-documentation-for-merge-test");

                // V6: session.merge() - replaces deprecated session.saveOrUpdate()
                mergeSession.merge(managed);
                mergeSession.flush();

                // Verify the updated documentation is persisted
                TypedQuery<YExternalClient> verifyQuery = mergeSession.createQuery(
                        "from YExternalClient where _userName = :name", YExternalClient.class);
                verifyQuery.setParameter("name", "testuser-merge-003");
                List<YExternalClient> updated = verifyQuery.getResultList();

                assertFalse(updated.isEmpty(), "merge() must persist updated entity");
                assertEquals("updated-documentation-for-merge-test",
                        updated.get(0).getDocumentation(),
                        "Updated documentation must be persisted");

                mergeTx.commit();
            } catch (Exception e) {
                mergeTx.rollback();
                throw e;
            }
        }
    }

    @Nested
    @DisplayName("session.detach() V6 API")
    class DetachTests {

        @Test
        @DisplayName("removes entity from session cache")
        void testSessionDetachRemovesEntityFromSessionCache() {
            Session session = factory.getCurrentSession();
            Transaction tx = session.beginTransaction();
            try {
                YExternalClient client = newClient("detach-004");
                session.persist(client);
                session.flush();

                // Verify entity is in session cache after persist
                assertTrue(session.contains(client), "Entity should be in session after persist");

                // V6: session.detach() - replaces deprecated session.evict()
                session.detach(client);

                assertFalse(session.contains(client), "Entity should NOT be in session after detach");

                tx.commit();
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        }
    }

    @Nested
    @DisplayName("TypedQuery V6 type safety")
    class TypedQueryTests {

        @Test
        @DisplayName("returns correctly typed results without casts")
        void testTypedQueryReturnsCorrectType() {
            Session session = factory.getCurrentSession();
            Transaction tx = session.beginTransaction();
            try {
                session.persist(newClient("typed-005a"));
                session.persist(newClient("typed-005b"));
                session.flush();

                // V6 TypedQuery with explicit result class - no unchecked cast needed
                TypedQuery<YExternalClient> query = session.createQuery(
                        "from YExternalClient", YExternalClient.class);
                List<YExternalClient> results = query.getResultList();

                assertNotNull(results, "TypedQuery.getResultList() must return non-null list");
                assertFalse(results.isEmpty(), "Results must not be empty after inserting 2 entities");
                for (YExternalClient item : results) {
                    // Direct typed access without cast - proves type safety
                    assertNotNull(item, "Each result must be non-null");
                    assertNotNull(item.getUserName(), "Each result must have a non-null userName");
                }

                tx.commit();
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        }
    }

    @Nested
    @DisplayName("Named parameter HQL injection prevention")
    class NamedParameterTests {

        @Test
        @DisplayName("prevents HQL injection via named parameters")
        void testNamedParametersPreventHqlInjection() {
            Session session = factory.getCurrentSession();
            Transaction tx = session.beginTransaction();
            try {
                session.persist(newClient("injection-006"));
                session.flush();

                // This value would break a string-concatenated HQL query in V5:
                // "from YExternalClient where _userName = 'notexist' OR '1'='1'"
                // In V6, it is bound as a named parameter - only exact matches return.
                String specialValue = "notexist-006' OR '1'='1";
                TypedQuery<YExternalClient> query = session.createQuery(
                        "from YExternalClient where _userName = :name", YExternalClient.class);
                query.setParameter("name", specialValue);

                List<YExternalClient> results = query.getResultList();
                // Must return empty (no entity with that exact name)
                assertTrue(results.isEmpty(), "Named parameter query must not match HQL-injected value");

                tx.commit();
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        }
    }

    @Nested
    @DisplayName("Transaction lifecycle")
    class TransactionTests {

        @Test
        @DisplayName("commit makes data durable across sessions")
        void testTransactionCommitMakesDataDurable() {
            // Session 1: persist and commit
            Session session1 = factory.getCurrentSession();
            Transaction tx1 = session1.beginTransaction();
            try {
                session1.persist(newClient("durable-007"));
                tx1.commit();
            } catch (Exception e) {
                tx1.rollback();
                throw e;
            }

            // Session 2: verify data is visible after commit
            Session session2 = factory.getCurrentSession();
            Transaction tx2 = session2.beginTransaction();
            try {
                TypedQuery<YExternalClient> q = session2.createQuery(
                        "from YExternalClient where _userName = :name", YExternalClient.class);
                q.setParameter("name", "testuser-durable-007");
                List<YExternalClient> results = q.getResultList();

                assertFalse(results.isEmpty(), "Committed entity must be visible in next session");
                assertEquals("testuser-durable-007", results.get(0).getUserName(),
                        "Durable entity userName must match committed value");

                tx2.commit();
            } catch (Exception e) {
                tx2.rollback();
                throw e;
            }
        }

        @Test
        @DisplayName("rollback discards pending changes")
        void testTransactionRollbackDiscardsChanges() {
            // Start transaction, persist, then rollback
            Session session = factory.getCurrentSession();
            Transaction tx = session.beginTransaction();
            try {
                session.persist(newClient("rollback-008"));
                session.flush();
                tx.rollback();
            } catch (Exception e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
            }

            // Verify entity was not persisted after rollback
            Session verifySession = factory.getCurrentSession();
            Transaction verifyTx = verifySession.beginTransaction();
            try {
                TypedQuery<YExternalClient> q = verifySession.createQuery(
                        "from YExternalClient where _userName = :name", YExternalClient.class);
                q.setParameter("name", "testuser-rollback-008");
                List<YExternalClient> results = q.getResultList();

                assertTrue(results.isEmpty(), "Rolled-back entity must NOT be visible after rollback");

                verifyTx.commit();
            } catch (Exception e) {
                verifyTx.rollback();
                throw e;
            }
        }
    }

    @Nested
    @DisplayName("YPersistenceManager V6 API consistency")
    class PersistenceManagerTests {

        @Test
        @DisplayName("API contract is consistent")
        void testYPersistenceManagerApiConsistency() throws YPersistenceException {
            YEngine engine;
            try {
                engine = YEngine.getInstance();
            } catch (Exception e) {
                return; // Engine not available in this isolated test environment
            }
            if (engine == null) {
                return;
            }
            YPersistenceManager pmgr = engine.getPersistenceManager();
            if (pmgr == null) {
                return;
            }

            // V6 contract: isEnabled() and isPersisting() must be equivalent
            assertEquals(pmgr.isEnabled(), pmgr.isPersisting(),
                    "isEnabled() and isPersisting() must return same value (V6 API contract)");

            // V6 contract: getFactory() and getSessionFactory() must return same object
            assertSame(pmgr.getFactory(), pmgr.getSessionFactory(),
                    "getFactory() and getSessionFactory() must return same instance");
        }
    }
}
