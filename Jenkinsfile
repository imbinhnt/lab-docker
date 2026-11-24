// Jenkins declarative pipeline for java-web-demo.
//
// Required Jenkins configuration:
//   - Credential "dockerhub" (Username with password): Docker Hub user + token
//   - Credential "ansible-vault-password" (Secret file): Ansible Vault password
//   - Agent with: JDK 21, Maven Wrapper (no global Maven needed), Docker, Ansible
//
// The GitHub Actions workflow in .github/workflows/ci.yml is intentionally
// kept as-is; this file is the Jenkins equivalent.

pipeline {
    agent any

    options {
        timestamps()
        ansiColor('xterm')
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        choice(name: 'DEPLOY_ENV', choices: ['staging', 'prod'],
                description: 'Ansible inventory to deploy to (main/tag builds only)')
        string(name: 'ANSIBLE_VAULT_CREDENTIALS_ID', defaultValue: 'ansible-vault-password',
                description: 'Jenkins Secret file credential holding the Ansible Vault password')
    }

    environment {
        MAVEN_OPTS           = '-Dmaven.repo.local=.m2/repository'
        DOCKER_BUILDKIT      = '1'
        DOCKERHUB_CRED_ID    = 'dockerhub'
        IMAGE_REPO           = 'java-web-demo'
        ANSIBLE_FORCE_COLOR  = 'true'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git rev-parse --short HEAD > .git-short-sha'
            }
        }

        stage('Build') {
            steps {
                sh './mvnw -B -DskipTests clean package'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw -B test'
            }
        }

        stage('JaCoCo') {
            steps {
                // Reuse the exec data produced by the Test stage and enforce
                // the coverage thresholds configured in pom.xml.
                sh './mvnw -B verify -DskipTests'
            }
        }

        stage('Build image') {
            steps {
                script {
                    env.GIT_SHORT_SHA = readFile('.git-short-sha').trim()
                    env.IMAGE_SHA = "${env.IMAGE_REPO}:sha-${env.GIT_SHORT_SHA}"
                    env.IMAGE_COMMIT = "${env.IMAGE_REPO}:${env.GIT_COMMIT}"
                    sh '''
                        set -eux
                        docker build \
                          --label "org.opencontainers.image.revision=${GIT_COMMIT}" \
                          --label "org.opencontainers.image.source=${GIT_URL:-unknown}" \
                          -t "${IMAGE_SHA}" \
                          -t "${IMAGE_COMMIT}" \
                          .
                    '''
                }
            }
        }

        stage('Push Docker Hub') {
            when {
                anyOf {
                    branch 'main'
                    buildingTag()
                }
            }
            steps {
                withCredentials([usernamePassword(
                        credentialsId: env.DOCKERHUB_CRED_ID,
                        usernameVariable: 'DOCKERHUB_USER',
                        passwordVariable: 'DOCKERHUB_TOKEN')]) {
                    script {
                        env.IMAGE_NAMESPACED_SHA = "${env.DOCKERHUB_USER}/${env.IMAGE_SHA}"
                        env.IMAGE_NAMESPACED_COMMIT = "${env.DOCKERHUB_USER}/${env.IMAGE_COMMIT}"
                        env.IMAGE_NAMESPACED_LATEST = "${env.DOCKERHUB_USER}/${env.IMAGE_REPO}:latest"
                    }
                    sh '''
                        set -eux
                        echo "$DOCKERHUB_TOKEN" | docker login -u "$DOCKERHUB_USER" --password-stdin
                        docker tag "${IMAGE_SHA}" "${IMAGE_NAMESPACED_SHA}"
                        docker tag "${IMAGE_COMMIT}" "${IMAGE_NAMESPACED_COMMIT}"
                        docker push "${IMAGE_NAMESPACED_SHA}"
                        docker push "${IMAGE_NAMESPACED_COMMIT}"
                        docker tag "${IMAGE_SHA}" "${IMAGE_NAMESPACED_LATEST}"
                        docker push "${IMAGE_NAMESPACED_LATEST}"
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy (Ansible)') {
            when {
                anyOf {
                    branch 'main'
                    buildingTag()
                }
            }
            steps {
                withCredentials([file(
                        credentialsId: params.ANSIBLE_VAULT_CREDENTIALS_ID,
                        variable: 'ANSIBLE_VAULT_PASSWORD_FILE')]) {
                    script {
                        env.IMAGE_TAG = env.GIT_COMMIT
                        env.DEPLOY_TARGET = params.DEPLOY_ENV
                    }
                    sh '''
                        set -eux
                        ansible-playbook \
                          -i "deploy/ansible/inventory/${DEPLOY_TARGET}/hosts.yml" \
                          deploy/ansible/deploy.yml \
                          --limit "${DEPLOY_TARGET}" \
                          -e "image_tag=${IMAGE_TAG}" \
                          -e "app_env=${DEPLOY_TARGET}" \
                          --vault-password-file "$ANSIBLE_VAULT_PASSWORD_FILE"
                    '''
                }
            }
        }
    }

    post {
        always {
            junit 'target/surefire-reports/*.xml', allowEmptyResults: true
            jacoco execPattern: 'target/jacoco.exec',
                   classPattern: 'target/classes',
                   sourcePattern: 'src/main/java',
                   exclusionPattern: ''
            archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
        }
        success {
            echo "Pipeline succeeded for ${env.GIT_COMMIT} (${env.BRANCH_NAME ?: env.TAG_NAME})"
        }
        failure {
            echo "Pipeline failed for ${env.GIT_COMMIT}; check the Test/JaCoCo/Docker stages."
        }
    }
}
