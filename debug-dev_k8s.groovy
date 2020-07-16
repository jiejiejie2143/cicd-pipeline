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
        stage('maven构建') {
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