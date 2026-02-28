# Documentation Contribution Guide

How to contribute documentation to YAWL v6.0.0.

---

## 🚀 Quick Start

### I want to add a new doc. Where do I start?

1. **Identify the quadrant**
   - **Tutorial**: "I want to teach someone how to do X"
   - **How-To**: "I want to help someone accomplish task Y"
   - **Reference**: "I want to document API Z"
   - **Explanation**: "I want to explain concept W"
   - See `DOCUMENTATION_STANDARDS.md` for details

2. **Check if it already exists**
   - Search `DOCUMENTATION_COMPLETENESS.md` for similar docs
   - Browse `docs/diataxis/INDEX.md`
   - Check topic index: `TOPIC_INDEX.md`

3. **Create the doc**
   - Use template for your quadrant (see below)
   - Place in correct directory
   - Test everything
   - Get feedback

4. **Submit for review**
   - Create PR with description
   - Link to related issues
   - Ask for 1-2 reviewers

---

## 📝 Templates by Quadrant

### Tutorial Template

```markdown
# [Topic] Getting Started / Tutorial

**Time**: 30 min | **Level**: Beginner | **Prerequisites**: [List any]

## What You'll Learn
- Concept A
- Concept B
- Concept C

## Prerequisites
- Installed [X]
- Basic knowledge of [Y]

## Step 1: [First Task]
Explain why you're doing this...

[Code example]

Verify it worked:
```bash
[verification command]
```

## Step 2: [Second Task]
[Repeat pattern]

## Step 3: [Third Task]
[Repeat pattern]

## Verify It Works
[How to verify the complete tutorial worked]

## What You Learned
- You learned A
- You learned B
- You can now do C

## Next Steps
- [Try this how-to guide](../how-to/...)
- [Understand the architecture](../explanation/...)
- [Read the reference](../reference/...)

## Troubleshooting
**Problem**: [Common issue]
→ **Solution**: [How to fix]
```

### How-To Guide Template

```markdown
# [Task]: [How to Accomplish It]

**Time**: 15 min | **Level**: Intermediate | **Prerequisites**: [List any]

## Before You Start
- You need [X]
- You understand [Y]
- You've done [Z]

## Problem
[Clear statement of the problem to solve]

## Solution Overview
[1-2 sentences explaining the approach]

## Step-by-Step Instructions

### Step 1: [Do This]
[Instructions]

[Code example if applicable]

### Step 2: [Then This]
[Instructions]

### Step 3: [Finally This]
[Instructions]

## Verify It Works
[How to check that you succeeded]

## Alternatives
**Option A**: [Alternative approach]
- Pros: ...
- Cons: ...

**Option B**: [Another alternative]
- Pros: ...
- Cons: ...

## Troubleshooting

**Issue**: [Problem]
→ **Solution**: [How to fix]

**Issue**: [Another problem]
→ **Solution**: [How to fix]

## Learn More
- [Reference: Full API](../reference/...)
- [Tutorial: Getting started](../tutorials/...)
- [Architecture explanation](../explanation/...)

## Related Guides
- [How to do X](./related-guide.md)
- [How to do Y](./another-guide.md)
```

### Reference Template

```markdown
# [Topic] API / Configuration Reference

**Updated**: [Date] | **Version**: 6.0.0+

## Quick Reference

| Parameter | Type | Default | Required | Description |
|-----------|------|---------|----------|-------------|
| `param1` | string | `"value"` | Yes | What it does |
| `param2` | int | `42` | No | What it does |

## Full Reference

### param1
```
Type: string
Default: "value"
Required: Yes
Since: 6.0.0
```

Description: Detailed explanation of what this parameter does.

**Example**:
```java
engine.setParam1("custom-value");
```

**Valid values**:
- `value1`: Description
- `value2`: Description

**See also**: [Related param](#param2)

### param2
[Repeat pattern]

## Configuration File Example

```yaml
param1: custom-value
param2: 100
```

## Compatibility

| Version | Status |
|---------|--------|
| 6.0.0+ | Supported |
| 5.9.x | Deprecated |
| 5.8.x | Not supported |

## Breaking Changes

### Migrated from v5
If your parameter changed from v5, explain:
- Old name: `oldParam`
- New name: `param1`
- Migration: [How to update]

## Learn More
- [How to configure X](../how-to/...)
- [Understanding X architecture](../explanation/...)
```

### Explanation Template

```markdown
# [Concept]: [What This Is About]

**Updated**: [Date] | **Level**: Intermediate

## Overview

[2-3 paragraphs of context]

## Background / History

[Why this matters, how it evolved]

## Design / Architecture

[How it works, key design decisions]

[ASCII diagram if helpful]

## Design Decisions & Trade-Offs

### Decision 1: [Option A vs Option B]

**We chose**: [Option A] because:
- [Reason 1]
- [Reason 2]

**Trade-offs**:
- Benefit: [B1]
- Cost: [C1]

**Alternative** (Option B):
- Would have [Benefit Y]
- But would cost [Cost Y]

### Decision 2: [Another choice]
[Repeat pattern]

## Why Does It Matter?

[Implications, consequences, when you encounter this]

## Related Concepts
- [Architecture](./related.md)
- [Design pattern](./pattern.md)

## Deeper Dive

[Links to papers, RFCs, external resources]

## Learn More
- [Tutorial: Getting started](../tutorials/...)
- [How-to: Accomplish task](../how-to/...)
- [Reference: Full API](../reference/...)
```

---

## ✅ Contribution Checklist

Before submitting your PR:

- [ ] **Correct quadrant**: Doc fits exactly ONE of Tutorials/How-To/Reference/Explanation
- [ ] **Audience clear**: Who is this for? (users, developers, DevOps, etc)
- [ ] **Complete**: Doesn't leave readers hanging
- [ ] **Examples tested**: All code runs (if included)
- [ ] **Links work**: All relative links verified
- [ ] **Markdown clean**: No formatting issues
- [ ] **Grammar checked**: Spell-checked and grammar-checked
- [ ] **Related docs linked**: Cross-references added
- [ ] **INDEX.md updated**: Entry added to diataxis/INDEX.md
- [ ] **No TODOs**: No placeholder text left
- [ ] **Tone appropriate**: Matches quadrant and audience

---

## 🔗 Linking Your New Doc

After creating a new doc, update these:

1. **docs/diataxis/INDEX.md**
   - Add entry in appropriate quadrant section
   - Link from related sections

2. **Related docs**
   - Add "See also" or "Learn more" links
   - Link back to your new doc

3. **Topic index** (if major doc)
   - Add to `TOPIC_INDEX.md`
   - Link from `USE_CASE_INDEX.md` if applicable

---

## 💡 Tips & Tricks

### Reuse Existing Content
- Don't duplicate information
- Link instead: "For details, see [this reference](./...)"
- Summarize and link to full version

### Use Clear Examples
- Show the exact command to run
- Show expected output
- Include error cases

### Help Users Succeed
- "Next steps" section is crucial
- Link to related docs
- Anticipate questions

### Test Before Submitting
- Run all code examples
- Click all links
- Read it as a user would
- Ask a colleague to read it

---

## 📋 PR Description Template

When submitting a PR with new docs:

```markdown
## Description

This PR adds documentation for [Topic].

**Type**: [Tutorial / How-To / Reference / Explanation]
**Quadrant**: [Tutorials / How-To / Reference / Explanation]
**Audience**: [Users / Developers / DevOps / Architects]

## What's Included

- `[Filename]`: [Brief description]
- Updated `INDEX.md` with new entry
- [Other related changes]

## Links to Related Issues

Closes #[issue number] (if applicable)
Related to #[other issues]

## Checklist

- [x] Follows DOCUMENTATION_STANDARDS.md
- [x] All code examples tested
- [x] Links verified
- [x] INDEX.md updated
- [x] Related docs linked
- [x] No TODO/FIXME placeholders
```

---

## 🎯 Common Contributions

### Contributing a Tutorial

1. Pick a feature or workflow
2. Write step-by-step instructions
3. Include complete code examples
4. Test the entire tutorial end-to-end
5. Submit with clear "What you'll learn"

**Good tutorial topics**:
- Getting started with [module]
- Build and deploy [common pattern]
- Integrate [external system]

### Contributing a How-To Guide

1. Identify a common task
2. Research current support questions
3. Write step-by-step solution
4. Include alternatives
5. Add troubleshooting

**Good how-to topics**:
- Set up [service]
- Optimize [component]
- Migrate [system]

### Contributing Reference

1. Identify missing API/config documentation
2. Extract from code comments and tests
3. Organize for lookup (not reading)
4. Include examples
5. Add compatibility info

**Good reference topics**:
- API documentation
- Configuration options
- CLI command reference
- Database schema

### Contributing Explanation

1. Identify a concept users struggle with
2. Research the design decisions
3. Explain the "why" not just "how"
4. Discuss trade-offs
5. Link to related concepts

**Good explanation topics**:
- Architecture patterns
- Design decisions (ADRs)
- Performance tuning philosophy
- Integration strategies

---

## 🤔 FAQ

**Q: Can I write in a different style?**
A: Follow DOCUMENTATION_STANDARDS.md for consistency. If you disagree with a standard, open an issue first.

**Q: How long should my doc be?**
A: Tutorials: 15-45 min, How-To: 5-20 min, Reference: varies, Explanation: 10-30 min of reading. Shorter is better.

**Q: What if the feature isn't released yet?**
A: Include "Unreleased" label. Link from docs/UNRELEASED.md or clearly mark as preview.

**Q: Can I update someone else's doc?**
A: Yes! Create PR, mention them in the review. Document is community-owned.

**Q: What if I disagree with the Diataxis framework?**
A: It's a proven framework used by Django, Kubernetes, and others. Try it before criticizing. Most confusion comes from mixing quadrants.

---

## 📞 Getting Help

- **Questions about standards?** → See `DOCUMENTATION_STANDARDS.md`
- **How to maintain docs?** → See `DOCUMENTATION_MAINTENANCE_GUIDE.md`
- **Need example?** → Browse similar docs in `docs/`
- **Stuck?** → Open an issue with `[docs]` prefix

---

*Last updated: 2026-02-28*
*YAWL v6.0.0 Documentation Contribution Guide*
