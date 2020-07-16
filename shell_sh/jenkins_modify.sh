#!/bin/sh
# by yanjie

# 用户输入自定义信息
jenkins_url=http://10.9.52.243/jenkins

read -p "请输入你想修改配置的文件夹名称:" floder
read -p "请输入当前正在使用的配置：" now_config
read -p "请输入你想要修改为的配置：" new_config
echo 你选择修改配置的文件夹是:\"$floder\" 你想要将\""$now_config"\"替换成\""$new_config"\"

sleep 1

# 用户确认
read -p "确认你的输入：继续请输入：Y，退出请输入任意键:" input1

if [ $input1 != "Y" ];then
   echo "Bye-bye!"
   exit 0
fi

# 修改配置  根据自己想改的配置，可能要修改查找config.xml文件路径
config_list=`find /data/.jenkins/jobs/$floder/jobs/  -type f -name config.xml`
for list in $config_list
do
    sed -i s/$now_config/$new_config/g $list
done
sleep 1
echo ">>>>>>>>>>>>>>>>>>>>>修改成功-正在重启Jenkins使配置生效<<<<<<<<<<<<<<<<<<<<<<<<<<"
curl -I -X POST $jenkins_url/restart --user yanjie:'yanjie@1234'
