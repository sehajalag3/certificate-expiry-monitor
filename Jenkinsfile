// Jenkins pipeline for the Certificate Expiry Monitor.
// Verified commands (same commands run locally during development):
//   ./mvnw -B test          -> 37 unit + integration tests
//   ./mvnw -B test -Pui     -> 5 Selenium browser journeys (headless Chrome)
//   ./mvnw -B package       -> executable Spring Boot jar
//   docker build -t ...     -> container image
pipeline {
    agent any

    tools {
        maven 'Maven-3.9.9'
        jdk 'JDK-21'
    }

    options {
        timestamps()
        timeout(time: 40, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'RUN_UI_TESTS', defaultValue: true, description: 'Run Selenium journeys (agent must have Google Chrome)')
        booleanParam(name: 'BUILD_IMAGE', defaultValue: true, description: 'Build the Docker image')
        booleanParam(name: 'PUSH_IMAGE', defaultValue: false, description: 'Push the image to the registry')
        string(name: 'REGISTRY', defaultValue: 'docker.io', description: 'Image registry host')
        string(name: 'IMAGE_NAME', defaultValue: 'your-dockerhub-user/certificate-expiry-monitor', description: 'Image repository')
        string(name: 'DEPLOY_HOST', defaultValue: '', description: 'SSH user@host for Ansible deployment (empty skips deploy)')
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
                sh 'git rev-parse --short HEAD'
            }
        }

        stage('Build and unit tests') {
            steps {
                sh './mvnw -B clean test'
            }
            post {
                always {
                    junit allowEmptyResults: false, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Selenium UI journeys') {
            when { expression { params.RUN_UI_TESTS } }
            steps {
                sh './mvnw -B test -Pui'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/TEST-*ui*.xml'
                    archiveArtifacts artifacts: 'target/selenium-screenshots/**', allowEmptyArchive: true
                }
            }
        }

        stage('Package') {
            steps {
                sh './mvnw -B -DskipTests package'
                sh 'test -f target/certificate-expiry-monitor-1.0.0.jar'
            }
        }

        stage('Docker image') {
            when { expression { params.BUILD_IMAGE } }
            steps {
                sh 'docker build -t "${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}" -t "${REGISTRY}/${IMAGE_NAME}:latest" .'
            }
        }

        stage('Push image') {
            when { allOf { expression { params.PUSH_IMAGE }; expression { params.BUILD_IMAGE } } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login "$REGISTRY" -u "$DOCKER_USER" --password-stdin
                        docker push "${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
                        docker push "${REGISTRY}/${IMAGE_NAME}:latest"
                        docker logout "$REGISTRY"
                    '''
                }
            }
        }

        stage('Deploy with Ansible') {
            when { expression { params.DEPLOY_HOST?.trim() } }
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'deploy-ssh-key', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
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
            echo "Build ${env.BUILD_NUMBER} finished. Image: ${env.REGISTRY}/${env.IMAGE_NAME}:${env.IMAGE_TAG}"
        }
        failure {
            echo 'Build failed. Check the test reports and (if present) Selenium screenshots above.'
        }
        always {
            cleanWs()
        }
    }
}
