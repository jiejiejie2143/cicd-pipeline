def getParas(keyword) {
    common = sh returnStdout: true, script: 'cat programs/' + env.project + '/program_paras|grep ' + env.app_name + '_' + keyword + '|awk -F "=" \'{print $2}\''
    common = common.tokenize('\n')[0]
    return common
}
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

                    env.appinfo = getParas('appinfo')
                    echo env.appinfo
                    env.appinfo_common = sh returnStdout: true, script: 'cat programs/'+env.project+'/program_paras|grep '+env.project+'_appinfo|awk -F "=" \'{print $2}\''
                    env.appinfo_common = env.appinfo_common.tokenize('\n')[0]
//                    env.appinfo = sh returnStdout: true, script: 'cat programs/'+env.project+'/program_paras|grep '+env.app_name+'_appinfo|awk -F "=" \'{print $2}\''
//                    env.appinfo = env.appinfo.tokenize('\n')[0]
                    env.apollo = sh returnStdout: true, script: 'cat programs/'+env.project+'/'+env.appenv+'_paras|grep '+env.app_name+'_apollo|awk -F "&" \'{print $2}\''
                    env.apollo = env.apollo.tokenize('\n')[0]
                    env.addr = sh returnStdout: true, script: 'cat programs/'+env.project+'/'+env.appenv+'_paras|grep '+env.app_name+'_addr|awk -F "=" \'{print $2}\''
                    env.addr = env.addr.tokenize('\n')[0]
                    env.start = sh returnStdout: true, script: 'cat programs/'+env.project+'/'+env.appenv+'_paras|grep '+env.app_name+'_start|awk -F "=" \'{print $2}\''
                    env.start = env.start.tokenize('\n')[0]
                    env.mem = sh returnStdout: true, script: 'cat programs/'+env.project+'/'+env.appenv+'_paras|grep '+env.app_name+'_mem|awk -F "=" \'{print $2}\''
                    env.mem = env.mem.tokenize('\n')[0]

                    echo "appinfo_common:"+env.appinfo_common
                    echo "appinfo:"+env.appinfo
                    if (env.appinfo == 'null')  {
                        env.appinfo = env.appinfo_common
                        echo "没有设置appinfo参数，使用公共参数:"+env.appinfo
                        } else {
                            echo '取得appinfo参数：'+env.appinfo
                        }
//                    dir(env.ci_dir) {
//                        echo "开始拉取git代码"
//                        checkout([$class: 'GitSCM', branches: [[name: git_branch]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'bdf54155-8605-461e-891c-6eabacf536b8', url: git_repository]]])
//                    }
                }
            }
        }
//        stage('maven构建') {
//            tools {
//                maven 'maven3.0.5'
//                jdk 'jdk8'
//            }
//            steps {
//                script {
//                    dir(env.work_dir) {
//                        echo "开始maven构建"
//                        if (env.appinfo == 'jar'||env.appinfo == 'war')  {
//                            sh 'mvn clean install -DskipTests'
//                        } else if (env.appinfo == 'deploy')  {
//                            sh 'clean install deploy'
//                        } else {
//                            error env.appinfo+'类型参数错误，请检查打包的语言类型'
//                        }
//                    }
//                }
//            }
//        }
//        stage('远程部署') {
//            steps {
//                script {
//                    dir(env.work_dir) {
//                        echo "文件传输至跳板机"
//                        echo env.appinfo
//                        echo env.apollo
//                        echo env.addr
//                        remote_Dir = env.appenv+'/'+env.project+'/'+env.app_name
//                        source_Files = 'target/'+env.app_name+'.'+env.appinfo
//                        jenkins_path = '/data/jenkins/jenkins_common_jar.sh'
//                        des_path = '/data/'+env.project+'/'+env.app_name
//                        file_path = '/data/jenkins/'+remote_Dir
//                        def cmd_exe = jenkins_path+' '+env.start+' '+env.apollo+' '+des_path+' '+env.app_name+' '+env.mem+' '+file_path+' '+env.addr
//                        echo cmd_exe
//                        sshPublisher(publishers: [sshPublisherDesc(configName: '114.55.42.166--jenkins_proxy（admin）', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: """ls /data""", execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: remote_Dir, remoteDirectorySDF: false, removePrefix: 'target/', sourceFiles: source_Files)], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
//                    }
//                }
//            }
//        }
    }
}