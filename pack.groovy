pipeline {
    agent any
    
    stages {
        stage('拉取gitlab代码') {
            steps {
                script {
                    env.project = env.JOB_BASE_NAME.tokenize('+')[0]
                    env.app_name = env.JOB_BASE_NAME.tokenize('+')[1]
                    env.branch = env.JOB_BASE_NAME.tokenize('+')[2]
                    env.appinfo = env.JOB_BASE_NAME.tokenize('+')[3]
                    env.ci_dir =  env.app_name+'-ci'
                    sh 'mkdir -pv '+env.ci_dir
                    
                    def git_repository = sh returnStdout: true, script: 'cat programs/'+env.project+'/program_paras|grep '+env.app_name+'_program|awk -F "=" \'{print $2}\''
                    def git_branch = env.branch
                    
                    dir(env.ci_dir) {
                        echo "开始拉取git代码"
                        checkout([$class: 'GitSCM', branches: [[name: git_branch]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'yanjie', url: git_repository]]])                      
                    }
                }
            }
        }
        stage('maven构建') {
            tools {
                maven 'maven3.6.1'
                jdk 'jdk8_161'
            }
            steps {
                script {
                    dir(env.ci_dir) {
                        echo "开始maven构建"
                        sh 'mvn clean install -DskipTests'
                    }
                }
            }
        }
        stage('docker打包') {
            steps {
                script {
                    dir(env.ci_dir) {
                        echo "开始docker打包"
                        
                    }
                }
            }
        }
        stage('推送仓库') {
            steps {
                script {
                    dir(env.ci_dir) {
                        echo "镜像推送至阿里云仓库"
                    }
                }
            }
        }
        stage('镜像部署') {
            steps {
                script {
                    dir(env.ci_dir) {
                        echo "镜像部署至k8s"
                    }
                }
            }
        }
    }
}