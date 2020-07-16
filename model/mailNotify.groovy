def mailNotify (send_mail = env.send_mail) {
    echo "本项目邮件发送列表为：${send_mail}"
    emailext body: '''${DEFAULT_CONTENT}''',
            attachLog: true,
            subject: '${DEFAULT_SUBJECT}',
            to: "${send_mail}",
            from: "postmaster@mymlsoft.com"
}

return this

