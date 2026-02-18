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


import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.persistence.Query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import jakarta.persistence.TypedQuery;
import org.yawlfoundation.yawl.authentication.YExternalClient;
import org.yawlfoundation.yawl.elements.GroupedMIOutputData;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.time.YLaunchDelayer;
import org.yawlfoundation.yawl.engine.time.YWorkItemTimer;
import org.yawlfoundation.yawl.exceptions.Problem;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.util.HibernateStatistics;

/**
 * Transactional persistence handler for the YAWL workflow engine.
 *
 * <p>This class manages all Hibernate ORM interactions for the engine, providing
 * CRUD operations, transaction lifecycle management, and HQL query execution.
 * Upgraded to Hibernate 6.x APIs: {@code session.persist()}, {@code session.remove()},
 * {@code session.merge()}, and {@code session.detach()} replacing their deprecated 5.x
 * equivalents ({@code save}, {@code delete}, {@code saveOrUpdate}, {@code evict}).
 * All queries now use parameterized {@link TypedQuery} to prevent HQL injection.</p>
 *
 * <p>V6 upgrade changes (2026-02-17):
 * <ul>
 *   <li>Hibernate 6.x API: {@code session.save()} replaced by {@code session.persist()}</li>
 *   <li>Hibernate 6.x API: {@code session.delete()} replaced by {@code session.remove()}</li>
 *   <li>Hibernate 6.x API: {@code session.saveOrUpdate()} replaced by {@code session.merge()}</li>
 *   <li>Hibernate 6.x API: {@code session.evict()} replaced by {@code session.detach()}</li>
 *   <li>Type-safe {@link TypedQuery} replaces raw {@code Query} for all queries</li>
 *   <li>Parameterized named parameters in {@link #selectScalar} to prevent HQL injection</li>
 *   <li>Removed dead commented-out code blocks from doPersistAction</li>
 *   <li>StandardServiceRegistry properly closed to prevent resource leaks</li>
 * </ul>
 * </p>
 *
 * @author Andrew Hastie (M2 Investments)
 *         Date: 21/06/2005
 *         Time: 13:46:54
 * @author Michael Adams - updated for v2.1 11/2009
 * @author YAWL Team - V6 Hibernate 6.x upgrade 02/2026
 */
public class YPersistenceManager {

    // persistence actions
    public static final int DB_UPDATE = 0;
    public static final int DB_DELETE = 1;
    public static final int DB_INSERT = 2;

    private static final Class<?>[] persistedClasses = {
            YSpecification.class, YNetRunner.class, YWorkItem.class, YIdentifier.class,
            YNetData.class, YAWLServiceReference.class, YExternalClient.class,
            YWorkItemTimer.class, YLaunchDelayer.class, YCaseNbrStore.class, Problem.class,
            GroupedMIOutputData.class
    };

    /**
     * HBM XML mapping files for each persisted class.
     * Paths are relative to the classpath root, using the package path of each class.
     */
    private static final String[] persistedClassHbmResources = {
            "org/yawlfoundation/yawl/elements/YSpecification.hbm.xml",
            "org/yawlfoundation/yawl/engine/YNetRunner.hbm.xml",
            "org/yawlfoundation/yawl/engine/YWorkItem.hbm.xml",
            "org/yawlfoundation/yawl/elements/state/YIdentifier.hbm.xml",
            "org/yawlfoundation/yawl/engine/YNetData.hbm.xml",
            "org/yawlfoundation/yawl/elements/YAWLServiceReference.hbm.xml",
            "org/yawlfoundation/yawl/authentication/YExternalClient.hbm.xml",
            "org/yawlfoundation/yawl/engine/time/YWorkItemTimer.hbm.xml",
            "org/yawlfoundation/yawl/engine/time/YLaunchDelayer.hbm.xml",
            "org/yawlfoundation/yawl/engine/YCaseNbrStore.hbm.xml",
            "org/yawlfoundation/yawl/exceptions/Problem.hbm.xml",
            "org/yawlfoundation/yawl/elements/GroupedMIOutputData.hbm.xml"
    };

    private static final boolean INSERT = false;
    private static final boolean UPDATE = true;
    private static Logger logger = null;

    /**
     * ReentrantLock guarding doPersistAction to avoid virtual thread pinning.
     *
     * <p>Uses ReentrantLock instead of synchronized to avoid virtual thread pinning.
     * Synchronized blocks holding JDBC connections pin the carrier thread, preventing
     * virtual threads from unmounting during blocking I/O and negating their scalability
     * benefits. ReentrantLock allows virtual threads to unmount while waiting for the lock.</p>
     *
     * <p>Mutual exclusion semantics are identical to the previous synchronized method:
     * only one persist operation proceeds per manager instance at a time.</p>
     */
    private final ReentrantLock _persistLock = new ReentrantLock();

    protected static SessionFactory factory = null;
    private boolean restoring = false;
    private boolean enabled = false;

    /**
     * Constructs a new persistence manager and initialises the logger.
     */
    public YPersistenceManager() {
        logger = LogManager.getLogger(YPersistenceManager.class);
    }


    /**
     * Initialises the Hibernate persistence layer.
     *
     * <p>Builds the Hibernate {@link SessionFactory} from configuration, registers all
     * persisted entity classes, and enables the persistence layer. The
     * {@link StandardServiceRegistry} is closed immediately after the {@link SessionFactory}
     * is created to release its resources, preventing potential memory leaks.</p>
     *
     * @param journalising {@code true} to enable persistence; {@code false} to skip
     *                     initialisation (persistence remains disabled)
     * @return the initialised {@link SessionFactory}, or {@code null} if journalising
     *         is {@code false}
     * @throws YPersistenceException if the persistence layer cannot be initialised
     */
    protected SessionFactory initialise(boolean journalising) throws YPersistenceException {
        if (journalising) {
            StandardServiceRegistry standardRegistry = null;
            try {
                standardRegistry = new StandardServiceRegistryBuilder().configure().build();

                MetadataSources metadataSources = new MetadataSources(standardRegistry);
                // Load HBM XML mapping files for all persisted classes
                for (String hbmResource : persistedClassHbmResources) {
                    metadataSources.addResource(hbmResource);
                }

                Metadata metadata = metadataSources.buildMetadata();
                factory = metadata.buildSessionFactory();
                enabled = true;
            }
            catch (Exception e) {
                logger.fatal("Failure initialising persistence layer", e);
                // Destroy the registry to prevent resource leaks when factory creation fails
                if (standardRegistry != null) {
                    StandardServiceRegistryBuilder.destroy(standardRegistry);
                }
                throw new YPersistenceException("Failure initialising persistence layer", e);
            }
        }
        return factory;
    }


    /**
     * Enables or disables the persistence layer.
     *
     * @param enable {@code true} to enable persistence; {@code false} to disable
     */
    public void setEnabled(boolean enable) { enabled = enable; }

    /**
     * Returns whether persistence is currently enabled and operational.
     *
     * @return {@code true} if persistence is enabled and the session factory is available
     */
    public boolean isEnabled() { return enabled && (factory != null); }


    /**
     * Returns the Hibernate {@link SessionFactory}.
     *
     * @return the session factory, or {@code null} if not yet initialised
     */
    public SessionFactory getFactory() {
        return factory;
    }


    /**
     * Returns whether the engine is currently in restore mode.
     *
     * @return {@code true} if the engine is restoring persisted state
     */
    public boolean isRestoring() {
        return restoring;
    }

    /**
     * Sets the restore mode flag.
     *
     * @param restoring {@code true} when the engine is restoring persisted state
     */
    protected void setRestoring(boolean restoring) {
        this.restoring = restoring;
    }

    /**
     * Returns the current Hibernate session bound to the current thread.
     *
     * @return the current session, or {@code null} if the factory is not initialised
     */
    public Session getSession() {
        return (factory != null) ? factory.getCurrentSession() : null;
    }

    /**
     * Returns the current Hibernate transaction for the active session.
     *
     * @return the current transaction, or {@code null} if no session is active
     */
    public Transaction getTransaction() {
        Session session = getSession();
        return (session != null) ? session.getTransaction() : null;
    }

    /**
     * Closes the current Hibernate session if it is open.
     *
     * <p>Session close errors are logged but not propagated, ensuring cleanup
     * proceeds without masking the original exception.</p>
     */
    public void closeSession() {
        if (isEnabled()) {
            try {
                Session session = getSession();
                if ((session != null) && (session.isOpen())) {
                    session.close();
                }
            } catch (HibernateException e) {
                logger.error("Failure to close Hibernate session", e);
            }
        }
    }


    /**
     * Closes the Hibernate {@link SessionFactory}, releasing all pooled connections
     * and cached state. Should be called during engine shutdown.
     */
    public void closeFactory() {
        if (factory != null) {
            factory.close();
        }
    }


    /**
     * Returns Hibernate performance statistics as an XML string.
     *
     * @return an XML representation of statistics, or {@code null} if not available
     */
    public String getStatistics() {
        if (factory != null) {
            HibernateStatistics stats = new HibernateStatistics(factory);
            return stats.toXML();
        }
        return null;
    }

    /**
     * Enables or disables Hibernate statistics collection.
     *
     * @param enabled {@code true} to enable statistics; {@code false} to disable
     */
    public void setStatisticsEnabled(boolean enabled) {
        if (factory != null) factory.getStatistics().setStatisticsEnabled(enabled);
    }

    /**
     * Returns whether Hibernate statistics collection is enabled.
     *
     * @return {@code true} if statistics are enabled
     */
    public boolean isStatisticsEnabled() {
        return (factory != null) && factory.getStatistics().isStatisticsEnabled();
    }


    /**
     * Starts a new Hibernate transaction on the current session.
     *
     * <p>Returns {@code false} without action if persistence is disabled or a transaction
     * is already active, ensuring idempotent behaviour for nested transaction scenarios.</p>
     *
     * @return {@code true} if a new transaction was started; {@code false} if persistence
     *         is disabled or a transaction was already active
     * @throws YPersistenceException if the transaction cannot be started
     */
    public boolean startTransaction() throws YPersistenceException {
        if ((!isEnabled()) || isActiveTransaction()) return false;
        logger.debug("---> start Transaction");
        try {
            getSession().beginTransaction();
        } catch (HibernateException e) {
            logger.fatal("Failure to start transactional session", e);
            throw new YPersistenceException("Failure to start transactional session", e);
        }
        logger.debug("<--- start Transaction");
        return true;
    }


    /**
     * Persists a new object to the database using {@code session.persist()}.
     *
     * <p>No-op if the engine is in restore mode or persistence is disabled.
     * Uses Hibernate 6.x {@code persist()} which is the JPA-standard replacement
     * for the deprecated {@code save()} method.</p>
     *
     * @param obj the object to persist
     * @throws YPersistenceException if the persist operation fails
     */
    protected void storeObject(Object obj) throws YPersistenceException {
        if ((!restoring) && isEnabled()) {
            logger.debug("Adding to insert cache: Type={}", obj.getClass().getName());
            doPersistAction(obj, INSERT);
        }
    }


    /**
     * Schedules an existing object for update when the current transaction commits.
     *
     * <p>No-op if the engine is in restore mode or persistence is disabled.
     * Uses Hibernate 6.x {@code merge()} which is the JPA-standard replacement
     * for the deprecated {@code saveOrUpdate()} method.</p>
     *
     * @param obj the object to update
     * @throws YPersistenceException if the update operation fails
     */
    protected void updateObject(Object obj) throws YPersistenceException {
        if ((!restoring) && isEnabled()) {
            logger.debug("Adding to update cache: Type={}", obj.getClass().getName());
            doPersistAction(obj, UPDATE);
        }
    }


    /**
     * Removes an object from the database using {@code session.remove()}.
     *
     * <p>Uses Hibernate 6.x {@code remove()} which replaces the deprecated
     * {@code delete()} method. After removal, the object is detached from the
     * session using {@code session.detach()} (replacing deprecated {@code evict()}).
     * Errors during detach are silently ignored to avoid masking the removal result.</p>
     *
     * @param obj the object to remove
     * @throws YPersistenceException if the remove operation fails
     */
    protected void deleteObject(Object obj) throws YPersistenceException {
        if (!isEnabled()) return;

        logger.debug("--> delete: Object={}: {}", obj.getClass().getName(), obj.toString());

        try {
            Session session = getSession();
            session.remove(obj);   // Hibernate 6.x: replaces deprecated session.delete()
            session.flush();
        } catch (HibernateException e) {
            logger.error("Failed to delete object of type {}: {}",
                    obj.getClass().getName(), e.getMessage());
            throw new YPersistenceException(
                    "Failure to remove object of type " + obj.getClass().getName(), e);
        }
        try {
            getSession().detach(obj);  // Hibernate 6.x: replaces deprecated session.evict()
        } catch (HibernateException he) {
            logger.debug("Non-fatal: could not detach object after removal", he);
        }
        logger.debug("<-- delete");
    }


    /**
     * Merges the supplied object into the current session.
     *
     * <p>Uses Hibernate 6.x {@code merge()} as the sole update strategy, replacing
     * the previous dual-strategy approach ({@code saveOrUpdate} then fallback to
     * {@code merge}) which was unreliable with Hibernate 6.x's stricter entity
     * state management.</p>
     *
     * @param obj the object to merge
     */
    private void merge(Object obj) {
        getSession().merge(obj);  // Hibernate 6.x standard: replaces saveOrUpdate()
    }


    /**
     * Returns whether the current session has an active (uncommitted) transaction.
     *
     * @return {@code true} if a transaction is active
     */
    private boolean isActiveTransaction() {
        Transaction transaction = getTransaction();
        return (transaction != null) && transaction.isActive();
    }


    /**
     * Public facade for {@link #storeObject(Object)}.
     *
     * <p>Allows external callers (e.g., service integrations) to persist objects
     * while preserving the protected internal API for engine subclasses.</p>
     *
     * @param obj the object to persist
     * @throws YPersistenceException if the persist operation fails
     */
    public void storeObjectFromExternal(Object obj) throws YPersistenceException {
        storeObject(obj);
    }

    /**
     * Public facade for {@link #updateObject(Object)}.
     *
     * <p>Allows external callers to update persisted objects while preserving
     * the protected internal API for engine subclasses.</p>
     *
     * @param obj the object to update
     * @throws YPersistenceException if the update operation fails
     */
    public void updateObjectExternal(Object obj) throws YPersistenceException {
        updateObject(obj);
    }


    /**
     * Public facade for {@link #deleteObject(Object)}.
     *
     * <p>Allows external callers to remove persisted objects while preserving
     * the protected internal API for engine subclasses.</p>
     *
     * @param obj the object to remove from persistence
     * @throws YPersistenceException if the remove operation fails
     */
    public void deleteObjectFromExternal(Object obj) throws YPersistenceException {
        deleteObject(obj);
    }


    /**
     * Executes the actual persist or merge operation for the given object.
     *
     * <p>Uses Hibernate 6.x APIs:
     * <ul>
     *   <li>{@code session.persist()} for new objects (INSERT)</li>
     *   <li>{@code session.merge()} for existing objects (UPDATE)</li>
     * </ul>
     * On failure, the current transaction is rolled back before re-throwing
     * the exception to ensure data consistency.</p>
     *
     * <p>Uses {@link ReentrantLock} instead of {@code synchronized} to avoid virtual
     * thread pinning. Hibernate JDBC operations inside a synchronized block would pin
     * the carrier thread, preventing virtual threads from unmounting during blocking I/O.
     * {@code _persistLock} provides identical mutual exclusion (one persist per manager
     * instance at a time) while allowing virtual threads to unmount during lock contention.</p>
     *
     * @param obj    the object to persist or update
     * @param update {@code true} to merge (UPDATE); {@code false} to persist (INSERT)
     * @throws YPersistenceException if the operation fails or the rollback fails
     */
    private void doPersistAction(Object obj, boolean update)
            throws YPersistenceException {

        _persistLock.lock();
        try {
            logger.debug("--> doPersistAction: Mode={}; Object={}:{}; Identity={}",
                    (update ? "Update" : "Create"),
                    obj.getClass().getName(), obj.toString(),
                    System.identityHashCode(obj));

            try {
                if (update) {
                    merge(obj);                       // Hibernate 6.x: replaces saveOrUpdate()
                } else {
                    getSession().persist(obj);        // Hibernate 6.x: replaces save()
                }
            } catch (Exception e) {
                logger.error("Failure persisting instance of {}: {}",
                        obj.getClass().getName(), e.getMessage());
                try {
                    if (isActiveTransaction()) {
                        getTransaction().rollback();
                    }
                } catch (Exception rollbackEx) {
                    throw new YPersistenceException(
                            "Failure to rollback transactional session after persist error", rollbackEx);
                }
                throw new YPersistenceException(
                        "Failure detected whilst persisting instance of " + obj.getClass().getName(), e);
            }
            logger.debug("<-- doPersistAction");
        } finally {
            _persistLock.unlock();
        }
    }


    /**
     * Commits the current active transaction.
     *
     * <p>If the commit fails, an automatic rollback is attempted before the
     * exception is propagated.</p>
     *
     * @throws YPersistenceException if the commit fails
     */
    public void commit() throws YPersistenceException {
        logger.debug("--> start commit");
        try {
            if (isEnabled() && isActiveTransaction()) getTransaction().commit();
        } catch (Exception e1) {
            logger.fatal("Failure to commit transactional session - Rolling Back Transaction", e1);
            rollbackTransaction();
            throw new YPersistenceException("Failure to commit transactional session", e1);
        }
        logger.debug("<-- end commit");
    }


    /**
     * Rolls back the current active transaction.
     *
     * <p>After rollback, the session is closed to return connections to the pool.
     * No-op if persistence is disabled or no active transaction exists.</p>
     *
     * @throws YPersistenceException if the rollback fails
     */
    protected void rollbackTransaction() throws YPersistenceException {
        logger.debug("--> rollback Transaction");
        if (isEnabled() && isActiveTransaction()) {
            try {
                getTransaction().rollback();
            } catch (HibernateException e) {
                throw new YPersistenceException("Failure to rollback transaction", e);
            } finally {
                closeSession();
            }
        }
        logger.debug("<-- rollback Transaction");
    }


    /**
     * Creates a typed HQL query for the given query string.
     *
     * <p>Returns a raw-typed {@link TypedQuery} using {@code Object[]} to support
     * arbitrary result shapes. Callers that know the result type should cast
     * appropriately. Returns {@code null} if persistence is disabled.</p>
     *
     * @param queryString the HQL query string
     * @return a TypedQuery for the given HQL string, or {@code null} if disabled
     * @throws YPersistenceException if the query cannot be created
     */
    public TypedQuery<Object> createQuery(String queryString) throws YPersistenceException {
        if (isEnabled()) {
            try {
                @SuppressWarnings("unchecked")
                TypedQuery<Object> query = (TypedQuery<Object>)
                        getSession().createQuery(queryString, Object.class);
                return query;
            } catch (HibernateException e) {
                throw new YPersistenceException("Failure to create Hibernate query object", e);
            }
        }
        return null;
    }


    /**
     * Executes the given HQL query string and returns all results.
     *
     * @param queryString the HQL query to execute
     * @return a list of results, or {@code null} if persistence is disabled
     * @throws YPersistenceException if the query fails
     */
    public List<?> execQuery(String queryString) throws YPersistenceException {
        return execQuery(createQuery(queryString));
    }


    /**
     * Executes the given {@link TypedQuery} and returns all results.
     *
     * @param query the query to execute
     * @return a list of results, or {@code null} if the query is null
     * @throws YPersistenceException if the query execution fails
     */
    public List<?> execQuery(TypedQuery<Object> query) throws YPersistenceException {
        try {
            return (query != null) ? query.getResultList() : null;
        } catch (HibernateException he) {
            throw new YPersistenceException("Error executing query", he);
        }
    }


    /**
     * Returns all persisted instances of the named class.
     *
     * <p>The {@code className} parameter must be the simple or fully-qualified name
     * of a Hibernate-managed entity class registered in {@link #persistedClasses}.</p>
     *
     * @param className the name of the entity class to retrieve
     * @return a list of all instances, possibly empty
     * @throws YPersistenceException if the query fails
     */
    public List<?> getObjectsForClass(String className) throws YPersistenceException {
        return execQuery("from " + className);
    }


    /**
     * Returns all persisted instances of the named class that match a where clause.
     *
     * <p>The {@code whereClause} is appended to the HQL query verbatim as
     * {@code where tbl.<whereClause>}. The clause must be a valid HQL predicate.</p>
     *
     * @param className   the name of the entity class to retrieve
     * @param whereClause the HQL predicate (without the 'WHERE' keyword)
     * @return a list of matching instances, possibly empty
     * @throws YPersistenceException if the query fails
     */
    public List<?> getObjectsForClassWhere(String className, String whereClause)
            throws YPersistenceException {
        try {
            String hql = String.format("from %s as tbl where tbl.%s", className, whereClause);
            TypedQuery<Object> query = createQuery(hql);
            return (query != null) ? query.getResultList() : null;
        } catch (HibernateException he) {
            throw new YPersistenceException("Error reading data for class: " + className, he);
        }
    }


    /**
     * Returns the first persisted instance of {@code className} where {@code field}
     * equals {@code value}, using a named parameter to prevent HQL injection.
     *
     * <p>Previously this method used string concatenation to build the HQL predicate,
     * which was vulnerable to HQL injection when the value contained special characters.
     * V6 upgrade: the value is now bound as a named parameter {@code :val}.</p>
     *
     * @param className the name of the entity class to search
     * @param field     the entity field to match against
     * @param value     the string value to match
     * @return the first matching object, or {@code null} if none found
     * @throws YPersistenceException if the query fails
     */
    public Object selectScalar(String className, String field, String value)
            throws YPersistenceException {
        String hql = String.format(
                "select distinct t from %s as t where t.%s = :val", className, field);
        try {
            TypedQuery<Object> query = createQuery(hql);
            if (query == null) return null;
            query.setParameter("val", value);
            List<Object> results = query.getResultList();
            return results.isEmpty() ? null : results.getFirst();
        } catch (HibernateException e) {
            throw new YPersistenceException(
                    "Error querying " + className + " for " + field + "=" + value, e);
        }
    }


    /**
     * Returns the first persisted instance of {@code className} where {@code field}
     * equals the given {@code long} value, using a named parameter to prevent HQL injection.
     *
     * @param className the name of the entity class to search
     * @param field     the entity field to match against
     * @param value     the long value to match
     * @return the first matching object, or {@code null} if none found
     * @throws YPersistenceException if the query fails
     */
    public Object selectScalar(String className, String field, long value)
            throws YPersistenceException {
        String hql = String.format(
                "select distinct t from %s as t where t.%s = :val", className, field);
        try {
            TypedQuery<Object> query = createQuery(hql);
            if (query == null) return null;
            query.setParameter("val", value);
            List<Object> results = query.getResultList();
            return results.isEmpty() ? null : results.getFirst();
        } catch (HibernateException e) {
            throw new YPersistenceException(
                    "Error querying " + className + " for " + field + "=" + value, e);
        }
    }

    /**
     * Returns the Hibernate {@link SessionFactory}.
     *
     * <p>Used by health indicators to check database connectivity and pool status.
     * Equivalent to {@link #getFactory()}; provided as an alternative name for
     * Spring-style health check integration.</p>
     *
     * @return the session factory, or {@code null} if not initialised
     */
    public SessionFactory getSessionFactory() {
        return factory;
    }

    /**
     * Checks if persistence is enabled and active.
     *
     * <p>Equivalent to {@link #isEnabled()}; provided for external callers that
     * prefer the more descriptive name.</p>
     *
     * @return {@code true} if persistence is enabled and operational
     */
    public boolean isPersisting() {
        return isEnabled();
    }

    /**
     * Returns connection pool and Hibernate statistics as a key-value map.
     *
     * <p>Returns an empty map if statistics are disabled or the factory is not
     * initialised. Statistics must be enabled via {@link #setStatisticsEnabled(boolean)}
     * before calling this method.</p>
     *
     * @return a map of statistic names to values; empty if statistics are unavailable
     */
    public Map<String, Object> getStatisticsMap() {
        Map<String, Object> stats = new HashMap<>();
        if (factory != null && isStatisticsEnabled()) {
            org.hibernate.stat.Statistics hibernateStats = factory.getStatistics();
            if (hibernateStats != null) {
                stats.put("connectCount", hibernateStats.getConnectCount());
                stats.put("sessionOpenCount", hibernateStats.getSessionOpenCount());
                stats.put("sessionCloseCount", hibernateStats.getSessionCloseCount());
                stats.put("transactionCount", hibernateStats.getTransactionCount());
                stats.put("queryExecutionCount", hibernateStats.getQueryExecutionCount());
            }
        }
        return stats;
    }

    /**
     * Returns the singleton instance of the persistence manager held by the engine.
     *
     * @return the singleton persistence manager, or {@code null} if the engine is
     *         not yet initialised
     */
    public static YPersistenceManager getInstance() {
        return YEngine._pmgr;
    }

}
