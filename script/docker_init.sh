#!/bin/bash
# $1 jar或war类型  $2 app名称
# 暂时弃用
if [ $1 == 'jar' ]
  then
    echo 'jar'
    cat > startup.sh <<EOF
#!/bin/bash
java  -server -d64  \$MEM_ENV -XX:+UseParNewGC -XX:ParallelGCThreads=4 -XX:MaxTenuringThreshold=5 -XX:+UseConcMarkSweepGC \
-XX:+DisableExplicitGC -XX:+UseCMSInitiatingOccupancyOnly -XX:+ScavengeBeforeFullGC  -XX:+CMSParallelRemarkEnabled  \
-XX:CMSInitiatingOccupancyFraction=60 -XX:+CMSClassUnloadingEnabled  -XX:SoftRefLRUPolicyMSPerMB=0  \
-XX:+ExplicitGCInvokesConcurrent -XX:+PrintGCDetails \$LOG_ENV  -Duser.home=/data  \
-XX:+PrintHeapAtGC -XX:+UseGCLogFileRotation -XX:+HeapDumpOnOutOfMemoryError -XX:-OmitStackTraceInFastThrow \
-Duser.timezone=Asia/Shanghai -Dclient.encoding.override=UTF-8 -Dfile.encoding=UTF-8 \
-Djava.security.egd=file:/dev/./urandom -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=5M -jar \$APLO_ENV /data/apps/${2}.jar
tail -f /dev/null
EOF
    chmod +x startup.sh
    cat > Dockerfile <<EOF
FROM registry-vpc.cn-hangzhou.aliyuncs.com/ml_base/jre1.8-alpine:v1.1
COPY target/${2}.jar /data/apps/${2}.jar
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
COPY target/${2}.war /data/tomcat8/webapps/${2}.war
COPY startup.sh /data/startup.sh
CMD ["/data/startup.sh"]
EOF

else
    echo 'error'
fi
