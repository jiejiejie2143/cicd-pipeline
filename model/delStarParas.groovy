
def delStarParas () {

    if (env.app_name.contains('facade') || env.app_name.contains('common')) {
        echo "该项目不需要远程部署,不做参数处理"
    }else {

        sh "echo '[hosts]' >inventory/host_playbook"
        for (ip in env.addr.tokenize(',')){
            sh  "echo ${ip} >> inventory/host_playbook "
        }
        sh 'cat inventory/host_playbook '

        //处理apollo参数，因为参数中有等号，取值会出错，需要进行二次处理
        if (env.appinfo == 'jar') {
            env.apollo = "-Denv=${env.apollo}"
            env.app_dir = "/data/${env.project}/${env.app_name}"        //远程服务器上应用的所在目录
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

        } else {
            echo '其他类型，apollo参数不做处理'
        }
    }
}



return  this