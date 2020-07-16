import org.apache.tools.ant.types.selectors.SelectSelector

//统一打包，物理机和k8s都用这个
//获取配置文件里自定义的参数的方法，programs_paras里的配置，需加两个参数，第一个为参数关键词，第二个为文件名；环境配置文件里的配置，只写一个参数关键词即可
//参数以应用指定参数优先使用，如果没有指定参数，则使用公共参数
def getParas(keyword,keyenv = env.appenv) {
    self_paras = sh returnStdout: true, script: "cat programs/${project}/${keyenv}_paras | grep ${app_name}_${keyword} | awk -F '==' '{print \$2}'"
    self_paras = self_paras.tokenize('\n')[0]
    common_paras = sh returnStdout: true, script: "cat programs/${project}/${keyenv}_paras | grep ${project}_${keyword}| awk -F '==' '{print \$2}'"
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
    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 10, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    stages {
        stage('拉取gitlab代码') {
            steps {
                script {
                    //分割项目名作为参数
                    env.project = env.JOB_BASE_NAME.tokenize('+')[0]
                    env.app_name = env.JOB_BASE_NAME.tokenize('+')[1]
                    env.branch = env.JOB_BASE_NAME.tokenize('+')[2]
                    env.appenv = env.JOB_BASE_NAME.tokenize('+')[3]
                    env.ci_dir = "${env.app_name}-ci"
                    sh "mkdir -pv ${ci_dir}"

                    //获取项目自定义参数值
                    env.git_repository = getParas('program', 'program')
                    env.appinfo = getParas('appinfo', 'program')

                    env.rele = getParas('rele')
                    env.pom = getParas('pom', 'program')

                    //判断pom文件的目录层级，给与正确的工作路径
                    if (env.pom == '0') {
                        env.work_dir = "${env.ci_dir}"
                        env.dot = '../'
                    } else if ( env.pom == '1' ) {
                        env.work_dir = "${env.ci_dir}/${env.app_name}"
                        env.dot = '../../'
                    } else if ( env.pom == '2' ) {
                        env.work_dir = "${env.ci_dir}/${env.project}/${env.app_name}"
                        env.dot = '../../../'
                    } else {
                        env.work_dir = "${env.ci_dir}/${env.app_name}"
                    }
                    dir(env.ci_dir) {
                        echo "开始拉取git代码"
                        checkout([$class: 'GitSCM', branches: [[name: env.branch]], doGenerateSubmoduleConfigurations: false,
                                  extensions: [[$class: 'CleanBeforeCheckout']], submoduleCfg: [],
                                  userRemoteConfigs: [[credentialsId: 'gitlab', url: git_repository]]])
                    }
                }
            }
        }
        stage('maven构建并推送app_repo') {
            tools {
                maven 'maven3.0.5'
                jdk 'jdk8'
            }
            steps {
                script {
                    dir(env.work_dir) {
                        echo "打包目录是 ${env.work_dir}"
                        echo "开始maven构建"
                        if (env.app_name.contains('facade') || env.app_name.contains('common')) {
                            sh 'mvn clean install deploy'
                            echo "该项目不需要远程部署"
                            env.rele_list = env.rele.tokenize(',')
                            echo "下游关联项目列表为 ${env.rele_list}"
                            for (rele in env.rele.tokenize(',')){
                                echo  "${rele}"
                                build  job: "${rele}",wait: false
                            }
                        } else {
                            sh 'mvn clean install -DskipTests'
                            dir("./target/") {   //target_dir 下
                                env.app_md5 = sh returnStdout: true, script: "md5sum  ${app_name}.${appinfo} |awk '{print \$1}'"
                                echo "该软件的md5码是  ${env.app_md5}  请核对"
                                sh "mkdir -pv /data/app_repo/${appenv}/${project}/${app_name}"
                                def app_repo_path = "/data/app_repo/${appenv}/${project}/${app_name}/"
                                env.tar_name = "${app_name}_${branch}_${env.BUILD_NUMBER}.tar.gz"
                                lib_status = sh(script:"ls lib/",returnStatus:true)
                                if (lib_status == 0) {
                                    sh returnStdout: true, script: "tar -zcf  ${tar_name}  lib/  ${app_name}.${appinfo} "
                                    sh "\\cp  ${tar_name}  ${app_repo_path}"
                                    sh "chown -R admin:admin /data/app_repo"
                                } else if (lib_status == 2) {
                                    sh returnStdout: true, script: "tar -zcf ${tar_name}  ${app_name}.${appinfo} "
                                    sh "\\cp  ${tar_name}  ${app_repo_path}"
                                    sh "chown -R admin:admin /data/app_repo"
                                } else {
                                    error("command is error,please check")
                                }
                            }
                        }
                    }
                }
            }
        }

    }
    post {
        always {
            script {
                echo  'package tar包 sucsess'
                ddnotify = load env.WORKSPACE+'/script/ddNotifyAlarm.groovy'
                ddnotify.notifyAlarm()

            }
        }
    }
}