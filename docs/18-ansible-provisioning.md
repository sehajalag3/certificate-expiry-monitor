# 18. Ansible provisioning

> **Status on this machine:** Ansible is **not installed** on the development machine, so no
> playbook run (not even `--syntax-check`) could be executed here. All YAML was parsed with a
> YAML parser and statically reviewed; the commands the playbooks invoke (docker, systemctl,
> curl health poll) mirror those verified locally. Execute on a real host as described below.

## 18.1 Layout

```
ansible/
├── ansible.cfg                 # inventory path, host key checking off, YAML callback
├── site.yml                    # play: roles common -> docker -> app
├── inventory/hosts.ini         # [app_server] target host, [local] for dry runs
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
- writes `app.env` (mode `0640`) from template: `SERVER_PORT`, `JAVA_OPTS`,
  `WARNING_THRESHOLD_DAYS`, `DEMO_DATA_ENABLED`, `THYMELEAF_CACHE`
- installs `/etc/systemd/system/certificate-monitor.service`:
  `docker run --name certificate-monitor --env-file ... -p 8080:8080 -v .../data:/app/data <image>`
  with `Restart=on-failure`, `RestartSec=10`
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

## 18.5 Verification performed here

| Check | Result |
|-------|--------|
| YAML parse of `site.yml`, all role `tasks/handlers/defaults`, `docker-compose.yml`, `application*.yml` | **10/10 files parsed OK** (Python `yaml.safe_load`) |
| Idempotency review (file/module usage, `notify` handlers, `when: deploy`) | reviewed |
| Playbook execution / `ansible-lint` | **not run** - Ansible absent on this machine |
