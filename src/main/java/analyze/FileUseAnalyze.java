package analyze;

import component.EntryPointAnalyze;
import component.ResolveManifest;
import constant.ApkAndJavaConstants;
import dataflow.FileUseDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.manifest.binary.AbstractBinaryAndroidComponent;
import util.DirTraversal;
import util.Log;
import util.SootInit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;


public class FileUseAnalyze {

//    private static String apkPath = ApkAndJavaConstants.apkDir;
    private static String apkPath = System.getProperty("user.dir")+File.separator+"targetApp";
//    private static String apkPath ="/home/shared/download_apks";
//    private static String androidJar = ApkAndJavaConstants.androidJarPath;
    private static String androidJar = "/home/ms/appAnalysis/AndroidHome";

    public static final Logger logger= LoggerFactory.getLogger(FileUseAnalyze.class);

    public static final FileUseDetector detector=new FileUseDetector();
    
    public static final String logRootPath="/home/ms/appAnalysis/log/appUseFileInfo";


    public static void main(String[] args) {
//        apkPath=args[0];
//        androidJar=args[1];
        File apkDir=new File(apkPath);
        Log.openLog("fileNameRebuild.txt",false);
        new DirTraversal(){
            @Override
            public void work(File apkFile) throws Exception {
                if(!apkFile.getName().endsWith(".apk"))
                    return;
                FileUseAnalyze.logger.info("开始检测App:{}",apkFile);
                Log.write(Log.Mode.APP,apkFile.getName());
                HashSet<AbstractBinaryAndroidComponent> components = ResolveManifest.getAllComponents(apkFile.getPath());
                SootInit.initSootForAndroid(apkFile.getPath(), androidJar);
                HashMap<String, HashSet<SootMethod>> component2MapEntryPoint =
                        EntryPointAnalyze.getComponent2MapEntryPoint(components);
                Scene.v().getOrMakeFastHierarchy();
                String packageName = ResolveManifest.getPackageName(apkFile);
                detector.setPackageName(packageName);
                for (String component : component2MapEntryPoint.keySet()) {
                    FileUseAnalyze.logger.info("开始检测Component:{}",component);
                    HashSet<SootMethod> entrypoints = component2MapEntryPoint.get(component);
                    for(SootMethod method:entrypoints) {
                        if(method==null)
                            continue;
                        FileUseAnalyze.logger.info("开始检测入entry point:{}",method.getSignature());
                        doAnalyze(method);
                    }

                }
                FileUseAnalyze.logger.info("结束了");
                detector.writeLog();
                Scene.v().releaseFastHierarchy();
            }
        }.traverse(apkDir);
        Log.closeLog();
    }

    public void analyze(String apkPath){
        if(!apkPath.endsWith(".apk"))
            return;
        File apkFile = new File(apkPath);
        FileUseAnalyze.logger.info("开始检测App:{}",apkFile);
        HashSet<AbstractBinaryAndroidComponent> components = ResolveManifest.getAllComponents(apkPath);
        SootInit.initSootForAndroid(apkFile.getPath(), androidJar);
        HashMap<String, HashSet<SootMethod>> component2MapEntryPoint =
                EntryPointAnalyze.getComponent2MapEntryPoint(components);
        Scene.v().getOrMakeFastHierarchy();
        String packageName = ResolveManifest.getPackageName(apkFile);
        detector.setPackageName(packageName);
        Log.openLog(String.format("%s/%s.txt",logRootPath,packageName),false);
        Log.write(Log.Mode.APP,apkFile.getName());
        for (String component : component2MapEntryPoint.keySet()) {
//            FileUseAnalyze.logger.info("开始检测Component:{}",component);
            HashSet<SootMethod> entrypoints = component2MapEntryPoint.get(component);
            for(SootMethod method:entrypoints) {
                if(method==null)
                    continue;
//                FileUseAnalyze.logger.info("开始检测入entry point:{}",method.getSignature());
                doAnalyze(method);
            }



        }
//        FileUseAnalyze.logger.info("结束了");
        detector.writeLog();
        Scene.v().releaseFastHierarchy();
        Log.closeLog();
    }

    public static void doAnalyze(SootMethod method){
        if(method==null)
            return;
        detector.inter_forward(method,0,new ArrayList<>());
    }
}
