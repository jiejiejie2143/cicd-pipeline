
def mavenPackage () {

    println("这是调用的mavenPackage函数")
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

def pushAppRepo () {
    dir(env.work_dir+'/target/') {
        sh "mkdir -pv /data/app_repo/${appenv}/${project}/${app_name}"
        def app_repo_path = "/data/app_repo/${appenv}/${project}/${app_name}/"
        sh "\\cp  ${tar_name}  ${app_repo_path}"
        sh "chown -R admin:admin /data/app_repo"
    }

}


return  this