#!/bin/bash
# $1 jar或war类型  $2 app名称
if [ $1 == 'jar' ]
  then
    echo 'jar'
    cat > startup.sh <<EOF
#!/bin/bash
java -jar -Dforce-dev=true /data/apps/${2}.jar
tail -f /dev/null
EOF
    cat > Dockerfile <<EOF
FROM registry.cn-hangzhou.aliyuncs.com/ml_test/centos_jdk:1.0
COPY target/${2}.jar /data/apps/${2}.jar
COPY startup.sh /data/startup.sh
CMD ["/bin/bash /data/startup.sh"]
EOF
    chmod +x startup.sh
elif [ $1 == 'war' ]
  then
    echo 'war'
    cat > startup.sh <<EOF
#!/bin/bash
/bin/bash /data/tomcat8/bin/startup.sh
tail -f /dev/null
EOF
    cat > Dockerfile <<EOF
FROM registry.cn-hangzhou.aliyuncs.com/ml_test/centos_jdk:1.0
COPY target/${2}.war /data/tomcat8/webapps/${2}.war
COPY startup.sh /data/startup.sh
CMD ["/bin/bash /data/startup.sh"]
EOF
    chmod +x startup.sh
else
    echo 'error'
fi