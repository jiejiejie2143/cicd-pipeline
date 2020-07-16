#!/bin/bash
id admin >& /dev/null
if [ $? -ne 0 ];then
        sudo useradd admin
fi
PARAMETER=$#
if [ $PARAMETER -ne 6  ];then
    echo "参数错误，格式为：$0 <启动个数> <运行环境> <服务目录> <服务名称> <服务启动内存>  <kill_back || delback_start>"
    exit 1 		
fi
NUM=$1
APLO_ENV=$2
SERVICE_DIR=$3
SERVICE_NAME=$4
START_MEM=$5
TYPE=$6
START_ENV="-server -d64 $START_MEM  -XX:+UseParNewGC -XX:ParallelGCThreads=4 -XX:MaxTenuringThreshold=5 -XX:+UseConcMarkSweepGC -XX:+DisableExplicitGC -XX:+UseCMSInitiatingOccupancyOnly -XX:+ScavengeBeforeFullGC  -XX:+CMSParallelRemarkEnabled  -XX:CMSInitiatingOccupancyFraction=60 -XX:+CMSClassUnloadingEnabled  -XX:SoftRefLRUPolicyMSPerMB=0   -XX:+ExplicitGCInvokesConcurrent -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCApplicationConcurrentTime -XX:+PrintHeapAtGC -XX:+UseGCLogFileRotation -XX:+HeapDumpOnOutOfMemoryError -XX:-OmitStackTraceInFastThrow -Duser.timezone=Asia/Shanghai -Dclient.encoding.override=UTF-8 -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom -Xloggc:$SERVICE_DIR/gc.log -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=5M -XX:HeapDumpPath=$SERVICE_DIR/HeapDumpOnOutOfMemoryError/ "
JAR_NAME=$SERVICE_NAME\.jar
BACK_DIR=$SERVICE_DIR/back_jar
#再次保证目录权限正确，特别是log目录，不然写不了日志就会导致服务启动不了！
set_permission(){
	sudo chown -R admin:admin $SERVICE_DIR
	sudo mkdir -p /data/logs
	sudo chown -R admin:admin /data/logs
}

##  这里有个大坑，是因为穿参执行的时候SERVICE_NAME是脚本的参数，所以就会把脚本执行本身的pid找到，就会导致后面的判断一致多参数报错，杀不了！！
##################如果$SERVICE_NAME 为空的话，pid就会去找.jar，就有一大堆！$SERVICE_NAME有值，pid大不了为空###################
if [ -z  $SERVICE_NAME  ];then
        echo "SERVICE_NAME参数错误，格式为：$0  <启动个数> <运行环境> <服务目录> <服务名称> <服务启动内存>  <kill_back || delback_start>"
        exit 1
elif [[ $SERVICE_DIR/$JAR_NAME == *data* ]];then
###所有的jar包都放在data目录启动，所有jar包的绝对路径必须包含data
        echo  "$SERVICE_DIR/$JAR_NAME 包含data 可以grep pid"
##  pid 这句要好好检查！！！一旦grep那个变量没有，就全部服务都杀了！！！
        P_ID=`ps -ef | grep -w "$SERVICE_DIR/$JAR_NAME" | grep -v "grep"  | grep -v  "$0" | awk '{print $2}'`
else
        echo 非法的grep 关键字 不能获取pid
        exit 1
fi
####################输出一下当前环境信息#######################################
echo 启动个数为$NUM个
echo  applo环境是$APLO_ENV
echo  服务启动目录是$SERVICE_DIR
echo  jar包名是$JAR_NAME
#########################备份函数逻辑##########################################
back(){
#// 备份并删除之前的服务lib目录及jar包
    if [ ! -d $BACK_DIR ];then
        mkdir -p $BACK_DIR
    fi
    if [ -f "$SERVICE_DIR/$JAR_NAME" ]; then
        if [ -d "$SERVICE_DIR/lib" ];then
            mkdir $BACK_DIR/$SERVICE_NAME-`date +%F-%H_%M`
            cp -r $SERVICE_DIR/lib $BACK_DIR/$SERVICE_NAME-`date +%F-%H_%M`/
            cp $SERVICE_DIR/$JAR_NAME  $BACK_DIR/$SERVICE_NAME-`date +%F-%H_%M`/
	          /bin/rm -rf $SERVICE_DIR/lib
	          /bin/rm -rf $SERVICE_DIR/$JAR_NAME
            echo "++++++已删除项目目录$SERVICE_DIR/lib 和 $SERVICE_DIR/$JAR_NAME +++++++"
        else
            cp $SERVICE_DIR/$JAR_NAME  $BACK_DIR/$SERVICE_NAME-`date +%F-%H_%M`.jar
	    /bin/rm -rf $SERVICE_DIR/$JAR_NAME
            echo "++++++已删除$SERVICE_DIR/$JAR_NAME+++++++"
        fi
    echo "==已完成jar包备份=="
    fi
    }
####################删除备份函数逻辑##########################################
 delback(){
###  永远只保留三个备份的jar包，不管修改日期。
    SAVE_COUNT_JAR=3
    cd  $BACK_DIR
    #1 判断当前备份jar包个数
    COUNT_JAR=$(ls -l | grep "$SERVICE_NAME" | wc -l)
    echo "###### total jar counts is " $COUNT_JAR
    echo "###### all jar  name : " $(ls -tr)
    # 能够删除的备份jar包个数
    DEL_COUNT_JAR=$[$COUNT_JAR - $SAVE_COUNT_JAR]
    echo "###### can be del jar count is : " $DEL_COUNT_JAR
    #2 判断并进行删除
    if [ $DEL_COUNT_JAR -gt 0 ] ; then
            echo "####starting del jar ..."
            #获取时间最久的JAR包名称
            DEL_JAR_NAME=$(ls -tr | head -n $DEL_COUNT_JAR)
            #循环依次删除时间久的文件
            for each in $DEL_JAR_NAME
                    do
                        echo "deling file is " $each
                        rm -rf $each
                    done
    fi
    echo "###### finally all jar name is : " $(ls -tr)
    }
##################杀服务逻辑，全部已经调整为这个#############################
 stop(){
     echo  之前的pid是  $P_ID ！
     if [ -z  "$P_ID" ];then ##之前的服务有多个也不会报错了！！
         echo "$JAR_NAME is not running !"
     else
         sudo kill -9 $P_ID  ###其实可以直接kill多个pid的！！！
         echo "=== stop $JAR_NAME"
     fi
        }
########################启动服务逻辑#########################################
 start(){
     echo 启动个数为$NUM
     if [ -z $NUM ];then
         echo "参数错误，格式为：$0 <启动个数> <jar包名字>"
     else
         while ((NUM>0))
     do
         echo  开始启动$JAR_NAME
	 nohup java $START_ENV  $APLO_ENV -jar $SERVICE_DIR/$JAR_NAME  >/dev/null 2>&1 &
         sleep 2
	 PID=`ps -ef | grep -w "$SERVICE_DIR/$JAR_NAME" | grep -v "grep"  | grep -v  "$0" | awk '{print $2}'`
    	 echo "++++++$SERVICE_NAME 服务已经启动 服务pid为 $PID!++++++++"
         ((NUM--))
     done
     fi
        }
 
 restart(){
    stop
    sleep 3
    start
    }
###################根据最后一个参数判断需要调用的函数########################################
if [ $TYPE == "kill_back" ];then
        set_permission
        stop
        back
elif [ $TYPE == "delback_start" ];then
        set_permission
        delback
        start
else
        echo "参数错误，格式为：$0  <启动个数> <运行环境> <服务目录> <服务名称> <服务启动内存>  <kill_back || delback_start>"
	exit 1
fi

