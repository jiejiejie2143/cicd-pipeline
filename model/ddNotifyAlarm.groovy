mobileList = [
        "yanjie" : "严杰, 13708094659"
]

def getSubmitter() {
    wrap([$class: 'BuildUser']) {
        jobUserId = sh encoding: 'UTF-8', returnStdout: true, script: 'echo "${BUILD_USER_ID}"'
        env.BUILD_USER_ID = BUILD_USER_ID
    }

    return jobUserId.trim()
}

def buildMessageTemplate() {
    mobile = mobileList.get(getSubmitter()) ? mobileList.get(getSubmitter()).split(',')[1] : ''
    buildUser = mobileList.get(getSubmitter()) ? mobileList.get(getSubmitter()).split(',')[0] : getSubmitter()
    buildTime = new Date().format('yyyy-MM-dd HH:mm:ss')
    //appList = app_need_update.toString()
    app_name = env.app_name
    appenv = env.appenv
    git_repository = env.git_repository

    if(env.JOB_NAME.contains('deploy_old') || env.JOB_NAME.contains('dev')) {
        addr = env.addr
        choose_rpm = env.tar_name
        if(env.branch == "null"){
            app_brance = choose_rpm.tokenize('_')[1]
        }else {
            app_brance = env.branch
        }

    }
    if(env.JOB_NAME.contains('package')){
        addr = "该任务没有远程部署"
        choose_rpm = env.tar_name
        app_brance = env.branch
    }
    if(env.JOB_NAME.contains('deploy_k8s')){
        addr = "访问地址为$env.appenv 环境的k8s集群"
        choose_rpm = env.choose_rpm
        app_brance = choose_rpm.tokenize('_')[1]
    }

    //println("appList: "+appList)


    content = "- **任务名称**:  ${env.JOB_NAME} \n" +
                "- **构建编号**:  ${env.BUILD_NUMBER} \n" +
                "- **构建人**： ${BUILD_USER_ID} \n" +
                "- **构建时间**:  ${buildTime} \n" +
                "- **构建git地址**:  ${git_repository} \n" +
                "- **构建分支**:  ${app_brance} \n" +
                "- **部署环境**:  ${appenv} \n" +
                "- **部署服务器ip**:  ${addr} \n" +
                "- **部署结果**:   ${currentBuild.currentResult} \n" +
                "- **部署的应用名**:  ${app_name} \n" +
                "- **部署的软件包**:  ${choose_rpm} \n" +
                "- **任务链接**:  [${env.BUILD_URL}console](${env.BUILD_URL}console) \n"


      atAll = 'true'
      content = content + " @所有人"

    //println(content)

    messageTemplate = """
    {
        "msgtype": "markdown",
        "markdown": {
            "title": "构建信息",
            "text": "${content}"
        },
        "at": {
            "atMobiles": [
                "${mobile}"
            ],
        "isAtAll": ${atAll}
        }
    }
    """

    //println(messageTemplate)

    return messageTemplate
}

def notifyAlarm() {

    messageTemplate = buildMessageTemplate()
    url = "https://oapi.dingtalk.com/robot/send?access_token=a536f1ba4f27488f082d1032b0d48f77a0e693ae8633c98a7a3b0afb226f2a3c"
    def response = httpRequest  httpMode: 'POST',
                        url: "${url}",
                        contentType: "APPLICATION_JSON_UTF8",
                        requestBody: "${messageTemplate}"
    
    //println("Status: "+response.status)
    //println("Content: "+response.content)
}

return this

