import hudson.model.*
import com.cloudbees.hudson.plugins.folder.*

def str_view = "常规项目-正式环境"
def str_new_view = "常规项目-测试环境"
def str_folder = "QiJianGongSi_Master"
def str_new_folder = "QiJianGongSi_Master-test42"
def str_search = "qijiangongsi"
def str_replace_list = ["test42"]
["common","superapp-chiq3ac-facade","superapp-facade-construction","superapp-facade-elecard","superapp-facade-joinservice","superapp-facade-tool","superapp-chiq3ac-service","superapp-service-construction","superapp-service-elecard","superapp-service-joinservice","superapp-service-tool"]
["cdc-common","cdc-facade-app","cdc-facade-cloud","cdc-facade-device","mqtt-push-facade","cdc-service-app","cdc-service-cloud","cdc-service-device","mqtt-push-service"]
def str_newview = "superapp-QiJianGongSi_Master"
def view = Hudson.instance.getView(str_view).getItem(str_folder).getAllJobs()
printf("需要遍历的任务是：$view \n")

// create a folder to save job if Newfolder is not exists
Folder Newfolder = Hudson.instance.getView(str_new_view).getItem(str_new_folder)
printf("判断文件夹是否存在：$Newfolder \n")
if (Newfolder == null) {
    Destfolder = Hudson.getInstance().createProject(Folder.class, str_new_folder )
    //create Destfolder and add it to str_new_view
    toadd_view = Hudson.instance.getView(str_new_view)
    toadd_view.add(Destfolder)
}else {
    Destfolder = Hudson.instance.getView(str_new_view).getItem(str_new_folder)
}
printf("复制后的任务需要添加到文件夹：$Destfolder \n")

//create a view to add job  if newview is not exists
ListView newview = Hudson.instance.getView(str_new_view).getItem(str_new_folder).getView(str_newview)
printf("判断目标视图是否存在：$newview \n")

if (newview == null) {
    Destview = new ListView(str_newview)
    Destfolder.addView(Destview)
}else {
    Destview = Hudson.instance.getView(str_new_view).getItem(str_new_folder).getView(str_newview)
}
printf("复制后的任务需要添加到视图：$Destview \n")

for (str_replace in str_replace_list) {
    printf("$str_replace \n")

    for (item in view) {
        newName = item.getName().replace(str_search, str_replace)
        def job
        try {
            job = Hudson.instance.copy(item, newName)

        } catch (IllegalArgumentException e) {
            println(e.toString())
            printf("$newName job is exists \n")
            continue
        } catch (Exception e) {
            println(e.toString())
            continue
        }
        job.disabled = false
        job.save()
        printf("复制并改名后的任务是：$job \n")
// move job to Destfolder and add to Destview
        Items.move(job, Destfolder)
        Destview.add(job)
    }
}