import org.apache.tools.ant.types.selectors.SelectSelector

pipeline {
    agent any
    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 10, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    triggers{
        gitlab( triggerOnPush: true,
                triggerOnMergeRequest: true,
                branchFilterType: "RegexBasedFilter",
                sourceBranchRegex: "dev-.*",
                secretToken: "${env.git_token}")
    }
    stages {
        stage('生成环境变量，拉取gitlab代码') {
            steps {
                script {
                    // 导入 initEnvSrc 模块
                    initEnvSrc = load env.WORKSPACE+'/model/initEnvSrc.groovy'

                    initEnvSrc.generateEnv()

                    echo "项目地址：$env.project\n工程名：$env.app_name\n部署分支：$env.branch\n部署环境：$env.appenv" +
                            "\n集成目录：$env.ci_dir\n代码仓库地址：$env.git_repository\n工程类型：$env.appinfo\n" +
                            "下游关联：$env.rele\nPOM文件层级：$env.pom"

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
//                    mavenPackage.pushAppRepo()
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
        stage('远程部署') {
            steps {
                script {
                    deployToHosts = load env.WORKSPACE+'/model/deployToHosts.groovy'
                    deployToHosts.deployToHosts()
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