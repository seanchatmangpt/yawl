---
name: cancel-ralph
version: 1.0.0
description: |
  Cancel an active ralph-loop session.
tags:
  - workflow
  - control
---

# Cancel Ralph Loop

Cancels an active ralph-loop session.

## Usage

```
/cancel-ralph
```

## What It Does

1. Clears loop environment markers
2. Updates state file status to "cancelled"
3. Allows session to exit normally

## Related

- `/ralph-loop` - Start an iterative loop
