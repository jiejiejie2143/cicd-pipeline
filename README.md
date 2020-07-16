# cicd-pipeline
  //重启脚本，用于监控触发自动重启应用
sh "echo '#!/bin/bash' > reset.sh"
sh "echo -e 'kill -9 `ps aux|grep ${env.app_dir}/${env.app_name}.jar|grep -v grep|awk '{print \$2}'`' >> reset.sh "
sh "echo -e 'bash ${env.app_dir}/start_jar.sh' >> reset.sh "
sh "chmod +x reset.sh"
