// 所有功能模块定义为函数，在主CICD脚本中进行加载调用。

def choseRpm () {

    def app_repo_path = "/data/app_repo/${appenv}/${project}/${app_name}/"

    if ( env.appenv.startsWith('test') ){
        app_list = sh returnStdout: true, script: "ls -l  ${app_repo_path} |grep ${app_name} |awk '{print \$NF}' |sort -t\"_\" -k2.10,2.14rn -k3rn"
        env.choose_rpm_default = sh returnStdout: true, script: "ls -l  ${app_repo_path} |grep ${app_name} |awk '{print \$NF}' |sort -t\"_\" -k2.10,2.14rn -k3rn |head -1"

    }else  {
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

    if ( env.appenv.startsWith('test') ) {

            env.version_base = env.choose_rpm.split('release-')[1]
            env.version_num = env.version_base.tokenize('_')[0]
            echo "正式的版本号为$env.version_num"      // 测试环境的k8s项目和老项目通用，都需要生成正式的版本号，推到正式仓库并改名用的
    }

    echo " 选择发布的tar包为： ${env.choose_rpm}"
    echo " tar包仓库地址为： ${env.choose_rpm_path}"
}


return  this

