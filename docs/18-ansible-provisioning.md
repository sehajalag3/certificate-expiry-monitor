# 18. Ansible provisioning

> **Status (2026-10-20):** Ansible-core **2.20.1** runs inside **WSL Ubuntu 26.04**, where root
> execution is available directly (`wsl -d Ubuntu -u root -- id` -> `uid=0(root)`); no sudoers or
> `NOPASSWD` change was made. The playbook was executed there against an **isolated local WSL
> node**: five real runs (provision, one intentional failure, fixed deploy, idempotency proof,
> rollback/recovery). Full evidence in [18.6](#186-isolated-wsl-test-run-executed-2026-10-20) and
> in [`20-final-validation.md` section 9](20-final-validation.md).
> **No remote host exists**, so none of this is a remote deployment.

## 18.1 Layout

```
ansible/
├── ansible.cfg                 # inventory path, host key checking off, built-in callback
│                                #   (yaml result format; the old community.general "yaml"
│                                #    callback was removed in community.general 12)
├── site.yml                    # play: roles common -> docker -> app
├── inventory/hosts.ini         # [app_server] target host, [local] for dry runs
├── inventory/wsl-test.ini      # isolated local test node (WSL) - never a real host
├── inventory/wsl-test-vars.yml # namespaced vars for that test node (path + port)
└── roles/
    ├── common/                 # packages, app user, directories
    ├── docker/                 # Docker CE repo + engine + compose plugin
    └── app/                    # env file, systemd unit, start + health gate
        ├── defaults/main.yml   # ports, image, threshold, demo flag (overridable)
        ├── tasks/main.yml
        ├── handlers/main.yml   # reload systemd
        └── templates/          # app.env.j2, certificate-monitor.service.j2
```

## 18.2 Usage

```bash
cd ansible

# dry run against localhost (safe)
ansible-playbook site.yml -i inventory/hosts.ini --check -l local

# provision + deploy for real (needs SSH access to inventory host)
ansible-playbook site.yml -e deploy=true

# custom image / settings
ansible-playbook site.yml -e deploy=true \
  -e image=docker.io/youruser/certificate-expiry-monitor:1.0.0-42 \
  -e warning_threshold_days=14 \
  -e demo_data_enabled=false
```

From Jenkins the same playbook is invoked with `ANSIBLE_PRIVATE_KEY_FILE` from the
`deploy-ssh-key` credential and `-e app_host=$DEPLOY_HOST`.

## 18.3 What each role does (idempotent)

**common**
- installs `ca-certificates curl gnupg unzip openjdk-21-jdk-headless`
- creates system group/user `certmonitor` (`nologin`, no home)
- creates `/opt/certificate-expiry-monitor/{data,logs}` owned by the app user

**docker**
- adds Docker's official apt GPG key + repository (Ubuntu/Debian)
- installs `docker-ce docker-ce-cli containerd.io docker-compose-plugin`
- enables/starts `docker`, adds the app user to the `docker` group

**app** (only starts the service when `-e deploy=true`)
- writes `app.env` (mode `0640`) from template: `SERVER_PORT` (the **container** port,
  `app_container_port`, image default `8080`), `JAVA_OPTS`, `WARNING_THRESHOLD_DAYS`,
  `DEMO_DATA_ENABLED`, `THYMELEAF_CACHE`
- installs `/etc/systemd/system/certificate-monitor.service`:
  `docker run --name certificate-monitor --env-file ... -p <app_port>:<app_container_port>
  -v .../data:/app/data <image>` with `Restart=on-failure`, `RestartSec=10`
  (`app_port` is the host port only - see the defect fixed in 18.6)
- starts the unit and **waits for `/actuator/health` (200)** before reporting success

Re-running the playbook changes nothing when the state already matches (safe re-deploys).

## 18.4 Inventory

```ini
[app_server]
app.example.com ansible_user=deploy ansible_port=22
```

Replace with your host; pass the key via `ANSIBLE_PRIVATE_KEY_FILE` or an ssh-agent.
`host_key_checking = False` is set for lab convenience - disable it (or pre-populate
`known_hosts`) for real environments.

`inventory/wsl-test.ini` + `inventory/wsl-test-vars.yml` are the parallel, isolated inventory for
the local drill described in 18.6; they never point at a real host.

## 18.5 Verification performed here

| Check | Result |
|-------|--------|
| YAML parse of `site.yml`, all role `tasks/handlers/defaults`, `docker-compose.yml`, `application*.yml` | **10/10 files parsed OK** (Python `yaml.safe_load`) |
| Idempotency review (file/module usage, `notify` handlers, `when: deploy`) | reviewed |
| Playbook execution | **EXECUTED in WSL** - 5 runs, see [18.6](#186-isolated-wsl-test-run-executed-2026-10-20) |
| `ansible-lint` | not installed - not run |

## 18.6 Isolated WSL test run (executed 2026-10-20)

Target: the local **WSL Ubuntu 26.04** distro, treated as a throw-away test node.

- **Authorization:** `wsl -d Ubuntu -u root -- id` returns `uid=0(root)`, so the playbook is run
  as root directly. **No `NOPASSWD`/sudoers entry was added** and no Windows credential was stored.
- **Isolation:** `inventory/wsl-test.ini` targets `localhost` with `ansible_connection=local`;
  `inventory/wsl-test-vars.yml` namespaces every path and the port -
  `app_home=/opt/certmon-wsl-drill`, `app_port=8090`. Real deployments keep
  `/opt/certificate-expiry-monitor` and `8080`, and the default inventory still points at the
  placeholder host, so a drill run cannot touch a production path.
- **Image:** `sehaj0003/certificate-expiry-monitor:1.0.0-8` pulled anonymously from Docker Hub and
  retagged `certificate-expiry-monitor:1.0.0` - the artifact Jenkins pushed in build #8.

Every run used the same command (root shell in WSL, run from the repository checkout):

```bash
cd <repo>/ansible
ANSIBLE_CONFIG=$PWD/ansible.cfg \
  ansible-playbook -i inventory/wsl-test.ini site.yml -e @inventory/wsl-test-vars.yml -e deploy=true
```

`ANSIBLE_CONFIG` must be exported explicitly: Ansible refuses to read an `ansible.cfg` discovered
in a world-writable directory, and `/mnt/c` is `0777` under drvfs.

### Runs and change counts

| # | Run | Recap `ok / changed / failed / skipped` | Outcome |
|---|-----|------------------------------------------|---------|
| 1 | provision (no `-e deploy`) | `16 / 11 / 0 / 2` | **PASS** - packages, Docker CE repo + engine, app user, dirs, unit file |
| 2 | deploy run 1 | `16 / 1 / 1 / 0` | **FAIL** - health gate: `[Errno 104] Connection reset by peer` on `http://127.0.0.1:8090/actuator/health` (defect 1 below) |
| 3 | deploy run 2, after the fix | `17 / 2 / 0 / 0` | **PASS** - health `{"status":"UP","groups":["liveness","readiness"]}` |
| 4 | deploy run 3, identical command | `17 / 0 / 0 / 0` | **PASS - idempotent, `changed=0`** |
| 5 | rollback + recovery re-run | `18 / 2 / 0 / 0` | **PASS** - service restored, data intact |

Health evidence taken inside the same sessions:

- `GET /actuator/health` -> `{"status":"UP","groups":["liveness","readiness"]}`
- `GET /` -> `HTTP 200`, `GET /certificates` -> `HTTP 200` with **10 certificate rows**
- container `certificate-monitor Up (healthy) 0.0.0.0:8090->8080/tcp`

### Rollback / recovery drill (no unrelated data deleted)

1. Marker written into the app data volume: `/opt/certmon-wsl-drill/data/drill-data-marker.txt`
   (`md5 854384fb431220a85a7aed5f8f7aafdc`), plus canaries `/root/ansible-drill-canary.txt`,
   `/opt/unrelated-data-canary.txt`, `/opt/unrelated-app-data/important.txt`.
2. **Rollback:** `systemctl disable --now certificate-monitor`, unit file removed,
   `systemctl daemon-reload`, `docker rm -f certificate-monitor`.
3. **After rollback:** `systemctl cat` reports *No files found*, port 8090 refuses connections,
   no container exists - and every check reports preserved:
   `PASS: app data marker preserved`, `PASS: /root canary preserved`,
   `PASS: /opt canary preserved`, `PASS: unrelated app data preserved`.
4. **Recovery:** the same playbook command with `-e deploy=true` -> `changed=2 failed=0`,
   health `UP`, `/certificates` still lists **10 rows**, marker md5 unchanged
   (`854384fb431220a85a7aed5f8f7aafdc`) - the rollback deleted deployment objects, never data.

Run logs live in `/root/ansible-drill-logs/0{1..5}-*.log` inside the WSL distro.

### Defects found by this run and fixed on this branch

| # | Defect | Fix |
|---|--------|-----|
| 1 | `app.env` rendered `SERVER_PORT={{ app_port }}`, so with `app_port=8090` the app listened on 8090 *inside* the container while the unit published `8090:8080`. Run 2 failed in the health gate with connection reset | new default `app_container_port: 8080`; `app.env` gets the container port, the unit publishes `{{ app_port }}:{{ app_container_port }}`. Behaviour with the default `app_port=8080` is unchanged |
| 2 | `ansible.cfg` set `stdout_callback = yaml`, which was removed from `community.general` 12 - `ansible-playbook` aborted before the first task | `stdout_callback = ansible.builtin.default` with `callback_result_format = yaml` (same YAML-style output) |
| 3 | `{{ ansible_distribution_release }}` raised the `INJECT_FACTS_AS_VARS` deprecation warning (removal in ansible-core 2.24) | `{{ ansible_facts['distribution_release'] }}` |

### Environment notes (honest limits)

- **WSL only.** These runs prove the playbook against a local, isolated Linux node. No remote
  host was provisioned, so nothing here is a remote deployment.
- WSL tears down the distro's userspace between command sessions (observed: PID 1 start time
  equals the start of the next `wsl` command), so the enabled unit comes up at session start and
  stops when the session ends. Every health check above was captured **inside** the session that
  ran the playbook.
- The Windows host could not reach `localhost:8090` or the WSL address `172.19.115.211:8090`
  (`TcpTestSucceeded = False`, WSL NAT + Hyper-V firewall), so health was verified from inside
  WSL with `curl` and with the playbook's own `uri` gate.
- Teardown was **not** executed - the drill node is left in place. To remove only drill data:
  `systemctl disable --now certificate-monitor && rm /etc/systemd/system/certificate-monitor.service && systemctl daemon-reload && docker rm -f certificate-monitor && rm -rf /opt/certmon-wsl-drill`.
