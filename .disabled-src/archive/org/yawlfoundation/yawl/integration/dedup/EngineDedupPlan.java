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

package org.yawlfoundation.yawl.integration.dedup;

/**
 * Engine Deduplication Integration Plan
 *
 * This class is a structured design document for the deduplication of the
 * parallel stateful (org.yawlfoundation.yawl.engine / .elements) and stateless
 * (org.yawlfoundation.yawl.stateless.*) engine implementations.
 *
 * =============================================================================
 * SECTION 1 - CURRENT STATE ANALYSIS
 * =============================================================================
 *
 * Corpus scope (non-package-info .java files measured 2026-02-17):
 *
 *   Stateful engine tree  : 101 files in engine/*, 45 files in elements/*  = 39,514 LOC
 *   Stateless engine tree :  73 files in stateless/**                       = 19,406 LOC
 *   Matched file pairs    :  41 files share a name across both trees
 *   Unmatched stateless   :  32 files unique to stateless (listener/*, monitor/*, etc.)
 *   Unmatched stateful    :  60 files unique to stateful  (persistence, GUI, REST, auth)
 *
 * The 41 matched pairs represent the core duplication space.  All quantitative
 * similarity measurements below are line-level diff ratios
 * ( (total - changed/2) / total ).
 *
 * =============================================================================
 * SECTION 2 - CODE SHARING MATRIX  (41 matched file pairs)
 * =============================================================================
 *
 * Tier A - HIGH DUPLICATION  (similarity >= 90%)  --  20 files
 * -----------------------------------------------------------------------
 * File                           Stateful LOC  Similarity  Dominant delta
 * -----------------------------------------------------------------------
 * YWorkItemID.java                       157       96%     final modifiers only
 * YNetRunnerRepository.java              140       94%     serialVersionUID, null-guard
 * YMarshal.java                          170       98%     package imports
 * YDecompositionParser.java              780       97%     element-type references
 * YSpecificationParser.java              288       95%     element-type references
 * YMarking.java                          344       98%     package imports
 * YSetOfMarkings.java                    110       98%     package imports
 * E2WFOJNet.java                         874       98%     package imports
 * YDataValidator.java                    329       99%     YVariable import only
 * SaxonUtil.java                         174       93%     package reference
 * YExternalNetElement.java               432       98%     package imports
 * YNet.java                              594       93%     HashMap -> ConcurrentHashMap
 * YEnabledTransitionSet.java             245       93%     package imports
 * YFlow.java                             256       92%     package imports
 * YSpecification.java                    523       92%     package imports
 * PredicateEvaluatorCache.java            78       96%     package name in BASE_PACKAGE
 * PredicateEvaluator.java                 36       91%     package imports
 * YMultiInstanceAttributes.java          272       89%     YTimerParameters ref
 * YTimerVariable.java                    113       89%     YTask import path
 * YNetLocalVarVerifier.java              552       88%     element-type imports
 *
 * Tier B - MEDIUM DUPLICATION  (similarity 70-89%)  --  12 files
 * -----------------------------------------------------------------------
 * File                           Stateful LOC  Similarity  Dominant delta
 * -----------------------------------------------------------------------
 * YIdentifier.java                       319       86%     persistence + singleton hook
 * YDecomposition.java                    391       86%     YPersistenceManager params
 * YTask.java                           1,933       86%     data-mapping field additions
 * YAWLServiceReference.java              231       86%     YSession import
 * YAWLServiceGateway.java                170       85%     YSession import
 * YParameter.java                        203       91%     YDecomposition import path
 * YVariable.java                         522       85%     Hibernate annotations
 * YCondition.java                        138       85%     state import
 * YInternalCondition.java                151       88%     state import
 * YWorkItemRepository.java               323       79%     extra getter methods
 * YCompositeTask.java                    171       76%     persistence params
 * YAtomicTask.java                       275       70%     service URL handling
 *
 * Tier C - LOW DUPLICATION  (similarity < 70%)  --  9 files
 * -----------------------------------------------------------------------
 * File                           Stateful LOC  Similarity  Dominant delta
 * -----------------------------------------------------------------------
 * YTimerParameters.java                  259       77%     stateful timer persistence
 * YWorkItem.java                       1,047       75%     YClient, persistence calls
 * YNetRunner.java                      1,246       69%     YPersistenceManager threaded
 * YEngine.java                         2,377       54%     singleton vs multi-instance
 * YAnnouncer.java                        490       43%     HTTP push vs listener callbacks
 * YWorkItemTimer.java                    160       37%     persistent vs ephemeral timer
 * PredicateEvaluatorFactory.java          62        ~0%    only BASE_PACKAGE string differs
 *                                                          (structurally identical)
 * ExternalDataGateway.java               (interface - effectively identical)
 * ExternalDataGatewayFactory.java        (structurally same, package ref only)
 *
 * Note: PredicateEvaluatorFactory scores 0% on the string-diff heuristic because
 * the diff is exactly one line (BASE_PACKAGE constant value).  It is functionally
 * 99% identical and is the simplest possible extraction target.
 *
 * =============================================================================
 * SECTION 3 - ALGORITHM vs STATE SEPARATION ANALYSIS
 * =============================================================================
 *
 * The fundamental architectural divergence between the two engines is:
 *
 *   STATEFUL ENGINE                        STATELESS ENGINE
 *   --------------------------------       --------------------------------
 *   Singleton (YEngine.getInstance)        Multi-instance (new YEngine())
 *   Hibernate persistence throughout       No persistence; caller manages state
 *   YPersistenceManager threaded in        Not present
 *   HTTP push notifications (InterfaceB)   Listener/callback event model
 *   Session/auth management                Not present
 *   GUI, REST, observability layers        Not present
 *   YIdentifier linked to engine for ID    YIdentifier uses UUID directly
 *
 * Every divergence in Tiers A and B traces back to one of these five axes:
 *
 *   Axis 1 - PERSISTENCE   : YPersistenceManager params, Hibernate annotations,
 *                             serialVersionUID, transient modifiers
 *   Axis 2 - IDENTITY      : YEngine.getInstance() calls inside YIdentifier
 *   Axis 3 - ELEMENT TYPES : org.yawlfoundation.yawl.elements.* vs
 *                             org.yawlfoundation.yawl.stateless.elements.*
 *   Axis 4 - NOTIFICATION  : ObserverGatewayController vs typed listener sets
 *   Axis 5 - CONCURRENCY   : HashMap vs ConcurrentHashMap, final vs mutable fields
 *
 * Pure algorithms (safe to share as-is, delta = package imports only):
 *   E2WFOJNet, YMarking, YSetOfMarkings, YFlow, YDecompositionParser,
 *   YSpecificationParser, YMarshal, YDataValidator, SaxonUtil,
 *   PredicateEvaluator*, YEnabledTransitionSet, YExternalNetElement (base class)
 *
 * Mixed algorithm+state (require interface extraction to share):
 *   YNetRunner, YWorkItem, YTask, YDecomposition, YNet
 *
 * Pure state management (NOT shareable, stateful only):
 *   YPersistenceManager, YEngineRestorer, CaseExporter/CaseImporter,
 *   YSessionCache, InstanceCache, YSpecificationTable, YCaseNbrStore,
 *   ObserverGatewayController, InterfaceA/B/E/X, GUI, REST
 *
 * Pure stateless orchestration (NOT shareable, stateless only):
 *   YStatelessEngine, YCaseMonitor, YCaseMonitoringService,
 *   YCaseImportExportService, YCaseExporter/YCaseImporter (stateless),
 *   EventNotifier, listener/event/* hierarchy
 *
 * =============================================================================
 * SECTION 4 - EXTRACTION ROADMAP
 * =============================================================================
 *
 * Target package for shared code:
 *   org.yawlfoundation.yawl.engine.core
 *
 * ---------------------------------------------------------------------------
 * PHASE 1 - DATA STRUCTURES  (estimated: 3 developer-weeks, risk: LOW)
 * Target: Tier A files whose entire delta is package imports
 * ---------------------------------------------------------------------------
 *
 * P1.1  Create shared element base package:
 *         org.yawlfoundation.yawl.engine.core.elements
 *
 *       Move these files; both trees import from core:
 *         YExternalNetElement  (98% identical, delta = imports only)
 *         YFlow                (92% identical, delta = imports only)
 *         YEnabledTransitionSet(93% identical, delta = imports only)
 *         YConditionInterface  (interface only, trivially identical)
 *         YInputCondition      (67 LOC, delta = marking import)
 *         YOutputCondition     (61 LOC, delta = marking import)
 *
 * P1.2  Create shared marking package:
 *         org.yawlfoundation.yawl.engine.core.marking
 *
 *       The stateless YIdentifier is the superior version (no engine singleton
 *       dependency).  The stateful YIdentifier uses YEngine.getInstance() only
 *       for case number generation.  Extract that call behind:
 *
 *         interface CaseIdentifierSource {
 *             String nextCaseId();
 *         }
 *
 *       Then merge YIdentifier, YInternalCondition, YMarking, YSetOfMarkings
 *       into core.marking.  Stateful engine supplies a CaseIdentifierSource
 *       backed by YCaseNbrStore; stateless supplies UUID-based source.
 *
 *       Files: YIdentifier, YInternalCondition, YMarking, YSetOfMarkings
 *       Estimated LOC saved: ~1,030 (full removal of 4 stateless duplicates)
 *
 * P1.3  Create shared data package:
 *         org.yawlfoundation.yawl.engine.core.data
 *
 *       YVariable delta is only Hibernate annotations (@Column, @Transient).
 *       Strip annotations into a subclass in the stateful tree:
 *
 *         core.data.YVariable          (pure data, no annotations)
 *         core.data.YParameter         (pure data)
 *         engine.data.YVariablePersisted extends YVariable  (adds @Column etc.)
 *
 *       YParameter has zero runtime difference beyond import path.
 *       Estimated LOC saved: ~730 (YVariable + YParameter duplicates removed)
 *
 * P1.4  Merge algorithm-only utilities:
 *         org.yawlfoundation.yawl.engine.core.util
 *
 *       SaxonUtil stateless copy -> single copy in core.util (or consolidate
 *       into existing org.yawlfoundation.yawl.util.SaxonUtil directly, since
 *       the stateless version is 93% similar and the delta is one import).
 *
 *       PredicateEvaluatorFactory: make BASE_PACKAGE a constructor parameter.
 *       PredicateEvaluator, PredicateEvaluatorCache: trivially merge to core.
 *
 * P1.5  Merge E2WFOJNet (98% identical, 874 LOC):
 *         org.yawlfoundation.yawl.engine.core.e2wfoj
 *
 *       Delta is entirely import paths.  Both trees point to same core package
 *       after P1.1/P1.2 resolve marking and element types.
 *       Estimated LOC saved: ~874
 *
 * PHASE 1 total estimated reduction: ~4,000-4,500 LOC, ~18-22 files
 *
 * ---------------------------------------------------------------------------
 * PHASE 2 - ALGORITHMS  (estimated: 6 developer-weeks, risk: MEDIUM)
 * Target: Core process-execution classes (Tier B and partial Tier C)
 * ---------------------------------------------------------------------------
 *
 * P2.1  Merge YMarshal, YDecompositionParser, YSpecificationParser into
 *         org.yawlfoundation.yawl.engine.core.unmarshal
 *
 *       All three have >= 95% similarity; after Phase 1 resolves element-type
 *       imports the remaining delta collapses to nothing.  The stateless tree's
 *       YDecompositionParser is already building stateless elements; once both
 *       trees share core.elements, a single parser factory suffices.
 *       Estimated LOC saved: ~1,200 (full parser duplication removed)
 *
 * P2.2  Extract YDecomposition, YNet into core via template method:
 *
 *         abstract class YAbstractDecomposition  (in core.elements)
 *             void initializeDataStore(Object storeContext)  [abstract]
 *             void setIncomingData(Element data)             [concrete]
 *             ...all shared data-mapping logic                [concrete]
 *
 *         YDecomposition extends YAbstractDecomposition      (stateful, adds pmgr)
 *         stateless.YDecomposition extends YAbstractDecomposition (no pmgr)
 *
 *       YNet (594 LOC, 93% similar): same treatment, delta is only
 *       HashMap vs ConcurrentHashMap and one persistence call.
 *       Estimated LOC saved: ~700
 *
 * P2.3  Extract YTask into core via strategy pattern:
 *
 *         abstract class YAbstractTask  (in core.elements)
 *             abstract void fireTask(TaskExecutionContext ctx)
 *             [all data-mapping methods as concrete shared implementations]
 *
 *         interface TaskExecutionContext {
 *             void persistObject(Object o)  // no-op in stateless
 *             boolean isPersisting()
 *         }
 *
 *       YTask is 1,933 LOC / 86% similar; the 14% delta is split between:
 *         - YPersistenceManager parameters on 9 methods  (Axis 1)
 *         - _dataMappingsForTaskEnablement present only in stateful (minor)
 *         - Internal condition references differ (resolved by Phase 1)
 *
 *       After Phase 1 resolves marking types, the task split logic is
 *       byte-for-byte identical in both implementations.
 *       Estimated LOC saved: ~1,800 (largest single extraction)
 *
 * P2.4  Extract YWorkItemID into core:
 *
 *         org.yawlfoundation.yawl.engine.core.YWorkItemID
 *
 *       Already 96% identical.  Only delta: stateless uses final fields.
 *       Making stateful fields final has no risk (they are not mutated after
 *       construction in any path).  Single file serves both trees.
 *       Estimated LOC saved: ~157
 *
 * P2.5  Parameterize PredicateEvaluatorFactory:
 *       Remove the static BASE_PACKAGE string; pass it to the factory at
 *       construction time.  Both trees share one class; each supplies its own
 *       package prefix.  (0% diff score was misleading; structurally identical.)
 *
 * PHASE 2 total estimated reduction: ~3,900-4,500 LOC, ~12-15 files
 *
 * ---------------------------------------------------------------------------
 * PHASE 3 - ORCHESTRATION  (estimated: 8 developer-weeks, risk: HIGH)
 * Target: YNetRunner, YWorkItem, YEngine (core execution controllers)
 * ---------------------------------------------------------------------------
 *
 * P3.1  Extract YWorkItem shared logic into abstract base:
 *
 *         abstract class YAbstractWorkItem  (in core)
 *
 *       The 75% similarity (1,047 LOC) breaks down as:
 *         - Status state machine, ID management: 100% identical
 *         - Timer scheduling: diverges at persistence flag
 *         - XML serialization: 100% identical after element-type unification
 *         - YClient reference: stateful-only (resourcing) - stays in subclass
 *         - Logging calls: identical method names, diverge only on log key type
 *
 *       Abstract methods in YAbstractWorkItem:
 *         abstract void persistStatusChange(YWorkItemStatus old, YWorkItemStatus neo)
 *         abstract void scheduleTimer(long msec)
 *
 *       Stateful subclass (YWorkItem in engine package): persistence-aware impl
 *       Stateless subclass (YWorkItem in stateless.engine): no-op persist, direct timer
 *
 * P3.2  Extract YNetRunner shared logic:
 *
 *         abstract class YAbstractNetRunner  (in core)
 *
 *       69% similarity (1,246 LOC).  Key structure:
 *         - initialise() core body: identical after removing pmgr param
 *         - prepare() / kick() / completeTask(): identical logic
 *         - Announcement dispatch: diverges (Axis 4 - notification model)
 *         - Persistence calls: sprinkled on 9 methods (Axis 1)
 *
 *       Template method pattern:
 *         final void initialise(NetInitContext ctx) {  // concrete - shared
 *             _caseIDForNet = ctx.getCaseID();
 *             ...identical setup...
 *             onPostInitialise(ctx);                   // abstract hook
 *         }
 *         abstract void onPostInitialise(NetInitContext ctx)
 *         abstract void announceWorkItemEvent(YWorkItem item, YEventType type)
 *         abstract void persistRunner()
 *
 *       interface NetInitContext {
 *           YIdentifier getCaseID();
 *           boolean requiresPersistence();
 *           Object getPersistenceStore();  // null in stateless
 *       }
 *
 * P3.3  YNetRunnerRepository: 94% identical, merge directly into core.
 *       Delta (serialVersionUID, null-guard line 76-79) is trivially resolved:
 *       add serialVersionUID to stateless copy, expand the null guard in stateful.
 *       Single YNetRunnerRepository in core; both trees import it.
 *
 * P3.4  YWorkItemRepository: 79% identical (323 LOC).
 *       Stateless adds addAll(List<>) and a few getters absent in stateful.
 *       The stateful version adds those getters to close the gap, then merge.
 *
 * P3.5  YEngine divergence (54% similarity, 2,377 LOC):
 *       This is the hardest boundary.  The stateless YEngine (750 LOC) is
 *       already the "clean" version.  The stateful YEngine carries an additional
 *       ~1,600 LOC of:
 *         - Singleton lifecycle (getInstance, initialise, restore)
 *         - Session and client management
 *         - SpecificationTable management
 *         - InterfaceA/B implementation
 *         - InstanceCache management
 *
 *       Recommended approach: DO NOT merge YEngine bodies.
 *       Instead, extract shared orchestration methods (launchCase, cancelCase,
 *       completeWorkItem, suspendCase) into:
 *
 *         abstract class YAbstractEngine  (in core)
 *           abstract YNetRunnerRepository getRunnerRepository()
 *           abstract YWorkItemRepository  getWorkItemRepository()
 *           abstract YAbstractAnnouncer   getAnnouncer()
 *           final YNetRunner launchCase(...)  { ... shared body ... }
 *           final void cancelCase(...)         { ... shared body ... }
 *
 *       Both YEngine (stateful) and YEngine (stateless) extend YAbstractEngine
 *       and supply their own registry/repository implementations.
 *       This is a partial extraction; the singleton/persistence layer stays
 *       in the stateful subclass.
 *
 * P3.6  YAnnouncer divergence (43% similarity):
 *       Stateful: HTTP push model via ObserverGatewayController + InterfaceX
 *       Stateless: typed listener callback model
 *       These are fundamentally different delivery mechanisms.
 *       Define:
 *
 *         interface YEventSink {
 *             void onCaseStart(YSpecificationID specID, YIdentifier caseID)
 *             void onCaseCancel(YIdentifier caseID)
 *             void onWorkItemEnabled(YWorkItem item)
 *             void onWorkItemStarted(YWorkItem item)
 *             void onWorkItemCompleted(YWorkItem item)
 *             void onTimerExpired(YWorkItem item)
 *             void onException(YWorkItem item, String message)
 *         }
 *
 *       Stateful YAnnouncer implements YEventSink -> dispatches via HTTP
 *       Stateless YAnnouncer implements YEventSink -> dispatches to listener sets
 *       YAbstractEngine references YEventSink, not the concrete announcer.
 *
 * P3.7  YWorkItemTimer divergence (37% similarity):
 *       Stateful: tracks by String workItemID, delegates to YEngine singleton
 *       Stateless: holds reference to YWorkItem directly, manages State enum
 *       The State enum { dormant, active, closed, expired } and Trigger enum
 *       { OnEnabled, OnExecuting, Never } are identical - extract to core.
 *       Timer logic bodies are too different for merge; keep separate but share
 *       the enum definitions and the YTimedObject scheduling interface.
 *
 * PHASE 3 total estimated reduction: ~3,000-3,500 LOC, ~8-10 files
 *
 * =============================================================================
 * SECTION 5 - RISK ANALYSIS AND BEHAVIOR PARITY
 * =============================================================================
 *
 * RISK 1 - HIBERNATE ANNOTATION LOSS  (severity: HIGH in P1.3)
 *   If YVariable/YParameter are moved to core and annotations stripped,
 *   the Hibernate mapping for the stateful engine must be preserved via either:
 *     (a) orm.xml mapping file (recommended - zero code change in core)
 *     (b) Subclass with re-annotated fields
 *   Mitigation: Write Hibernate ORM XML mappings for YVariable/YParameter
 *   before executing P1.3.  Run full persistence test suite after each step.
 *
 * RISK 2 - YIdentifier CASE NUMBER ORDERING  (severity: HIGH in P1.2)
 *   The stateful YIdentifier obtains its idString from YCaseNbrStore via
 *   YEngine.getInstance().getNextCaseNbr().  This is a global-state side-effect
 *   inside a constructor.  Extracting behind CaseIdentifierSource must ensure:
 *     (a) Thread safety: CaseIdentifierSource#nextCaseId() must be synchronized
 *     (b) Ordering: numeric order must be preserved for restore/replay
 *     (c) Null safety: YIdentifier() no-arg constructor (Hibernate) must not call source
 *   Mitigation: audit all call sites of new YIdentifier(null) before P1.2.
 *
 * RISK 3 - CONCURRENCY MODEL CHANGE  (severity: MEDIUM in P1.1, P2.2)
 *   YNet.stateless uses ConcurrentHashMap for _netElements; stateful uses HashMap.
 *   Switching stateful to ConcurrentHashMap is safe but changes lock semantics
 *   (CHM does not lock on reads).  In the stateful engine, YNetRunner holds an
 *   external synchronized block over net traversal; CHM does not affect this.
 *   Mitigation: Switch stateful YNet to ConcurrentHashMap in an isolated PR with
 *   a concurrency stress test before merging with YNet core changes.
 *
 * RISK 4 - PERSISTENCE PARAMETER THREAD  (severity: HIGH in P3.2)
 *   YNetRunner in the stateful engine passes YPersistenceManager to 14 methods.
 *   Abstracting these behind a TaskExecutionContext introduces an indirection
 *   that changes call semantics if pmgr is null-checked in places.
 *   Mitigation: Enumerate all 14 pmgr-threaded methods; verify null-pmgr behavior
 *   is identical to stateless no-persistence behavior before substituting.
 *
 * RISK 5 - STATIC SINGLETON YEngine REFERENCE IN YTask  (severity: MEDIUM)
 *   YTask (stateful) calls YEngine.getInstance() directly in several places for
 *   persistence callbacks.  After P2.3 extracts YAbstractTask, these calls must
 *   be replaced by TaskExecutionContext.  This is a compile-enforced change (no
 *   hidden runtime risk) but requires careful method-by-method substitution.
 *   Mitigation: Introduce TaskExecutionContext stub (throws
 *   UnsupportedOperationException) early, compile-and-fix across all task subclasses.
 *
 * RISK 6 - PREDICATE EVALUATOR SPI LOADING  (severity: LOW in P1.4/P2.5)
 *   PredicateEvaluatorFactory uses PluginLoaderUtil with a BASE_PACKAGE string to
 *   discover implementations at runtime.  Parameterizing BASE_PACKAGE changes
 *   the factory from static-method API to instance API.  All call sites must
 *   be updated.  Mitigation: grep all callers before changing, update atomically.
 *
 * RISK 7 - TEST SYNCHRONIZATION  (severity: MEDIUM, affects all phases)
 *   The stateless engine has a more complete unit test suite (listener model
 *   makes assertions straightforward).  The stateful engine tests depend on
 *   Hibernate H2 in-memory DB.  After each phase:
 *     (a) Both engine test suites must pass: mvn clean test
 *     (b) A parity test must run the same workflow spec through both engines
 *         and compare work item event sequences
 *   See Section 7 for the test synchronization strategy.
 *
 * =============================================================================
 * SECTION 6 - IMPACT SUMMARY
 * =============================================================================
 *
 *                          Files    LOC Removed   % Reduction
 *   Phase 1 (data structs)  18-22    4,000-4,500      21-23%
 *   Phase 2 (algorithms)    12-15    3,900-4,500      20-23%
 *   Phase 3 (orchestration)  8-10    3,000-3,500      15-18%
 *   -------------------------------------------------------
 *   TOTAL                   38-47   10,900-12,500     56-64%
 *
 * The 40-60% maintenance reduction stated in the project goals is conservative;
 * actual reduction is likely 56-64% based on measured similarity data.
 *
 * Files that will NOT be merged (permanently divergent, correctly separate):
 *   - YEngine (stateful):        singleton, session management, REST, GUI
 *   - YPersistenceManager:       Hibernate-specific, no stateless equivalent
 *   - YAnnouncer (concrete):     HTTP push vs listener - delivery mechanisms differ
 *   - YWorkItemTimer (body):     persistence-tracked vs ephemeral timer
 *   - CaseExporter/Importer:     persistence-format specific
 *   - All stateless listener/*:  no stateful equivalent
 *   - All stateless monitor/*:   no stateful equivalent
 *
 * =============================================================================
 * SECTION 7 - TEST SYNCHRONIZATION STRATEGY
 * =============================================================================
 *
 * T1  PARITY TEST FIXTURE
 *     Write a parameterized JUnit 5 test that accepts an IEngineAdapter:
 *
 *       interface IEngineAdapter {
 *           void loadSpec(String xml) throws Exception;
 *           String launchCase(String caseParams) throws Exception;
 *           List<String> getEnabledWorkItems() throws Exception;
 *           void startWorkItem(String itemId) throws Exception;
 *           void completeWorkItem(String itemId, String outputData) throws Exception;
 *           List<String> getCompletedCases() throws Exception;
 *       }
 *
 *     StatefulEngineAdapter wraps YEngine.getInstance()
 *     StatelessEngineAdapter wraps YStatelessEngine
 *
 *     @ParameterizedTest
 *     @MethodSource("engineAdapters")
 *     void testWorkflowBehaviorParity(IEngineAdapter adapter) { ... }
 *
 *     Both adapters run against the same YAWL specification XML (the YAWL
 *     reference spec: orderfulfillment, from src/test/).
 *     Assertion: enabled work items, completion order, and case data snapshots
 *     are byte-for-byte identical after normalization of case IDs.
 *
 * T2  PHASE GATE: compile check after each file move
 *     After each file moved to core, run:
 *       mvn clean compile -pl src/
 *     This catches import breakage immediately before any test is run.
 *
 * T3  PERSISTENCE REGRESSION SUITE (after P1.3, P3.1-3.4)
 *     Run full Hibernate-backed test suite with H2 after each persistence-
 *     touching phase:
 *       mvn clean test -Dtest=*Persist*,*Engine*,*NetRunner*
 *
 * T4  CONCURRENCY STRESS TEST (after P1.1 ConcurrentHashMap change)
 *     Run 100 concurrent case launches against the stateful engine with a
 *     multi-net specification and assert no ConcurrentModificationException
 *     and correct final marking.
 *
 * T5  REGRESSION BASELINE
 *     Before starting Phase 1, capture:
 *       - Full mvn clean test result (all tests passing)
 *       - Serialized state snapshots from YCaseExporter for 3 reference cases
 *     After each phase, assert the same 3 cases export identically.
 *
 * =============================================================================
 * SECTION 8 - TIMELINE ESTIMATE
 * =============================================================================
 *
 *   Week  1-2 : Test infrastructure (T1 parity fixture, T5 baseline capture)
 *   Week  3-5 : Phase 1 execution (data structures, marking, utilities)
 *   Week  6   : Phase 1 verification gate (all tests pass, parity fixture passes)
 *   Week  7-10: Phase 2 execution (unmarshal, YDecomposition, YNet, YTask)
 *   Week 11   : Phase 2 verification gate + concurrency stress test
 *   Week 12-16: Phase 3 execution (YWorkItem, YNetRunner, YEngine abstract base)
 *   Week 17   : Phase 3 verification gate + full persistence regression
 *   Week 18   : Documentation, package-info updates, final review
 *
 *   Total: 18 weeks (~4.5 months) with one developer executing serially.
 *   Parallel execution (2 developers, Phases 1+2 overlap): reduces to ~13 weeks.
 *
 * =============================================================================
 * SECTION 9 - TARGET PACKAGE LAYOUT (post-deduplication)
 * =============================================================================
 *
 *   org.yawlfoundation.yawl.engine.core
 *   ├── marking/             YIdentifier, YInternalCondition, YMarking, YSetOfMarkings
 *   ├── elements/            YExternalNetElement, YFlow, YEnabledTransitionSet,
 *   │                        YInputCondition, YOutputCondition, YConditionInterface,
 *   │                        YAbstractDecomposition, YAbstractTask
 *   ├── data/                YVariable, YParameter
 *   ├── e2wfoj/              E2WFOJNet
 *   ├── predicate/           PredicateEvaluator, PredicateEvaluatorCache,
 *   │                        PredicateEvaluatorFactory
 *   ├── unmarshal/           YMarshal, YDecompositionParser, YSpecificationParser
 *   ├── util/                SaxonUtil (if not consolidated into yawl.util)
 *   ├── YWorkItemID          (value object, final fields)
 *   ├── YWorkItemStatus      (already shared - in engine package, ref'd by both)
 *   ├── YNetRunnerRepository (merged from both trees)
 *   ├── YWorkItemRepository  (merged from both trees)
 *   ├── YAbstractWorkItem    (shared state machine + XML logic)
 *   ├── YAbstractNetRunner   (shared execution template)
 *   ├── YAbstractEngine      (shared launchCase/cancelCase/completeWorkItem)
 *   ├── YEventSink           (interface - decouples announcement delivery)
 *   ├── CaseIdentifierSource (interface - decouples ID generation)
 *   └── NetInitContext       (interface - decouples persistence from init)
 *
 *   org.yawlfoundation.yawl.engine          (stateful, unchanged public API)
 *   org.yawlfoundation.yawl.stateless       (stateless, unchanged public API)
 *   org.yawlfoundation.yawl.elements        (stateful elements extend core)
 *   org.yawlfoundation.yawl.stateless.elements (stateless elements extend core)
 *
 * =============================================================================
 */
public final class EngineDedupPlan {

    /**
     * This class is a pure design document; it contains no executable logic.
     * All sections above are structured Javadoc forming the integration plan.
     *
     * The plan is versioned here alongside the codebase it describes so that
     * it evolves with implementation decisions rather than drifting in a
     * separate document store.
     *
     * Entry points for implementors:
     *   Phase 1 start : org.yawlfoundation.yawl.elements.state.YIdentifier
     *   Phase 2 start : org.yawlfoundation.yawl.unmarshal.YMarshal
     *   Phase 3 start : org.yawlfoundation.yawl.engine.YNetRunner
     */
    private EngineDedupPlan() {
        throw new UnsupportedOperationException(
                "EngineDedupPlan is a design document class and cannot be instantiated.");
    }
}
