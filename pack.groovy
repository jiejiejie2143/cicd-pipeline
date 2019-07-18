pipeline {
    agent any
    
    stages {
        stage('拉取gitlab') {
            tools {
                maven 'maven3.6.1'
                jdk 'jdk8_161'
            }
        steps {
            println('hello world!!!')
        }
        }
    }
}