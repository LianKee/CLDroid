package dataflow;

import cg.CallGraphUtils;
import component.EntryPointAnalyze;
import component.FragementCreater;
import component.ResolveManifest;
import constant.EntryPointsDefinition;
import constant.FileUsageDefinition;
import constant.StrawPointsDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.manifest.binary.AbstractBinaryAndroidComponent;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.util.queue.QueueReader;
import util.DirTraversal;
import util.Log;
import util.SootInit;

import java.io.*;
import java.util.*;

import static soot.SootClass.BODIES;


public class ExportedConfigFileDetector extends Thread {

    //本工具完成对应用中的数据注入点的检测
    private Logger logger = LoggerFactory.getLogger(ExportedConfigFileDetector.class);

    private final List<String> lifeCycleMethodList = EntryPointsDefinition.getAllLifeCycleMethodList();

    private static final String GET_INTENT = "android.content.Intent getIntent()";

    private String androidJar;

    private String logFileName;

    private String permissionInfoFile;

    private String systemActionFile;

    private HashMap<String, Integer> permissionInfo = new HashMap<>();

    private HashSet<String> systemActions=new HashSet<>();

    private String filterAppInfo;

    private String apkDir;

    private HashSet<String> detectedAppInfo = new HashSet<>();

    private String sinkFile;

    private String dataSavedDir;


    private HashMap<String, String> sinks = new HashMap<>();

    private JimpleBasedInterproceduralCFG icfg = null;

    private Queue<String> appWaitForProcess = new LinkedList<>();

    private HashMap<String,HashSet<String>> spDB=new HashMap<>();

    private HashSet<AbstractBinaryAndroidComponent> reachableComponents = null;


    //子线程，具体执行各种分析
    private Thread executor = null;
    //子线程开启的时间
    long star_time;
    //超时
    double timeout = 300;

    //设置超时
    public void setTimeout(double timeout) {
        this.timeout = timeout;
    }

    public static int exportComponentNumber=0;
    public static int app_id=0;

    //组件黑名单，这些组件被许多应用使用，他们从静态分析角度不能进行区分，给实验结果带来比较多的误报
    public static final String[] component_array={
            "com.onesignal.NotificationOpenedReceiver",
            "com.appsflyer.SingleInstallBroadcastReceiver",
            "com.learnium.RNDeviceInfo.RNDeviceReceiver",
            "com.adobe.phonegap.push.PushHandlerActivity",
            "com.appsflyer.MultipleInstallBroadcastReceiver",
            "host.exp.exponent.LauncherActivity",
            "com.onesignal.NotificationOpenedReceiverAndroid22AndOlder",
            "host.exp.exponent.referrer.InstallReferrerReceiver",
            "com.appbrain.ReferrerReceiver",
            "com.kochava.base.ReferralReceiver",
            "org.altbeacon.beacon.startup.StartupBroadcastReceiver",
            "host.exp.exponent.MainActivity",
            "com.kidoz.sdk.api.receivers.SdkReceiver",
            "com.blingstory.app.ui.launcher.LauncherActivity"
    };

    public static final List<String> component_blackList=Arrays.asList(component_array);

    public ExportedConfigFileDetector(String apkDir, String androidJar, String logFileName, String permissionInfoFile,String systemActionFile, String sinkInfo,String dataSavedDir) {
        this.apkDir = apkDir;
        this.androidJar = androidJar;
        this.logFileName = logFileName;
        this.permissionInfoFile = permissionInfoFile;
        this.sinkFile = sinkInfo;
        this.dataSavedDir=dataSavedDir;
        this.systemActionFile=systemActionFile;
        init();
    }

    private void init() {
        //进行一些初始化的工作
        logger.info("init the detector ...");
        //初始化设备的一些信息,用于检测应用中的暴露组件
        permissionInfo = getDevicePermissionInfo(new File(permissionInfoFile));
        //初始化系统action
        systemActions=getAndroidSystemAction(systemActionFile);
        //初始化sink信息
        initSinks();
    }


    public void cal_exportComponentNumber(){
        new DirTraversal() {

            @Override
            public void work(File f) throws Exception {
                if (!f.getName().endsWith(".apk")) {
                    return;
                }
               //我们分析统计结果
                app_id+=1;
                HashSet<AbstractBinaryAndroidComponent> reachableComponents = ResolveManifest.getReachableComponents(f.getPath(), permissionInfo, systemActions);
                if(reachableComponents!=null&&!reachableComponents.isEmpty()){
                    exportComponentNumber+=reachableComponents.size();
                }
                System.out.println("[App Id]: "+app_id);
                System.out.println("[Export Number]: "+exportComponentNumber);
            }
        }.traverse(new File(apkDir));

    }

    public void analyze() throws Exception {

        logger.info("Begin detect app ....");

        //逐个分析目录下的应用
        new DirTraversal() {

            @Override
            public void work(File f) throws Exception {
                if (!f.getName().endsWith(".apk")) {
                    logger.info("Exit:0");
                    return;
                }
                //获取此时的日志信息，判断当前应用是否被分析过
                detectedAppInfo = getDetectedAppInfo(logFileName);
                if (detectedAppInfo.contains(f.getName())) {
                    logger.info("Exit:1");
                    return;
                }

                appWaitForProcess.add(f.getPath());
                //开始分析
//                run(f.getPath());
            }
        }.traverse(new File(apkDir));
    }

    public void run() {
        while (!appWaitForProcess.isEmpty()) {
            try {
                Thread.sleep(1000);
                if (executor == null) {
                    //设置当前线程的开始时间
                    start_childThread();
                } else {
                    //判断当前线程的执行时间是否超时
                    long curTime = System.nanoTime();
                    if ((curTime - star_time) / 1E9 > timeout) {
                        //kill掉当前线程
                        logger.info("Time Out!");
                        executor.stop();
                        //重新创建子线程，开启
                        start_childThread();
                    } else if (!executor.isAlive()) {
                        start_childThread();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (executor != null) {
            while (executor.isAlive() && (System.nanoTime() - star_time) / 1E9 < timeout) {

            }
            executor.stop();
        }
    }

    private void start_childThread() {
        if (appWaitForProcess.isEmpty())
            return;
        HashSet<String> detectedAppInfo = getDetectedAppInfo(logFileName);
        String app = appWaitForProcess.poll();
        while (detectedAppInfo.contains(app) && !appWaitForProcess.isEmpty()) {
            app = appWaitForProcess.poll();
        }
        star_time = System.nanoTime();
        String finalApp = app;
        executor = new Thread(new Runnable() {
            @Override
            public void run() {
                ExportedConfigFileDetector.this.run(finalApp);
            }
        });
        executor.start();
    }

    private void handForTimeout(String info) {
        //处理超时
        try {
            String path = "./timout_info.txt";
            File file = new File(path);
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
            writer.write(info + '\n');
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run(String apkPath) {

        File file = new File(apkPath);
        HashSet<String> detectedAppInfo = getDetectedAppInfo(logFileName);
        if (detectedAppInfo.contains(apkPath)) {
            logger.info("App has been analyzed!");
            return;
        }

        logger.info("detector begin detect {}", file.getName());

        //我们首先检测应用中是否包含暴露组件,如果不包含暴露组件我们不再进行检测
        reachableComponents = ResolveManifest.getReachableComponents(apkPath, permissionInfo,systemActions);
        if(reachableComponents==null||reachableComponents.isEmpty()){
            Log.openLog(logFileName, true);
            Log.write(Log.Mode.APP, file.getName());
            String versionName = ResolveManifest.getVersionName(file);
            Log.write(Log.Mode.VERSION, versionName);
            Log.write(Log.Mode.PATH, file.getPath());
            Log.closeLog();
            return;
        }

        SootInit.initSootForAndroid(apkPath, androidJar);

        List<SootMethod> entrys = new ArrayList<>();
        //1、将应用中的所有组件的生命周期函数作为入口
        HashSet<AbstractBinaryAndroidComponent> allComponents = ResolveManifest.getAllComponents(apkPath);
        logger.info("Add all component life cycle methods as entry! ");
        assert allComponents != null;
        HashMap<String, HashSet<SootMethod>> component2MapEntryPoint =
                EntryPointAnalyze.getComponent2MapEntryPoint(allComponents);
        for (String components : component2MapEntryPoint.keySet())
            for (SootMethod m : component2MapEntryPoint.get(components))
                if (m != null)
                    entrys.add(m);
        //2.todo,我们还要或的应用中的所有Clinit方法
        logger.info("Add all <clinit> methods as entry!");
        entrys.addAll(EntryPointAnalyze.getClinitMethod());
        //3.todo,我们还需要将应用中的Fragment加入进来
        logger.info("Add all Fragment life cycle methods as entry!");
        entrys.addAll(FragementCreater.getAllFragmentInApp());
        //4、application的生命周期函数也是应该看作程序的入口
        try {
            ProcessManifest manifest = new ProcessManifest(apkPath);
            String applicationName = manifest.getApplication().getName();
            if(applicationName!=null) {
                SootClass application = Scene.v().getSootClass(applicationName);
                HashSet<SootMethod> applicationLifeCycleMethod = EntryPointAnalyze.getApplicationLifeCycleMethod(application);
                entrys.addAll(applicationLifeCycleMethod);
                logger.info("Add application as entry!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.info("There are {} entry points", entrys.size());
        //使用CHA算法进行构建调用图
        logger.info("Building CG ......");
        CallGraphUtils.buildCGbyCHA(entrys);
        logger.info("Call Graph Size: {}", Scene.v().getCallGraph().size());


        //我们判断应用中是否存在我们设置的sink点，如果没有我们设置的sink点我们直接返回
        int counter_sink = computeSinkInApp();
        if (counter_sink == 0) {
            logger.info("No sink is found in App!");
            Log.openLog(logFileName, true);
            Log.write(Log.Mode.APP, file.getName());
            String versionName = ResolveManifest.getVersionName(file);
            Log.write(Log.Mode.VERSION, versionName);
            Log.write(Log.Mode.PATH, file.getPath());
            Log.closeLog();
            return;
        }

        logger.info("There are {} sinks which is found in App!", counter_sink);

        //我们创建过程间调用图，我们基于此进行过程间数据流分析
        logger.info("Building ICFG ......");
        icfg = new JimpleBasedInterproceduralCFG();


        //规则检查器,需要配置的是icfg和mainifest
        try {
            ProcessManifest processManifest = new ProcessManifest(apkPath);
            //暴露文件注入的checker
            ExportedFileChecker checker = new ExportedFileChecker(processManifest, icfg);
            //数据流分析引擎
            DataFlowEngine detector = new DataFlowEngine(icfg);
            buildSpDB();
            //checer对应的sp数据库
            checker.setSpDB(spDB);
            //将规则检查器装入到数据流分析引擎中
            detector.setChecker(checker);
            //当检测暴露文件时，我们将不分析隐式流，因为这可能带来比较多的误报
            detector.setImplicitMode(false);

            //以所有暴露组件的生命周期函数作为攻击入口
            HashMap<String, HashSet<SootMethod>> mapEntryPoint = EntryPointAnalyze.getComponent2MapEntryPoint(reachableComponents);
            for (String component : mapEntryPoint.keySet()) {
                //这里需要对组件进行分析，看是不是在黑名单中，如果在的话就不分析
                if(component_blackList.contains(component))
                    continue;
                HashSet<SootMethod> methods = mapEntryPoint.get(component);
                logger.info("Detecting component: {}, there are {} life cycle methods which may be exploited!", component, methods.size());
                for (SootMethod entryPoint : methods) {
                    if (entryPoint == null)
                        continue;
                    checker.setComponent(component);
                    checker.setEntryPoint(entryPoint);
                    //分析应用中的数据注入操作
                    doAnalyze(entryPoint, detector);
                    //获取分析结果
                    HashMap<SootMethod, HashSet<Point>> res = checker.getRes();
                    if (res.containsKey(entryPoint)) {
                        //如果发现了数据注入行为，记录下数据注入的情况
                        for (Point sinkInfo : res.get(entryPoint)) {
                            recordInsertInfo(apkPath, component, entryPoint.getSignature(), sinkInfo.unit, sinkInfo.method, sinkInfo.type, sinkInfo.otherMsg);
                        }
                    }
                }
            }

            logger.info("Insert analyze has been finished!");

            //数据使用情况分析

            HashSet<String> fileInfos = checker.getFileInfos();
            HashSet<SpInfo> spInfos = checker.getSpInfos();
            HashSet<DBInfo> dbInfos = checker.getDbInfos();

            //如果没有数据可以注入，我们直接返回
            if (dbInfos.isEmpty()&&fileInfos.isEmpty()&&spInfos.isEmpty()) {
                logger.info("No can be inserted some important file!");
                Log.openLog(logFileName, true);
                Log.write(Log.Mode.APP, file.getName());
                String versionName = ResolveManifest.getVersionName(file);
                Log.write(Log.Mode.VERSION, versionName);
                Log.write(Log.Mode.PATH, file.getPath());
                Log.closeLog();
                return;
            }

            //文件信息恢复器
            FileLoaderDetector fileLoaderDetector = new FileLoaderDetector(icfg);
            //用于记录用用中检测到的数据加载点到敏感行为的映射
            HashMap<Unit, HashSet<String>> loader2SensitiveSite = new HashMap<>();
            //用于记录数据加载点对应的元数据信息
            HashMap<Unit,String> loader2MetaData=new HashMap<>();

            //初始化敏感行为检测器
            SensitiveActionChecker sensitiveActionChecker = new SensitiveActionChecker(sinks);
            //初始化新的数据流分析引擎
            DataFlowEngine sensitiveDataUsageDetector = new DataFlowEngine(icfg);
            //装配checker
            sensitiveDataUsageDetector.setChecker(sensitiveActionChecker);
            //对于敏感行为的检测，我们需要关注控制流依赖相关的问题
            sensitiveDataUsageDetector.setImplicitMode(true);
            //我们设置分析的超时时间,我们默认5分钟
            sensitiveDataUsageDetector.setTimeOut(300);


            if (!dbInfos.isEmpty()) {
                //数据库信息映射到他们的调用点
                HashMap<FileLoaderDetector.DataBaseInfo, HashSet<Unit>> dataBaseMap2LoaderSite = new HashMap<>();
                logger.info("there are {} DataBase data item which can be inserted!", dbInfos.size());
                //我们找到所有的数据库使用的位置,并提取他们的数据库信息
                QueueReader<MethodOrMethodContext> listener = Scene.v().getReachableMethods().listener();
                while (listener.hasNext()) {
                    SootMethod method = listener.next().method();
                    //查找数据库的使用方法
                    if (FileUsageDefinition.getDataBaseQueryAPIList().contains(method.getSubSignature()) ||
                            FileUsageDefinition.getContentResolverQueryAPIList().contains(method.getSubSignature())) {
                        Collection<Unit> callersOf = icfg.getCallersOf(method);
                        for (Unit callSite : callersOf) {
                            //获取数据库信息
                            FileLoaderDetector.DataBaseInfo dataBaseInfo = fileLoaderDetector.getDataBaseInfo(callSite, icfg.getMethodOf(callSite));
                            if (dataBaseInfo == null)
                                continue;
                            if (!dataBaseMap2LoaderSite.containsKey(dataBaseInfo))
                                dataBaseMap2LoaderSite.put(dataBaseInfo, new HashSet<>());
                            //我们将此处使用的数据库信息和相关的语句进行映射
                            dataBaseMap2LoaderSite.get(dataBaseInfo).add(callSite);
                        }
                    }
                }
                logger.info("detected {} dataBases is used in App", dataBaseMap2LoaderSite.size());

                //todo,我们还要进行注入数据库和使用数据库的信息比较，以确定加载点的数据库是我们注入的数据库
                //todo,我们这里应该做两件事，第一件事是确认可以注入的数据库被加载了，第二件事是确认可以影响的数据的数据被使用了
                for (FileLoaderDetector.DataBaseInfo loaderDataBaseInfo : dataBaseMap2LoaderSite.keySet()) {
                    //首先考虑数据库加载，我们只需要关心我们注入的一个数据库被加载了就可，不需要关注它的加载地点
                    for(DBInfo insertDataBaseInfo:dbInfos){
                        //需要判断这个注入的数据库是不是可以改变它的大小
                        if(!insertDataBaseInfo.insert||!isSameDataBase(insertDataBaseInfo,loaderDataBaseInfo))
                            continue;
                        //如果满足上述的条件，我们就认为这是一个危险的数据库
                        for(Unit dataBaseLoaderSite:dataBaseMap2LoaderSite.get(loaderDataBaseInfo))
                            recordFileLoadInfo(apkPath,insertDataBaseInfo.toString(),dataBaseLoaderSite,icfg.getMethodOf(dataBaseLoaderSite),"DATA_BASE");
                    }
                    //接着我们需要看影响的数据库是不是可以想象一些敏感操作，即此处的加载点是不是可以由我们呢控制
                    boolean checked=false;
                    for (DBInfo insertDataBaseInfo : dbInfos) {
                        if (checked||!isSameDataBase(insertDataBaseInfo, loaderDataBaseInfo))
                            continue;
                        checked=true;
                        //我们需要对此进行数据流分析
                        for (Unit dataBaseLoaderSite : dataBaseMap2LoaderSite.get(loaderDataBaseInfo)) {
                            //进行数据流分析
                            //我们要将数据加载信息记录下来，记录的是注入点的信息
                            sensitiveDataUsageDetector.runFlowAnalysis(icfg.getMethodOf(dataBaseLoaderSite), dataBaseLoaderSite, "");
                            HashSet<String> sinkMethodTrue = sensitiveActionChecker.getSinkMethodTrue();
                            if (!sinkMethodTrue.isEmpty()) {
                                if (!loader2SensitiveSite.containsKey(dataBaseLoaderSite))
                                    loader2SensitiveSite.put(dataBaseLoaderSite, new HashSet<>());
                                loader2SensitiveSite.get(dataBaseLoaderSite).addAll(sinkMethodTrue);
                                loader2MetaData.put(dataBaseLoaderSite,insertDataBaseInfo.toString());
                                sensitiveActionChecker.clearSinkMethodTrue();
                            }
                        }

                    }
                }
            }

            if (!spInfos.isEmpty()) {
                //preferences信息映射到他们的加载点
                HashMap<FileLoaderDetector.SharedPreferencesInfo, HashSet<Unit>> preferencesMap2LoaderSite = new HashMap<>();
                logger.info("there are {} sharedPreferences data item which can be inserted!", spInfos.size());
                //寻找应用中的所有sharedPreferences的使用情况
                QueueReader<MethodOrMethodContext> listener = Scene.v().getReachableMethods().listener();
                while (listener.hasNext()) {
                    SootMethod method = listener.next().method();
                    //查找preference的使用方法,首先我们可以根据signature进行判定，我们同时还发现很多使用其他signature的地方
                    if (FileUsageDefinition.getSharedPreferencesReadAPIList().contains(method.getSignature())) {
                        Collection<Unit> callersOf = icfg.getCallersOf(method);
                        for (Unit callSite : callersOf) {
                            //获取preferences的信息
                            FileLoaderDetector.SharedPreferencesInfo sharedPreferencesInfo = fileLoaderDetector.getSharedPreferencesInfo(callSite, icfg.getMethodOf(callSite));
                            if (sharedPreferencesInfo == null)
                                continue;
                            if (!preferencesMap2LoaderSite.containsKey(sharedPreferencesInfo))
                                preferencesMap2LoaderSite.put(sharedPreferencesInfo, new HashSet<>());
                            //我们将此处使用的preferences信息和相关的语句进行映射
                            preferencesMap2LoaderSite.get(sharedPreferencesInfo).add(callSite);
                        }
                    }
                }
                //我们需要对检查到的preferences的数据注入点信息进行分析，对于我们可以想象的key值的文件，我们需要获取他们在应用中的使用情况
                logger.info("detected {} preferences is used in App", preferencesMap2LoaderSite.size());

                //todo,我们这里关注的是也包括两件事，第一件我们需要判断我们可以改变sp大小的文件是否可以被加载，第二件事我们需要判断可以修改的sp是否可以影响敏感操作
                for (FileLoaderDetector.SharedPreferencesInfo loadPreferencesInfo : preferencesMap2LoaderSite.keySet()) {
                    //首先，我们需要判断可以改变大小的sp是不是会被加载
                    for(SpInfo insertInfo:spInfos){
                        for(Unit loadPreferencesSite:preferencesMap2LoaderSite.get(loadPreferencesInfo)){
                            boolean flag = loadPreferencesSite.toString().contains("getAll");
                            //需要注入点的信息满足可以影响key,并且它俩是一个文件
                            if(!insertInfo.affectKey||!isSameSharedPreferences(insertInfo,loadPreferencesInfo,flag))
                                continue;
                            recordFileLoadInfo(apkPath,insertInfo.toString(),loadPreferencesSite,icfg.getMethodOf(loadPreferencesSite),"SHARED_PREFERENCES");
                        }
                    }
                    //接着我们应该判断某处的加载点恕不是可以被我们控制
                    boolean checked=false;
                    for(SpInfo insertInfo:spInfos){
                        //这一步判断加载的信息是不是可以由我们控制
                        if(checked)
                            continue;
                        for(Unit loadPreferencesSite:preferencesMap2LoaderSite.get(loadPreferencesInfo)){
                            boolean flag = loadPreferencesSite.toString().contains("getAll");
                            if(!isSamePreferences(insertInfo,loadPreferencesInfo,flag))
                                continue;
                            checked=true;

                            sensitiveDataUsageDetector.runFlowAnalysis(icfg.getMethodOf(loadPreferencesSite), loadPreferencesSite, "");
                            HashSet<String> sinkMethodTrue = sensitiveActionChecker.getSinkMethodTrue();
                            if (!sinkMethodTrue.isEmpty()) {
                                if (!loader2SensitiveSite.containsKey(loadPreferencesSite))
                                    loader2SensitiveSite.put(loadPreferencesSite, new HashSet<>());
                                loader2SensitiveSite.get(loadPreferencesSite).addAll(sinkMethodTrue);
                                loader2MetaData.put(loadPreferencesSite,insertInfo.toString());
                                sensitiveActionChecker.clearSinkMethodTrue();
                            }
                        }
                    }
                }
            }
            if(fileInfos.size()!=0){
                for(String file_input:FileUsageDefinition.getInputStreamConstructorList()){
                    SootMethod method = Scene.v().getMethod(file_input);
                    Collection<Unit> callers = icfg.getCallersOf(method);
                    for(Unit callSite:callers){
                        //我们分析加载点
                        SootMethod methodOf = icfg.getMethodOf(callSite);
                        HashSet<String> loadFileInfos = fileLoaderDetector.getStandardFileInfo(callSite, methodOf);
                        //需要判断文件加载是不是我们注入的文件
                        for(String loadFileName:loadFileInfos){
                            if(fileInfos.contains(loadFileName)){
                                //如果两个文件名相同，我们就认为文件被加载了,我们需要把加载的文件名记录下来
                                recordFileLoadInfo(apkPath,"FILE_NAME: "+loadFileName,callSite,methodOf,"FILE");
                                //我们需要进行污点分析，这是一个流的初始化语句
                                InvokeStmt invokeStmt = (InvokeStmt) callSite;
                                InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                                InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
                                ValueBox baseBox = instanceInvokeExpr.getBaseBox();
                                baseBox.addTag(new AccessPathTag());
                                sensitiveDataUsageDetector.forwardDataFlow(methodOf,null,callSite,baseBox,-1,0);
                                HashSet<String> sinkMethodTrue = sensitiveActionChecker.getSinkMethodTrue();
                                if (!sinkMethodTrue.isEmpty()) {
                                    if (!loader2SensitiveSite.containsKey(callSite))
                                        loader2SensitiveSite.put(callSite, new HashSet<>());
                                    loader2SensitiveSite.get(callSite).addAll(sinkMethodTrue);
                                    loader2MetaData.put(callSite,"FILE_NAME: "+loadFileName);
                                    sensitiveActionChecker.clearSinkMethodTrue();
                                }

                            }
                        }
                    }

                }


            }

            //将统计的敏感行为信息记录下来
            logger.info("Record analyze result ...");
            recordAnalyze(apkPath, loader2SensitiveSite,loader2MetaData);
            //将统计的元数据信息统计下来
            logger.info("Record meta data ...");
            recordMetaData(apkPath, dbInfos, spInfos,fileInfos);
            //将分析成功的APP信息记录下来
            Log.openLog(logFileName, true);
            Log.write(Log.Mode.APP, file.getName());
            String versionName = ResolveManifest.getVersionName(file);
            Log.write(Log.Mode.VERSION, versionName);
            Log.write(Log.Mode.PATH, file.getPath());
            Log.closeLog();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void doAnalyze(SootMethod entryPoint, DataFlowEngine analyzer) {
        logger.info("Begin detecting {}", entryPoint.getSignature());
        if (lifeCycleMethodList.contains(entryPoint.getSubSignature())) {
            int index = 0;
            List<Type> parameterTypes = entryPoint.getParameterTypes();
            for (; index < parameterTypes.size(); index++) {
                if (parameterTypes.get(index).toString().equals("android.content.Intent") || parameterTypes.get(index).toString().equals("android.content.ContentValues"))
                    break;
            }
            if (index != parameterTypes.size()) {
                logger.info("run data flow analyze ....");
                //我们要找到对应的实参赋值的那条语句
                Unit argumentsAssignment = getArgumentsAssignment(entryPoint, index);
                if (argumentsAssignment == null) {
                    logger.info("Can't find argument assign unit!");
                } else {
                    logger.info("检测到参数赋值语句为： {}", argumentsAssignment);
                    analyzer.runFlowAnalysis(entryPoint, argumentsAssignment, "");
                }
            }
            //分析代码中是否包含getIntent调用,我们只分析一层代码调用
            HashSet<Unit> invokeUnits = getGetIntentInvokeUnit(entryPoint);
            for (Unit invokeUnit : invokeUnits) {
                logger.info("run data flow analyze ...");
                analyzer.runFlowAnalysis(entryPoint, invokeUnit, "");

            }

        } else {
            //我们认为onBind对应的实际接口中的所有参数都是可以控制的
            logger.info("this is an onBind service {}", entryPoint.getSignature());
            int parameterCount = entryPoint.getParameterCount();
            for (int index = 0; index < parameterCount; index++) {
                logger.info("run data flow analyze ....");
                Unit argumentsAssignment = getArgumentsAssignment(entryPoint, index);
                if (argumentsAssignment == null) {
                    logger.info("Can't find argument assign unit!");
                } else {
                    analyzer.runFlowAnalysis(entryPoint, argumentsAssignment, "");
                }
            }
        }


    }

    private int computeSinkInApp() {
        //判断应用中存在多少sink点，sink点就是敏感API的调用
        int counter = 0;
        //我们需要的是signature
        ReachableMethods reachableMethods = Scene.v().getReachableMethods();
        for (String sig : sinks.keySet()) {
            try {
                SootMethod method = Scene.v().getMethod(sig);
                if (reachableMethods.contains(method))
                    counter++;

            } catch (Exception e) {}
        }
        return counter;
    }


    private boolean isSameDataBase(FileLoaderDetector.DataBaseInfo insertDataBaseInfo, FileLoaderDetector.DataBaseInfo loaderDataBaseInfo) {
        //需要补充数据库注入点与数据库加载点的数据库信息是否相同的逻辑规则
        //判断注入点与加载点的是否为同一个数据库
        //如何判断呢？
        //如果数据加载点是使用contentResolver的方式，这个时候必须匹配的是uri
//        logger.info("[Insert]: {}",insertDataBaseInfo);
//        logger.info("[Loader]: {}",loaderDataBaseInfo);
        //数据注入点的uri
        HashSet<String> standardUri_insert = insertDataBaseInfo.getStandardUri();
        //数据注入点的table
        HashSet<String> standardTableName_insert = insertDataBaseInfo.getStandardTableName();
        //数据注入点的dataBase
        HashSet<String> standardDataBaseName_insert = insertDataBaseInfo.getStandardDataBaseName();

        //数据加载点的uri
        HashSet<String> standardUri_loader = loaderDataBaseInfo.getStandardUri();
        //数据加载点的table
        HashSet<String> standardTableName_loader = loaderDataBaseInfo.getStandardTableName();
        //数据加载点的dataBase
        HashSet<String> standardDataBaseName_loader = loaderDataBaseInfo.getStandardDataBaseName();

        //首先判断数据加载点是不是是通过ContentResolver的方式进行数据库查询的
        //判断依据是uri是否为空
        if (standardUri_loader != null) {
            if (standardUri_insert == null)
                return false;
            if (isSameUri(standardUri_insert, standardUri_loader))
                return true;
            return false;
        } else {
            //如果是直接使用数据库查询的方式进行查询的
            //我们需要需要比较数据库名和表明
            if (standardDataBaseName_insert == null || standardDataBaseName_loader == null) {
                //如果两者的数据库有一方的数据库名没有办法恢复成功
                //如果特们的表名相同，我们就认为他们是同一个文件
                if (standardTableName_insert == null || standardTableName_loader == null)
                    return false;
                if (isIntersection(standardTableName_insert, standardTableName_loader))
                    return true;
                return false;
            } else {
                //比较他们的数据库名
                if (isIntersection(standardDataBaseName_insert, standardDataBaseName_loader)) {
                    if (standardTableName_insert == null || standardTableName_loader == null)
                        return false;
                    if (isIntersection(standardTableName_insert, standardTableName_loader))
                        return true;
                    return false;
                } else {
                    return false;
                }
            }

        }
    }

    private boolean isSameDataBase(DBInfo insertInfo, FileLoaderDetector.DataBaseInfo loaderDataBaseInfo){
        //判断是不是相同的数据库
        HashSet<String> standardUri_loader = loaderDataBaseInfo.getStandardUri();
        //数据加载点的table
        HashSet<String> standardTableName_loader = loaderDataBaseInfo.getStandardTableName();
        //数据加载点的dataBase
        HashSet<String> standardDataBaseName_loader = loaderDataBaseInfo.getStandardDataBaseName();

        //如果他们的uri相同，那么是相同的数据库
        if(insertInfo.uri!=""&&!insertInfo.uri.contains("null")&!standardUri_loader.isEmpty()&&standardUri_loader.contains(insertInfo.uri))
            return true;
        //如果他们的表名相同，那么他们是相同的数据库
        if(insertInfo.tableName!=""&&!insertInfo.tableName.contains("null")&&!standardTableName_loader.isEmpty()) {
            //如果可以比较他们的表名
            if(standardTableName_loader.contains(insertInfo.tableName))
                return true;
            return false;
        }
        //如果他们的数据库名相同，我们认为也可能是相同的数据库
        if(insertInfo.dbName!=""&&!insertInfo.dbName.contains("null")&&!standardDataBaseName_loader.isEmpty()&&standardDataBaseName_loader.contains(insertInfo.dbName))
            return true;
        return false;

    }

    private boolean isSameUri(HashSet<String> insert, HashSet<String> loader) {
        //我们需要比较数据注入处的uri和数据加载点的uri是否相同
        //我们认为数据注入点的uri就是Provider对应的authority
        //因此只需要加载点的uri的authority匹配即可
        for (String a : insert)
            for (String b : loader)
                if (b.contains(a))
                    return true;
        return false;
    }

    private boolean isSamePreferences(FileLoaderDetector.SharedPreferencesInfo insertPreferenceInfo, FileLoaderDetector.SharedPreferencesInfo loaderPreferencesInfo, boolean isGetAll) {
        //需要补充Preference注入点与加载点是否相同的逻辑规则
        //todo,这里的规则应该更加清晰一点
        //注入点Preferences信息
        HashSet<String> standardPreferencesName_insert = insertPreferenceInfo.getStandardPreferencesName();
        //注入点的key值信息
        HashSet<String> standardKey_insert = insertPreferenceInfo.getStandardKey();
        //加载点Preferences信息
        HashSet<String> standardPreferencesName_loader = loaderPreferencesInfo.getStandardPreferencesName();
        //加载点的key值信息
        HashSet<String> standardKey_loader = loaderPreferencesInfo.getStandardKey();

        if (standardPreferencesName_insert.isEmpty() || standardPreferencesName_loader.isEmpty()) {
            //如果两者之间他们有一个的Preferences的名字是恢复不成功的
            //那么如果他们的key值相同，我们认为就是同一个
            if (standardKey_insert.isEmpty() || standardKey_loader.isEmpty())
                return false;
            return isIntersection(standardKey_insert, standardKey_loader);
        } else {
            //否则需要比较他们的preferencesName,如果名字不相同，肯定不是一噶文件
            if (isIntersection(standardPreferencesName_insert, standardPreferencesName_loader)) {
                //如果名字相同再比较key值
                if (insertPreferenceInfo.affectKey)
                    return true;
                if (isGetAll)
                    return true;
                if (standardKey_insert.isEmpty() || standardKey_loader.isEmpty())
                    return false;
                return isIntersection(standardKey_insert, standardKey_loader);

            } else {
                return false;
            }
        }
    }

    boolean isSamePreferences(SpInfo insertInfo, FileLoaderDetector.SharedPreferencesInfo loadSharedPreferencesInfo,boolean isGetAll){
        //判断注入的数据是不是被使用了
        HashSet<String> loadSpNames = loadSharedPreferencesInfo.getStandardPreferencesName();
        HashSet<String> standardKey = loadSharedPreferencesInfo.getStandardKey();

        //如果无法比较他们的文件名
        if(!insertInfo.affectKey&&(Objects.equals(insertInfo.spName, "") ||loadSpNames.isEmpty())){
            //我们需要比较他们的key是不是相同的
            if(insertInfo.keyName==""||standardKey.isEmpty())
                return false;
            return standardKey.contains(insertInfo.keyName);
        }else {
            //我们需要比较他们是不是相同的文件
            if(loadSpNames.contains(insertInfo.spName)||insertInfo.affect_sp_name){
                //我们接着需要比较他们的是不是相同的key
                //如果注入时可以影响key,那么这个值是被影响的
                if(insertInfo.affectKey)
                    return true;
                //如果不能影响key,但是获取的是所有元素，那么是污染的
                if(isGetAll)
                    return true;
                //需要判断注入的key是否相同
                if(Objects.equals(insertInfo.keyName, "") ||standardKey.isEmpty())
                    return false;
                return standardKey.contains(insertInfo.keyName);

            }
            return false;

        }
    }

    private boolean isSameSharedPreferences(SpInfo insertInfo, FileLoaderDetector.SharedPreferencesInfo loadSharedPreferences,boolean isGetAll){
        //判断两个文件是不一个文件
        HashSet<String> standardPreferencesName = loadSharedPreferences.getStandardPreferencesName();
        HashSet<String> standardKey = loadSharedPreferences.getStandardKey();

        //需要比较文件名，key值
        //如果无法比较文件名
        if(insertInfo.spName==""||standardPreferencesName.isEmpty()){
            //判断key是不是相同的
            if(insertInfo.keyName==""||standardKey.isEmpty())
                return false;
            return standardKey.contains(insertInfo.keyName);

        }else {
            //比较两个文件名是否相同
            if(standardPreferencesName.contains(insertInfo.spName)){
                //如果可以影响key
                if(insertInfo.affectKey)
                    return true;
                if(isGetAll)
                    return true;
                //如果不可以影响key,需要比较名字
                if(insertInfo.keyName==""||standardKey.isEmpty())
                    return false;
                //我们需要判断key是不是相同的
                return standardKey.contains(insertInfo.keyName);

            }
            return false;

        }
    }

    private boolean isIntersection(HashSet<String> a, HashSet<String> b) {
        //判断两个字符串集合是否含有交集
        for (String s : a) {
            if (b.contains(s))
                return true;
        }
        return false;
    }


    //获取方法中的getIntent调用
    public static HashSet<Unit> getGetIntentInvokeUnit(SootMethod method) {

        HashSet<Unit> res = new HashSet<>();
        if (method.getDeclaringClass().resolvingLevel() != BODIES)
            return res;
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

    //找到实参赋值的那条语句
    private Unit getArgumentsAssignment(SootMethod method, int paramIndex) {
        if (method.getDeclaringClass().resolvingLevel() != BODIES)
            return null;
        for (Unit unit : method.retrieveActiveBody().getUnits()) {
            if (unit instanceof IdentityStmt)
                if (unit.toString().contains("@parameter" + paramIndex + ":"))
                    return unit;
        }
        return null;
    }

    private void recordMetaData(String appName, HashSet<DBInfo> dataBaseInfos,
                                HashSet<SpInfo> sharedPreferencesInfos, HashSet<String> fileInfos) {
        //记录应用中的元数据信息、
        try {
            String dirPath = dataSavedDir+File.separator+"meta_data";
            File dir = new File(dirPath);
            if(!dir.exists())
                dir.mkdirs();
            File temp = new File(appName);
            String path = dirPath + File.separator + temp.getName().replace("apk", "txt");
            File file = new File(path);
            FileWriter writer = new FileWriter(file, true);
            BufferedWriter bufferedWriter = new BufferedWriter(writer);
            bufferedWriter.write("=================================================================================\n");
            bufferedWriter.write("App Name: " + appName + "\n");

            bufferedWriter.write("********************************************DataBase Info*****************************************\n");
            int counter = 0;
            for (DBInfo dataBaseInfo : dataBaseInfos) {
                bufferedWriter.write("-------------------------DataBase " + counter + "----------------------------------\n");
                bufferedWriter.write(dataBaseInfo.toString()+'\n');
                counter++;
            }
            counter = 0;
            bufferedWriter.write("*********************************************Preferences Info******************************************\n");
            for (SpInfo sharedPreferencesInfo : sharedPreferencesInfos) {
                bufferedWriter.write("-------------------------SharedPreferences " + counter + "----------------------------------\n");
                bufferedWriter.write(sharedPreferencesInfo.toString()+'\n');
                counter++;
            }
            counter = 0;
            bufferedWriter.write("*********************************************File Info*********************************************\n");
            for (String fieldInfo : fileInfos) {
                bufferedWriter.write("-------------------------File " + counter + "----------------------------------\n");
                bufferedWriter.write(fieldInfo + "\n");
                counter++;
            }
            bufferedWriter.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void recordAnalyze(String apkName, HashMap<Unit, HashSet<String>> res,HashMap<Unit,String> loader2Metadata) {
        try {
            String dirPath = dataSavedDir+File.separator+"data_use_info";
            File dir = new File(dirPath);
            if(!dir.exists())
                dir.mkdirs();
            File temp = new File(apkName);
            String path = dirPath + File.separator + temp.getName().replace("apk", "txt");
            File file = new File(path);
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
            writer.write("[App]:" + apkName + "\n");
            for (Unit loaderSite : res.keySet()) {
                writer.write("=============================================================================\n");
                writer.write("[Loader Site]: " + loaderSite + '\n');
                writer.write("[LOAD_FILE_INFO]: \n"+loader2Metadata.get(loaderSite)+'\n');
                SootMethod method = icfg.getMethodOf(loaderSite);
                writer.write("[Load Method]: " + method.getSignature() + '\n');
                int counter = 0;
                for (String info : res.get(loaderSite)) {
                    writer.write("--------------------------------sink method " + counter + "---------------------------------" + '\n');
                    writer.write(info + '\n');
                    counter++;
                }
            }

            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void recordInsertInfo(String appName, String componentName, String entrypoint, String unit, String sinkMethod, String type, String otherMsg) {
        try {
            String dirPath = dataSavedDir+File.separator+"data_insert_info";
            File dir = new File(dirPath);
            if(!dir.exists())
                dir.mkdirs();
            File temp = new File(appName);
            String path = dirPath + File.separator + temp.getName().replace("apk", "txt");
            File file = new File(path);
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
            //我们将应用的注入信息记录下来
            writer.write("***********************************<Component>*****************************\n");
            writer.write("COMPONENT:" + componentName + '\n');
            writer.write("ENTRY_POINT: " + entrypoint + '\n');
            writer.write("UNIT:" + unit + '\n');
            writer.write("METHOD:" + sinkMethod + '\n');
            writer.write("TYPE:" + type + '\n');
            if (!otherMsg.equals(""))
                writer.write(otherMsg + '\n');
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void recordFileLoadInfo(String appName,String loadFileInfo,Unit loadSite,SootMethod method,String type){
        //记录文件加载信息，包括加载文件的信息，以及加载位置
        try {
            String dirPath = dataSavedDir+File.separator+"file_load_info";
            File dir = new File(dirPath);
            if(!dir.exists())
                dir.mkdirs();
            File temp = new File(appName);
            String path = dirPath + File.separator + temp.getName().replace("apk", "txt");
            File file = new File(path);
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
            //我们将应用的注入信息记录下来
            writer.write("***********************************<Load File Info>*****************************\n");
            writer.write("FILE_INSERT_INFO: \n");
            writer.write("FILE_TYPE: "+type+'\n');
            writer.write(loadFileInfo+'\n');
            writer.write("LOAD_SITE: "+loadSite.toString()+'\n');
            writer.write("LOAD_METHOD: "+method.getSignature()+'\n');
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }



    }


    //获取日志文件中已经分析过的App
    public static HashSet<String> getDetectedAppInfo(String fileName) {
        HashSet<String> res = new HashSet<>();
        try {
            FileReader fileReader = new FileReader(fileName);
            BufferedReader reader = new BufferedReader(fileReader);
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.contains("APP:")) {
                    String[] split = line.split(":");
                    res.add(split[1].substring(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
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

    public static HashSet<String> getAndroidSystemAction(String systemActionFile){
        HashSet<String> systemAction=new HashSet<>();
        try{
            File file = new File(systemActionFile);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            String line=null;
            while ((line=bufferedReader.readLine())!=null){
                String action=line.trim();
                systemAction.add(action);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return systemAction;
    }


    private void initSinks() {
        //初始化sink信息，包括数据流依赖和数据流依赖的信息
        logger.info("Init Sinks Info ....");
        try {
            File file = new File(this.sinkFile);
            FileReader reader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                String s = line.trim();
                String[] split = s.split(">");
                sinks.put(split[0] + ">", split[1]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("Sinks size: {}",sinks.size());

    }

    public void buildSpDB(){
        //建立全程序SharedPreferences文件的数据库，建立文件名和内容的映射
        FileLoaderDetector fileLoaderDetector = new FileLoaderDetector(icfg);

        List<String> spAPIList=new ArrayList<>();
        spAPIList.addAll(StrawPointsDefinition.getSharedPreferencesWriteMethodList());
        spAPIList.addAll(FileUsageDefinition.getSharedPreferencesReadAPIList());
        logger.info("Building shared preferences database ....");
        //分析所有的读API
        for(String sig:spAPIList){
            SootMethod method = Scene.v().getMethod(sig);
            for(Unit callSite:icfg.getCallersOf(method)){
                //找到所有的数据加载点
                SootMethod methodOf = icfg.getMethodOf(callSite);
                //恢复他的内容
                FileLoaderDetector.SharedPreferencesInfo sharedPreferencesInfo = fileLoaderDetector.getSharedPreferencesInfo(callSite, methodOf);
                //将分析的数据放在数据库中
                HashSet<String> standardPreferencesName = sharedPreferencesInfo.getStandardPreferencesName();
                if(standardPreferencesName.isEmpty())
                    continue;
                HashSet<String> standardKey = sharedPreferencesInfo.getStandardKey();
                if(standardKey.isEmpty())
                    continue;
                for(String spName:standardPreferencesName){
                    if(!spDB.containsKey(spName))
                        spDB.put(spName,new HashSet<>());
                    spDB.get(spName).addAll(standardKey);
                }
            }
        }
        logger.info("Build shared preferences database,Done!");
    }

}
