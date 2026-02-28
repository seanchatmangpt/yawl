# YAWL Documentation Standards & Guidelines

This document defines the standards and best practices for maintaining YAWL v6.0.0 documentation.

---

## 📋 Diataxis Framework (4 Quadrants)

Every document **must** fit exactly one quadrant. Confusing quadrants is the primary reason documentation frustrates users.

### 1. **Tutorials** — Learning by Doing
- **Purpose**: Teach users by having them complete a working example
- **User state**: "I want to learn"
- **Structure**: Introduction → Goal → Step-by-step instructions → Verification → What you learned
- **Length**: 15-45 minutes
- **Tone**: Encouraging, hands-on, assume no prior knowledge
- **DO**: Include complete, runnable code | Teach fundamental concepts | Show "why"
- **DON'T**: Skip steps | Use jargon without explanation | Create reference material

**Location**: `docs/tutorials/`

**Examples**:
- `tutorials/01-build-yawl.md` (Build from source)
- `tutorials/03-run-your-first-workflow.md` (Execute a workflow)

---

### 2. **How-To Guides** — Accomplishing a Specific Task
- **Purpose**: Help users accomplish a specific goal
- **User state**: "I know what I want to do, show me how"
- **Structure**: Problem statement → Solution → Step-by-step → Variations/alternatives
- **Length**: 5-20 minutes
- **Tone**: Practical, action-oriented, assume basic knowledge
- **DO**: Be problem-oriented | Show multiple approaches | Link to reference for details
- **DON'T**: Teach concepts | Assume extensive knowledge | Include unnecessary theory

**Location**: `docs/how-to/`

**Examples**:
- `how-to/yawl-authentication-setup.md` (Set up JWT)
- `how-to/deployment/stateless-deployment.md` (Deploy to production)

---

### 3. **Reference** — Accurate Technical Facts
- **Purpose**: Be a reliable source for looking up information
- **User state**: "I need to know exactly what this parameter does"
- **Structure**: Organized for lookup (not reading top-to-bottom) | Comprehensive | Precise
- **Length**: Varies (API docs can be long)
- **Tone**: Factual, precise, no fluff
- **DO**: Be complete | Include defaults and constraints | Use tables and lists
- **DON'T**: Teach concepts | Explain "why" | Be verbose

**Location**: `docs/reference/`

**Examples**:
- `reference/yawl-engine-api.md` (API reference)
- `reference/yawl-engine-config.md` (Configuration reference)

---

### 4. **Explanation** — Understanding Concepts
- **Purpose**: Help users understand why things are the way they are
- **User state**: "I want to understand the design and trade-offs"
- **Structure**: Context → Background → Design decisions → Trade-offs → Implications
- **Length**: 10-30 minutes
- **Tone**: Educational, thoughtful, balanced
- **DO**: Discuss alternatives and trade-offs | Explain design decisions | Provide context
- **DON'T**: Be prescriptive | Teach implementation details | Include step-by-step instructions

**Location**: `docs/explanation/`

**Examples**:
- `explanation/yawl-engine-architecture.md` (Engine design)
- `explanation/decisions/ADR-004-spring-boot-34-java-25.md` (Architecture decision)

---

## 📐 Content Standards

### Markdown Formatting
- Use GitHub-flavored markdown
- Headings: H1 (`#`) for title, H2+ (`##`) for sections
- **Bold** for UI elements and important terms
- `code` for identifiers, commands, parameters
- Code blocks with language specified (```java, ```bash, etc)
- Tables for structured data (use `|---|` separator)

### Structure
- Start with a clear title (H1)
- Follow with 1-2 sentence summary
- Use headings to organize (H2 for major sections, H3 for subsections)
- Break up long text with visuals, lists, tables
- End with "Learn more" or "Next steps" links when appropriate

### Code Examples
- **Always test before including** (no untested code)
- Include copy-paste ready examples
- Add comments explaining non-obvious parts
- Show realistic (not toy) examples
- Link to full working examples when possible

### Tone
- Use "you" when addressing readers
- Be conversational but professional
- Avoid jargon; define technical terms on first use
- Use inclusive language
- Avoid gendered pronouns (use "they/them" or rephrase)

---

## 🏷️ Metadata

Add metadata to the top of each file (optional but recommended):

```markdown
# Document Title

**Quadrant**: [Tutorials|How-To|Reference|Explanation]
**Audience**: [Users|Developers|DevOps|Architects]
**Level**: [Beginner|Intermediate|Advanced]
**Time**: [5 min|15 min|30 min|1 hour]
**Updated**: [Date]
```

Example:
```markdown
# Write a YAWL Specification

**Quadrant**: Tutorials
**Audience**: Workflow designers, developers
**Level**: Beginner
**Time**: 45 min
**Updated**: 2026-02-28
```

---

## 📁 File Organization

### Tutorials
```
docs/tutorials/
├── 01-build-yawl.md
├── 02-understand-the-build.md
├── 03-run-your-first-workflow.md
├── yawl-engine-getting-started.md
└── ...
```
**Naming**: `{number}-{topic}.md` or `{module}-getting-started.md`

### How-To Guides
```
docs/how-to/
├── deployment/
│   ├── stateless-deployment.md
│   ├── webapps-deployment.md
│   └── kubernetes-deployment.md
├── cicd/
│   ├── build.md
│   ├── testing.md
│   └── deployment.md
├── yawl-authentication-setup.md
└── ...
```
**Naming**: `{topic}-{subtopic}.md` or `{topic}.md`

### Reference
```
docs/reference/
├── yawl-engine-api.md
├── yawl-engine-config.md
├── yawl-authentication-config.md
├── workflow-patterns.md
└── ...
```
**Naming**: `{topic}-{type}.md` (e.g., `{module}-api.md`, `{module}-config.md`)

### Explanation
```
docs/explanation/
├── yawl-engine-architecture.md
├── case-lifecycle.md
├── decisions/
│   ├── ADR-001-dual-engine-architecture.md
│   ├── ADR-002-singleton-vs-instance-yengine.md
│   └── ...
└── ...
```
**Naming**: `{topic}-{aspect}.md` or `ADR-{number}-{title}.md`

---

## 🔗 Linking

### Internal Links
- Use relative paths: `[text](../path/to/file.md)` (not absolute URLs)
- Link to sections: `[text](./file.md#section-name)` (use kebab-case)
- Link from how-to to reference: See `../reference/...`
- Link from tutorial to how-to: Next, try `../how-to/...`

### Cross-Quadrant Navigation
- Tutorials → How-To: "Ready to tackle a real-world problem? Try [this guide](../how-to/...)"
- How-To → Reference: "For details on configuration, see [API Reference](../reference/...)"
- Reference → Explanation: "Want to understand the design? See [Architecture](../explanation/...)"
- Explanation → Tutorial: "Ready to try it? Start with [this tutorial](../tutorials/...)"

### External Links
- Use full URLs: `[text](https://example.com)`
- Prefer stable URLs (avoid date-based URLs that rot)
- Add context: What is this link about?

---

## ✅ Quality Checklist

Before committing any documentation:

- [ ] **Quadrant**: Document fits exactly one quadrant (not blended)
- [ ] **Audience**: Clear who this is for (users, developers, DevOps, etc)
- [ ] **Structure**: Has clear organization with descriptive headings
- [ ] **Accuracy**: All information is current and correct
- [ ] **Examples**: Code examples are tested and runnable
- [ ] **Links**: All internal links work (relative paths)
- [ ] **Language**: Clear, concise, grammatically correct
- [ ] **Completeness**: Doesn't leave readers hanging
- [ ] **Navigation**: Links to related docs and next steps
- [ ] **Tone**: Appropriate for the audience and quadrant
- [ ] **No TODO/FIXME**: All placeholders completed
- [ ] **Metadata**: Includes quadrant, audience, level, time (if standard)

---

## 📊 4-Quadrant Coverage Goal

For each module, aim for:
- ✅ **At least 1 Tutorial** (getting started)
- ✅ **2-3 How-To Guides** (common tasks)
- ✅ **1 Reference Doc** (API or configuration)
- ✅ **1 Explanation** (architecture or concepts)

**Total**: ~5-7 documents per module

**Current coverage**: See `DOCUMENTATION_COMPLETENESS.md`

---

## 🔄 Maintenance Cycle

### Quarterly Review
1. Run `bash scripts/dx.sh` to verify docs still work
2. Check links (broken link checker)
3. Update version numbers and compatibility
4. Review for outdated content

### When Making Code Changes
1. Update **Reference** docs immediately (APIs, configuration)
2. Update **How-To** guides if procedures changed
3. Add **Tutorial** if new major feature
4. Update **Explanation** if design changed
5. Create ADR if architectural decision made

### When Adding Features
1. Create Tutorial (getting started)
2. Create How-To (common use case)
3. Create Reference (full API/config)
4. Add to Explanation (why/when to use)

---

## 🎯 Best Practices

1. **One document, one purpose** — Don't mix quadrants
2. **Assume minimal knowledge** — Users are busy and impatient
3. **Be specific** — "Run this exact command" beats "run something"
4. **Link relentlessly** — Help readers discover related docs
5. **Use examples** — Working code teaches better than explanation
6. **Test everything** — Especially code examples and procedures
7. **Update regularly** — Outdated docs are worse than no docs
8. **Listen to users** — Fix docs based on support questions
9. **Keep it simple** — Simplify ruthlessly
10. **Be consistent** — Use same terminology, formatting, style

---

## 📞 Questions?

See `DOCUMENTATION_MAINTENANCE_GUIDE.md` for day-to-day maintenance.
See `DOCUMENTATION_CONTRIBUTION_GUIDE.md` for adding new docs.

---

*Last updated: 2026-02-28*
*YAWL v6.0.0 Documentation Standards*
