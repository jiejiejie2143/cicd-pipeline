import org.apache.tools.ant.types.selectors.SelectSelector

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
                    echo " 获取gitlab地址为：${git_repository} "
                    echo "分支为：${env.branch} "
                    env.appinfo = getParas('appinfo', 'program')
                    echo "应用类型为：${appinfo} "
                    env.apollo = getParas('apollo')

                    //处理apollo参数，因为参数中有等号，取值会出错，需要进行二次处理
                    if (env.appinfo == 'jar') {
                        env.apollo = "-Denv=${env.apollo}"
                    } else if (env.appinfo == 'war') {
                        env.apollo_base = sh returnStdout: true, script: "echo ${env.apollo} | awk  '{print \$1}' "
                        env.apollo_base = env.apollo_base.tokenize('\n')[0]
                        env.apollo_extr = sh returnStdout: true, script: "echo ${env.apollo} | awk -F '-D' '{print \$2}'"
                        env.apollo_extr = env.apollo_extr.tokenize('\n')[0]
                        echo "额外的applo参数为：${env.apollo_extr}"
                        if (env.apollo_extr == "null" )  {
                            env.apollo = 'env='+env.apollo_base
                        } else {
                            env.apollo = 'env='+env.apollo_base+'\\\\n'+env.apollo_extr
                        }
                        env.tomcat = getParas('tomcat', 'program')
                        echo "该项目的Apollo 环境为 ${env.apollo} "
                        echo "该项目tomcat的目录为：${tomcat}"
                    } else {
                        echo '其他类型，apollo参数不做处理'
                    }

                    echo "apollp环境为：${apollo}"
                    env.addr = getParas('addr')
                    echo "需要部署的IP为：${env.addr}"
                    env.start = getParas('start')
                    echo "应用需要启动的个数为：${start} "
                    env.mem = getParas('mem')
                    echo "应用启动预分配的内存为：${mem}"
                    env.rele = getParas('rele')
                    echo "该项目关联的下游项目为： ${rele}"
                    env.pom = getParas('pom', 'program')
                    echo "该项目pom文件的层级为: ${pom}"

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
                                  userRemoteConfigs: [[credentialsId: '58ef9945-683d-415f-bce6-b075e1273074', url: git_repository]]])
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
                    //根据ip判断是用什么用户进行部署
                    if (env.addr.startsWith('10.') ) {
                        echo "该项目是经典网络，需要用root用户"
                        env.deploy_user = "root"
                    } else if (env.addr.startsWith('172.')) {
                        echo "该项目是vpc网络，需要用admin用户"
                        env.deploy_user = "admin"
                    }
                    else {
                        error "非法都ip地址 退出部署"
                    }
                    if (env.app_name.contains('facade') || env.app_name.contains('common')) {
                        echo "该项目不需要远程部署"
                        env.rele_list = env.rele.tokenize(',')
                        echo "下游关联项目列表为 ${env.rele_list}"
                        for (rele in env.rele.tokenize(',')){
                            echo  "${rele}"
                            build  job: "${rele}",wait: false
                        }

                    } else {
                        echo "继续远程部署流程"
                        dir(env.work_dir) {
                        echo "文件传输至跳板机"
                        echo env.appinfo
                        echo env.apollo
                        echo env.addr           //需要部署的服务器地址
                        remote_Dir = "${env.appenv}/${env.project}/${env.app_name}"     //打包后文件上传到跳板机的目录
                        source_Files = "target/${app_name}.${appinfo},target/lib/*.jar"
                            //其实这个是兼容的！！！有无lib都可以，前提是jar包和superapp-light-scene-device-touch（app名）一定要一致，不一致让开发改为一致！
                        //source_Files = "target/${app_name}.${appinfo}"
                        jenkins_path = "/data/jenkins/jenkins_common_${appinfo}.sh"     //跳板机上执行的对应应用类型脚本
                        file_path = "/data/jenkins/${remote_Dir}"                      //跳板机执行脚本的工作目录
                        //根据jar和war不同的类型生成不同的参数
                        if (env.appinfo == 'jar') {
                            des_path = "/data/${env.project}/${env.app_name}"        //远程服务器上应用的所在目录
                            cmd_exe = "${jenkins_path} ${start} ${apollo} ${des_path} ${app_name} ${mem} ${file_path} ${addr}"
                            echo cmd_exe
                            sshPublisher(publishers: [sshPublisherDesc(configName: "114.55.42.166--jenkins_proxy（${env.deploy_user}）",
                                    transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: """$cmd_exe""",
                                            execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false,
                                            patternSeparator: '[, ]+', remoteDirectory: remote_Dir, remoteDirectorySDF: false,
                                            removePrefix: 'target/', sourceFiles: source_Files)], usePromotionTimestamp: false,
                                    useWorkspaceInPromotion: false, verbose: false)])
                        } else if (env.appinfo == 'war') {
                            for (TOMCAT in env.tomcat){
                                echo "${TOMCAT}"
                                des_path = "/data/${env.project}/${TOMCAT}"
                                cmd_exe = "${jenkins_path} ${apollo} ${des_path} ${app_name} ${file_path} ${addr}"
                                echo cmd_exe
                                sshPublisher(publishers: [sshPublisherDesc(configName: "114.55.42.166--jenkins_proxy（${env.deploy_user}）",
                                    transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: """$cmd_exe""",
                                            execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false,
                                            patternSeparator: '[, ]+', remoteDirectory: remote_Dir, remoteDirectorySDF: false,
                                            removePrefix: 'target/', sourceFiles: source_Files)], usePromotionTimestamp: false,
                                    useWorkspaceInPromotion: false, verbose: false)])
                            }
                        } else {
                            error "${env.appinfo}其他类型，不能进行部署"
                        }

                        }
                    }
                }
            }
        }
    }
}