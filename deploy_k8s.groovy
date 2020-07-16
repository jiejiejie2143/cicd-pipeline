//k8s部署，打包部署已经分开，test和prod都是用的这个
//获取配置文件里自定义的参数的方法，programs_paras里的配置，需加两个参数，第一个为参数关键词，第二个为文件名；环境配置文件里的配置，只写一个参数关键词即可
//参数以应用指定参数优先使用，如果没有指定参数，则使用公共参数
//默认配置文件是根据环境配置的，但是k8s项目只需要一个配置文件即可所以都是传的program
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
        timeout(time: 5, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    stages {
        stage('选择部署软件包，并生成镜像tag') {
            steps {
                script {
                    //分割项目名作为参数
                    env.project = env.JOB_BASE_NAME.tokenize('+')[0]
                    env.app_name = env.JOB_BASE_NAME.tokenize('+')[1]
                    env.appenv = env.JOB_BASE_NAME.tokenize('+')[2]
                    env.git_repository = getParas('program', 'program')

                    //获取项目自定义参数值，生成镜像tag
                    env.docker_repository = "registry-vpc.cn-hangzhou.aliyuncs.com"
                    env.appinfo = getParas('appinfo', 'program')
                    echo "应用类型为：${appinfo}"
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
                            env.choose_rpm_path = env.choose_rpm_path.tokenize('\n')[0]   //grovvy 赋值默认后面有个换行符！！！必须去掉。
                        } else {
                            error("Pipeline 被[${user}] 终止")
                        }
                    }
                    echo " 选择发布的tar包为： ${env.choose_rpm}"
                    echo " tar包仓库地址为： ${env.choose_rpm_path}"

                    if (env.appenv == 'prod') {
                        echo "haha prod"
                        env.version_base = env.choose_rpm.split('_')[1]
                        env.version_num = env.version_base.split('.tar')[0]
                        echo "正式镜像的版本号为$env.version_num"
                    }else if (env.appenv == 'test'){
                        env.version_base1 = env.choose_rpm.split('release-')[1]
                        env.version_base2 = env.version_base1.split('.tar')[0]
                        env.version_num = "release-$env.version_base2"
                        echo "测试镜像的版本号为$env.version_num"
                    }else{
                        echo "未知环境，待开发"
                    }
                    env.tag = "${registry}/${app_name}:${version_num}"
                    echo "本次拟打包的镜像名为： ${tag} "
                }
            }
        }

        stage('build镜像') {
            steps {
                script {
                        echo "开始docker打包"
                        sh "mkdir -pv target"
                        sh "cp  ${env.choose_rpm_path} ./target/"
//                        String cmd = 'tar  -zxf '+"${env.choose_rpm_path} -C ./target/ "
//                        sh "echo $cmd"
//                        sh returnStdout: true, script: "$cmd"
//                        // sh "tar -zxf ${env.choose_rpm} -C ./target/"   自己选择的包名，字符串中没带\N换行符，抓异常赋值时候的变量是带了换行的。
                        sh "tar -zxf ${env.choose_rpm_path} -C ./target/"
                        env.app_md5 = sh returnStdout: true, script: "md5sum  ./target/${app_name}.${appinfo} |awk '{print \$1}'"
                        echo "该软件的md5码是  ${env.app_md5}  请核对"
                        if(env.appenv.startsWith('test')|| env.appenv.startsWith('dev')){
                            env.log_env = "test"
                        }else {
                            env.log_env = "pro"
                        }
//                        sh "sh ./script/docker_init.sh ${env.appinfo} ${env.app_name}"
                        sh "sh ./script/docker_image_init.sh ${env.appinfo} ${env.choose_rpm} ${env.log_env}"
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
                }
            }
        }

        stage('更新软件包到下一级仓库') {
            steps{
                script {
                    if (env.appenv == 'test') {
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
                        echo '软件包将更新到生产环境'
                        env.version_base = env.choose_rpm.split('release-')[1]
                        env.version_num = env.version_base.tokenize('_')[0]
                        echo "正式的版本号为$env.version_num"
                        env.app_prod_name = "${env.app_name}_${env.version_num}.tar.gz"
                        sh "mkdir -pv /data/app_repo/prod/${project}/${app_name}"
                        sh "\\cp -f  $env.choose_rpm_path  /data/app_repo/prod/${project}/${app_name}/${env.app_prod_name}"
                        sh "chown -R admin:admin /data/app_repo"
                    } else {
                        echo "不更新到下级仓库"
                    }

                    if (env.appenv == 'pre_prod') {
                        echo '软件包将更新到生产环境'
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
