# 12. Jenkins setup

> **Status on this machine (2026-10-20):** Jenkins **2.580.1** runs as a Windows service
> (`http://localhost:8080`, service account `LocalSystem`) with the pipeline job
> `Certificate-Expiry-Monitor` (Pipeline script from SCM, `Jenkinsfile`, branch `*/main`).
> **Builds #1-#3 are green:** #1 ran checkout + 37 tests + package; #2 added the Selenium
> stage (5/5) and published JUnit results; #3 built the Docker image
> `sehaj0003/certificate-expiry-monitor:1.0.0-3` (+ `latest`). Security is enabled
> (`HudsonPrivateSecurityRealm`, user `sehajadmin`, anonymous read denied), so the checklist
> items below were performed through the web UI.

## 12.1 Install Jenkins (Linux server, recommended for the team)

```bash
# Ubuntu/Debian - LTS via official repo
sudo wget -O /usr/share/keyrings/jenkins-keyring.asc \
  https://pkg.jenkins.io/debian/jenkins.io-2023.key
echo "deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian binary/" \
  | sudo tee /etc/apt/sources.list.d/jenkins.list
sudo apt update && sudo apt install -y openjdk-21-jdk-headless jenkins
sudo systemctl enable --now jenkins
```

Open `http://<server>:8080`, unlock with `/var/lib/jenkins/secrets/initialAdminPassword`,
install the suggested plugins.

## 12.2 Required plugins

- Pipeline, Git, Timestamper, JUnit
- Docker Pipeline (for `docker build/push` stages)
- SSH Agent (for Ansible deployment)
- Credentials Binding
- Optional: Blue Ocean for a nicer view

## 12.3 Tools (Manage Jenkins -> Tools)

| Tool | Name used in `Jenkinsfile` | Notes |
|------|---------------------------|-------|
| JDK | `JDK-21` | JDK 21 installation, `JAVA_HOME` set (here: `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot`) |
| Maven | - | not required: the pipeline calls `mvnw.cmd`, so the wrapper's Maven 3.9.9 is used |

A global Maven tool is unnecessary, so a mismatched global Maven version cannot break the build.

## 12.4 Credentials (Manage Jenkins -> Credentials)

| ID | Type | Used by |
|----|------|---------|
| `dockerhub-credentials` | Username/password | image push stage |
| `deploy-ssh-key` | SSH private key | Ansible deploy stage |

**Verified 2026-10-20:** the Jenkins credential store is empty (`credentials.xml` does not
exist), so both credentials still have to be created under *Manage Jenkins -> Credentials ->
System -> Global credentials*. Until then, keep `PUSH_IMAGE=false` and `DEPLOY_HOST` empty.

Never hard-code registry or server passwords in the `Jenkinsfile`.

## 12.5 Agent requirements

- Linux (or Windows) agent with: JDK 21, Docker CLI, Ansible (only if deploying), Git.
- **Google Chrome** if `RUN_UI_TESTS=true` (headless Chrome runs the 5 journeys; Chrome 155
  is installed machine-wide on this host, so the `LocalSystem` service can launch it).
- Network access to Maven Central (first build downloads dependencies).
- **Windows note:** the pipeline calls `docker` from `bat`. On this host the `LocalSystem`
  service resolves the Docker CLI and build #3 completed `docker build`, so no extra
  configuration is needed here. If a fresh agent reports `'docker' is not recognized`, add the
  Docker bin directory (`%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin` for a per-user
  install) to the machine `PATH` or to a Jenkins global environment variable. The Docker named
  pipes grant `NT AUTHORITY\SYSTEM` full control.

## 12.6 Create the job

1. *New Item -> Pipeline*, name `certificate-expiry-monitor`.
2. Under *Pipeline*, choose **Pipeline script from SCM**, point to the repository,
   script path `Jenkinsfile`.
3. Save, *Build with Parameters*.

## 12.7 Automatic triggers

The `Jenkinsfile` registers `triggers { pollSCM('H/5 * * * *') }`, i.e. Jenkins polls
GitHub for new commits on `main` every ~5 minutes.

- **Works without a public URL.** Polling is outbound from Jenkins; the repository is public
  (`git ls-remote` succeeds without credentials), so no Jenkins credential is needed.
- **Webhook alternative:** *GitHub hook trigger for GITScm polling* plus a repository webhook
  pointing at `http://<jenkins-host>:8080/github-webhook/`. This needs Jenkins to be reachable
  from GitHub (public URL or tunnel), which is not the case for `localhost`.
- Declarative triggers apply on the next build of the job. After this change reaches `main`,
  run *Build with Parameters* once; afterwards polling appears in the job's *Polling Log*.
  Verified 2026-10-20: the job config still had an empty `<triggers/>` after builds #1-#3,
  because those builds ran the previously pushed `Jenkinsfile`.

## 12.8 First run checklist

- [ ] Stage *Build and unit tests* green; test results appear under *Test Results* (37 tests).
- [ ] Stage *Selenium UI journeys* green (or disabled) with screenshots archived on failure.
- [ ] *Package* produces `target/certificate-expiry-monitor-1.0.0.jar` (archived artifact).
- [ ] *Docker image* tagged `...:1.0.0-<build no>` and `:latest`.
- [ ] Push/Deploy stages skip cleanly when their parameters are empty/false.

## 12.9 Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `./mvnw: Permission denied` | checkout lost the executable bit | `chmod +x mvnw` in a pre-step or `git update-index --chmod=+x mvnw` |
| Chrome driver errors in UI stage | Chrome missing on agent | install Chrome or set `RUN_UI_TESTS=false` |
| `docker: not found` | Docker CLI absent | install Docker or disable `BUILD_IMAGE` |
| Deploy skipped | `DEPLOY_HOST` empty | set `user@host` parameter and add the SSH key credential |
| Workspace grows | old build dirs | `cleanWs()` runs in `post { always }` already |
