# Architecture Decision Records

Create an ADR only for an explicitly established or newly accepted architectural decision with durable consequences. Do not use ADRs for implementation notes, inferred intent, or work in progress.

Use sequential filenames: `NNNN-short-decision-title.md`.

Each ADR contains:

1. **Context** — the decision problem and constraints.
2. **Decision** — the accepted choice.
3. **Rationale** — why it was chosen.
4. **Alternatives** — material options considered.
5. **Consequences** — important benefits, costs, and follow-up obligations.

Keep the record concise and update its status only when a later explicit decision supersedes it.

## Index

- `0001-hub-printer-projection-and-tenant-bootstrap.md` — Core-owned printer UUIDs, Hub tenant projections, default-tenant bootstrap, and expand/backfill/contract rollout.
- `0002-secure-multi-tenant-architecture.md` — Accepted tenant ownership, authentication, persistence isolation, service trust, MQTT, and device-enrollment architecture.
