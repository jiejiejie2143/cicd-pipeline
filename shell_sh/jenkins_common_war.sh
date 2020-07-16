#!/bin/bash
#/data/jenkins_common_war.sh   env=FAT  /data/superapp/tomcat-saserver-8180/   saserver  /data/jenkins/test/superapp 172.16.10.26  172.16.10.27
#$1 服务运行环境
#$2 远程服务目录
#$3 服务名称 不含.war 
#$4 该脚本工作目录
# 远程脚本为 $REMOTE_PATH/war.sh  <运行环境> <服务目录> <服务名称不含.war> <kill_back || delback_start>
# 后面所有的位置变量：需要部署的服务器ip地址
PARAMETER=$#
if [ $PARAMETER -le 4  ];then
    echo "参数错误，格式为：$0  <运行环境> <服务目录> <服务名称不含.war>  <脚本工作目录>"
    exit 1         
fi
SCRIPT_DIR=/data/jenkins
#sudo chown -R admin:admin $SCRIPT_DIR
WORK_DIR=$4
ANSIBLE_HOSTS=$WORK_DIR/hosts
WAR_NAME=$3\.war
REMOTE_PATH=$2
WAR_PATH=$REMOTE_PATH/webapps
WAR_DIR=$WAR_PATH/$3
SHELL_KILL_BACK="bash $REMOTE_PATH/common_war.sh  '$1'  $2  $3  kill_back "
SHELL_DELBACK_START="bash $REMOTE_PATH/common_war.sh  '$1'  $2  $3  delback_start "
SHFIT=4
echo 去掉的参数个数为$SHFIT
shift $SHFIT
echo 部署的目标服务器为$* 
for i in $*
do
cat > $ANSIBLE_HOSTS << EOF
[all]
$i
EOF
     echo "########################开始部署$i上的服务#####################################"
     echo 同步脚本到远程服务器上 	
     	 ansible -i $ANSIBLE_HOSTS all -m copy -a "src=$SCRIPT_DIR/common_war.sh  dest=$REMOTE_PATH mode=0755 owner=admin group=admin"
     echo 执行远程脚本$SHELL_KILL_BACK 杀死服务并备份war包及删除项目目录
     	 ansible -i $ANSIBLE_HOSTS all -m shell  -a " $SHELL_KILL_BACK "
     echo 同步war包到远程主机$i 的$WAR_PATH
     	 ansible -i $ANSIBLE_HOSTS all -m copy -a "src=$WORK_DIR/$WAR_NAME  dest=$WAR_PATH mode=0755 owner=admin group=admin"
     echo 执行远程脚本$SHELL_DELBACK_START 删除过期备份并启动服务 
     	 ansible -i $ANSIBLE_HOSTS all -m shell  -a " $SHELL_DELBACK_START "
done

echo "########################删除跳板机上的war包####################################"
/bin/rm $WORK_DIR/$WAR_NAME

