
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
                    archiveArtifacts artifacts: 'target/selenium-screenshots/**',
                                     allowEmptyArchive: true
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
                    bat '''
                        @echo off
                        echo %DOCKER_PASS% | docker login "%REGISTRY%" -u "%DOCKER_USER%" --password-stdin
                        if errorlevel 1 exit /b 1
                        docker push "%REGISTRY%/%IMAGE_NAME%:%IMAGE_TAG%"
                        if errorlevel 1 exit /b 1
                        docker push "%REGISTRY%/%IMAGE_NAME%:latest"
                        if errorlevel 1 exit /b 1
                        docker logout "%REGISTRY%"
                    '''
                }
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
