# 12. Jenkins setup

> **Status on this machine:** Jenkins is **not installed** on the development machine, so the
> steps below could not be executed here. The pipeline itself (`Jenkinsfile`) uses only
> commands that *were* executed locally and are re-verified in `docs/20-final-validation.md`.

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
| JDK | `JDK-21` | JDK 21 installation, `JAVA_HOME` set |
| Maven | `Maven-3.9.9` | the project still uses its own wrapper (`./mvnw`) - the tool entry satisfies `tools {}` and warms the local repo |

The pipeline uses `./mvnw`, so a mismatched global Maven version cannot break the build.

## 12.4 Credentials (Manage Jenkins -> Credentials)

| ID | Type | Used by |
|----|------|---------|
| `dockerhub-credentials` | Username/password | image push stage |
| `deploy-ssh-key` | SSH private key | Ansible deploy stage |

Never hard-code registry or server passwords in the `Jenkinsfile`.

## 12.5 Agent requirements

- Linux (or Windows) agent with: JDK 21, Docker CLI, Ansible (only if deploying), Git.
- **Google Chrome** if `RUN_UI_TESTS=true` (headless Chrome runs the 5 journeys).
- Network access to Maven Central (first build downloads dependencies).

## 12.6 Create the job

1. *New Item -> Pipeline*, name `certificate-expiry-monitor`.
2. Under *Pipeline*, choose **Pipeline script from SCM**, point to the repository,
   script path `Jenkinsfile`.
3. Save, *Build with Parameters*.

## 12.7 First run checklist

- [ ] Stage *Build and unit tests* green; test results appear under *Test Results* (37 tests).
- [ ] Stage *Selenium UI journeys* green (or disabled) with screenshots archived on failure.
- [ ] *Package* produces `target/certificate-expiry-monitor-1.0.0.jar` (archived artifact).
- [ ] *Docker image* tagged `...:1.0.0-<build no>` and `:latest`.
- [ ] Push/Deploy stages skip cleanly when their parameters are empty/false.

## 12.8 Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `./mvnw: Permission denied` | checkout lost the executable bit | `chmod +x mvnw` in a pre-step or `git update-index --chmod=+x mvnw` |
| Chrome driver errors in UI stage | Chrome missing on agent | install Chrome or set `RUN_UI_TESTS=false` |
| `docker: not found` | Docker CLI absent | install Docker or disable `BUILD_IMAGE` |
| Deploy skipped | `DEPLOY_HOST` empty | set `user@host` parameter and add the SSH key credential |
| Workspace grows | old build dirs | `cleanWs()` runs in `post { always }` already |
