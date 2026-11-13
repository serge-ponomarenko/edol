# ADR 0001: Hub Printer Projection and Tenant Bootstrap

**Status:** Accepted. The tenant bootstrap was superseded by
[ADR 0002](0002-secure-multi-tenant-architecture.md).

## Context

Core owns printer provisioning. Hub needs a stable local printer identity for
operational data, including history from the original single-printer schema.

## Decision

Core assigns the printer UUID. Hub uses the same UUID for its tenant-owned
projection and per-printer state, and keeps history after Core decommissions
a printer. Printer-specific routes carry the explicit UUID.

Adopt expand, backfill and contract in that order. Backfill uses a real
synchronized Core UUID and stops on ambiguous ownership. Contract only after
verification establishes all mandatory printer relationships.

## Rationale and consequences

A separate Hub UUID would require permanent identity translation. Hardcoding
a printer UUID would break clean installs. Application-assisted backfill can
establish the Core catalog facts that database-only migrations cannot infer.
Hub can serve existing projections while Core is unavailable and retry
synchronization. Secure tenant selection now follows ADR 0002.
