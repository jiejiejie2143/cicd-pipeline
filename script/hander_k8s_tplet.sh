#!/bin/bash
NAME_SPACE=$1
APP_NAME=$2
APLO_ENV_VALUE="$3"
APP_PORT=$4
IMAGE_TAG=$5
MEM_ENV_VALUE="$6"
sed -i  "/APLO_ENV_VALUE/s#APLO_ENV_VALUE#$APLO_ENV_VALUE#" ./templete/deploy_k8s.yaml
sed -i  "/MEM_ENV_VALUE/s#MEM_ENV_VALUE#$MEM_ENV_VALUE#" ./templete/deploy_k8s.yaml
for i in "/NAME_SPACE/s/NAME_SPACE/$NAME_SPACE/" \
"/APP_NAME/s/APP_NAME/$APP_NAME/" \
"/APP_PORT/s/APP_PORT/$APP_PORT/"  \
"/IMAGE_TAG/s#IMAGE_TAG#$IMAGE_TAG#"

do
sed -i $i ./templete/deploy_k8s.yaml
done
# 这里这个脚本是在 deploy_k8s.groovy 这个主脚本中执行的 所以相对路径是针对deploy_k8s.groovy 来讲的 所以是："./templete/deploy_k8s.yaml"