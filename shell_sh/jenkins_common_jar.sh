#!/bin/bash
#/data/jenkins.sh 4  -Denv=FAT  /data/GPS/tigereye  meiling-paascloud-provider-tigereye '-Xmx1G -Xms1G -Xmn512m' /data/jenkins/test 172.16.10.26  172.16.10.27
#$1 远程服务启动服务个数
#$2 远程服务运行环境
#$3 远程服务目录  不含最后的/
#$4 远程服务名称 不含.jar 
#$5 远程服务启动参数  必须要有单引号‘’
#$6 当前脚本工作目录 不含最后的/
# 远程脚本为 $REMOTE_PATH/jar.sh <启动个数> <运行环境> <服务目录> <服务名称不含.jar> <jar包启动内存参数> 
# 后面所有的位置变量：需要部署的服务器ip地址
PARAMETER=$#
if [ $PARAMETER -le 6  ];then
    echo "参数错误，格式为：$0 <启动个数> <运行环境> <服务目录> <服务名称不含.jar> <jar包启动内存参数> <跳板机脚本工作目录>"
    exit 1         
fi
SCRIPT_DIR=/data/jenkins
#sudo chown -R admin:admin $SCRIPT_DIR
JAR_SH=common_jar.sh
WORK_DIR=$6
ANSIBLE_HOSTS=$WORK_DIR/hosts
JAR_NAME=$4\.jar
REMOTE_PATH=$3
MKDIR="mkdir -p $REMOTE_PATH"
SHELL_KILL_BACK="bash $REMOTE_PATH/$JAR_SH  $1  '$2'  $3  $4  '$5' kill_back "  #位置变量5必须要加''号
SHELL_DELBACK_START="bash $REMOTE_PATH/$JAR_SH  $1  '$2'  $3  $4  '$5' delback_start "
SHFIT=6
echo 去掉的参数个数为$SHFIT
shift $SHFIT
echo 部署的目标服务器为$* 
#  //防止$WORK_DIR 传了空参数进来，后面删除$WORK_DIR/lib时 删掉的是/lib
if [ -z $WORK_DIR  ];then
    echo "参数错误，格式为：$0 <启动个数> <运行环境> <服务目录> <服务名称不含.jar> <jar包启动内存参数> <跳板机脚本工作目录>"
    exit 1         
fi
# //再次防止$REMOTE_PATH传了空参数进来，后面删除远程服务器$REMOTE_PATH/lib时 删掉的是/lib
if [ -z $REMOTE_PATH  ];then
    echo "参数错误，格式为：$0 <启动个数> <运行环境> <服务目录> <服务名称不含.jar> <jar包启动内存参数> <跳板机脚本工作目录>"
    exit 1         
fi
for i in $*
do
cat > $ANSIBLE_HOSTS << EOF
[all]
$i
EOF
     echo "########################开始部署$i上的服务#####################################"
     echo 创建远程目录$REMOTE_PATH
     	 ansible -i $ANSIBLE_HOSTS all -m file  -a " path=$REMOTE_PATH  state=directory mode=0755 "
     echo 同步脚本到远程服务器上
     	 ansible -i $ANSIBLE_HOSTS all -m copy -a "src=$SCRIPT_DIR/$JAR_SH  dest=$REMOTE_PATH mode=0755 owner=admin group=admin"
     echo 执行远程脚本$SHELL_KILL_BACK 杀死服务并备份jar包及删除lib目录
	 ansible -i $ANSIBLE_HOSTS all -m shell  -a " $SHELL_KILL_BACK "
     echo 同步jar及lib（如果有）到主机$i 的$REMOTE_PATH
     if [ -d $WORK_DIR/lib ];then
        echo 开始拷贝lib目录到目标主机$i
            scp -r $WORK_DIR/lib $i:$REMOTE_PATH/       
     fi
     echo 开始拷贝jar包到目标主机$i	
     	    scp $WORK_DIR/$JAR_NAME $i:$REMOTE_PATH
     echo 执行远程脚本$SHELL_DELBACK_START 删除过期备份并启动服务
     	 ansible -i $ANSIBLE_HOSTS all -m shell  -a " $SHELL_DELBACK_START "
done
echo 删除跳板机上的jar包
     /bin/rm -rf $WORK_DIR/$JAR_NAME
if [ -d $WORK_DIR/lib ];then
     echo 删除跳板机上的lib目录
     /bin/rm -rf $WORK_DIR/lib
fi

