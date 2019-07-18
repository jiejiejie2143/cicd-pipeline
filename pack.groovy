pipeline {
    agent any
    
    stages {
        stage('拉取gitlab代码') {
            tools {
                maven 'maven3.6.1'
                jdk 'jdk8_161'
            }
            steps {
                script {
                    dir(test) {
                        def git_repository = 'http://10.9.52.243:8088/cloud/ml-auth.git'
                        def git_branch = master
                        echo "开始拉取git代码"
                        checkout([$class: 'GitSCM', branches: [[name: git_branch]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'yanjie', url: git_repository]]])                        
                    }

                }
            }
        }
        stage('打包') {
            steps {
                echo '打包命令：mvn -Dmaven.test.skip=true clean package'
            }
        }
    }
}