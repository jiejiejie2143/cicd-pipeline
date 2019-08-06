pipeline {
    agent any
    
    stages {
        stage('拉取gitlab代码') {
            steps {
                script {
                    env.project = env.JOB_BASE_NAME.tokenize('+')[0]
                    env.app_name = env.JOB_BASE_NAME.tokenize('+')[1]
                    env.branch = env.JOB_BASE_NAME.tokenize('+')[2]
                    env.appenv = env.JOB_BASE_NAME.tokenize('+')[3]
                    env.ci_dir =  env.app_name+'-ci'
                    sh 'mkdir -pv '+env.ci_dir
                    
                    def git_repository = sh returnStdout: true, script: 'cat programs/'+env.project+'/program_paras|grep '+env.app_name+'_program|awk -F "=" \'{print $2}\''
                    def git_branch = env.branch
                    
                    dir(env.ci_dir) {
                        echo "开始拉取git代码"
                        checkout([$class: 'GitSCM', branches: [[name: git_branch]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'bdf54155-8605-461e-891c-6eabacf536b8', url: git_repository]]])
                    }
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
                    dir(env.ci_dir) {
                        echo "开始maven构建"
                        sh 'mvn clean install -DskipTests'
                    }
                }
            }
        }
        stage('远程部署') {
            steps {
                script {
                    dir(env.ci_dir) {
                        echo env.branch+"文件传输至跳板机"
                        def env_appinfo = sh returnStdout: true, script: 'cat ../programs/'+env.project+'/program_paras|grep '+env.app_name+'_appinfo|awk -F "=" \'{print $2}\''
                        env_appinfo = env_appinfo.tokenize('\n')
                        def env_apollo = sh returnStdout: true, script: 'cat ../programs/'+env.project+'/'+env.appenv+'_paras|grep '+env.app_name+'_apollo|awk -F "&" \'{print $2}\''
                        def env_addr = sh returnStdout: true, script: 'cat ../programs/'+env.project+'/'+env.appenv+'_paras|grep '+env.app_name+'_addr|awk -F "=" \'{print $2}\''
                        echo env_appinfo+"文件传输至跳板机"
                        echo env_apollo+"文件传输至跳板机"
                        echo env_addr+"文件传输至跳板机"
                        if (env_appinfo == 'war') {
                            source_Files = 'target/'+env.app_name+'.war'
                        } else if (env_appinfo == 'jar')  {
                            source_Files = 'target/'+env.app_name+'.jar'
                        } else {
                            error env_appinfo+'项目参数错误，请检查打包的语言类型'
                        }
                        echo source_Files
                        sshPublisher(publishers: [sshPublisherDesc(configName: '114.55.42.166--jenkins_proxy（admin）', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: '''ls /data''', execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: env.project, remoteDirectorySDF: false, removePrefix: 'target/', sourceFiles: source_Files)], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
                    }
                }
            }
        }
    }
}