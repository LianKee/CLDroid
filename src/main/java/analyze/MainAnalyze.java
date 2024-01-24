package analyze;

import constant.ApkAndJavaConstants;
import dataflow.ExportedConfigFileDetector;
import org.xmlpull.v1.XmlPullParserException;
import soot.Scene;
import soot.SootClass;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.IAndroidComponent;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.manifest.binary.BinaryManifestActivity;
import soot.jimple.infoflow.android.manifest.binary.BinaryManifestBroadcastReceiver;
import soot.jimple.infoflow.android.manifest.binary.BinaryManifestContentProvider;
import soot.jimple.infoflow.android.manifest.binary.BinaryManifestService;
import util.SootInit;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class MainAnalyze {


    public static  String apkDir="";

    public static String androidJar="";

    public static  String logFileName="";

    public static  String permissionInfoFile="";

    public static  String filterAppInfo="";

    public static String sinkInfo="";

    public static String dataSavedDir="";

    public static String systemActionFile="";

    public static void main(String[] args) throws Exception {

//        apkDir=System.getProperty("user.dir")+ File.separator+"app";
        apkDir="/home/shared/download_apks";

//        androidJar= ApkAndJavaConstants.androidJarPath;
        androidJar= "/home/ms/appAnalysis/AndroidHome";
//        logFileName="Log.txt";
        logFileName="log7.txt";

//        logFileName="myLog.txt";
//
//        permissionInfoFile=System.getProperty("user.dir")+ File.separator+"PermissionInfo.txt";
        permissionInfoFile="/home/ms/ICFinder/PermissionInfo_Pixel3.txt";

//        filterAppInfo="./filterApp.txt";
//        sinkInfo=System.getProperty("user.dir")+File.separator+"sinks"+File.separator+"sink.txt";
        sinkInfo="/home/ms/ICFinder/sinks/sink.txt";

        dataSavedDir=System.getProperty("user.dir")+File.separator+"analyze_result";

//        systemActionFile=System.getProperty("user.dir")+File.separator+"SystemProtectedBroadcast_Pixel3.txt";
        systemActionFile="/home/ms/ICFinder/SystemProtectedBroadcast_Pixel3.txt";

//

        ExportedConfigFileDetector detector = new ExportedConfigFileDetector(apkDir, androidJar, logFileName, permissionInfoFile,systemActionFile,sinkInfo,dataSavedDir);
        detector.cal_exportComponentNumber();
//        detector.analyze();
//        detector.setTimeout(10000);
//        detector.start();

    }


    public static void runAnalyze(String apkPath){

//        apkDir="/home/shared/download_apks";
        apkDir="/home/ms/target";
        androidJar= "/home/ms/appAnalysis/AndroidHome";
        logFileName="log7.txt";
        permissionInfoFile="/home/ms/ICFinder/PermissionInfo_Pixel3.txt";
        filterAppInfo="./filterApp.txt";
        sinkInfo="/home/ms/ICFinder/sinks/sink.txt";
        dataSavedDir="/home/ms/ICFinder/analyze_result/data7";
        systemActionFile="/home/ms/ICFinder/SystemProtectedBroadcast_Pixel3XL.txt";

        ExportedConfigFileDetector detector = new ExportedConfigFileDetector(apkDir, androidJar, logFileName, permissionInfoFile, systemActionFile,sinkInfo,dataSavedDir);
        detector.run(apkPath);
    }

    public static void analyze(String apkPath){
        androidJar= "/home/ms/appAnalysis/AndroidHome";
        SootInit.initSootForAndroid(apkPath,androidJar);
        System.out.println("开始分析："+apkPath);

        boolean flag=false;
        for(SootClass cls:Scene.v().getClasses())
            if(cls.getName().contains("com.alibaba.fastjson")){
                flag=true;
                break;
            }
        if(flag){
            try {
                FileWriter fileWriter = new FileWriter("/home/ms/ICFinder/fastJosn.txt",true);
                BufferedWriter writer = new BufferedWriter(fileWriter);
                writer.write(apkPath+'\n');
                writer.close();
            }catch (Exception e){
                e.printStackTrace();
            }

        }
    }

    //获取设备中的权限信息
    public static HashMap<String, Integer> getDevicePermissionInfo(File permissionFile) {
        HashMap<String, Integer> res = new HashMap<>();
        try {
            FileInputStream in = new FileInputStream(permissionFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line = null;
            String permission = null;
            String protectLevel = null;
            while ((line = reader.readLine()) != null) {
                if (line.contains("permission:")) {
                    permission = line.split(":")[1];
                }
                if (line.contains("protectionLevel:")) {
                    protectLevel = line.split(":")[1];
                    if (protectLevel.contains("normal")) {
                        res.put(permission, 0);
                    } else if (protectLevel.contains("dangerous")) {
                        res.put(permission, 1);
                    } else if (protectLevel.contains("signature")) {
                        res.put(permission, 2);
                    } else {
                        res.put(permission, 3);
                    }
                }
            }
            return res;
        } catch (Exception e) {
            return res;
        }
    }


    // 对于给定应用的指定组件统计他们是不是存在权限申明了permission但是permission是normal的级别


    public static void analyze(String apkPath, HashSet<String> components) throws XmlPullParserException, IOException {
        //我们需要获得设备的权限列表
        permissionInfoFile="/home/ms/ICFinder/PermissionInfo_Pixel3.txt";
        System.out.println("开始分析应用："+apkPath);
        HashMap<String, Integer> devicePermissionInfo = getDevicePermissionInfo(new File(permissionInfoFile));

        ProcessManifest manifest = new ProcessManifest(apkPath);
        HashSet<String> res=new HashSet<>();

        //获取应用种自定义权限和他保护等级的映射
        for(AXmlNode permission:manifest.getManifest().getChildrenWithTag("permission")) {
            if(permission.getAttribute("protectionLevel") == null) devicePermissionInfo.put(permission.getAttribute("name").getValue().toString(), 0);
            else {
                devicePermissionInfo.put(permission.getAttribute("name").getValue().toString(),(Integer) permission.getAttribute("protectionLevel").getValue());
            }
        }

        //开始遍历组件
        //判断这个组件是不是我们要找的组件，如果是我们看是否申明了权限，如果申明了，看他的权限是不不是normal级别的
        for(BinaryManifestService service: manifest.getServices()){
            AXmlNode aXmlNode=service.getAXmlNode();
            String componentName = aXmlNode.getAttribute("name").getValue().toString();
            if(!components.contains(componentName))
                continue;
            System.out.println("检测到组件");
            if(!aXmlNode.hasAttribute("permission"))
                continue;
            String permission = aXmlNode.getAttribute("permission").getValue().toString();
            System.out.println("权限是："+permission);
            if(devicePermissionInfo.get(permission)==0){
                //如果应用中的权限未定义或者该权限的级别只为normal，那么记录下该应用以及组件
                res.add(componentName);
            }
        }
        for(BinaryManifestActivity activity: manifest.getActivities()){
            AXmlNode aXmlNode=activity.getAXmlNode();
            String componentName = aXmlNode.getAttribute("name").getValue().toString();
            if(!components.contains(componentName))
                continue;
            System.out.println("检测到组件");
            if(!aXmlNode.hasAttribute("permission"))
                continue;
            String permission = aXmlNode.getAttribute("permission").getValue().toString();
            System.out.println("权限是："+permission);
            if(devicePermissionInfo.get(permission)==0){
                //如果应用中的权限未定义或者该权限的级别只为normal，那么记录下该应用以及组件
                res.add(componentName);
            }
        }
        for(BinaryManifestBroadcastReceiver receiver: manifest.getBroadcastReceivers()){
            AXmlNode aXmlNode=receiver.getAXmlNode();
            String componentName = aXmlNode.getAttribute("name").getValue().toString();
            if(!components.contains(componentName))
                continue;
            System.out.println("检测到组件");
            if(!aXmlNode.hasAttribute("permission"))
                continue;
            String permission = aXmlNode.getAttribute("permission").getValue().toString();
            System.out.println("权限是："+permission);

            if(devicePermissionInfo.get(permission)==0){
                //如果应用中的权限未定义或者该权限的级别只为normal，那么记录下该应用以及组件
                res.add(componentName);
            }
        }
        for(BinaryManifestContentProvider provider: manifest.getContentProviders()){
            AXmlNode aXmlNode=provider.getAXmlNode();
            //provider这里我们只关心它的write permission
            String componentName = aXmlNode.getAttribute("name").getValue().toString();
            if(!components.contains(componentName))
                continue;
            System.out.println("检测到组件");

            if(!aXmlNode.hasAttribute("writePermission"))
                continue;
            String permission = aXmlNode.getAttribute("writePermission").getValue().toString();
            System.out.println("权限是："+permission);

            if(devicePermissionInfo.get(permission)==0){
                //如果应用中的权限未定义或者该权限的级别只为normal，那么记录下该应用以及组件
                res.add(componentName);
            }
        }

        //我们根据分析结果进行统计
        if(res.isEmpty())
            return;
        System.out.println("检测到使用权限过低的组件");
        try{
            BufferedWriter writer = new BufferedWriter(new FileWriter("/home/ms/ICFinder/weakPermissionAppAndConponents", true));
            writer.write("App:"+apkPath+'\n');
            for(String component : res){
                writer.write("COMPONENT:"+component+'\n');
            }
            writer.close();

        }catch (Exception e){
            e.printStackTrace();
        }

    }







}
