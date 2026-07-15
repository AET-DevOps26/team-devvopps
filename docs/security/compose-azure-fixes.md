# compose.azure.yml security fixes — applied on this branch

`compose.azure.yml` is deployed by `deploy-vm.yml` to a public-facing Azure
VM (real domain via nip.io, TLS via Traefik/Let's Encrypt) — unlike the
other compose files in this repo, this one is genuinely production-facing.

**Note for merge coordination**: a teammate has been working on this same
file independently on a separate branch (commits `53feec6`, `2575b6c`,
not yet in this branch). These two fixes were applied directly here
rather than left as a suggestion, since the goal was to actually close
the KICS findings before a rescan — expect a merge conflict or duplicate
work when reconciling the two branches. Compare before merging.

## 1. Hardcoded `postgres`/`postgres` password — fixed

`POSTGRES_PASSWORD` / `DB_PASSWORD` were hardcoded to the literal
`postgres` in `postgres`, `user-service`, `course-service`, and
`roadmap-service`. Changed to
`${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set in .env.prod}`,
same pattern as `infra/docker-compose.yml` and `server/compose.yaml`.

`.github/workflows/deploy-vm.yml` was updated to match:
- A fail-fast check before deploying (`Check required secrets` step) —
  refuses to deploy if the `POSTGRES_PASSWORD` GitHub Actions secret
  isn't configured, same pattern as `deploy-k8s.yml`'s existing checks.
- The `.env.prod` file the workflow writes to the VM now includes
  `POSTGRES_PASSWORD=${{ secrets.POSTGRES_PASSWORD }}`, which
  `docker compose --env-file=.env.prod` picks up at deploy time.

**Requires a `POSTGRES_PASSWORD` GitHub Actions secret to exist** in
repo settings before this deploy will succeed — add it if it doesn't
already exist.

**Data migration note**: `postgres_data` is a named volume that likely
already has data initialized with the current `postgres`/`postgres`
password on the live VM. Postgres only applies `POSTGRES_PASSWORD` on
first init of an *empty* volume — an existing volume won't pick up the
new password automatically. Whoever runs this needs to either accept a
fresh volume (data loss) or manually rotate the password inside the
running container to match the new secret. Same situation walked
through for the Kubernetes postgres StatefulSet earlier in this project;
see that discussion for the exact `ALTER USER` steps if a live rotation
is preferred over a reset.

## 2. Docker socket mounted directly in Traefik — fixed via socket proxy

Previously:
```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock   # Needed for Traefik to discover services
```

Mounting the Docker socket directly gives the `reverse-proxy` container
root-equivalent control over the whole VM — if Traefik were ever
compromised, the attacker gets the host, not just the container. It's
also the standard way Traefik's Docker provider does service discovery,
so removing it outright would break `--providers.docker=true`
auto-discovery.

Fixed by inserting `tecnativa/docker-socket-proxy` between Traefik and
the real socket:
- New `docker-socket-proxy` service mounts the real socket read-only
  (`:ro`) and exposes only the `CONTAINERS` API (everything else denied
  by that image's defaults). Not published on any host port — only
  reachable from `reverse-proxy` over the default Docker network.
- `reverse-proxy` no longer mounts the socket at all; instead points
  Traefik's Docker provider at the proxy via
  `--providers.docker.endpoint=tcp://docker-socket-proxy:2375`.

This is the pattern Traefik's own docs recommend for production
deployments, not just a mitigation — Traefik functionally can't do
anything to the host beyond listing/inspecting containers now.

Verified: `docker compose -f compose.azure.yml config` renders
successfully with both changes (using placeholder env values for the
required variables). Also live-tested `docker-socket-proxy` and
`reverse-proxy` locally (the only two services using public images,
rather than private GHCR images this machine can't pull) — both reached
`healthy`, and Traefik's logs showed no connection errors talking to the
proxy at its new endpoint.

## 3. Capabilities, security_opt, and healthchecks — added to every service

Following the same hardening already applied to `infra/docker-compose.yml`
and `server/compose.yaml`:

- `security_opt: [no-new-privileges:true]` and `cap_drop: [ALL]` added to
  all 7 services.
- `cap_add` exceptions kept narrow and documented inline: `postgres`
  needs CHOWN/FOWNER/DAC_OVERRIDE/SETUID/SETGID for its first-init chown;
  `client` (nginx) needs CHOWN/SETUID/SETGID for the same reason; Traefik
  needs `NET_BIND_SERVICE` to bind ports 80/443.
- Healthchecks added to every service that didn't have one:
  `docker-socket-proxy` (wget against its own `/_ping`), `reverse-proxy`
  (a dedicated internal-only Traefik entrypoint on port 8082 for `--ping`,
  not published to the host — `--ping` defaults to the `traefik` API
  entrypoint, which isn't defined by this file and we didn't want to
  expose the dashboard/API to the internet to enable it), `user-service`
  / `course-service` / `roadmap-service` / `api-gateway` (bash `/dev/tcp`
  against `/actuator/health`, since the `eclipse-temurin` base image has
  no curl/wget), `client` (wget against `/`), `llm-service` (the
  distroless final image's own `python3` via `urllib`, since it has no
  shell/curl/wget at all — note this file runs llm-service without a
  `PORT` env var, so it health-checks port 8004, its `main.py` default,
  not 8084 like `infra/docker-compose.yml`).

Not yet live-tested: the 5 GHCR-image services (`user-service`,
`course-service`, `roadmap-service`, `api-gateway`, `client`, `llm-service`)
can't be pulled/run from this machine (private registry). They use the
exact same capability/healthcheck patterns already verified working live
in `infra/docker-compose.yml`, but that's inference, not direct proof for
this specific file — worth a first real deploy being watched closely.
