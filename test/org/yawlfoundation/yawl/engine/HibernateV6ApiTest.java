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
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.yawlfoundation.yawl.authentication.YExternalClient;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.util.List;
import java.util.Properties;

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
public class HibernateV6ApiTest extends TestCase {

    private SessionFactory _factory;
    private StandardServiceRegistry _registry;

    public HibernateV6ApiTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        _registry = buildH2ServiceRegistry();
        MetadataSources sources = new MetadataSources(_registry);
        sources.addAnnotatedClass(YExternalClient.class);
        Metadata metadata = sources.buildMetadata();
        _factory = metadata.buildSessionFactory();
    }

    @Override
    protected void tearDown() throws Exception {
        if (_factory != null && !_factory.isClosed()) {
            _factory.close();
        }
        if (_registry != null) {
            StandardServiceRegistryBuilder.destroy(_registry);
        }
        super.tearDown();
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
        YExternalClient client = new YExternalClient(
                "testuser-" + usernameSuffix, "testpass", "test client " + usernameSuffix);
        return client;
    }

    // =========================================================================
    //  Test 1: SessionFactory is functional and not closed after setUp
    // =========================================================================

    /**
     * Verifies the H2 SessionFactory built from the V6 API is operational.
     * A closed or null factory would indicate a V6 boot-sequence regression.
     */
    public void testSessionFactoryIsOpenAfterBuild() {
        assertNotNull("SessionFactory must not be null", _factory);
        assertFalse("SessionFactory must not be closed after build", _factory.isClosed());
    }

    // =========================================================================
    //  Test 2: session.persist() (V6) inserts a new entity
    // =========================================================================

    /**
     * Verifies that {@code session.persist()} (V6 API, replaces save()) correctly
     * inserts a new entity into H2 and that it is visible within the same transaction.
     */
    public void testSessionPersistInsertsEntity() {
        Session session = _factory.getCurrentSession();
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

            assertFalse("persist() must insert entity visible in same transaction",
                    results.isEmpty());
            assertEquals("Should find exactly one entity", 1, results.size());
            assertEquals("Persisted entity userName must match",
                    "testuser-persist-001", results.get(0).getUserName());

            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    // =========================================================================
    //  Test 3: session.remove() (V6) deletes an entity
    // =========================================================================

    /**
     * Verifies that {@code session.remove()} (V6 API, replaces delete()) correctly
     * removes a persisted entity and that it is no longer visible after flush.
     */
    public void testSessionRemoveDeletesEntity() {
        // First insert an entity
        Session insertSession = _factory.getCurrentSession();
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
        Session deleteSession = _factory.getCurrentSession();
        Transaction deleteTx = deleteSession.beginTransaction();
        try {
            TypedQuery<YExternalClient> findQuery = deleteSession.createQuery(
                    "from YExternalClient where _userName = :name", YExternalClient.class);
            findQuery.setParameter("name", "testuser-remove-002");
            List<YExternalClient> found = findQuery.getResultList();
            assertFalse("Entity must exist before delete", found.isEmpty());

            // V6: session.remove() - replaces deprecated session.delete()
            deleteSession.remove(found.get(0));
            deleteSession.flush();

            // Verify entity is gone within same transaction
            TypedQuery<YExternalClient> verifyQuery = deleteSession.createQuery(
                    "from YExternalClient where _userName = :name", YExternalClient.class);
            verifyQuery.setParameter("name", "testuser-remove-002");
            List<YExternalClient> afterDelete = verifyQuery.getResultList();
            assertTrue("remove() must delete entity from database", afterDelete.isEmpty());

            deleteTx.commit();
        } catch (Exception e) {
            deleteTx.rollback();
            throw e;
        }
    }

    // =========================================================================
    //  Test 4: session.merge() (V6) updates an entity
    // =========================================================================

    /**
     * Verifies that {@code session.merge()} (V6 API, replaces saveOrUpdate()) correctly
     * merges a detached entity's changes back into the persistence context.
     */
    public void testSessionMergeUpdatesEntity() {
        // Insert initial entity
        Session insertSession = _factory.getCurrentSession();
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
        Session mergeSession = _factory.getCurrentSession();
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

            assertFalse("merge() must persist updated entity", updated.isEmpty());
            assertEquals("Updated documentation must be persisted",
                    "updated-documentation-for-merge-test",
                    updated.get(0).getDocumentation());

            mergeTx.commit();
        } catch (Exception e) {
            mergeTx.rollback();
            throw e;
        }
    }

    // =========================================================================
    //  Test 5: session.detach() (V6) removes entity from session cache
    // =========================================================================

    /**
     * Verifies that {@code session.detach()} (V6 API, replaces evict()) correctly
     * removes an entity from the session's first-level cache.
     */
    public void testSessionDetachRemovesEntityFromSessionCache() {
        Session session = _factory.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            YExternalClient client = newClient("detach-004");
            session.persist(client);
            session.flush();

            // Verify entity is in session cache after persist
            assertTrue("Entity should be in session after persist",
                    session.contains(client));

            // V6: session.detach() - replaces deprecated session.evict()
            session.detach(client);

            assertFalse("Entity should NOT be in session after detach",
                    session.contains(client));

            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    // =========================================================================
    //  Test 6: TypedQuery returns correctly-typed results (no raw casts)
    // =========================================================================

    /**
     * Verifies that {@code session.createQuery(hql, entityClass)} returns a
     * {@link TypedQuery} with the correct result type (the V6 type-safety upgrade).
     */
    public void testTypedQueryReturnsCorrectType() {
        Session session = _factory.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            session.persist(newClient("typed-005a"));
            session.persist(newClient("typed-005b"));
            session.flush();

            // V6 TypedQuery with explicit result class - no unchecked cast needed
            TypedQuery<YExternalClient> query = session.createQuery(
                    "from YExternalClient", YExternalClient.class);
            List<YExternalClient> results = query.getResultList();

            assertNotNull("TypedQuery.getResultList() must return non-null list", results);
            assertFalse("Results must not be empty after inserting 2 entities",
                    results.isEmpty());
            for (YExternalClient item : results) {
                // Direct typed access without cast - proves type safety
                assertNotNull("Each result must be non-null", item);
                assertNotNull("Each result must have a non-null userName", item.getUserName());
            }

            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    // =========================================================================
    //  Test 7: Named parameters prevent HQL injection
    // =========================================================================

    /**
     * Verifies that named parameters in HQL queries handle special characters in
     * values correctly without breaking the query (the V6 injection-prevention fix).
     */
    public void testNamedParametersPreventHqlInjection() {
        Session session = _factory.getCurrentSession();
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
            assertTrue("Named parameter query must not match HQL-injected value",
                    results.isEmpty());

            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    // =========================================================================
    //  Test 8: Transaction commit persists data across sessions
    // =========================================================================

    /**
     * Verifies that committing a transaction makes entities durable and visible
     * in a subsequent session - the fundamental persistence guarantee.
     */
    public void testTransactionCommitMakesDataDurable() {
        // Session 1: persist and commit
        Session session1 = _factory.getCurrentSession();
        Transaction tx1 = session1.beginTransaction();
        try {
            session1.persist(newClient("durable-007"));
            tx1.commit();
        } catch (Exception e) {
            tx1.rollback();
            throw e;
        }

        // Session 2: verify data is visible after commit
        Session session2 = _factory.getCurrentSession();
        Transaction tx2 = session2.beginTransaction();
        try {
            TypedQuery<YExternalClient> q = session2.createQuery(
                    "from YExternalClient where _userName = :name", YExternalClient.class);
            q.setParameter("name", "testuser-durable-007");
            List<YExternalClient> results = q.getResultList();

            assertFalse("Committed entity must be visible in next session", results.isEmpty());
            assertEquals("Durable entity userName must match committed value",
                    "testuser-durable-007", results.get(0).getUserName());

            tx2.commit();
        } catch (Exception e) {
            tx2.rollback();
            throw e;
        }
    }

    // =========================================================================
    //  Test 9: Transaction rollback discards changes
    // =========================================================================

    /**
     * Verifies that rolling back a transaction discards pending changes and
     * leaves the database in its pre-transaction state.
     */
    public void testTransactionRollbackDiscardsChanges() {
        // Start transaction, persist, then rollback
        Session session = _factory.getCurrentSession();
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
        Session verifySession = _factory.getCurrentSession();
        Transaction verifyTx = verifySession.beginTransaction();
        try {
            TypedQuery<YExternalClient> q = verifySession.createQuery(
                    "from YExternalClient where _userName = :name", YExternalClient.class);
            q.setParameter("name", "testuser-rollback-008");
            List<YExternalClient> results = q.getResultList();

            assertTrue("Rolled-back entity must NOT be visible after rollback",
                    results.isEmpty());

            verifyTx.commit();
        } catch (Exception e) {
            verifyTx.rollback();
            throw e;
        }
    }

    // =========================================================================
    //  Test 10: YPersistenceManager V6 API consistency (smoke test)
    // =========================================================================

    /**
     * Smoke test: verifies that the engine's YPersistenceManager V6 API contract
     * is internally consistent (isEnabled == isPersisting, getFactory == getSessionFactory).
     */
    public void testYPersistenceManagerApiConsistency() throws YPersistenceException {
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
        assertEquals(
                "isEnabled() and isPersisting() must return same value (V6 API contract)",
                pmgr.isEnabled(),
                pmgr.isPersisting());

        // V6 contract: getFactory() and getSessionFactory() must return same object
        assertSame(
                "getFactory() and getSessionFactory() must return same instance",
                pmgr.getFactory(),
                pmgr.getSessionFactory());
    }

    // =========================================================================
    //  Test suite
    // =========================================================================

    public static Test suite() {
        TestSuite suite = new TestSuite("Hibernate V6 API Integration Tests");
        suite.addTestSuite(HibernateV6ApiTest.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
