#!/bin/bash
#使用示例： ./common.war.sh  env=FAT /data/superapp/tomcat-saboss-8220  saboss < kill_back || delback_start >
#$1=applo环境    
#$2=tomcat的路径    不能有最后的/ 
#$3=war包的名称    不带.war
id admin >& /dev/null
if [ $? -ne 0 ];then
	sudo useradd admin
fi
PARAMETER=$#
if [ $PARAMETER -ne 4  ];then
    echo "参数错误，格式为：$0  <运行环境> <服务目录> <服务名称不含.war>  <kill_back || delback_start>"
    exit 1
fi
APLO_ENVFILE=/opt/settings/server.properties
APLO_ENV=$1
TOMCAT_DIR=$2
WAR_DIR=$TOMCAT_DIR/webapps
START_SH=$TOMCAT_DIR/bin/catalina.sh
WAR_NAME=$3                          ## 就写Jenkins上的项目的war包名就行
TYPE=$4 
## applo env   tomcat的工程，先把环境写进去再说，无所谓的！
sudo mkdir -p /opt/settings
sudo chown -R admin:admin /opt/settings
sudo echo -e $APLO_ENV > $APLO_ENVFILE
#cat > $APLO_ENVFILE << EOF
#$APLO_ENV
#EOF
##如果$TOMCAT_DIR为空的话，pid就会有一大堆！$TOMCAT_DIR有值，pid大不了为空！###
if [ -z  $TOMCAT_DIR  ];then  
        echo "参数错误，格式为：$0  <运行环境> <服务目录> <服务名称不含.war>"
        exit 1
elif [[ $TOMCAT_DIR == *tomcat* ]];then
##war工程grep的关键字，必须包含tomcat，不然不让杀！
	echo  "$TOMCAT_DIR 包含tomcat 可以grep pid"
	P_ID=`ps -ef | grep -w "$TOMCAT_DIR" | grep -v "grep"  | grep -v  "$0" | awk '{print $2}'`
else 
	echo 非法的tomcat目录
	exit 1
fi
#########输出一下当前环境的一些信息#####################################
echo  当前环境是 `cat  $APLO_ENVFILE` !
echo  tomcat目录是$TOMCAT_DIR
echo  war包名是$WAR_NAME
echo 之前服务的pid为$P_ID
########调整文件及目录的一些权限#######################################
set_permission(){
	sudo chown -R admin:admin $TOMCAT_DIR
	sudo mkdir -p  /opt/settings
	sudo chown -R admin:admin /opt/settings
	sudo mkdir -p /data/logs
	sudo chown -R admin:admin /data/logs
}

################################################################################################################
BACK_DIR=$TOMCAT_DIR/back_war

back(){  
    ##备份完之后就要删除项目目录
    if [ ! -d  $BACK_DIR ];then
        mkdir $BACK_DIR
    fi
    if [ -f $WAR_DIR/$WAR_NAME\.war ]; then
    	cp $WAR_DIR/$WAR_NAME\.war  $BACK_DIR/${WAR_NAME}-`date +%F-%H_%M`.war
    	echo "++++++已完成$WAR_DIR/$WAR_NAME\.war备份++++++"
 	/bin/rm -rf $WAR_DIR/$WAR_NAME
	echo "++++++已删除项目目录$WAR_DIR/$WAR_NAME+++++++"
    fi
}
delback(){
    SAVE_COUNT_WAR=3
    cd  $BACK_DIR
    #1 判断当前备份WAR包个数
    COUNT_WAR=$(ls -l | grep "$WAR_NAME" | wc -l)
    echo "###### total WAR counts is " $COUNT_WAR
    echo "###### all WAR  name : " $(ls -tr)
    # 能够删除的备份WAR包个数
    DEL_COUNT_WAR=$[$COUNT_WAR - $SAVE_COUNT_WAR]
    echo "###### can be del WAR count is : " $DEL_COUNT_WAR
    #2 判断并进行删除
    if [ $DEL_COUNT_WAR -gt 0 ] ; then
            echo "####starting del WAR ..."
            #获取时间最久的WAR包名称
            DEL_WAR_NAME=$(ls -tr | head -n $DEL_COUNT_WAR)
            #循环依次删除时间久的文件
            for each in $DEL_WAR_NAME
                    do
                        echo "deling file is " $each
                        rm -rf $each
                    done
    fi
    echo "###### finally all WAR name is : " $(ls -tr)
    }
##########################################################################################################
 start(){
    nohup $START_SH start &
    sleep 2
    PID=`ps -ef | grep -w "$TOMCAT_DIR" | grep -v "grep"  | grep -v  "$0" | awk '{print $2}'`
    echo "++++++$TOMCAT_DIR 服务已经启动 服务pid为 $PID!++++++++"
}
###########################################################################################################
 stop(){
    if [ -z  "$P_ID" ];then ##之前的服务有多个也不会报错了！！
            echo "++++之前的$TOMCAT_DIR服务 并没有运行"
        else
            sudo kill -9 $P_ID	###其实可以直接kill多个pid的！！！
            echo "+++++++已经杀死 $TOMCAT_DIR 服务++++++++++"
        fi
}
############################################################################################################
 restart(){
    stop
    sleep 3
    start
}

if [ $TYPE == "kill_back" ];then
        set_permission
	stop 
        back
elif [ $TYPE == "delback_start" ];then
	set_permission
	delback
	start 
else
	echo "参数错误，格式为：$0  <运行环境> <服务目录> <服务名称不含.war> < kill_back || delback_start>"
fi

exit 0

