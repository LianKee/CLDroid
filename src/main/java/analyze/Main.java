package analyze;

import cg.CallGraphUtils;
import component.EntryPointAnalyze;
import component.ResolveManifest;
import constant.ApkAndJavaConstants;
import constant.EntryPointsDefinition;
import constant.StrawPointsDefinition;
import dataflow.Point;
import dataflow.StrawDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.infoflow.android.manifest.binary.AbstractBinaryAndroidComponent;
import util.DirTraversal;
import util.Log;
import util.SootInit;

import java.io.*;
import java.util.*;


public class Main {

    private static String apkDir = ApkAndJavaConstants.apkDir;
    private static String androidJar = ApkAndJavaConstants.androidJarPath;
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final List<String> lifeCycleMethodList = EntryPointsDefinition.getAllLifeCycleMethodList();

    private static final String GET_INTENT = "android.content.Intent getIntent()";

    private static final StrawDetector analyzer=new StrawDetector();

    private static String logFileName = "Log.txt";

    private static String permissionInfoPath="./PermissionInfo.txt";

    private static int max = 100000;

    private static HashSet<String> filterAppSet=new HashSet<>();

    private static String appFilterFile="/home/ms/appAnalysis/filterApp.txt";


    public static void main(String[] args) throws IOException, XmlPullParserException {

//        apkDir = args[0];
        apkDir = System.getProperty("user.dir")+File.separator+"App_test";
//        androidJar = args[1];
        androidJar = ApkAndJavaConstants.androidJarPath;
//        logFileName = args[2];
        logFileName = "./detectData";
//        max = Integer.parseInt(args[3]);
        max = 100;
//        permissionInfoPath=args[4];
        permissionInfoPath=System.getProperty("user.dir")+File.separator+"PermissionInfo.txt";

        logger.info("the straw detector begin running ...");

//        appFilterFile=args[5];
        appFilterFile="./filterApp.txt";


        Log.openLog(logFileName, false);
        File apkDir = new File(Main.apkDir);

        HashSet<String> detectedAppInfo = getDetectedAppInfo(logFileName);
        logger.info("retrieve permission info ... ");
        HashMap<String, Integer> permissionInfo = getDevicePermissionInfo(new File(permissionInfoPath));
        logger.info("init filter app set ...");
        initFilterApp();

        final int[] counter = {0};

        new DirTraversal(){
            @Override
            public void work(File apkFile) throws Exception {

                if (counter[0] > max)
                    return;
                if(detectedAppInfo.contains(apkFile.getName()))
                    return;
                if(!apkFile.getName().endsWith(".apk"))
                    return;
                if(!filterAppSet.isEmpty()&&!filterAppSet.contains(apkFile.getName().split(".apk")[0].trim()))
                    return;

                counter[0]++;

                Log.write(Log.Mode.APP, apkFile.getName());
                String versionName = ResolveManifest.getVersionName(apkFile);
                Log.write(Log.Mode.VERSION,versionName);
                Log.write(Log.Mode.PATH,apkFile.getPath());

                Main.logger.info("straw detector begin detect {}", apkFile.getName());

                Main.logger.info("straw detector begin detecting all exported components ");
                HashSet<AbstractBinaryAndroidComponent> components = ResolveManifest.getReachableComponents(apkFile.getPath(),permissionInfo,new HashSet<>());
                SootInit.initSootForAndroid(apkFile.getPath(), androidJar);
                Main.logger.info("begin detecting app: {} ,there are {} exported components in it", apkFile.getName(), components.size());
                HashMap<String, HashSet<SootMethod>> component2MapEntryPoint =
                        EntryPointAnalyze.getComponent2MapEntryPoint(components);
//                List<SootMethod> entryPoints=new ArrayList<>();
//                for (String component : component2MapEntryPoint.keySet()) {
//                    HashSet<SootMethod> entrypoints = component2MapEntryPoint.get(component);
//                    for(SootMethod m:entrypoints){
//                        if(m==null)
//                            continue;
//                        entryPoints.add(m);
//                    }
//                }
//                Scene.v().setEntryPoints(entryPoints);
//                try {
//                    Main.logger.info("begin build cg ...");
//                    PackManager.v().getPack("cg").apply();
//                }catch (Exception e){
//                    return;
//                }
                Main.logger.info("begin data flow analyze");
                for (String component : component2MapEntryPoint.keySet()) {
                    HashSet<SootMethod> entrypoints = component2MapEntryPoint.get(component);
                    Main.logger.info("begin detect component: {} there are {} entry points that may be exploited", component, entrypoints.size());
                    doAnalyze(entrypoints, component);
                }
            }
        }.traverse(apkDir);
        Log.closeLog();

    }

    private static void doAnalyze(HashSet<SootMethod> entrypoints, String component) {

        boolean flag = false;

        for (SootMethod entryPoint : entrypoints) {
            if (entryPoint == null)
                continue;
            analyzer.setEntryPoint(entryPoint.getSignature());
            logger.info("begin detecting {}", entryPoint.getSignature());
            if (lifeCycleMethodList.contains(entryPoint.getSubSignature())) {
                int index = 0;
                List<Type> parameterTypes = entryPoint.getParameterTypes();
                for (; index < parameterTypes.size(); index++) {
                    if (parameterTypes.get(index).toString().equals("android.content.Intent") || parameterTypes.get(index).toString().equals("android.content.ContentValues"))
                        break;
                }
                if (index != parameterTypes.size()) {
                    logger.info("run data flow analyze ....");
                    analyzer.run(entryPoint,null,null,index,0,new ArrayList<>());
                }
                // 分析代码中是否包含getIntent调用
                HashSet<Unit> invokeUnits = getGetIntentInvokeUnit(entryPoint);
                for (Unit invokeUnit : invokeUnits) {
                    logger.info("run data flow analyze ...");
                    analyzer.run(entryPoint,invokeUnit,invokeUnit.getDefBoxes().get(0),0,0,new ArrayList<>() );
                }

            } else {
                logger.info("this is an onBind service {}", entryPoint.getSignature());
                int parameterCount = entryPoint.getParameterCount();
                for (int index = 0; index < parameterCount; index++) {
                    logger.info("run data flow analyze ....");
                    analyzer.run(entryPoint,null,null,index,0,new ArrayList<>());
                }
            }
            HashSet<Point> analysisResultOfEntry = analyzer.getAnalysisResultOfEntry(entryPoint.getSignature());
            if(analysisResultOfEntry!=null){
                writeAnalyzeResult(analysisResultOfEntry,entryPoint.getSignature());
                flag=true;
            }
        }
        if (flag) {
            Log.write(Log.Mode.COMPONENT, component);
        }

    }

    public static void writeAnalyzeResult(HashSet<Point> analysisResult, String entryPoint) {
        if (analysisResult == null)
            return;
        for (Point p : analysisResult)
            Log.write(Log.Mode.SINK, entryPoint, p.unit, p.method, p.type,p.otherMsg);
    }

    private static boolean reachableAnalyze(SootMethod method) {

        boolean entry_usable = false;
        if (!lifeCycleMethodList.contains(method.getSubSignature())) {
            //如果是bounded Service
            entry_usable = true;
        } else {
            for (Type paramType : method.getParameterTypes()) {
                if (paramType.toString().equals("android.content.Intent") || paramType.toString().equals("android.content.ContentValues")) {
                    entry_usable = true;
                    break;
                }
            }
            if (!entry_usable) {
                if (CallGraphUtils.isMethodReachable2Target(method, "android.content.Intent getIntent()"))
                    entry_usable = true;
            }

        }
        if (entry_usable) {
            if (CallGraphUtils.isMethodReachable2Target(method, StrawPointsDefinition.getAllInsertMethodList()))//这个方法并不通用
                return true;
            logger.info("no straw point is found in the call chain");
            return false;

        } else {
            logger.info("no getIntent is called and no parameter is usable");
            return false;
        }
    }

    public static HashSet<Unit> getGetIntentInvokeUnit(SootMethod method) {
        HashSet<Unit> res = new HashSet<>();
        for (Unit unit : method.retrieveActiveBody().getUnits()) {
            if (unit instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) unit;
                if (assignStmt.containsInvokeExpr()) {
                    String subSignature = assignStmt.getInvokeExpr().getMethod().getSubSignature();
                    if (subSignature.equals(GET_INTENT)) {
                        res.add(unit);
                    }
                }
            }
        }
        return res;
    }

    public static HashSet<String> getDetectedAppInfo(String fileName){
        HashSet<String> res=new HashSet<>();
        try {
            FileReader fileReader = new FileReader(fileName);
            BufferedReader reader = new BufferedReader(fileReader);
            String line=null;
            while ((line=reader.readLine())!=null){
                if(line.contains("APP:")){
                    String[] split = line.split(":");
                    res.add(split[1].substring(1));
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return res;
    }

    public static HashMap<String,Integer> getDevicePermissionInfo(File permissionFile){
        HashMap<String,Integer> res=new HashMap<>();
        try {
            FileInputStream in = new FileInputStream(permissionFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line=null;
            String permission=null;
            String protectLevel=null;
            while ((line=reader.readLine())!=null){
                if(line.contains("permission:")){
                    permission=line.split(":")[1];
                }
                if(line.contains("protectionLevel:")){
                    protectLevel=line.split(":")[1];
                    if(protectLevel.contains("normal")) {
                        res.put(permission, 0);
                    }else if(protectLevel.contains("dangerous")){
                        res.put(permission,1);
                    }else if(protectLevel.contains("signature")) {
                        res.put(permission, 2);
                    }else {
                        res.put(permission,3);
                    }
                }
            }
            return res;
        }catch (Exception e){
            return res;
        }
    }

    public static void initFilterApp(){
        try {
            File file = new File(appFilterFile);
            FileReader reader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line=null;
            while ((line=bufferedReader.readLine())!=null){
                filterAppSet.add(line.trim());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


}
