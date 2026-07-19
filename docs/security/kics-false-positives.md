# KICS scan triage: false positives and accepted tradeoffs

Findings from KICS scans of this repository, triaged so future scans and
reviewers don't re-litigate the same ground. Each finding class below is
either (a) a **false positive** confirmed by direct testing, (b) an
**accepted tradeoff** required for the component to do its job, or
(c) a **known, consciously accepted item**.

## Current state (2026-07-17, course scanner)

| Critical | High | Medium | Low | Info | Total |
|---|---|---|---|---|---|
| 0 | 26 | 59 | 36 | 14 | 135 |

**Verdict:** zero critical; every high-severity finding is triaged below;
no credential is committed to the repository (corroborated by gitleaks —
see [Cross-checks](#cross-checks)).

## History

| Scan | Total | High | Medium | Low | Info |
|---|---|---|---|---|---|
| Baseline | 215 | 33 | 117 | 49 | 16 |
| After first fix pass | 202 | 30 | 113 | 43 | 16 |
| After securityContext/probes pass | 144 | 29 | 61 | 40 | 14 |
| After compose.azure.yml + prometheus fixes | 107 | 23 | 34 | 34 | 16 |
| Re-scan, course scanner (2026-07-17) | 135 | 26 | 59 | 36 | 14 |

The reductions came from three fix passes (securityContexts, probes,
capability minimization, credential injection). The final rise from 107 to
135 has one main cause: [the compose.azure.yml revert](#the-composeazureyml-revert).

## High (26)

### 21× `Passwords And Secrets - Generic Password` — false positive, proven

The rule matches **key names** (`password:`, `*_PASSWORD:`), never values.
We proved this by testing three structurally different safe patterns — the
finding count didn't move for any of them:

- `${POSTGRES_PASSWORD:?must be set}` env reference (no literal secret,
  fails startup if unset) — still flagged
- a non-word placeholder instead of a readable default — still flagged
- a pure Helm `{{ required ... }}` template reference — still flagged

All 21 current hits were re-checked individually on 2026-07-17: every
compose-file hit is a `${VAR:?fail-if-unset}` reference, the Helm secret
templates use `required()` (render fails if empty), and the `values.yaml`
hits are chart defaults that the deploy workflow provably never ships —
`deploy-k8s.yml` exits before `helm upgrade` if any real credential is
unset. None is an actual secret in the repository.

### 3× `Volume Has Sensitive Host Directory` — accepted tradeoff

Promtail's log-collection mounts. A log collector's entire purpose is
reading files off the host; there is no volume-level alternative. The
mounts are minimized: `readOnly: true`, `readOnlyRootFilesystem: true`,
`allowPrivilegeEscalation: false`, `capabilities: drop: [ALL]`.

### 2× `Docker Socket Mounted In Container` — one tradeoff, one real

- `infra/docker-compose.yml` (promtail): read-only socket, used solely for
  container discovery — same tradeoff class as above.
- `compose.azure.yml` (Traefik): **real finding** — Traefik mounts the
  socket directly again since [the revert](#the-composeazureyml-revert).

## The compose.azure.yml revert

To restore a working Azure VM deployment, `compose.azure.yml` was rolled
back to the last known-good version (commit `0cd6267`, "going back to
working solution"). That removed the hardening earlier passes had added to
the VM stack: per-service `security_opt`, `cap_drop` minimization, most
healthchecks, and the docker-socket-proxy that brokered Traefik's socket
access. A working deployment was consciously prioritized over VM-level
hardening (the VM is a staging/demo target). **The Kubernetes/AET
deployment is unaffected** — its security contexts, dropped capabilities,
and gateway-only llm-service access are intact.

## Cross-checks

**gitleaks** (course scanner, 2026-07-17): 2 findings, both historical and
harmless — a `postgres`/`postgres` dev default in the long-deleted
`infra/k8s/secret.yaml` (superseded by fail-fast secret injection; never a
production value) and the filename `99-replication.sh` pattern-matching
the generic-api-key rule. No real credential has ever been committed.
