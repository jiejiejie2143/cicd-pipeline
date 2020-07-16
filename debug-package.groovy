import org.apache.tools.ant.types.selectors.SelectSelector

pipeline {
    agent any
    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 10, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    stages {
        stage('生成环境变量，拉取gitlab代码') {
            steps {
                script {
                    initEnvSrc = load env.WORKSPACE+'/model/initEnvSrc.groovy'
                    initEnvSrc.generateEnv()
                    initEnvSrc.gitClone()
                }
            }
        }
        stage('maven构建并推送app_repo') {
            tools {
                maven 'maven3.0.5'
                jdk 'jdk8'
            }
            steps {
                script {
                    mavenPackage = load env.WORKSPACE+'/model/mavenPackage.groovy'
                    mavenPackage.mavenPackage()
                    mavenPackage.pushAppRepo()
                }
            }
        }
    }

    post {
        always {
            script {
                mailNotify = load env.WORKSPACE+'/model/mailNotify.groovy'
                mailNotify.mailNotify()

                ddnotify = load env.WORKSPACE+'/script/ddNotifyAlarm.groovy'
                ddnotify.notifyAlarm()

            }
        }
    }
}