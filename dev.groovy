import org.apache.tools.ant.types.selectors.SelectSelector
//物理机部署，打包部署未分开
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
        stage('处理applo和启动参数') {
            steps {
                script {
                    //获取项目自定义参数值
                    if (env.app_name.contains('facade') || env.app_name.contains('common')) {
                        echo "该项目不需要远程部署,不做参数处理"
                    }else {
                        env.addr = getParas('addr')
                        echo "需要部署的IP为：${env.addr}"
                        sh "echo '[hosts]' >inventory/host_playbook"
                        for (ip in env.addr.tokenize(',')){
                            sh  "echo ${ip} >> inventory/host_playbook "
                        }
                        sh 'cat inventory/host_playbook '

                        //处理apollo参数，因为参数中有等号，取值会出错，需要进行二次处理
                        env.appinfo = getParas('appinfo', 'program')
                        env.apollo = getParas('apollo')
                        if (env.appinfo == 'jar') {
                            env.apollo = "-Denv=${env.apollo}"

                            env.start = getParas('start')
                            echo "应用需要启动的个数为：${start} "
                            env.mem = getParas('mem')
                            echo "应用启动预分配的内存为：${mem}"
                            env.app_dir = "/data/${env.project}/${env.app_name}"        //远程服务器上应用的所在目录
                            if(env.appenv.startsWith('test')|| env.appenv.startsWith('dev')){
                                env.log_env = "test"
                            }else {
                                env.log_env = "pro"
                            }
                            env.start_env = " -server -d64  ${mem}  ${apollo} -Duser.home=/data -Dlog.env=${log_env} -XX:+UseParNewGC " +
                                    "-XX:ParallelGCThreads=4 -XX:MaxTenuringThreshold=5 " +
                                    "-XX:+UseConcMarkSweepGC -XX:+DisableExplicitGC -XX:+UseCMSInitiatingOccupancyOnly -XX:+ScavengeBeforeFullGC " +
                                    " -XX:+CMSParallelRemarkEnabled  -XX:CMSInitiatingOccupancyFraction=60 -XX:+CMSClassUnloadingEnabled  " +
                                    "-XX:SoftRefLRUPolicyMSPerMB=0   -XX:+ExplicitGCInvokesConcurrent -XX:+PrintGCDetails  " +
                                    " -XX:+PrintHeapAtGC -XX:+UseGCLogFileRotation -XX:+HeapDumpOnOutOfMemoryError " +
                                    " -XX:-OmitStackTraceInFastThrow -Duser.timezone=Asia/Shanghai -Dclient.encoding.override=UTF-8 -Dfile.encoding=UTF-8" +
                                    " -Djava.security.egd=file:/dev/./urandom -Xloggc:${app_dir}/gc.log -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=5M " +
                                    "-XX:HeapDumpPath=${app_dir}/HeapDump.hprof "

                        } else if (env.appinfo == 'war') {
                            env.apollo_base = sh returnStdout: true, script: "echo ${env.apollo} | awk  '{print \$1}' "
                            env.apollo_base = env.apollo_base.tokenize('\n')[0]
                            env.apollo_extr = sh returnStdout: true, script: "echo ${env.apollo} | awk -F '-D' '{print \$2}'"
                            env.apollo_extr = env.apollo_extr.tokenize('\n')[0]
                            echo "额外的applo参数为：${env.apollo_extr}"
                            if (env.apollo_extr == "null" )  {
                                env.apollo = 'env='+env.apollo_base
                                echo "tomcat的applo参数为：${env.apollo}"
                            } else {
                                env.apollo = 'env='+env.apollo_base+'\\\\n'+env.apollo_extr
                                echo "tomcat的applo参数为：${env.apollo}"
                            }
                            env.tomcat_dir = getParas('tomcat' )
                            env.app_port = getParas('app_port')
                            echo "需要检查的服务端口是 ${app_port}"
                        } else {
                            echo '其他类型，apollo参数不做处理'
                        }
                    }
                }
            }
        }
        stage('远程部署') {
            steps {
                script {
                    if (env.app_name.contains('facade') || env.app_name.contains('common')) {
                        echo "该项目不需要远程部署,不继续部署"
                    }else {
                        echo " 开始发布 ${env.tar_name}"
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
                        env.choose_rpm_path = "${env.work_dir}/target/${env.tar_name}"
                        env.choose_rpm = env.tar_name

                        if (env.appinfo == 'jar') {
                            sh "echo '#!/bin/bash' > start_jar.sh"
                            sh "echo -e 'for i in {1..${env.start}}; do nohup java  ${env.start_env}  -jar  ${env.app_dir}/${env.app_name}.jar  >/dev/null 2>&1 &  done ' >> start_jar.sh"
                            sh  "chmod +x start_jar.sh"
                            // 不知道为什么捕捉异常之后，choose_rpm_path 这个变量之后的-e 参数就没有了，所以调整顺序使得choose_rpm_path 这个变量在最后 就正常了。
                            def play_praras = " -e deploy_user=${env.deploy_user}  -e app_dir=${env.app_dir}  -e app_name=${env.app_name}" +
                                    " -e start_num=${env.start} -e app_info=jar  -e choose_rpm_path=${env.choose_rpm_path}   "
                            sh "sudo -u $env.deploy_user ansible-playbook -i inventory/host_playbook deploy.yaml  $play_praras -v"

                        } else if (env.appinfo == 'war') {
                            sh "echo -e ${env.apollo} > server.properties"
                            for (TOMCAT in env.tomcat_dir.tokenize(',')){
                                echo "${TOMCAT}"
                                def app_dir = "/data/${env.project}/${TOMCAT}"
                                def play_praras = " -e deploy_user=${env.deploy_user} -e app_dir=$app_dir -e app_name=${env.app_name} " +
                                        " -e app_port=${env.app_port}  -e app_info=war  -e choose_rpm_path=${env.choose_rpm_path}  "
                                sh "sudo -u $env.deploy_user ansible-playbook -i inventory/host_playbook deploy.yaml  $play_praras"
                            }
                        } else {
                            error "${env.appinfo}其他类型，不能进行部署"
                        }
                    }
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