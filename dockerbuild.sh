#!/bin/bash
cd $WORKSPACE/$JOB_NAME
if [[ $GIT_BRANCH =~ "master" ]];then
		ENV=ml_prod
    else
    	ENV=ml_test
fi
echo 部署环境为$ENV
docker login  registry.cn-hangzhou.aliyuncs.com

cat > Dockerfile <<EOF
FROM registry.cn-hangzhou.aliyuncs.com/$ENV/centos_jdk:1.0
ADD  target/$JOB_NAME.jar /data/apps/$JOB_NAME.jar
EXPOSE 9999
ENTRYPOINT [ "java",  "-Dforce-dev=true", "-jar", "/data/apps/$JOB_NAME.jar" ]
EOF

docker build -t registry.cn-hangzhou.aliyuncs.com/$ENV/$JOB_NAME:$BUILD_NUMBER .
docker push registry.cn-hangzhou.aliyuncs.com/$ENV/$JOB_NAME:$BUILD_NUMBER
docker image rm registry.cn-hangzhou.aliyuncs.com/$ENV/$JOB_NAME:$BUILD_NUMBER

kubectl --kubeconfig=/data/kubernetes/k8s.config -n default  \
set image deployment/$JOB_NAME   $JOB_NAME=registry.cn-hangzhou.aliyuncs.com/$ENV/$JOB_NAME:$BUILD_NUMBER