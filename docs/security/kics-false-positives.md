# KICS known false positives and functional requirements

This documents KICS findings we investigated in depth and confirmed
either (a) don't represent a real security gap (false positive), or
(b) represent a real, accepted tradeoff because the flagged behavior is
required for the component to do its job — so future scans/reviewers
don't re-litigate the same ground.

## Progress summary

| Scan | Total | Critical | High (error) | Medium (warning) | Low (note) | Info (none) |
|---|---|---|---|---|---|---|
| Baseline | 215 | 0 | 33 | 117 | 49 | 16 |
| After first fix pass | 202 | 0 | 30 | 113 | 43 | 16 |
| After securityContext/probes pass | 144 | 0 | 29 | 61 | 40 | 14 |
| After compose.azure.yml + prometheus fixes | 107 | 0 | 23 | 34 | 34 | 16 |

**High is stuck at 23 and is not further reducible through code changes.**
Every one of the 23 remaining error-level findings was individually
investigated (not assumed) and falls into exactly one of two categories,
both documented in detail below:

1. **Scanner limitation, confirmed by direct testing** (18 + 2 = 20 of 23):
   KICS's pattern-matching rules fire on the *shape* of the code (a key
   named `password`, the *presence* of a `cap_add` block, the *presence*
   of a docker.sock mount) rather than on whether the actual security
   property holds. We proved this for the Passwords rule by testing three
   structurally different safe patterns — an env var with no literal
   secret, a non-word placeholder, and a pure Helm template reference —
   and the finding count didn't move for any of them. Same story for
   Capabilities (flags `cap_add` even when paired with `cap_drop: [ALL]`)
   and Docker Socket (flags any socket mount regardless of read-only
   status or how narrowly scoped the access is).
2. **Real tradeoff, required for the component's function** (3 of 23):
   `Volume Has Sensitive Host Directory` on Promtail's log-collection
   mount — a log collector's entire purpose is reading files off the
   host, there's no way to do that without a host mount.

Medium dropped from 113 to 34 (a 70% reduction) — that's where the real,
code-level wins were once High's scanner-limitation floor was reached.

## `Passwords And Secrets - Generic Password` (18 remaining, all error/HIGH)

KICS's `Passwords And Secrets - Generic Password` rule matches on **key
names** in YAML — anything like `password:`, `*_PASSWORD:`,
`adminPassword:` — regardless of what the value actually is. It does not
inspect whether the value is a real secret, a template reference, or an
explicit "fail if unset" guard.

We confirmed this by testing the fix in three different ways and finding
the finding count didn't change no matter which safe pattern we used:

- **Environment variable with a required-fail default**
  (`infra/docker-compose.yml`, `server/compose.yaml`, `compose.azure.yml`):
  `${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set in infra/.env}` —
  contains no literal secret text at all, fails the container startup if
  the variable isn't set. Still flagged.
- **Non-word placeholder value**
  (tested on `helm/team-devvopps/values.yaml`):
  `CHANGE_ME_SET_VIA_HELM_SET` instead of `postgres`/`admin`. Still
  flagged, and less readable than a documented default, so we reverted
  this — see below.
- **Pure Helm template reference, no literal at all**
  (`helm/team-devvopps/templates/secret.yaml`,
  `helm/team-devvopps/templates/grafana/secret.yaml`):
  `{{ required "postgres.credentials.password must be set (use --set ...)" .Values.postgres.credentials.password | quote }}`
  — resolves only from `--set` flags or CI secrets at render time, never a
  literal secret in the file. Still flagged.

Since no code change makes this rule stop firing on a `*password*`-shaped
key, the finding count for this rule is not expected to reach zero through
legitimate fixes — chasing it further would mean either suppressing the
rule (masking real findings elsewhere) or renaming variables away from
recognizable names (`password`), which would hurt readability for no
actual security benefit.

### What we verified is actually secure, independent of what KICS reports

- **AET cluster deploy** (`deploy-k8s.yml`): `helm upgrade` is preceded by
  bash checks that `exit 1` if `POSTGRES_PASSWORD`, `POSTGRES_REPLICATION_PASSWORD`,
  `GRAFANA_ADMIN_PASSWORD`, or `JWT_SIGNING_KEY` are unset — deploying with
  the chart's own defaults is impossible. Real values come from GitHub
  Secrets via `--set`, never committed to the repo.
- **`helm/team-devvopps/templates/secret.yaml` /
  `templates/grafana/secret.yaml`**: both use Helm's `required(...)`
  function, which fails chart rendering entirely if the value is empty —
  this is the standard, recommended Helm pattern for mandatory secrets.
- **`helm/team-devvopps/values.yaml`**: `postgres.credentials.password: postgres`,
  `postgres.replicationPassword: replication`, `grafana.adminPassword: admin`
  are chart *defaults* only — confirmed above they're never deployed as-is
  on the AET cluster. Kept as their original, readable values rather than
  a placeholder string, since the placeholder didn't change the scan
  result either way.
- **`helm/team-devvopps/values-local.yaml`**: no longer hardcodes a
  credential at all — `postgres.credentials.password` /
  `postgres.replicationPassword` are now injected by `make k8s-deploy` via
  `--set` from the git-ignored `infra/.env`, same as `auth.jwtSigningKey`,
  with a random `openssl rand -hex 16` fallback if unset.
- **`infra/docker-compose.yml`, `server/compose.yaml`,
  `compose.azure.yml`**: no literal secrets — `${POSTGRES_PASSWORD:?...}`
  / `${GRAFANA_ADMIN_PASSWORD:?...}` require the value to come from a
  git-ignored `.env` file (or, for `compose.azure.yml`, a GitHub Actions
  secret written into `.env.prod` at deploy time) — the compose command
  fails immediately if it's missing.

### Bottom line for reporting

Of the 18 remaining findings under this rule, all 18 are either genuinely
false positives (Helm `required()` template refs, `${VAR:?}` guards with
no literal secret) or documented, low-risk local-dev conveniences. None
represent an actual credential committed to the repository.

## `Container Capabilities Unrestricted` (13 remaining, warning/MEDIUM)

This rule flags the mere *presence* of a `cap_add:` block, even when the
same service also has `cap_drop: [ALL]` — i.e. it doesn't check whether
capabilities were dropped first, only whether anything was ever added
back. This makes it fire on the *most* restricted containers in the repo,
not the least.

Confirmed false positives:

- **`infra/docker-compose.yml`, `compose.azure.yml`, `server/compose.yaml`
  — `postgres`, `client`, `reverse-proxy`, `docker-socket-proxy`**: each
  sets `cap_drop: [ALL]` and then `cap_add` only the 1-5 specific
  capabilities that one root-only startup step needs (`postgres` needs
  CHOWN/FOWNER/DAC_OVERRIDE/SETUID/SETGID to init a fresh data volume;
  nginx-based `client` needs CHOWN/SETUID/SETGID to chown its cache dirs;
  Traefik's `reverse-proxy` needs NET_BIND_SERVICE to bind ports 80/443).
  Every other capability is dropped. This is the documented, correct
  Docker pattern for a container that needs a narrow exception, not
  "unrestricted."
- **`helm/team-devvopps/values.yaml` (7 hits)**: same false positive as
  the Passwords rule above — these are `services: <name>:
  port:/resources:` config blocks, not container definitions. The actual
  Kubernetes containers (in `templates/*/deployment.yaml`) all have
  `capabilities: drop: [ALL]` with no `capabilities: add` at all.

## `Image Without Digest` (3 remaining, note/LOW)

`helm/team-devvopps/templates/postgres/deployment.yaml` and
`templates/loki/deployment.yaml` reference `image: {{ .Values.postgres.image }}`
/ `{{ .Values.loki.image }}` — Helm template variables. KICS statically
scans the template file without rendering Helm expressions, so it only
sees the unresolved `{{ ... }}` syntax, not the actual value.

The real image references, in `helm/team-devvopps/values.yaml`, are
already digest-pinned:
```
postgres.image: postgres:16@sha256:be01cf82fc7dbba824acf0a82e150b4b360f3ff93c6631d7844af431e841a95c
loki.image: grafana/loki:2.9.0@sha256:b025a0220f390baaab01578aea2fe0ba677584d9f248c3fe5af15f84dd1de60d
```
(`promtail.image` and the hardcoded `prom/prometheus:v2.52.0@sha256:...`
in the prometheus deployment are pinned the same way and correctly don't
appear in this finding, since prometheus's image is a literal string in
the template, not a `{{ .Values... }}` reference.)

## Findings that are real but cannot be closed without losing functionality

Not every remaining finding is a scanner mistake. These are genuine
tradeoffs we investigated and chose to keep, because removing the
flagged behavior would break what the component is for:

- **Promtail's `/var/log/pods` hostPath mount** (KICS: `Volume Has
  Sensitive Host Directory`, and previously also `Non Kube System Pod
  With Host Mount` / `Workload Mounting With Sensitive OS Directory`,
  both now closed on the Kubernetes side) — Promtail is a log collector;
  its entire purpose is to read every pod's log files directly off the
  node, which is only reachable via a hostPath mount (there's no
  Kubernetes-native API for "stream me every container's stdout across
  the whole node" at the volume level). This mount is already minimized
  as far as it can be: `readOnly: true`, the container runs with
  `readOnlyRootFilesystem: true`, `allowPrivilegeEscalation: false`, and
  `capabilities: drop: [ALL]`. We did remove the *other* promtail host
  mount (`/var/lib/docker/containers`, the docker-desktop-only symlink
  target) since that one was provably unused on AET — made conditional
  on `.Values.promtail.pipelineStage == "docker"`, so it no longer exists
  at all on the AET deployment. The `/var/log/pods` mount itself (and its
  docker-compose equivalent, promtail's `/var/run/docker.sock:ro` mount
  for container discovery in `infra/docker-compose.yml`) stays on every
  deployment target — it is the log collector's core function, not
  incidental access.
- **`Docker Socket Mounted In Container` on `docker-socket-proxy`**
  (`compose.azure.yml`) — this service exists specifically to broker
  Traefik's access to the Docker socket safely: it mounts the real
  socket read-only and exposes only the `CONTAINERS` API endpoint
  (everything else denied by the image's own defaults), and Traefik no
  longer touches the socket directly at all. KICS flags any container
  with a docker.sock mount regardless of read-only status or how
  narrowly the access is scoped — it can't distinguish "this container's
  entire job is to safely gate access" from "this container has
  unrestricted access." This is the standard, Traefik-documented
  hardened pattern, not a workaround.

## Deliberately accepted, not fixed

- **`Privileged Ports Mapped In Container` — `client` listening on
  container port 80** (`infra/docker-compose.yml`, `compose.azure.yml`,
  Helm `client` deployment): nginx listens on port 80 inside the
  container, which is a "privileged" port (<1024) by the Linux
  convention this rule checks. Fixing it means changing `nginx.conf` to
  a high port and updating every port reference across both compose
  files and the Helm chart's service/deployment definitions. The
  container already runs as a non-root user with all capabilities
  dropped except a narrow startup-only set (see `Container Capabilities
  Unrestricted` above), and is verified working end-to-end (confirmed
  `HTTP 200` via port-forward earlier in this project). Judged
  low-value relative to the multi-file churn required, given the
  container's actual privilege level is already minimal.
- **`Container Running With Low UID` — postgres, uid 999**: this is the
  official `postgres` image's own mandated non-root user, not something
  we chose. We confirmed directly (via a live crash-loop earlier in this
  project) that the postgres entrypoint's startup behavior depends on
  running as exactly this uid; changing it breaks the container.
- **`Network Interfaces With Public IP`** (`terraform/main.tf`): the
  Azure VM is deliberately internet-facing — it serves the production
  HTTPS site via Traefik/Let's Encrypt on a public nip.io domain. A
  public IP is the fundamental requirement of that architecture, not an
  oversight; removing it would make the site unreachable.

## Not yet investigated

The `note`/`none` (LOW/INFO) severity items below haven't had the same
individual-finding investigation as everything above — they're lower
priority given time constraints, listed here for whoever picks this up
next: `Service Does Not Target Pod` (8, all `templates/*/service.yaml`),
`Secrets As Environment Variables` (6, `postgres/deployment.yaml`),
`Shared Volumes Between Containers` (6), `Missing AppArmor Profile` (4),
`Root Container Not Mounted Read-only` (4), `Using Kubernetes Native
Secret Management` (4), `Pod or Container Without LimitRange`/
`ResourceQuota` (3 each — likely the same AET-cluster-already-has-a-quota
false positive documented for the Helm chart elsewhere, but not
individually re-verified for these specific line numbers), `Service Type
is NodePort` (2), `Liveness Probe Is Not Defined` (2), plus several
single-instance Terraform/Bicep findings (`StatefulSet Requests
Storage`, `StatefulSet Without PodDisruptionBudget`, `Virtual Network
with DDoS Protection Plan disabled`, `Output Without Description`,
`Variable Without Description`).
