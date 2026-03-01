# YAWL Actor Model Design — Complete Index

**Version**: 6.0.0-GA
**Date**: 2026-02-28
**Status**: COMPLETE — Ready for Implementation

---

## Quick Navigation

### For Different Audiences

**Architects & Decision Makers**
→ Start with: [`ACTOR-MODEL-DESIGN-SUMMARY.md`](#actor-model-design-summary)
- Executive overview
- Design decisions with rationale
- Risk analysis
- Timeline estimates

**Implementation Engineers**
→ Start with: [`ACTOR-INTERFACES.md`](#actor-interfaces) + [`ACTOR-MIGRATION-CHECKLIST.md`](#actor-migration-checklist)
- Complete interface designs
- Method signatures
- Step-by-step migration plan
- Code examples

**Reviewers & QA**
→ Start with: [`ACTOR-INTERFACE-SPECS.md`](#actor-interface-specs)
- Detailed specifications
- Thread-safety guarantees
- Performance targets
- Error handling behavior

**Legacy Code Maintainers**
→ Start with: [`ACTOR-MIGRATION-CHECKLIST.md`](#actor-migration-checklist)
- Phase-by-phase migration
- All files affected
- Before/after code patterns
- Backward compatibility notes

---

## Document Collection

### ACTOR-MODEL-DESIGN-SUMMARY
**File**: `.claude/ACTOR-MODEL-DESIGN-SUMMARY.md`
**Length**: 8 KB, ~180 lines
**Audience**: Architecture team, decision makers

**Contains**:
- Executive summary of 4 interfaces (ActorRef, Msg, ActorRuntime, Supervisor)
- 6 key design decisions with rationale
- Migration path from Agent → ActorRef
- Performance characteristics at scale (10M agents)
- Implementation timeline (16 hours, 3 engineers)
- Delivered artifacts summary
- Risk analysis & mitigation

**Key Sections**:
- What Was Designed (quick overview)
- Key Design Decisions (why, not how)
- Performance Characteristics (throughput, latency, memory)
- Implementation Timeline (phase breakdown)
- Next Steps (immediate, short-term, final)

**Start here if**: You need the executive summary or want to understand design trade-offs.

---

### ACTOR-INTERFACES
**File**: `.claude/ACTOR-INTERFACES.md`
**Length**: 120 KB, ~1300 lines
**Audience**: All developers, comprehensive reference

**Contains**:
- Complete interface design for 4 core classes
- Design principles (immutability, zero-overhead, lock-free)
- Byte accounting & memory efficiency analysis
- Hot path optimization strategies
- Message hierarchy design (sealed Msg)
- Supervisor architecture (OTP one_for_one)
- Migration contract (phases 1-5)
- Code examples (ping-pong, request-reply, supervision)
- Implementation checklist (7 phases)

**Key Sections**:
1. Design Principles (5 core principles)
2. Core Interfaces (ActorRef, Msg, ActorRuntime, Supervisor)
3. Byte Accounting (memory layout, scaling to 10M)
4. Hot Path Optimization (tell, ask, dispatch performance)
5. Message Hierarchy (sealed interface design)
6. Supervisor Architecture (one_for_one strategy)
7. Migration Contract (from Agent → ActorRef)
8. Code Examples (3 complete examples with explanation)

**Start here if**: You need the complete design document with full rationale and examples.

---

### ACTOR-MIGRATION-CHECKLIST
**File**: `.claude/ACTOR-MIGRATION-CHECKLIST.md`
**Length**: 80 KB, ~650 lines
**Audience**: Implementation engineers, QA, project managers

**Contains**:
- 9-phase migration plan (from internal Agent to public ActorRef)
- All files to modify (with exact locations)
- Before/after code patterns
- Data structures to add (restart counters, behavior registry)
- Test fixtures needed
- Integration test files
- Performance benchmarks to run
- Backward compatibility options
- Documentation to create
- Timeline estimates (16 hours total)

**Key Sections**:
1. Phase 1: Class Rename & Interface (1h)
2. Phase 2: Update All Callers (3h)
3. Phase 3: Complete Supervisor (2h)
4. Phase 4: Update Tests (2h)
5. Phase 5: Code Style (1h)
6. Phase 6: Integration Tests (2h)
7. Phase 7: Performance Testing (2h)
8. Phase 8: Backward Compatibility (1h)
9. Phase 9: Documentation (2h)

**Checkboxes**: Every task has a [ ] checkbox for tracking progress.

**Start here if**: You're implementing the changes or managing the project.

---

### ACTOR-INTERFACE-SPECS
**File**: `.claude/ACTOR-INTERFACE-SPECS.md`
**Length**: 90 KB, ~750 lines
**Audience**: Detailed specification readers, code reviewers, QA

**Contains**:
- Detailed method signatures for all public APIs
- Thread-safety guarantees per method
- Performance characteristics (latency, throughput)
- Error handling behavior (what throws, what's no-op)
- Memory layout specifications
- Data structure choices explained
- Contention analysis at scale
- Error cases matrix
- Quick reference table

**Key Sections**:
1. ActorRef Specification (8 methods with full details)
2. Msg Specification (5 record types)
3. ActorRuntime Specification (6 methods)
4. Supervisor Specification (1 enum, 4 methods)
5. Internal Classes (Agent, VirtualThreadRuntime)
6. Thread Safety & Concurrency Matrix
7. Error Handling Behavior
8. Memory & Performance Targets

**Tables**:
- Thread-Safety Guarantee Matrix
- Data Structure Properties
- Error Cases & Behavior
- Performance Targets vs Expected

**Start here if**: You need detailed specifications for code review or implementation details.

---

## Cross-References

### ActorRef Design
- **Overview**: ACTOR-MODEL-DESIGN-SUMMARY → "What Was Designed" section
- **Full Design**: ACTOR-INTERFACES → "ActorRef Specification" section
- **Detailed Specs**: ACTOR-INTERFACE-SPECS → "ActorRef Specification" section
- **Migration**: ACTOR-MIGRATION-CHECKLIST → Phase 2 (Update All Callers)

### Msg Hierarchy
- **Overview**: ACTOR-MODEL-DESIGN-SUMMARY → "What Was Designed" section
- **Full Design**: ACTOR-INTERFACES → "Message Hierarchy Design" section
- **Detailed Specs**: ACTOR-INTERFACE-SPECS → "Msg Specification" section
- **Examples**: ACTOR-INTERFACES → "Code Examples" section

### Supervisor Architecture
- **Overview**: ACTOR-MODEL-DESIGN-SUMMARY → "What Was Designed" section
- **Full Design**: ACTOR-INTERFACES → "Supervisor Architecture" section
- **Detailed Specs**: ACTOR-INTERFACE-SPECS → "Supervisor Specification" section
- **Implementation**: ACTOR-MIGRATION-CHECKLIST → Phase 3 (Complete Supervisor)

### Migration Path
- **High-level**: ACTOR-MODEL-DESIGN-SUMMARY → "Migration Path" section
- **Detailed**: ACTOR-INTERFACES → "Migration Contract" section
- **Checklist**: ACTOR-MIGRATION-CHECKLIST → All 9 phases

---

## Design Decision Matrix

| Decision | Summary | Document | Full Rationale |
|----------|---------|----------|---|
| ActorRef as value type | Wrap Agent in ActorRef (20 bytes) | SUMMARY | INTERFACES → Design Principles |
| Sealed Msg | Sealed interface with 5 records | SUMMARY | INTERFACES → Message Hierarchy |
| One VirtualThread per actor | Not thread pool with work-stealing | SUMMARY | INTERFACES → Supervisor Architecture |
| Supervisor wraps Runtime | Not separate orchestrator | SUMMARY | INTERFACES → Supervisor Architecture |
| 4-byte int ID | Not 16-byte UUID | SUMMARY | INTERFACES → Byte Accounting |
| LinkedTransferQueue mailbox | Not bounded array | SUMMARY | INTERFACES → Hot Path Optimization |

---

## File Locations (Implementation)

### Existing Implementation (Already in Codebase)
```
yawl-engine/src/main/java/org/yawlfoundation/yawl/engine/agent/core/
  ✓ ActorRef.java (128 lines, complete)
  ✓ Msg.java (125 lines, complete)
  ✓ ActorRuntime.java (interface, 69 lines, complete)
  ✓ Agent.java (internal, 43 lines, unchanged)
  ✓ Supervisor.java (161 lines, skeleton with TODOs)
  ✓ RequestReply.java (helper, 121 lines, complete)
```

### To Be Created
```
yawl-engine/src/main/java/org/yawlfoundation/yawl/engine/agent/core/
  [ ] VirtualThreadRuntime.java (rename from Runtime.java)
  [ ] package-info.java (package documentation)

yawl-engine/src/test/java/org/yawlfoundation/yawl/engine/agent/core/
  [ ] VirtualThreadRuntimeTest.java
  [ ] ActorRefTest.java
  [ ] MsgTest.java
  [ ] SupervisorTest.java
  [ ] RequestReplyTest.java
  [ ] ... (update existing tests)

docs/how-to/
  [ ] migrate-actor-model.md (migration guide)
```

### To Be Modified
```
yawl-engine/src/main/java/org/yawlfoundation/yawl/engine/agent/
  [ ] YawlAgentEngine.java (if uses Runtime.spawn)
  [ ] AgentEngineService.java (if uses Runtime.spawn)
  [ ] AgentRegistry.java (if uses Runtime.spawn)
  [ ] ... (estimated 5-10 files total)
```

---

## How to Use These Documents

### Scenario 1: Architecture Review Meeting
1. **Start**: ACTOR-MODEL-DESIGN-SUMMARY (10 min)
2. **Deep dive**: ACTOR-INTERFACES → Design Principles section (10 min)
3. **Q&A**: Reference specific decisions in SUMMARY or INTERFACES

### Scenario 2: Starting Implementation
1. **Understand scope**: ACTOR-MIGRATION-CHECKLIST → Phases 1-2 (15 min)
2. **Get details**: ACTOR-INTERFACES → Migration Contract section (15 min)
3. **Start coding**: Follow checklist phase by phase

### Scenario 3: Code Review
1. **Check signatures**: ACTOR-INTERFACE-SPECS → Method specifications (5 min)
2. **Verify thread-safety**: ACTOR-INTERFACE-SPECS → Thread Safety Matrix (5 min)
3. **Review performance**: ACTOR-INTERFACE-SPECS → Performance Targets section (5 min)

### Scenario 4: Updating Tests
1. **Test requirements**: ACTOR-MIGRATION-CHECKLIST → Phase 4 (10 min)
2. **Success criteria**: ACTOR-INTERFACES → Implementation Checklist (5 min)
3. **Performance benchmarks**: ACTOR-MIGRATION-CHECKLIST → Phase 7 (10 min)

### Scenario 5: Documenting for Users
1. **Overview**: ACTOR-MODEL-DESIGN-SUMMARY → What Was Designed (5 min)
2. **Examples**: ACTOR-INTERFACES → Code Examples section (10 min)
3. **Migration guide**: ACTOR-MIGRATION-CHECKLIST → Summary of Changes (5 min)

---

## Key Statistics

### Lines of Documentation
- ACTOR-INTERFACES.md: ~1,300 lines (120 KB)
- ACTOR-MIGRATION-CHECKLIST.md: ~650 lines (80 KB)
- ACTOR-INTERFACE-SPECS.md: ~750 lines (90 KB)
- ACTOR-MODEL-DESIGN-SUMMARY.md: ~180 lines (8 KB)
- **Total**: ~2,880 lines (298 KB)

### Lines of Code (Existing + To Implement)
- ActorRef.java: 128 lines ✓
- Msg.java: 125 lines ✓
- ActorRuntime.java: 69 lines ✓ (interface)
- Supervisor.java: 161 lines (partial, needs completion)
- Agent.java: 43 lines ✓ (internal)
- VirtualThreadRuntime: ~150 lines (to implement)
- package-info.java: ~50 lines (to create)
- **Total existing**: 526 lines, **New code**: ~200 lines, **Total**: ~726 lines

### Implementation Effort
- Phase 1 (Rename & Interface): 1 hour
- Phase 2 (Update Callers): 3 hours
- Phase 3 (Complete Supervisor): 2 hours
- Phase 4-9 (Tests, Docs, Review): 10 hours
- **Total**: 16 hours (2 days with 3 engineers)

---

## Success Metrics

### Build Success
- [ ] `mvn clean verify -pl yawl-engine` passes
- [ ] No compilation warnings
- [ ] All tests pass (unit, integration, e2e)

### Performance Targets
- [ ] tell() throughput: > 1M msgs/sec
- [ ] ask() latency: < 10 µs (median)
- [ ] spawn() throughput: > 100K actors/sec
- [ ] Memory per idle actor: < 200 bytes

### Documentation
- [ ] All public methods have JavaDoc
- [ ] Migration guide published
- [ ] Examples included in code comments
- [ ] Architecture decisions documented

### Code Quality
- [ ] Code review approved
- [ ] Spotbugs/PMD clean
- [ ] No security violations (OWASP)
- [ ] Performance benchmarks green

---

## Related Documentation

### Internal References
- `.claude/rules/java25/modern-java.md` — Java 25 conventions (sealed, records, virtual threads)
- `.claude/HYPER_STANDARDS.md` — Code quality standards
- `.claude/rules/TEAMS-GUIDE.md` — Team coordination (if using teams)

### External References
- OTP Design Principles: https://www.erlang.org/doc/design_principles/des_princ.html
- Java Sealed Classes (JEP 409): https://openjdk.org/jeps/409
- Java Pattern Matching (JEP 440): https://openjdk.org/jeps/440
- Virtual Threads (JEP 444): https://openjdk.org/jeps/444
- ScopedValue (JEP 446): https://openjdk.org/jeps/446

---

## Questions & Answers

### Q: Why not expose Agent directly?
A: Agent is an internal implementation detail (24 bytes). ActorRef is the public contract (20 bytes, immutable value type). See INTERFACES → "ActorRef Specification" for full rationale.

### Q: Can I swap ActorRuntime implementations?
A: Yes, that's the point. ActorRuntime is an interface. You can implement VirtualThreadRuntime, ReactiveRuntime, TestRuntime, etc. See INTERFACES → "ActorRuntime Specification".

### Q: How do I send a message to an actor?
A: Use `ref.tell(msg)` for fire-and-forget, or `ref.ask(query, timeout)` for request-reply. See INTERFACES → "Code Examples" for full examples.

### Q: What if my actor crashes?
A: Supervisor catches the exception and restarts the actor (up to maxRestarts in restartWindow). See INTERFACES → "Supervisor Architecture".

### Q: How many actors can I create?
A: Limited by memory and JVM heap. With -XX:+UseCompactObjectHeaders, expect ~1.5 GB for 10M agents. See INTERFACES → "Byte Accounting".

### Q: Will tell() block my thread?
A: No, tell() is fire-and-forget and non-blocking. It returns immediately. See INTERFACE-SPECS → "ActorRef.tell() Specification".

### Q: How do I wait for a reply?
A: Use ask() which returns a CompletableFuture. The CF completes when reply arrives or timeout expires. See INTERFACES → "Code Examples" section 2.

### Q: What happens to messages for dead actors?
A: By default, silently dropped (no-op). Register a dead letter handler via Supervisor.registerDeadLetterHandler() to capture them. See INTERFACES → "Supervisor Architecture".

---

## Change Log

| Version | Date | Status | Changes |
|---------|------|--------|---------|
| 6.0.0-GA | 2026-02-28 | COMPLETE | Initial design, 4 interfaces, migration plan |

---

## Document Maintenance

**Last Updated**: 2026-02-28
**Next Review**: After implementation phase (2026-03-10)
**Owner**: YAWL Architecture Team (Claude Code)

**To Update**:
1. Modify relevant .md file
2. Update this index (add changelog entry)
3. Commit all changes together
4. Tag release: `actor-model-v6.0.0`

---

## Summary

This index provides complete guidance for the YAWL Actor Model redesign (v6.0.0):

- **4 core interfaces**: ActorRef, Msg, ActorRuntime, Supervisor
- **3 design documents**: Summary, Detailed Design, Specifications
- **1 implementation checklist**: 9 phases, 16 hours, 3 engineers
- **~3000 lines of documentation**: Covering every detail
- **Ready for immediate implementation**: All design decisions made

**Next step**: Begin Phase 1 (Rename Runtime → VirtualThreadRuntime)

---

**Generated**: 2026-02-28
**Status**: COMPLETE ✓
**Ready for Implementation**: YES ✓
