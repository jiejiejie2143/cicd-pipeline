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
        stage('生成环境变量') {
            steps {
                script {
                    initEnvSrc = load env.WORKSPACE+'/model/initEnvSrc.groovy'
                    initEnvSrc.generateEnv()

                }
            }
        }
        stage('选择软件包') {
            steps {
                script {
                    choseRpm = load env.WORKSPACE+'/model/choseRpm.groovy'
                    choseRpm.choseRpm()
                }
            }
        }
        stage('生成docker镜像并推送registry') {
            steps {
                script {
                    buildImg = load env.WORKSPACE+'/model/buildImg.groovy'
                    buildImg.generateTag()
                    buildImg.buildImg()
                    buildImg.pushImg()
                }
            }
        }
        stage('远程部署到k8s集群') {
            steps {
                script {
                    deployToK8s = load env.WORKSPACE+'/model/deployToK8s.groovy'
                    deployToK8s.deployToK8s()
                }
            }
        }
        stage('更新到下一级仓库') {
            steps {
                script {
                    pushToNextRepo = load env.WORKSPACE+'/model/pushToNextRepo.groovy'
                    pushToNextRepo.pushToNextRepo()
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