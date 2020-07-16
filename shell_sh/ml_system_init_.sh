#!/bin/bash
#####################################################################################
#time:2017-11-02
#系统基础优化脚本
#####################################################################################

#配置备份目录
BACK_CONF=~/back_conf
if [ ! -d "BACK_CONF" ]; then
    mkdir -p "$BACK_CONF"
fi

#检测云服务环境，HVM domU为亚马逊，Alibaba Cloud ECS为阿里云
yum -y install dmidecode  > /dev/null
ProductName=`dmidecode -t system|grep 'Product Name'|awk -F': ' '{print $2}'`
#系统版本
version=`cat /etc/redhat-release|awk -F'.' '{print $1}'|awk '{print $NF}'`

HOST_IP=$(ip route get 1.1.1.1 | awk '{print $NF;exit}')
echo "HOST_IP=$HOST_IP"


set_basic(){
echo "停止不必要服务,字符编码-------------------------------------------------------------------->"
version=`cat /etc/redhat-release|awk -F'.' '{print $1}'|awk '{print $NF}'`
if [ $version = 7 ];then
    #change the init 3
	#ln -sf /lib/systemd/system/multi-user.target /etc/systemd/system/default.target
	#systemctl set-default multi-user.target
	
    #disable unless services
	systemctl list-unit-files|grep enabled|awk '{ print $1 }'|grep -E -v "(^crond.service|^NetworkManager.service|^rsyslog.service|^sshd.service)" > chkconfig.txt
	for i in `cat ./chkconfig.txt`
	do
        systemctl disable $i > /dev/null 2>&1
	done
	sleep 1
	rm -rf chkconfig.txt
	
	systemctl stop firewalld > /dev/null
	systemctl disable firewalld > /dev/null
	#修改时区
	timezome=`date +%z`
	if [ $timezome != '+0800' ];then
	sudo ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
	fi
#configure the font display
cat > /etc/locale.conf  <<EOF
LANG="en_US.UTF-8"
EOF
#echo "font configure done"

	#set time
	yum install -y ntp > /dev/null
	systemctl enable ntpd
	systemctl start ntpd
	#/usr/sbin/ntpdate -u ntp.api.bz
	#/usr/sbin/ntpdate -u pool.ntp.org
	#timedatectl set-local-rtc 0
	
elif [ $version = 5 -o $version = 6 ];then
	#change the init 3,disable unless services
	yum -y install parted > /dev/null
	yum install -y sed > /dev/null
	sed -i 's/id:5:initdefault:/id:3:initdefault:/g' /etc/inittab
	chkconfig --list |awk '{ print $1 }'|grep -E -v "(^crond|^network|^sshd|^rsyslog)" > chkconfig.txt
	for i in `cat ./chkconfig.txt`
	do 
		chkconfig $i off
	done
	sleep 1
	rm -rf chkconfig.txt
	service iptables stop
	echo "init,chkconfig configure done"
#configure the font display
cat > /etc/sysconfig/i18n <<EOF
LANG="en_US.UTF-8"
EOF

#$echo "font configure done"
#set time
cat > /etc/sysconfig/clock <<EOF
ZONE="Asia/Shanghai"
UTC=true
ARC=false
EOF
	yum install -y ntp > /dev/null
	/usr/sbin/ntpdate ntp.api.bz
	date
	hwclock --systohc
	#turn off ipv6
	echo  "NETWORKING_IPV6=off" >> /etc/sysconfig/network
	echo "IPV6 disable"
else
	echo "script only support centos 5 later"
	exit 1
fi
}

set_limits() 
{
echo "优化资源使用配置文件----------------------------------------------------------------------->"
\cp /etc/security/limits.conf $BACK_CONF/limits.conf.`date '+%Y%m%d%H%M%S'`
\cp /etc/rc.local  $BACK_CONF/rc.local.`date '+%Y%m%d%H%M%S'`
#设置最大连接数
sed -i '/ulimit -SHn/d'  /etc/rc.local
echo  "ulimit -SHn 2097152" >> /etc/rc.local
#最大进程数和最大文件打开数限制
sed -i '/*          soft    nproc/d'  /etc/security/limits.d/*-nproc.conf
echo "*          soft    nproc     2097152" >> /etc/security/limits.d/*-nproc.conf
cat > /etc/security/limits.conf <<EOF  
# nofile 可以被理解为是文件句柄数 文件描述符 还有socket数
* soft nofile 1048576
* hard nofile 1048576
# 最大进程数
* soft nproc 1048576
* hard nproc 1048576
EOF
}


set_sysctl()
{
echo "优化系统内核------------------------------------------------------------------------------->"
\cp /etc/sysctl.conf $BACK_CONF/sysctl.conf.`date '+%Y%m%d%H%M%S'`
cat << EOF > /etc/sysctl.conf
#禁用包过滤功能
net.ipv4.ip_forward = 0
#不能启用源路由核查功能,会导致负载均衡概率性丢包
net.ipv4.conf.default.rp_filter = 0
net.ipv4.conf.all.rp_filter = 0
net.ipv4.conf.eth0.rp_filter = 0
#禁用所有IP源路由
net.ipv4.conf.default.accept_source_route = 0
#使用sysrq组合键是了解系统目前运行情况，为安全起见设为0关闭
kernel.sysrq = 0
#控制core文件的文件名是否添加pid作为扩展
kernel.core_uses_pid = 1
#开启SYN Cookies，当出现SYN等待队列溢出时，启用cookies来处理
net.ipv4.tcp_syncookies = 1
#每个消息队列的大小（单位：字节）限制
kernel.msgmnb = 65536
#整个系统最大消息队列数量限制
kernel.msgmax = 65536
#单个共享内存段的大小（单位：字节）限制，计算公式64G*1024*1024*1024(字节)
kernel.shmmax = 68719476736
#所有内存大小（单位：页，1页 = 4Kb），计算公式16G*1024*1024*1024/4KB(页)
kernel.shmall = 4294967296
#timewait的数量，默认是180000
net.ipv4.tcp_max_tw_buckets = 6000
#开启有选择的应答
net.ipv4.tcp_sack = 1
#支持更大的TCP窗口. 如果TCP窗口最大超过65535(64K), 必须设置该数值为1
net.ipv4.tcp_window_scaling = 1
#TCP读buffer
net.ipv4.tcp_rmem = 4096 131072 1048576
#TCP写buffer
net.ipv4.tcp_wmem = 4096 131072 1048576
#为TCP socket预留用于发送缓冲的内存默认值（单位：字节）
net.core.wmem_default = 8388608
#为TCP socket预留用于发送缓冲的内存最大值（单位：字节）
net.core.wmem_max = 16777216
#为TCP socket预留用于接收缓冲的内存默认值（单位：字节）
net.core.rmem_default = 8388608
#为TCP socket预留用于接收缓冲的内存最大值（单位：字节）
net.core.rmem_max = 16777216
#每个网络接口接收数据包的速率比内核处理这些包的速率快时，允许送到队列的数据包的最大数目
net.core.netdev_max_backlog = 262144
#web应用中listen函数的backlog默认会给我们内核参数的net.core.somaxconn限制到128，而nginx定义的NGX_LISTEN_BACKLOG默认为511，所以有必要调整这个值
#net.core.somaxconn = 262144
#系统中最多有多少个TCP套接字不被关联到任何一个用户文件句柄上。这个限制仅仅是为了防止简单的DoS攻击，不能过分依靠它或者人为地减小这个值，更应该增加这个值(如果增加了内存之后)
net.ipv4.tcp_max_orphans = 3276800
#记录的那些尚未收到客户端确认信息的连接请求的最大值。对于有128M内存的系统而言，缺省值是1024，小内存的系统则是128
net.ipv4.tcp_max_syn_backlog = 262144
#时间戳可以避免序列号的卷绕。一个1Gbps的链路肯定会遇到以前用过的序列号。时间戳能够让内核接受这种“异常”的数据包。这里需要将其关掉
net.ipv4.tcp_timestamps = 0
#为了打开对端的连接，内核需要发送一个SYN并附带一个回应前面一个SYN的ACK。也就是所谓三次握手中的第二次握手。这个设置决定了内核放弃连接之前发送SYN+ACK包的数量
net.ipv4.tcp_synack_retries = 1
#在内核放弃建立连接之前发送SYN包的数量
net.ipv4.tcp_syn_retries = 1
#开启TCP连接中time_wait sockets的快速回收
net.ipv4.tcp_tw_recycle = 1
#开启TCP连接复用功能，允许将time_wait sockets重新用于新的TCP连接（主要针对time_wait连接）
net.ipv4.tcp_tw_reuse = 1
#1st低于此值,TCP没有内存压力,2nd进入内存压力阶段,3rdTCP拒绝分配socket(单位：内存页)
net.ipv4.tcp_mem = 94500000 915000000 927000000
#如果套接字由本端要求关闭，这个参数决定了它保持在FIN-WAIT-2状态的时间。对端可以出错并永远不关闭连接，甚至意外当机。缺省值是60 秒。2.2 内核的通常值是180秒，你可以按这个设置，但要记住的是，即使你的机器是一个轻载的WEB服务器，也有因为大量的死套接字而内存溢出的风险，FIN- WAIT-2的危险性比FIN-WAIT-1要小，因为它最多只能吃掉1.5K内存，但是它们的生存期长些。
net.ipv4.tcp_fin_timeout = 15
#表示当keepalive起用的时候，TCP发送keepalive消息的频度（单位：秒）
net.ipv4.tcp_keepalive_time = 30
#对外连接端口范围
net.ipv4.ip_local_port_range = 2048 65000
#表示文件句柄的最大数量
fs.file-max = 102400
EOF
/sbin/sysctl -p >/dev/null 2>&1;
}

fomart_disk()
{
echo "格式化磁盘,要求4K对齐---------------------------------------------------------------------->"
#A=`fdisk -l|awk -F',' '/Disk \/dev\// {print $1}'`
A=`lsblk |grep vdb|grep -v vdb1|awk '{print $1}'`

if [ "$ProductName" = "Alibaba Cloud ECS" ]
then
#判断有无vdb,阿里云
#B="/dev/vdb"
B="vdb"
if [[ $A == $B ]]
then
    echo "包含/dev/vdb"
    if [ "`df -h | awk '{print $6}'|grep '^/data$'`" = "/data" ]
    then
      echo "/data已经挂载成功,不需要处理"
    else
      echo "/data未挂载,开始格式化磁盘"
	  [ ! -d /data ] && sudo mkdir -p /data
	  sudo parted /dev/vdb -s mklabel gpt  > /dev/null 2>&1
	  sudo parted /dev/vdb -s mkpart  primary 2048s 100%   > /dev/null 2>&1
	  sudo mkfs.ext4 -m0 /dev/vdb1   > /dev/null 2>&1
   	  sudo bash -c 'echo "/dev/vdb1               /data                   ext4    defaults        0 0" >> /etc/fstab'
	  sudo mount -a
    fi
 else
    echo "不包含/dev/vdb"
fi
elif [ "$ProductName" = "HVM domU" ]
then
#判断有无xvdb,亚马逊
B="/dev/xvdb"   #//亚马逊
B="/dev/xvde"	#//天翼云
if [[ $A == *$B* ]]
then
    echo "包含/dev/xvdb"
    if [ "`df -h | awk '{print $6}'|grep '^/data$'`" = "/data" ]
    then
      echo "/data已经挂载成功,不需要处理"
    else
      echo "/data未挂载,开始格式化磁盘"
	  [ ! -d /data ] && sudo mkdir -p /data
	  sudo parted /dev/xvdb -s mklabel gpt
	  sudo parted /dev/xvdb -s mkpart  primary 2048s 100%
	  sudo mkfs.ext4 -m0 /dev/xvdb1
	  sudo bash -c 'echo "/dev/xvdb1               /data                   ext4    defaults        0 0" >> /etc/fstab'
	  sudo mount -a
    fi
 else
    echo "不包含/dev/xvdb"
fi
else
  echo "未知环境"
fi
}

check_user_admin()
{
echo "admin用户检查------------------------------------------------------------------------------>" 
id admin >& /dev/null  
if [ $? -ne 0 ]  
then
   echo "admin用户不存在，开始创建"
   useradd admin
fi 
#创建公钥
if [ ! -d "/home/admin/.ssh" ];
then  
   mkdir /home/admin/.ssh
fi   
chown -R admin  /home/admin/.ssh 
chgrp -R admin  /home/admin/.ssh
cat > /home/admin/.ssh/authorized_keys <<EOF
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCs2Xtp7jKeUSoBvSa47ZvlgkOXcHVec23SeNemKMObwUWTPvg4BTCr6wJLlnIPCAAgMS8WWJmpsTcDKVovI4ytKi6eNLF2nwUzy5PvQ4NZPWK1hqSrsO025DHmoH8p6Wl0JFQ1+b6CBRWX763/rMU5L1WtQ8nyqBHjK3TVZhfqVO9tt2XQsaxtAKIZ2JIQEkNSLuo3rAcTG9uwGa/H3hpLMsb66iOfOv/bNwf0kpOTo2s/GulvoJl7e7P9ZtcE+oOvlytD1kJBfKVFizSNwI2aZZeqsuOu2UHH+8GNCUf38SK76JKWhpDijpHFpwTLB+bXgJ46VFOfUDTZz1Rh4u9h admin@iZ23kdt42rrZ
EOF
chown admin /home/admin/.ssh/authorized_keys
chgrp admin /home/admin/.ssh/authorized_keys
chmod 600 /home/admin/.ssh/authorized_keys
#sudo 权限
sed -i '/admin ALL=(ALL) NOPASSWD: ALL/d'  /etc/sudoers
echo "admin ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers

if [  -d "/data/home/admin" ];
then  
   echo "/data/home/admin 已经存在" 
   chown -R admin.admin /data
  else
   echo "开始创建/data/home/admin目录"
   mkdir -p /data/home/admin
   chown -R admin.admin /data
fi 
}

set_core_history(){
echo "设置core-file,histroy记录显示时间条数------------------------------------------------------>"
sed -i 's/^HISTSIZE=.*/HISTSIZE=100000/' /etc/profile

}

deny_ssh_login(){
echo "禁止ssh密码登录"
version=`cat /etc/redhat-release|awk -F'.' '{print $1}'|awk '{print $NF}'`
if [ $version = 7 ];then
   #sed -i 's/PermitRootLogin yes/PermitRootLogin no/' /etc/ssh/sshd_config
   sed -i 's/PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
   systemctl restart sshd
elif [ $version = 6 ];then
   #sed -i 's/PermitRootLogin yes/PermitRootLogin no/' /etc/ssh/sshd_config
   sed -i 's/PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
   service sshd restart
else
   echo "script only support centos 6 later"
   exit 1
fi
}
dis_selinux(){
echo "关闭SELINUX-------------------------------------------------------------------------------->"
cat /etc/selinux/config | grep '^SELINUX=enforcing'  >  /dev/null
   if [ $? -eq 0 ]
    then
     sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config 2>&1
	 echo "2s后重启------------------------------------------------------------------------------->"
	 sync
	 sleep 2
	 sync
	 reboot
   fi
}

jdk_install(){
echo "172.16.30.110 yum.mymlsoft.com" >> /etc/hosts
yum install -y nc lsof > /dev/null 2>&1
nc -v -z -w 2 yum.mymlsoft.com 80 > /dev/null 2>&1
if [ $? -eq 0 ]
  then
    cd ~
    wget http://yum.mymlsoft.com/jdk-8u221-linux-x64.rpm > /dev/null 2>&1
    yum localinstall jdk-8u221-linux-x64.rpm -y > /dev/null 2>&1
    sed -i s'#securerandom.source=file:/dev/random#securerandom.source=file:/dev/./urandom#' /usr/java/jdk1.8.0_221-amd64/jre/lib/security/java.security
    echo "JDK安装成功"
  else
    echo "无法访问yum，JDK安装失败！！！"
fi
}

echo "--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------"
set_basic
set_limits
set_sysctl
fomart_disk
check_user_admin
set_core_history
deny_ssh_login
dis_selinux
jdk_install
