
pipeline {
    agent any

    tools {
        jdk 'JDK-21'
    }

    options {
        timestamps()
        timeout(time: 40, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    triggers {
        // Poll the repository for new commits every ~5 minutes. Polling works
        // with a controller that is not reachable from the internet; a GitHub
        // webhook is an alternative once a public Jenkins URL exists.
        pollSCM('H/5 * * * *')
    }

    parameters {
        booleanParam(
            name: 'RUN_UI_TESTS',
            defaultValue: false,
            description: 'Run Selenium tests; requires Chrome on the Jenkins agent'
        )
        booleanParam(
            name: 'BUILD_IMAGE',
            defaultValue: false,
            description: 'Build the Docker image'
        )
        booleanParam(
            name: 'PUSH_IMAGE',
            defaultValue: false,
            description: 'Push image; requires dockerhub-credentials in Jenkins'
        )
        string(
            name: 'REGISTRY',
            defaultValue: 'docker.io',
            description: 'Image registry'
        )
        string(
            name: 'IMAGE_NAME',
            defaultValue: 'sehaj0003/certificate-expiry-monitor',
            description: 'Image repository'
        )
        string(
            name: 'DEPLOY_HOST',
            defaultValue: '',
            description: 'SSH user@host; Linux agent required for Ansible'
        )
        booleanParam(
            name: 'DEPLOY_CONTAINER',
            defaultValue: false,
            description: 'Deploy a fresh app container behind Nginx on the agent (continuous deployment)'
        )
        string(
            name: 'DEPLOY_PORT',
            defaultValue: '8888',
            description: 'Host port exposed by the Nginx front door for the deployed container'
        )
    }

    environment {
        APP_NAME = 'certificate-expiry-monitor'
        IMAGE_TAG = "1.0.0-${env.BUILD_NUMBER}"
        JAR_NAME = 'target/certificate-expiry-monitor-1.0.0.jar'
        MAVEN_OPTS = '-Dmaven.artifact.threads=4'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                bat 'git rev-parse --short HEAD'
            }
        }

        stage('Build and Unit Tests') {
            steps {
                bat 'mvnw.cmd -B clean test'
            }
            post {
                always {
                    junit allowEmptyResults: false,
                          testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Selenium UI Journeys') {
            when {
                expression { params.RUN_UI_TESTS }
            }
            steps {
                bat 'mvnw.cmd -B test -Pui'
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'target/surefire-reports/TEST-*ui*.xml'
                    script {
                        // Screenshots are only written when a journey fails; avoid the
                        // "No artifacts found ... Configuration error?" noise on green runs.
                        if (fileExists('target/selenium-screenshots')) {
                            archiveArtifacts artifacts: 'target/selenium-screenshots/**',
                                             allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        stage('Package') {
            steps {
                bat 'mvnw.cmd -B -DskipTests package'
                bat 'if not exist "target\\certificate-expiry-monitor-1.0.0.jar" exit /b 1'
                archiveArtifacts artifacts: 'target/*.jar',
                                 fingerprint: true
            }
        }

        stage('Docker Image') {
            when {
                expression { params.BUILD_IMAGE }
            }
            steps {
                // Fail with a clear message when the agent has no docker CLI on PATH.
                bat 'where docker'
                bat 'docker build -t "%REGISTRY%/%IMAGE_NAME%:%IMAGE_TAG%" -t "%REGISTRY%/%IMAGE_NAME%:latest" .'
            }
        }

        stage('Push Image') {
            when {
                allOf {
                    expression { params.PUSH_IMAGE }
                    expression { params.BUILD_IMAGE }
                }
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    // Do NOT pipe the secret with cmd.exe `echo %VAR% |`. On Windows
                    // cmd appends a trailing space before the pipe, and
                    // `docker login --password-stdin` only strips CR/LF (not spaces),
                    // so the space becomes part of the token and Docker Hub answers
                    // "unauthorized: incorrect username or password". Pipe the
                    // environment variable from PowerShell instead, and never
                    // interpolate the secret into the command line or logs.
                    bat '''
                        @echo off
                        powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = $env:DOCKER_PASS; if ([string]::IsNullOrEmpty($p)) { Write-Host 'DIAG: DOCKER_PASS is empty'; exit 3 }; if ($p.Length -ne $p.Trim().Length) { Write-Host 'DIAG: credential has surrounding whitespace; the stored token may have been pasted with extra characters' }; $p | docker login $env:REGISTRY --username $env:DOCKER_USER --password-stdin; if ($LASTEXITCODE -ne 0) { Write-Host ('DIAG: %REGISTRY% rejected the credential (docker login exit code ' + $LASTEXITCODE + ')'); exit $LASTEXITCODE }; Write-Host 'DIAG: docker login succeeded'; exit 0"
                        if errorlevel 1 exit /b 1
                        powershell -NoProfile -ExecutionPolicy Bypass -Command "docker push ($env:REGISTRY + '/' + $env:IMAGE_NAME + ':' + $env:IMAGE_TAG); exit $LASTEXITCODE"
                        if errorlevel 1 exit /b 1
                        powershell -NoProfile -ExecutionPolicy Bypass -Command "docker push ($env:REGISTRY + '/' + $env:IMAGE_NAME + ':latest'); exit $LASTEXITCODE"
                        if errorlevel 1 exit /b 1
                        powershell -NoProfile -ExecutionPolicy Bypass -Command "docker logout $env:REGISTRY"
                    '''
                }
            }
        }

        stage('Deploy Container') {
            when {
                expression { params.BUILD_IMAGE && params.DEPLOY_CONTAINER }
            }
            steps {
                // Continuous deployment: replace the running container with the
                // freshly built image, front it with Nginx (deploy/nginx/nginx.conf)
                // and gate success on the health endpoint through the proxy.
                //
                // Why the standalone `docker-compose.exe` and not `docker compose`:
                // the Compose CLI plugin is discovered through the *user* config dir
                // (%DOCKER_CONFIG%\cli-plugins). The Jenkins service account has no
                // such directory, so `docker compose ...` falls back to parsing the
                // whole line as a root docker command and dies with
                // `unknown shorthand flag: 'f' in -f` (exit 125). The standalone
                // binary sits next to docker.exe on the machine PATH, so it resolves
                // for every account. The file is existence-checked and parsed
                // (`config --quiet`) before anything is deployed.
                //
                // The health body comes back as Byte[] (nginx answers
                // application/json, which Windows PowerShell does not decode), so it
                // is converted to text before the UP match; otherwise the gate can
                // never pass even on HTTP 200.
                bat '''
                    @echo off
                    where docker || exit /b 1
                    where docker-compose || exit /b 1
                    set "IMAGE_REF=%REGISTRY%/%IMAGE_NAME%:%IMAGE_TAG%"
                    set "HTTP_PORT=%DEPLOY_PORT%"
                    if not exist "deploy/docker-compose.deploy.yml" (echo DIAG: compose file not found: deploy/docker-compose.deploy.yml & exit /b 1)
                    docker-compose -f deploy/docker-compose.deploy.yml config --quiet || exit /b 1
                    docker-compose -f deploy/docker-compose.deploy.yml up -d --force-recreate || exit /b 1
                    powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='http://localhost:%DEPLOY_PORT%/actuator/health'; for($i=0;$i -lt 30;$i++){ try{ $r=Invoke-WebRequest -UseBasicParsing $u -TimeoutSec 3; $b=$r.Content; if($b -isnot [string]){ $b=[Text.Encoding]::UTF8.GetString($b) }; if($r.StatusCode -eq 200 -and $b -match 'UP'){ Write-Host 'DIAG: deployed container healthy behind Nginx'; exit 0 } }catch{}; Start-Sleep -Seconds 2 }; Write-Host 'DIAG: health check failed'; exit 1"
                    if errorlevel 1 exit /b 1
                '''
            }
        }

        stage('Deploy with Ansible') {
            when {
                expression {
                    isUnix() && params.DEPLOY_HOST?.trim()
                }
            }
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'deploy-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    sh '''
                        ANSIBLE_HOST_KEY_CHECKING=False \
                        ANSIBLE_PRIVATE_KEY_FILE="$SSH_KEY" \
                        ansible-playbook -i ansible/inventory/hosts.ini ansible/site.yml \
                          -e "app_user=$SSH_USER" \
                          -e "app_host=$DEPLOY_HOST" \
                          -e "image=${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}" \
                          -e "deploy=true"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Build ${env.BUILD_NUMBER} completed successfully."
        }
        failure {
            echo 'Build failed. Review the stage console output and test reports.'
        }
        always {
            cleanWs()
        }
    }
}
