// 所有功能模块定义为函数，在主CICD脚本中进行加载调用。
def getParas (keyword,keyenv = env.appenv) {
    self_paras = sh returnStdout: true, script: "cat programs/${project}/${keyenv}_paras | grep ${app_name}_${keyword} | awk -F '==' '{print \$2}' "
//    self_paras = self_paras.trim()
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

def generateEnv () {

    println("这是调用的generateEnv函数")
    job_base_name = env.JOB_BASE_NAME.tokenize('+')
    echo  "任务的长度为：" + job_base_name.size()

    env.project = env.JOB_BASE_NAME.tokenize('+')[0]
    env.app_name = env.JOB_BASE_NAME.tokenize('+')[1]

    env.git_repository = getParas('program', 'program')  // 为了dd报警脚本中有用到该变量

    // 长度为5 代表是打包 或者是dev 都是需要打包的
    if ( job_base_name.size() == 5 ) {
        // 从job名获取环境变量参数
        env.branch = env.JOB_BASE_NAME.tokenize('+')[2]
        env.appenv = env.JOB_BASE_NAME.tokenize('+')[3]
        env.jobtype = env.JOB_BASE_NAME.tokenize('+')[4]

        // 配置文件中获取的参数
        env.appinfo = getParas('appinfo', 'program')
        env.rele = getParas('rele')
        env.pom = getParas('pom', 'program')
        env.send_mail = getParas('mail')

        env.ci_dir = "${env.app_name}-ci"
        sh "mkdir -pv ${ci_dir}"

        // 判断部署环境，给出正确的日志级别
        if(env.appenv.startsWith('test')|| env.appenv.startsWith('dev')){
            env.log_env = "test"
        }else {
            env.log_env = "pro"
        }

        // 判断pom文件的目录层级，给与正确的工作路径
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

       // dev_old  dev_k8s
        if (env.jobtype == 'dev_old') {

            env.addr = getParas('addr')
            echo "需要部署的IP为：${env.addr}"
            env.apollo = getParas('apollo')

            if (env.appinfo == 'jar') {
                env.start = getParas('start')
                echo "应用需要启动的个数为：${start} "

                env.mem = getParas('mem')
                echo "应用启动预分配的内存为：${mem}"

            } else if (env.appinfo == 'war') {

                env.tomcat_dir = getParas('tomcat' )
                env.app_port = getParas('app_port')
                echo "需要检查的服务端口是 ${app_port}"

            }else {
                echo '其他类型，不做处理'
            }

        }else if ( env.jobtype == 'dev_k8s' ) {
            // 获取项目k8s专用配置
            env.docker_repository = "registry-vpc.cn-hangzhou.aliyuncs.com"
            env.registry = "${env.docker_repository}/ml_${appenv}"
            echo "本次拟推送的镜像仓库为： ${registry}"

            env.app_port = getParas('app_port')
            echo "需要检查的服务端口是 ${app_port}"

            env.apollo = getParas('apollo')
            env.apollo = "-Denv=${env.apollo}"
            echo "该应用的applo环境是 ${apollo}"

            env.namespace = getParas('namespace')
            echo "该应用部署的名称空间是 ${namespace}"

            env.mem = getParas('mem')
            echo "应用启动预分配的内存为：${mem}"
        }else if ( env.jobtype == 'package' ) {
            echo "++++++++++该任务只打包不部署++++++++++"
        }else {
            error("任务类型不合法！")
        }

    }else if ( job_base_name.size() == 4 ) {
        // 长度为4  代表是部署 从job名获取环境变量

        env.appenv = env.JOB_BASE_NAME.tokenize('+')[2]
        env.jobtype = env.JOB_BASE_NAME.tokenize('+')[3]

        // 配置文件中获取的参数
        env.appinfo = getParas('appinfo', 'program')
        env.apollo = getParas('apollo')
        env.send_mail = getParas('mail')
        env.addr = getParas('addr')
        echo "需要部署的IP为：${env.addr}"

        // 判断部署环境，给出正确的日志级别
        if(env.appenv.startsWith('test')|| env.appenv.startsWith('dev')){
            env.log_env = "test"
        }else {
            env.log_env = "pro"
        }

        // deploy_old  deploy_k8s
        if (env.jobtype == 'deploy_old') {
            env.addr = getParas('addr')
            if (env.appinfo == 'jar') {
                env.start = getParas('start')
                echo "应用需要启动的个数为：${start} "

                env.mem = getParas('mem')
                echo "应用启动预分配的内存为：${mem}"

            } else if (env.appinfo == 'war') {

                env.tomcat_dir = getParas('tomcat' )
                env.app_port = getParas('app_port')
                echo "需要检查的服务端口是 ${app_port}"

            }else {
                echo '其他类型，不做处理'
            }

        }else if ( env.jobtype == 'deploy_k8s' ) {
            // 获取项目k8s专用配置
            env.docker_repository = "registry-vpc.cn-hangzhou.aliyuncs.com"
            env.registry = "${env.docker_repository}/ml_${appenv}"
            echo "本次拟推送的镜像仓库为： ${registry}"

            env.app_port = getParas('app_port')
            echo "需要检查的服务端口是 ${app_port}"

            env.apollo = "-Denv=${env.apollo}"
            echo "该应用的applo环境是 ${apollo}"

            env.namespace = getParas('namespace')
            echo "该应用部署的名称空间是 ${namespace}"

            env.mem = getParas('mem')
            echo "应用启动预分配的内存为：${mem}"
        }else {
            error("任务类型不合法！")
        }

    }else {
        error("任务长度不合法！")
    }

}

def gitClone () {

    println("这是调用的gitClone函数")
    echo "开始拉取git代码"
    dir(env.ci_dir) {
        checkout([$class: 'GitSCM', branches: [[name: env.branch]], doGenerateSubmoduleConfigurations: false,
                  extensions: [[$class: 'CleanBeforeCheckout']], submoduleCfg: [],
                  userRemoteConfigs: [[credentialsId: 'gitlab', url: git_repository]]])
    }
}



return  this

