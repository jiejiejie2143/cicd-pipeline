pipeline {
    agent any
    
    stages {
        stage('拉取gitlab代码') {
            steps {
                script {
                    env.ci_dir =  env.JOB_BASE_NAME+'-ci'
                    dir(env.ci_dir) {
                        echo "开始拉取git代码"
                        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'yanjie', url: 'http://10.9.52.243:8088/cloud/ml-auth.git']]])                       
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
        stage('镜像推送') {
            steps {
                script {
                    dir(env.ci_dir) {
                        echo "镜像推送"
                    }
                }
            }
        }
    }
}