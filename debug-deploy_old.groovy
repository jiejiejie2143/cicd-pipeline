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
        stage('处理applo和启动参数') {
            steps {
                script {
                    delStarParas = load env.WORKSPACE+'/model/delStarParas.groovy'
                    delStarParas.delStarParas()
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
        stage('远程部署') {
            steps {
                script {
                    deployToHosts = load env.WORKSPACE+'/model/deployToHosts.groovy'
                    deployToHosts.deployToHosts()
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