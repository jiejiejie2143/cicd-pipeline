import hudson.model.*
import com.cloudbees.hudson.plugins.folder.*

def maxNumber = 64
def str_view = "常规项目-开发环境"

// 思路 ：找出视图下的所有文件夹（拿到文件名）---找到文件夹下的所有job（拿到job全名）---删除job的构建历史！！
def wenjianjias = Hudson.instance.getView(str_view).getAllItems()
//printf("需要遍历文件夹是：$wenjianjias \n")
for (wenjianjia in wenjianjias) {
    def fname = wenjianjia.getFullName()
//    printf("每次名字$fname \n")
    Job_List = Hudson.instance.getView(str_view).getItem(fname).getAllJobs()
//    printf("需要遍历的任务是：$Job_List \n")
    for(jbname in Job_List) {
        def jbbame = jbname.getFullName()
        printf("每次名字是：$jbbame \n")
        Jenkins.instance.getItemByFullName(jbbame).builds.findAll {
            it.number <= maxNumber
        }.each {
            it.delete()
        }
    }
}

// 这样拿到的全名是可以直接删除的，不用管他的
// base-共享冰箱-test/base-fridge-server-test/com.hongmei:resource-service（这个com.xxxxx这个也是名字中的一部分）

//Job_List = Hudson.instance.getView(str_view).getItem(str_folder).getAllJobs()
//printf("需要遍历的任务是：$Job_List \n")
//
//for(jbname in Job_List) {
//    def jbbame = jbname.getFullName()
//    printf("每次名字$jbbame \n")
//}

