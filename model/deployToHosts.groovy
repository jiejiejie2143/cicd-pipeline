
def deployToHosts () {
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

        if ( env.appenv.startsWith('dev') ) {
            env.choose_rpm_path = "${env.work_dir}/target/${env.tar_name}"
            env.choose_rpm = env.tar_name
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
                def play_praras = " -e deploy_user=${env.deploy_user} -e app_dir=$app_dir -e app_name=${env.app_name} " +
                        " -e app_port=${env.app_port}  -e app_info=war  -e choose_rpm_path=${env.choose_rpm_path}  "
                sh "sudo -u $env.deploy_user ansible-playbook -i inventory/host_playbook deploy.yaml  $play_praras"
            }
        } else {
            error "${env.appinfo}其他类型，不能进行部署"
        }
    }
}



return  this