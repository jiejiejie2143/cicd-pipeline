
def deployToK8s () {
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


return  this