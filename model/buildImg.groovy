
def generateTag () {
    if (env.appenv == 'prod') {

        env.version_base = env.choose_rpm.split('_')[1]
        env.version_num = env.version_base.split('.tar')[0]
        echo "正式镜像的版本号为$env.version_num"
        env.tag = "${registry}/${app_name}:${version_num}"
        echo "本次拟打包的镜像名为： ${tag} "

    }else if (env.appenv == 'test'){

        env.version_base1 = env.choose_rpm.split('release-')[1]
        env.version_base2 = env.version_base1.split('.tar')[0]
        env.version_num = "release-$env.version_base2"
        echo "测试镜像的版本号为$env.version_num"
        env.tag = "${registry}/${app_name}:${version_num}"
        echo "本次拟打包的镜像名为： ${tag} "

    }else{  // dev

        env.tag = "${env.registry}/${env.app_name}:${env.branch}_${env.BUILD_NUMBER}"
        echo "本次拟打包的镜像名为： ${tag} "
    }
}

def buildImg () {

    echo "开始docker打包"
    sh "mkdir -pv target"
    if ( env.appenv.startsWith('dev') ) {      // 没选包，所有没有choose_rpm

        sh "mv  ${env.work_dir}/target/${env.tar_name} ./target/"
        sh "sh ./script/docker_image_init.sh ${env.appinfo} ${env.tar_name} ${env.log_env}"

    } else {

        sh "cp  ${env.choose_rpm_path} ./target/"
        sh "tar -zxf ${env.choose_rpm_path} -C ./target/"
        env.app_md5 = sh returnStdout: true, script: "md5sum  ./target/${app_name}.${appinfo} |awk '{print \$1}'"
        echo "该软件的md5码是  ${env.app_md5}  请核对"       // 部署时未调用打包函数，所以再次输出确认一下
        sh "sh ./script/docker_image_init.sh ${env.appinfo} ${env.choose_rpm} ${env.log_env}"

    }

    sh "docker build -t ${env.tag} ."
}

def pushImg () {
    sh "docker login ${env.docker_repository}"
    sh "docker push  ${env.tag}"
}


return  this