
def pushToNextRepo () {

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


return  this