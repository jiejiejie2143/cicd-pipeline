//获取配置文件里自定义的参数的方法，programs_paras里的配置，需加两个参数，第一个为参数关键词，第二个为文件名；环境配置文件里的配置，只写一个参数关键词即可
//参数以应用指定参数优先使用，如果没有指定参数，则使用公共参数
def getParas(keyword,keyenv = env.appenv) {
    self_paras = sh returnStdout: true, script: 'cat programs/' + env.project + '/'+keyenv+'_paras|grep ' + env.app_name + '_' + keyword + '|awk -F "=" \'{print $2}\''
    self_paras = self_paras.tokenize('\n')[0]
    common_paras = sh returnStdout: true, script: 'cat programs/' + env.project + '/'+keyenv+'_paras|grep ' + env.project + '_' + keyword + '|awk -F "=" \'{print $2}\''
    common_paras = common_paras.tokenize('\n')[0]
    if (self_paras == null )  {
        paras = common_paras
    } else {
        paras = self_paras
    }
    return paras
}
pipeline {
    agent any

    stages {
        stage('拉取gitlab代码') {
            steps {
                script {
                    //分割项目名作为参数
                    env.project = env.JOB_BASE_NAME.tokenize('+')[0]
                    env.app_name = env.JOB_BASE_NAME.tokenize('+')[1]
                    env.branch = env.JOB_BASE_NAME.tokenize('+')[2]
                    env.appenv = env.JOB_BASE_NAME.tokenize('+')[3]
                    env.ci_dir = env.app_name + '-ci'
                    sh 'mkdir -pv ' + env.ci_dir

                    //获取项目自定义参数值
                    def git_repository = getParas('program', 'program')
                    echo '获取gitlab地址为：' + git_repository
                    echo '分支为：' + env.branch
                    env.appinfo = getParas('appinfo', 'program')
                    echo '应用类型为：' + env.appinfo
                    env.apollo = getParas('apollo')

                    //处理apollo参数，因为参数中有等号，取值会出错，需要进行二次处理
                    if (env.appinfo == 'jar') {
                        env.apollo = '-Denv=' + env.apollo
                    } else if (env.appinfo == 'war') {
                        env.apollo = 'env=' + env.apollo
                    } else {
                        echo '其他类型，apollo参数不做处理'
                    }

                    echo 'apollp环境为：' + env.apollo
                    env.addr = getParas('addr')
                    echo '需要部署的IP为：' + env.addr
                    env.start = getParas('start')
                    echo '应用需要启动的个数为：' + env.start
                    env.mem = getParas('mem')
                    echo '应用启动所需的内存为：' + env.mem
                    env.rele = getParas('rele')
                    echo '该项目关联的下游项目为：' + env.rele
                    env.pom = getParas('pom', 'program')
                    echo '该项目pom文件的层级为：' + env.pom

                    //判断pom文件的目录层级，给与正确的工作路径
                    if (env.pom == '0') {
                        env.work_dir = env.ci_dir
                    } else if ( env.pom == '1' ) {
                        env.work_dir = env.ci_dir + '/' + env.app_name
                    } else if ( env.pom == '2' ) {
                        env.work_dir = env.ci_dir + '/' + env.project + '/' + env.app_name
                    } else {
                        env.work_dir = env.ci_dir + '/' + env.app_name
                    }

                    dir(env.ci_dir) {
                        echo "开始拉取git代码"
                        checkout([$class: 'GitSCM', branches: [[name: env.branch]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'jiangcheng', url: git_repository]]])
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
                        if (env.app_name.contains('facade') || env.app_name.contains('common')) {
                            sh 'mvn clean install deploy'
                        } else {
                            sh 'mvn clean install -DskipTests'
                        }
                    }
                }
            }
        }
        stage('远程部署') {
            steps {
                script {
                    if (env.app_name.contains('facade') || env.app_name.contains('common')) {
                        echo "该项目不需要远程部署"
                        build env.rele
                    } else {
                        echo "继续远程部署流程"
                        dir(env.work_dir) {
                        echo "文件传输至跳板机"
                        echo env.appinfo
                        echo env.apollo
                        echo env.addr
                        remote_Dir = env.appenv+'/'+env.project+'/'+env.app_name
                        source_Files = 'target/'+env.app_name+'.'+env.appinfo+',target/lib/*.jar'
                        jenkins_path = '/data/jenkins/jenkins_common_jar.sh'
                        des_path = '/data/'+env.project+'/'+env.app_name
                        file_path = '/data/jenkins/'+remote_Dir
                        def cmd_exe = jenkins_path+' '+env.start+' '+env.apollo+' '+des_path+' '+env.app_name+' '+env.mem+' '+file_path+' '+env.addr
                        echo cmd_exe
                        sshPublisher(publishers: [sshPublisherDesc(configName: '114.55.42.166--jenkins_proxy（admin）', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: """$cmd_exe""", execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: remote_Dir, remoteDirectorySDF: false, removePrefix: 'target/', sourceFiles: source_Files)], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: false)])
                        }
                    }
                }
            }
        }
    }
}