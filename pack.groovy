pipeline {
    agent any
    
    stages {
        stage('拉取gitlab代码') {
            tools {
                maven 'maven3.6.1'
                jdk 'jdk8_161'
            }
            steps {
                println('hello world!!!')
            }
        }
        stage('打包') {
            steps {
                echo '打包命令：mvn -Dmaven.test.skip=true clean package'
            }
        }
    }
}