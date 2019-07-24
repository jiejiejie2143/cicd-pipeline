FROM registry.cn-hangzhou.aliyuncs.com/ml_test/centos_jdk:1.0
COPY  target/*.war /data/tomcat8/webapps/
CMD ["/bin/bash /data/startup.sh"]