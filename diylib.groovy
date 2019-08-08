def getParas(keyword) {
    common = sh returnStdout: true, script: 'cat programs/' + env.project + '/program_paras|grep ' + env.app_name + '_' + keyword + '|awk -F "=" \'{print $2}\''
    common = common.tokenize('\n')[0]
    return common
}