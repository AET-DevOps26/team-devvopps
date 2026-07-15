# Known Issues

## Promtail crash-looping on `k8s-stud-pve-worker5` (AET cluster)

**Status:** Root cause of the actual bug is fixed and verified. One residual,
node-specific failure remains — it does not affect app functionality or
deploy success.

### Summary

Promtail (log shipper, runs as a DaemonSet — one pod per node) previously
crashed with `too many open files` unpredictably during AET deploys, which
made `helm upgrade --wait` time out and fail the whole deploy. There were two
separate causes, one in our own config (now fixed) and one that is a
characteristic of a specific shared-cluster node (not something this repo can
fix).

### Cause 1 (fixed): missing node-scoping in Promtail's discovery config

Promtail's `kubernetes_sd_configs` discovers pods via the Kubernetes API,
scoped only by *namespace* — not by node. A `$HOSTNAME` env var and a code
comment implied discovery was already restricted to each pod's own node, but
that was never actually wired up: the container was missing
`-config.expand-env=true`, and the scrape config had no relabel rule
referencing `$HOSTNAME` at all. The result: every one of the ~28 DaemonSet
replicas discovered and tried to watch log files for *every* pod in the
namespace cluster-wide, not just the ~1–2 pods on its own node.

This bug existed from the day Promtail was added to the chart
(`cf4724f`, 2026-07-08) but stayed latent for about a week. It was exposed
on 2026-07-14 when a separate, well-justified fix
(`9dd193c` — changing the DaemonSet's rolling update from the Kubernetes
default of one-node-at-a-time to `maxUnavailable: 50%`, to keep a full
rollout within Helm's `--wait` timeout) meant ~14 replicas across 14 nodes
now start their (buggy) discovery simultaneously on every deploy, instead of
one at a time. That concurrency is what turned a latent bug into a
reproducible failure.

**Fix** (`helm/team-devvopps/templates/promtail/{daemonset,configmap}.yaml`):
added `-config.expand-env=true` and a `keep` relabel rule filtering
discovered pods to `__meta_kubernetes_pod_node_name == $HOSTNAME`. Verified
by dry-running the real `promtail` binary against the rendered config (confirms
env expansion and the parsed relabel rule), and live on the AET cluster:
27/28 replicas now come up healthy with zero restarts, consistently, across
multiple deploys — versus the previous cluster-wide fan-out.

### Cause 2 (open, external): `k8s-stud-pve-worker5` specifically

Even after the fix above, the Promtail replica on this one node
(`k8s-stud-pve-worker5.aet.cit.tum.de`) still crashes with the identical
`failed to make file target manager: too many open files` error — reproduced
identically across three separate deploys. This is now a much smaller,
per-node issue rather than the systemic bug above:

- Node conditions (`MemoryPressure`, `DiskPressure`, `PIDPressure`) are all
  reported healthy — but Kubernetes has no condition for inotify/file-descriptor
  pressure, so a clean node status doesn't rule this out.
- Pod density on this node (`kubectl describe node`, the
  `management.cattle.io/pod-requests` annotation) is modestly higher than a
  healthy comparison node (50 pods vs. 42), with similar CPU/memory requests —
  consistent with more tenants/processes on a shared student cluster
  contending for node-wide inotify watch/instance limits, though not a
  dramatic outlier on its own.
- We do not have cluster-scoped or node-shell access (student RBAC) to
  inspect the node's actual `fs.inotify.max_user_watches` /
  `max_user_instances` or per-UID usage directly.

This looks like a per-node resource ceiling on the shared course cluster,
outside anything this Helm chart controls. **Recommended follow-up:** report
to AET-DevOps course staff with this evidence trail, since it has now
reproduced identically on the same physical node three times.

### Current impact

- **Deploys:** none. Promtail's readiness is treated as best-effort (see
  `.github/workflows/deploy-k8s.yml`) — a DaemonSet rollout that doesn't
  reach 100% only produces a warning, not a failed job. The application
  Deployments (postgres, APIs, client, Grafana, etc.) still have a hard
  readiness gate and are unaffected.
- **Logging:** any pod scheduled onto `k8s-stud-pve-worker5` will have its
  logs missing from Loki/Grafana until that node's Promtail replica can
  start. Pods on all other nodes ship logs normally. Check current exposure
  with:
  ```
  kubectl get pods -n team-devvopps -o wide | grep k8s-stud-pve-worker5
  ```
  If only a `promtail-*` pod matches, no application logs are currently
  affected.
