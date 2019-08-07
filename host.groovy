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
                    env.work_dir = env.ci_dir+'/'+env.app_name
                    sh 'mkdir -pv '+env.ci_dir
                    
                    def git_repository = sh returnStdout: true, script: 'cat programs/'+env.project+'/program_paras|grep '+env.app_name+'_program|awk -F "=" \'{print $2}\''
                    def git_branch = env.branch

                    env.appinfo = sh returnStdout: true, script: 'cat programs/'+env.project+'/program_paras|grep '+env.app_name+'_appinfo|awk -F "=" \'{print $2}\''
                    env.appinfo = env.appinfo.tokenize('\n')[0]
                    env.apollo = sh returnStdout: true, script: 'cat programs/'+env.project+'/'+env.appenv+'_paras|grep '+env.app_name+'_apollo|awk -F "&" \'{print $2}\''
                    env.apollo = env.apollo.tokenize('\n')[0]
                    env.addr = sh returnStdout: true, script: 'cat programs/'+env.project+'/'+env.appenv+'_paras|grep '+env.app_name+'_addr|awk -F "=" \'{print $2}\''
                    env.addr = env.addr.tokenize('\n')[0]

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
                    dir(env.work_dir) {
                        echo "开始maven构建"
                        if (env.appinfo == 'jar'||env.appinfo == 'war')  {
                            sh 'mvn clean install -DskipTests'
                        } else if (env.appinfo == 'deploy')  {
                            sh 'clean install deploy'
                        } else {
                            error env.appinfo+'类型参数错误，请检查打包的语言类型'
                        }
                    }
                }
            }
        }
        stage('远程部署') {
            steps {
                script {
                    dir(env.work_dir) {
                        echo "文件传输至跳板机"
                        echo env.appinfo
                        echo env.apollo
                        echo env.addr
                        remote_Dir = env.appenv+'/'+env.project+'/'+env.app_name
                        source_Files = 'target/'+env.app_name+'.'+env.appinfo
                        echo source_Files
                        def cmd_exe = 'ls /data'
                        sshPublisher(publishers: [sshPublisherDesc(configName: '114.55.42.166--jenkins_proxy（admin）', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: """$cmd_exe""", execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: remote_Dir, remoteDirectorySDF: false, removePrefix: 'target/', sourceFiles: source_Files)], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
                    }
                }
            }
        }
    }
}