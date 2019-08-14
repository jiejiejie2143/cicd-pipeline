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
                        checkout([$class: 'GitSCM', branches: [[name: env.branch]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'gitlab', url: git_repository]]])
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
                        sh 'mvn clean install -DskipTests'
                    }
                }
            }
        }
        stage('docker打包') {
            steps {
                script {
                    dir(env.work_dir) {
                        echo "开始docker打包"
                        sh 'bash ../init.sh'+' '+env.appinfo+' '+env.app_name
                        sh 'docker build -t registry-vpc.cn-hangzhou.aliyuncs.com/ml_test/'+env.app_name+':'+env.BUILD_NUMBER+' .'
                    }
                }
            }
        }
        stage('推送仓库') {
            steps {
                script {
                    dir(env.work_dir) {
                        echo "镜像推送至阿里云仓库"
                        sh 'docker login registry-vpc.cn-hangzhou.aliyuncs.com'
                        sh 'docker push registry-vpc.cn-hangzhou.aliyuncs.com/ml_test/'+env.app_name+':'+env.BUILD_NUMBER
                    }
                }
            }
        }
        stage('镜像部署') {
            steps {
                script {
                    dir(env.work_dir) {
                        echo "镜像部署至k8s"
                        sh 'docker rmi registry-vpc.cn-hangzhou.aliyuncs.com/ml_test/'+env.app_name+':'+env.BUILD_NUMBER
                    }
                }
            }
        }
    }
}