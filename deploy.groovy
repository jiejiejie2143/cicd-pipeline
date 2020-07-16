import org.apache.tools.ant.types.selectors.SelectSelector
//物理机部署，打包部署已经分开，老的test和prod都是用的这个
//获取配置文件里自定义的参数的方法，programs_paras里的配置，需加两个参数，第一个为参数关键词，第二个为文件名；环境配置文件里的配置，只写一个参数关键词即可
//参数以应用指定参数优先使用，如果没有指定参数，则使用公共参数
def getParas(keyword,keyenv = env.appenv) {
    self_paras = sh returnStdout: true, script: "cat programs/${project}/${keyenv}_paras | grep ${app_name}_${keyword} | awk -F '==' '{print \$2}' "
    self_paras = self_paras.tokenize('\n')[0]
    common_paras = sh returnStdout: true, script: "cat programs/${project}/${keyenv}_paras | grep ${project}_${keyword}| awk -F '==' '{print \$2}' "
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
        timeout(time: 5, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('设定环境变量') {
            steps {
                script {
                    //分割项目名作为参数
                    env.project = env.JOB_BASE_NAME.tokenize('+')[0]
                    env.app_name = env.JOB_BASE_NAME.tokenize('+')[1]
                    env.appenv = env.JOB_BASE_NAME.tokenize('+')[2]
                    env.git_repository = getParas('program', 'program')
                    //获取项目自定义参数值
                    env.addr = getParas('addr')
                    echo "需要部署的IP为：${env.addr}"
                    sh "echo '[hosts]' >inventory/host_playbook"
                    for (ip in env.addr.tokenize(',')){
                       sh  "echo ${ip} >> inventory/host_playbook "
                    }
                    sh 'cat inventory/host_playbook '
                }
            }
        }
        stage('处理applo和启动参数') {
            steps {
                script {
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
                        env.start_env = " -server -d64  ${mem}  ${apollo} -Duser.home=/data -Dlog.env=${log_env} -XX:+UseParNewGC -XX:ParallelGCThreads=4 -XX:MaxTenuringThreshold=5 " +
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
                        } else {
                            env.apollo = 'env='+env.apollo_base+'\\\\n'+env.apollo_extr
                        }
                        env.tomcat_dir = getParas('tomcat')
                        env.app_port = getParas('app_port')
                        echo "需要检查的服务端口是 ${app_port}"
                    } else {
                        echo '其他类型，apollo参数不做处理'
                    }
                }
            }
        }

        stage('拉取选择部署软件包') {
            steps {
                script {
                    def app_repo_path = "/data/app_repo/${appenv}/${project}/${app_name}/"
                    if ( env.appenv.startsWith('test') ){
                        app_list = sh returnStdout: true, script: "ls -l  ${app_repo_path} |grep ${app_name} |awk '{print \$NF}' |sort -t\"_\" -k2.10,2.14rn -k3rn"
                        env.choose_rpm_default = sh returnStdout: true, script: "ls -l  ${app_repo_path} |grep ${app_name} |awk '{print \$NF}' |sort -t\"_\" -k2.10,2.14rn -k3rn |head -1"
                    }else {
                        app_list = sh returnStdout: true, script: "ls -l  ${app_repo_path} |grep ${app_name} |awk '{print \$NF}' |sort -rn  "
                        env.choose_rpm_default = sh returnStdout: true, script: "ls -l  ${app_repo_path} |grep ${app_name} |awk '{print \$NF}' |sort -rn |head -1 "
                    }

                    try {
                        timeout(time:30, unit:'SECONDS') {
                            env.choose_rpm = input(id: 'userInput', message: "选择需要部署的tar包", parameters: [
                                    choice(choices: app_list, description: '', name: 'tar包列表')])
                            env.choose_rpm_path = "${app_repo_path}${choose_rpm}"
                        }
                    } catch(err) { // timeout reached or input Aborted
                        def user = err.getCauses()[0].getUser()   //要执行该函数 必须要Jenkins允许两个类的加载，报错之后同意执行（两次同意）即可
                        if('SYSTEM' == user.toString()) { // SYSTEM means timeout
                            env.choose_rpm = env.choose_rpm_default   //这里是内部脚本的执行了，不能再用sh 来调用ls 获取默认的第一个值，所以在前面提前获取，这里赋值即可
                            echo ("等待超时，将使用默认部署应用: " + env.choose_rpm)
                            env.choose_rpm_path = "${app_repo_path}${choose_rpm}"
                        } else {
                            error("Pipeline 被[${user}] 终止")
                        }
                    }
                    if( env.appenv.startsWith('test') ) {
                        echo " 选择发布的tar包为： ${env.choose_rpm}"
                        env.version_base = env.choose_rpm.split('release-')[1]
                        env.version_num = env.version_base.tokenize('_')[0]
                        echo "正式的版本号为$env.version_num"      //为了推到正式仓库改名用的
                        echo " tar包仓库地址为： ${env.choose_rpm_path}"
                    }else {
                        echo " 选择发布的tar包为： ${env.choose_rpm}"
                        echo " tar包仓库地址为： ${env.choose_rpm_path}"
                    }
                }
            }
        }
        stage('远程部署') {
            steps {
                script {
                    echo " 开始发布 ${env.choose_rpm}"
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

                    if (env.appinfo == 'jar') {
                        sh "echo '#!/bin/bash' > start_jar.sh"
                        sh "echo -e 'for i in {1..${env.start}}; do nohup java  ${env.start_env}  -jar  ${env.app_dir}/${env.app_name}.jar  >/dev/null 2>&1 &  done ' >> start_jar.sh"
                        sh "echo -e  'sleep 3 && ps -ef |grep ${env.app_dir}/${env.app_name}.jar | grep -v 'grep' '  >>  start_jar.sh "
                        sh  "chmod +x start_jar.sh"
                        // 不知道为什么捕捉异常之后，choose_rpm_path 这个变量之后的-e 参数就没有了，所以调整顺序使得choose_rpm_path 这个变量在最后 就正常了。
                        def play_praras = " -e deploy_user=${env.deploy_user}  -e app_dir=${env.app_dir}  -e app_name=${env.app_name}" +
                                          " -e start_num=${env.start} -e app_info=jar  -e choose_rpm_path=${env.choose_rpm_path}   "
                            sh "sudo -u $env.deploy_user ansible-playbook -i inventory/host_playbook deploy.yaml  $play_praras "

                    } else if (env.appinfo == 'war') {
                        sh "echo -e ${env.apollo} > server.properties"
                        for (TOMCAT in env.tomcat_dir.tokenize(',')){
                            echo "${TOMCAT}"
                            def app_dir = "/data/${env.project}/${TOMCAT}"
                            def play_praras = " -e deploy_user=$env.deploy_user -e app_dir=$app_dir  -e app_name=${env.app_name}  " +
                                              " -e app_port=${env.app_port}  -e app_info=war  -e choose_rpm_path=${env.choose_rpm_path}  "
                            sh "sudo -u $env.deploy_user ansible-playbook -i inventory/host_playbook deploy.yaml  $play_praras"
                        }
                    } else {
                        error "${env.appinfo}其他类型，不能进行部署"
                    }
                }
            }
        }
        stage('更新软件包到下一级仓库') {
            steps{
                script {
                    if (env.appenv.startsWith('test')) {
                        try {
                            timeout(time: 10, unit: 'SECONDS') {
                                env.choose_put = input id: '推送下级', message: '是否更新到生产环境', parameters: [booleanParam(defaultValue: false, description: '', name: 'are you want to push to next repo')]
                                echo "用户选择是否推送仓库为: $env.choose_put "
                            }
                        } catch (err) {  // timeout reached or input Aborted
                            def user = err.getCauses()[0].getUser()   //要执行该函数 必须要Jenkins允许两个类的加载，报错之后同意执行（两次同意）即可
                            if ('SYSTEM' == user.toString()) {
                                env.choose_put = false
                                echo ("等待超时，不更新到下级仓库 $env.choose_put")
                            } else {
                                error("Pipeline 被[${user}] 终止")
                            }
                        }
                    }

                    if (env.choose_put == "true") {
                        echo '软件包蒋更新到生产环境'
                        env.app_prod_name = "${env.app_name}_${env.version_num}.tar.gz"
                        sh "mkdir -pv /data/app_repo/prod/${project}/${app_name}"
                        sh "\\cp -f  $env.choose_rpm_path  /data/app_repo/prod/${project}/${app_name}/${env.app_prod_name}"
                        sh "chown -R admin:admin /data/app_repo"
                    } else {
                        echo "不更新到下级仓库"
                    }

                    if (env.appenv.startsWith('pre_prod')) {
                        echo '软件包蒋更新到生产环境'
                        sh "mkdir -pv /data/app_repo/prod/${project}/${app_name}"
                        sh "\\cp -f  $env.choose_rpm_path  /data/app_repo/prod/${project}/${app_name}/"
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