pipeline {
    agent {
        docker {
            image 'maven:3.9-eclipse-temurin-21'
            args  '--platform linux/amd64 -v /var/run/docker.sock:/var/run/docker.sock -v maven-cache:/root/.m2'
        }
    }

    environment {
        NO_COLOR     = '1'
        CLICOLOR     = '0'
        TERM         = 'dumb'
        CMT_CONSOLE_HOME = "${WORKSPACE}/cmt-console"
        TESTCONTAINERS_HOST_OVERRIDE = 'host.docker.internal'
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        skipDefaultCheckout(true)
    }

    stages {
        stage('Checkout') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: scm.branches,
                    extensions: [[$class: 'CleanBeforeCheckout']],
                    userRemoteConfigs: scm.userRemoteConfigs
                ])
            }
        }

        stage('Build Console') {
            steps {
                sh 'chmod +x build.sh && ./build.sh -p c'
                sh '''
                    mkdir -p ${CMT_CONSOLE_HOME}
                    tar xzf target/CUBRID-Migration-Toolkit-console-*.tar.gz \
                        --strip-components=1 -C ${CMT_CONSOLE_HOME}
                    chmod +x ${CMT_CONSOLE_HOME}/migration.sh
                    echo "Console provisioned: $(ls ${CMT_CONSOLE_HOME})"
                '''
            }
        }

        stage('E2E Test') {
            steps {
                dir('e2e') {
                    sh '''
                        mvn test \
                            -Dmaven.test.failure.ignore=true \
                            -Dpicocli.ansi=false \
                            -Dorg.fusesource.jansi.Ansi.disable=true
                    '''
                }
            }
        }
    }

    post {
        always {
            dir('e2e') {
                junit allowEmptyResults: true,
                    testResults: 'target/surefire-reports/*.xml'

                archiveArtifacts allowEmptyArchive: true, fingerprint: true,
                    artifacts: 'target/e2e/**/diff.patch, target/e2e/**/actual.log, target/e2e/**/expected.log'
            }
        }
    }
}
