import org.apache.tools.ant.types.selectors.SelectSelector
//k8s部署，打包部署未分开
//获取配置文件里自定义的参数的方法，programs_paras里的配置，需加两个参数，第一个为参数关键词，第二个为文件名；环境配置文件里的配置，只写一个参数关键词即可
//参数以应用指定参数优先使用，如果没有指定参数，则使用公共参数
def getParas(keyword,keyenv = env.appenv) {
    self_paras = sh (returnStdout: true, script: "cat programs/${project}/${keyenv}_paras | grep ${app_name}_${keyword} | awk -F '==' '{print \$2}'")
    self_paras = self_paras.tokenize('\n')[0]
    common_paras = sh (returnStdout: true, script: "cat programs/${project}/${keyenv}_paras | grep ${project}_${keyword}| awk -F '==' '{print \$2}'")
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
    triggers{
        gitlab( triggerOnPush: true,
                triggerOnMergeRequest: true,
                branchFilterType: "RegexBasedFilter",
                sourceBranchRegex: "dev-.*",
                secretToken: "${env.git_token}")
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

                    //获取项目自定义参数值，生成镜像tag
                    env.docker_repository = "registry-vpc.cn-hangzhou.aliyuncs.com"
                    env.registry = "${env.docker_repository}/ml_${appenv}"
                    echo "本次拟推送的仓库为： ${registry}"
                    env.app_port = getParas('app_port')
                    echo "需要检查的服务端口是 ${app_port}"
                    env.apollo = getParas('apollo')
                    env.apollo = "-Denv=${env.apollo}"
                    echo "该应用的applo环境是 ${apollo}"
                    env.namespace = getParas('namespace')
                    echo "该应用部署的名称空间是 ${namespace}"
                    env.mem = getParas('mem')
                    echo "应用启动预分配的内存为：${mem}"

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
        stage('maven构建并打tar包生成tag') {
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
                                env.tar_name = "${app_name}_${branch}_${env.BUILD_NUMBER}.tar.gz"
                                lib_status = sh(script:"ls lib/",returnStatus:true)
                                if (lib_status == 0) {
                                    sh returnStdout: true, script: "tar -zcf  ${tar_name}  lib/  ${app_name}.${appinfo} "
                                } else if (lib_status == 2) {
                                    sh returnStdout: true, script: "tar -zcf ${tar_name}  ${app_name}.${appinfo} "
                                } else {
                                    error("command is error,please check")
                                }
                            }
                        }
                    }
                }
            }
        }
        stage('build镜像') {
            steps {
                script {
                    echo "开始docker打包"
                    sh "mkdir -pv target"
                    sh "mv  ${env.work_dir}/target/${env.tar_name} ./target/"
                    env.tag = "${env.registry}/${env.app_name}:${env.branch}_${env.BUILD_NUMBER}"
                    echo "本次拟打包的镜像名为： ${tag} "
                    if(env.appenv.startsWith('test')|| env.appenv.startsWith('dev')){
                        env.log_env = "test"
                    }else {
                        env.log_env = "pro"
                    }
                    sh "sh ./script/docker_image_init.sh ${env.appinfo} ${env.tar_name} ${env.log_env}"
                    sh "docker build -t ${env.tag} ."
                }
            }
        }
        stage('推送到阿里容器仓库') {
            steps {
                script {
                    echo "镜像推送至阿里云仓库"
                    sh "docker login ${env.docker_repository}"
                    sh "docker push  ${env.tag}"
                }
            }
        }
        stage('部署镜像到rancher') {
            steps {
                script {
                    echo "镜像部署至k8s"
                    sh "docker rmi ${env.tag}"
                    def deployment_status = sh(script:"kubectl --kubeconfig=/opt/k8s_config/${env.appenv} -n ${env.namespace} get deployments  ${env.app_name}",returnStatus:true)
                    if (deployment_status == 0) {
                        sh "echo deployments ${env.app_name} is exist"
                        sh 'kubectl --kubeconfig=/opt/k8s_config/'+env.appenv+' -n '+env.namespace+' ' +
                                'set image deployment/'+env.app_name+' '+env.app_name+'='+env.tag
                    } else  {
                        sh "echo deployments ${env.app_name} is not exist"
                        sh  "sh  ./script/hander_k8s_tplet.sh ${env.namespace}  ${env.app_name}  '${env.apollo}'  ${env.app_port}  ${env.tag}  '${env.mem}' "
                        sh 'kubectl --kubeconfig=/opt/k8s_config/'+env.appenv+' -n '+env.namespace+' ' +
                                'apply -f  ./templete/deploy_k8s.yaml'
                    }
//                    sh  "sh  ./script/hander_k8s_tplet.sh ${env.namespace}  ${env.app_name}  '${env.apollo}'  ${env.app_port}  ${env.tag}  '${env.mem}' "
//                    sh 'kubectl --kubeconfig=/opt/k8s_config/'+env.appenv+' -n '+env.namespace+' ' +
//                                'apply -f  ./templete/deploy_k8s.yaml'
                }
            }
        }
    }
    post {
        always {
            script {
                env.send_mail = getParas('mail')
                echo "本项目邮件发送列表为：${env.send_mail}"
                emailext body: '''${DEFAULT_CONTENT}''',
                        attachLog: true,
                        subject: '${DEFAULT_SUBJECT}',
                        to: "$env.send_mail",
                        from: "postmaster@mymlsoft.com"
                ddnotify = load env.WORKSPACE+'/script/ddNotifyAlarm.groovy'
                ddnotify.notifyAlarm()

            }
        }
    }
}