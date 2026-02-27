# .claude/decisions — Release Date Change Control (PY-5)

Poka-yoke against **FM9** (GA date accelerated without change-control, RPN=245).

## Convention

Any release date compression **> 7 days** from the DoD §12.5 target requires a dated
decision record in this directory before the release tag may be created.

**Filename format**: `YYYY-MM-DD-<release>-timeline-<reason>.md`

**Example**: `.claude/decisions/2026-02-25-ga-timeline-compression.md`

## Required Fields

```markdown
# Release Date Change Decision — <date>

| Field | Value |
|-------|-------|
| Release | v6.0.0-GA |
| DoD target | 2026-03-21 |
| New target | 2026-02-25 |
| Compression | 24 days |
| Justification | <concrete reason> |
| Approver | <human name and role> |
| Risks accepted | <list of deferred gates or requirements> |
| Mitigation | <how deferred items will be addressed post-release> |
```

## Gate Enforcement

`scripts/validation/validate-release.sh` will be updated in a future iteration to assert
that a decision record exists in this directory whenever the actual tag date precedes the
DoD target by more than 7 days.

Until automated enforcement is in place, this directory serves as the **manual audit trail**.
Any release made without a corresponding decision record here is a process violation (FM9).

## Existing Decisions

| Date | Release | Compression | Approver |
|------|---------|-------------|---------|
| _(none yet — add entries here as decisions are made)_ | | | |
