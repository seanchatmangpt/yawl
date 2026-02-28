# Documentation Maintenance Guide

How to keep YAWL documentation accurate, current, and useful.

---

## 📋 Daily/Weekly Tasks

### Monitor Support Questions
- **Where**: GitHub Issues, Discussions, Stack Overflow
- **Action**: If users ask the same question, update the relevant doc
- **Which doc?**
  - Repeated question = missing tutorial or how-to
  - Confusion about behavior = missing reference doc
  - "Why?" questions = missing explanation doc

### Check Broken Links
- **Tool**: `bash scripts/docs-link-check.sh` (if available)
- **Action**: Fix broken links immediately
- **Prevention**: Use relative links (`../path/file.md`)

### Verify Code Examples
- **When**: Before each release
- **Action**: Run all code examples to ensure they still work
- **Track**: Add comment with last test date

---

## 🔄 Monthly Tasks

### Update Version Numbers
- Search for old version numbers (e.g., v5.9.0)
- Replace with current version (e.g., v6.0.0)
- Update release dates
- Verify compatibility statements

### Review Recent Changes
- Check git log for recent code changes
- Update affected documentation
- Add notes about breaking changes

### Check Link Rot
- Verify external links still work
- Replace dead links
- Update outdated references

---

## 📅 Quarterly Tasks

### Comprehensive Review
1. **Coverage**: Run `check-4-quadrant-coverage.sh` (if available)
   - Do all 22 modules have tutorials, how-tos, reference, explanation?
   - Are critical features covered?
   - What's missing?

2. **Accuracy**: For each module
   - Sample 3-5 docs
   - Verify they match current code behavior
   - Run code examples
   - Check links

3. **Completeness**:
   - Check for TODO/FIXME comments
   - Verify all procedures still work
   - Update any deprecated patterns

### Update Module Health Dashboard
See `MODULE_HEALTH_DASHBOARD.md`
- Update test coverage percentages
- Update maturity levels
- Mark breaking changes

### Update Documentation Completeness
See `DOCUMENTATION_COMPLETENESS.md`
- Count docs per module
- Verify 4-quadrant coverage
- Identify and prioritize gaps

---

## 🔧 When Code Changes (Reactive Maintenance)

### New Feature Added
```
1. Check: Does existing How-To cover this?
   → YES: Update it
   → NO: Create new How-To

2. Check: Does Reference doc exist?
   → YES: Update with new parameters
   → NO: Create Reference

3. Check: Should Tutorial be updated?
   → YES if feature is fundamental
   → Add it as step in existing tutorial

4. Create ADR if architectural decision was made
```

### Bug Fixed
```
1. If workaround was documented: Remove workaround docs
2. If root cause was subtle: Add explanation doc
3. If users hit it: Update troubleshooting guide
```

### API Changed
```
1. Update Reference docs IMMEDIATELY
2. Update How-To guides that use old API
3. Create Migration guide (old → new)
4. Mark old API as deprecated in explanation
```

### Performance Regression Fixed
```
1. Update Performance Matrix
2. Update Scaling Decisions guide
3. Update any benchmarking tutorials
```

---

## 📊 Quarterly Metrics

Track these quarterly:

| Metric | Target | How to Check |
|--------|--------|-------------|
| **4-Quadrant Coverage** | 100% per module | `DOCUMENTATION_COMPLETENESS.md` |
| **Broken Links** | 0 | `link-checker` tool |
| **Code Example Pass Rate** | 100% | Test all examples |
| **User Questions→Docs** | 90% answers in docs | Support issue tracker |
| **Documentation Recency** | 90% updated in last 6mo | Git blame |
| **Module Health** | ≥80% average | `MODULE_HEALTH_DASHBOARD.md` |

---

## 🎯 Gap Closing Priority

When you find a gap, prioritize by:

1. **P0 (Critical)** — Affects 50%+ of users
   - Missing tutorial for core feature
   - Broken reference documentation
   - Migration guide missing
   - Fix within 1 week

2. **P1 (High)** — Affects 20-50% of users
   - Missing how-to for common task
   - Outdated explanation
   - Missing troubleshooting info
   - Fix within 1 month

3. **P2 (Medium)** — Affects 5-20% of users
   - Missing niche how-to
   - Incomplete explanation
   - Missing code example
   - Fix within 3 months

4. **P3 (Low)** — Affects <5% of users
   - Nice-to-have improvement
   - Style or clarity enhancement
   - Fix when convenient

---

## 📝 How to Update a Doc

### Quick Update (Fix/Clarify)
```bash
1. cd /home/user/yawl/docs
2. Edit the file: git checkout -b fix/doc-clarity
3. Update the content
4. Test: bash scripts/dx.sh (if code examples)
5. Commit: git add -A && git commit -m "docs: clarify [topic]"
6. Push: git push origin fix/doc-clarity
7. Create PR for review
```

### Major Update (Rewrite/New Section)
```bash
1. Discuss in issue first (avoid duplicate work)
2. Create branch: git checkout -b docs/[topic]
3. Write comprehensive update
4. Test everything
5. Get review from 1-2 people
6. Commit and push
7. Create PR with detailed description
```

### Adding New Doc
```bash
1. Follow DOCUMENTATION_STANDARDS.md
2. Create in correct directory (tutorials, how-to, reference, explanation)
3. Add to INDEX.md
4. Cross-link from related docs
5. Test all links and examples
6. Commit with clear message
7. Create PR
```

---

## 🔍 Verification Checklist

Before committing doc changes:

```bash
# Check links
find docs -name "*.md" -exec grep -l "](.*)" {} \; | \
  while read f; do
    echo "Checking $f..."
    # Manual check or use tool
  done

# Test code examples (if any)
bash scripts/dx.sh

# Check markdown syntax
npx markdownlint docs/**/*.md

# Verify git
git diff docs/ | grep "^+" | grep "TODO\|FIXME" && echo "WARNING: TODOs found"

# Check quadrant consistency
grep -l "^## " docs/tutorials/*.md > /dev/null && echo "✓ Tutorials OK"
```

---

## 🤝 Contributing Guidelines

### Before Writing
1. Check `DOCUMENTATION_STANDARDS.md`
2. Check what quadrant you're writing (Tutorials/How-To/Reference/Explanation)
3. Look at similar docs for style/structure
4. Create issue to coordinate (avoid duplicate work)

### While Writing
1. Follow the template for your quadrant
2. Use clear headings and structure
3. Include working code examples
4. Test everything before committing
5. Link to related docs

### Before Submitting
1. Check all links work (relative paths)
2. Run code examples
3. Verify grammar/spelling
4. Check markdown formatting
5. Read your own doc as a user would
6. Get feedback if major update

---

## 📚 Documentation Tools & Scripts

(These are recommended; create if needed)

```bash
# Check for broken links
docs-link-check.sh

# Verify 4-quadrant coverage
check-4-quadrant-coverage.sh

# Test all code examples
test-code-examples.sh

# Find orphaned docs (not linked from INDEX)
find-orphaned-docs.sh

# Generate documentation metrics
generate-doc-metrics.sh

# Spell check
docs-spellcheck.sh
```

---

## 🚨 When You Find a Problem

### Broken Link
→ Fix immediately (add to next commit)

### Outdated Information
→ Check with code owner, then update

### Missing Documentation
→ File issue, set priority, add to backlog

### Conflicting Information
→ Find source of truth, consolidate

### User Confusion
→ Simplify explanation, add example

---

## 📞 Questions or Issues?

1. **How do I add a new doc?** → See `DOCUMENTATION_CONTRIBUTION_GUIDE.md`
2. **Which quadrant?** → See `DOCUMENTATION_STANDARDS.md`
3. **Is this outdated?** → File an issue
4. **Need help?** → Contact docs maintainer

---

*Last updated: 2026-02-28*
*YAWL v6.0.0 Documentation Maintenance*
