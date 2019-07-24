FROM registry.cn-hangzhou.aliyuncs.com/ml_base/centos_jdk:1.0
COPY  target/*.war /data/tomcat8/webapps/
CMD ["/data/startup.sh"]