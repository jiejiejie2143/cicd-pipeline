#!/bin/bash
# $1 jar或war类型  $2 tar包名称
JAR_NAME=$(echo $2 |cut  -f1 -d'_')
LOG_ENV=$3
if [ $1 == 'jar' ]
  then
    echo 'jar'
    cat > startup.sh <<EOF
#!/bin/bash
java  -server -d64  \$MEM_ENV -XX:+UseParNewGC -XX:ParallelGCThreads=4 -XX:MaxTenuringThreshold=5 -XX:+UseConcMarkSweepGC \
-XX:+DisableExplicitGC -XX:+UseCMSInitiatingOccupancyOnly -XX:+ScavengeBeforeFullGC  -XX:+CMSParallelRemarkEnabled  \
-XX:CMSInitiatingOccupancyFraction=60 -XX:+CMSClassUnloadingEnabled  -XX:SoftRefLRUPolicyMSPerMB=0  \
-XX:+ExplicitGCInvokesConcurrent -XX:+PrintGCDetails -Dlog.env=$LOG_ENV  -Duser.home=/data  \
-XX:+PrintHeapAtGC -XX:+UseGCLogFileRotation -XX:+HeapDumpOnOutOfMemoryError -XX:-OmitStackTraceInFastThrow \
-Duser.timezone=Asia/Shanghai -Dclient.encoding.override=UTF-8 -Dfile.encoding=UTF-8 \
-Djava.security.egd=file:/dev/./urandom -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=5M -jar \$APLO_ENV /data/apps/$JAR_NAME.jar
tail -f /dev/null
EOF
    chmod +x startup.sh
    cat > Dockerfile <<EOF
FROM registry-vpc.cn-hangzhou.aliyuncs.com/ml_base/jre1.8-alpine:v1.1
ADD target/${2} /data/apps/
COPY startup.sh /data/startup.sh
CMD ["/bin/sh","/data/startup.sh"]
EOF

elif [ $1 == 'war' ]
  then
    echo 'war'
    cat > startup.sh <<EOF
#!/bin/bash
mkdir -p /opt/settings
touch /opt/settings/server.properties
echo "env=\$APLO_ENV" > /opt/settings/server.properties
/bin/bash /data/tomcat8/bin/startup.sh
tail -f /dev/null
EOF
    chmod +x startup.sh
    cat > Dockerfile <<EOF
FROM registry-vpc.cn-hangzhou.aliyuncs.com/ml_test/centos_jdk:1.0
ADD target/${2} /data/tomcat8/webapps/
COPY startup.sh /data/startup.sh
CMD ["/data/startup.sh"]
EOF

else
    echo 'error'
fi
