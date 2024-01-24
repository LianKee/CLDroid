package analyze;

import cg.CallGraphUtils;
import component.EntryPointAnalyze;
import component.ResolveManifest;
import constant.ApkAndJavaConstants;
import constant.FileUsageDefinition;
import dataflow.Analyze;
import dataflow.CallSite;
import dataflow.DatabaseUseDetector;
import dataflow.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.manifest.binary.AbstractBinaryAndroidComponent;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.callgraph.TransitiveTargets;
import soot.util.queue.QueueReader;
import util.DirTraversal;
import util.Log;
import util.SootInit;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class AnalyzeDatabase {

    public static final Logger logger = LoggerFactory.getLogger(AnalyzeDatabase.class);


    //直接使用数据库提供的API进行查询的
    public static final String DATA_BASE_QUERY_0 = "android.database.Cursor query(java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String)";
    public static final String DATA_BASE_QUERY_1 = "android.database.Cursor query(java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String,java.lang.String)";
    public static final String DATA_BASE_QUERY_2 = "android.database.Cursor query(boolean,java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String,java.lang.String)";
    public static final String DATA_BASE_QUERY_3 = "android.database.Cursor query(boolean,java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String,java.lang.String,android.os.CancellationSignal)";
    public static final String DATA_BASE_QUERY_4 = "android.database.Cursor rawQuery(java.lang.String,java.lang.String[])";
    public static final String DATA_BASE_QUERY_5 = "android.database.Cursor rawQuery(java.lang.String,java.lang.String[],android.os.CancellationSignal)";
    public static final String DATA_BASE_QUERY_6 = "android.database.Cursor rawQueryWithFactory(android.database.sqlite.SQLiteDatabase$CursorFactory,java.lang.String,java.lang.String[],java.lang.String)";
    public static final String DATA_BASE_QUERY_7 = "android.database.Cursor rawQueryWithFactory(android.database.sqlite.SQLiteDatabase$CursorFactory,java.lang.String,java.lang.String[],java.lang.String,android.os.CancellationSignal)";
    public static final String DATA_BASE_QUERY_8 = "android.database.Cursor queryWithFactory(android.database.sqlite.SQLiteDatabase$CursorFactory,boolean,java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String,java.lang.String)";
    public static final String DATA_BASE_QUERY_9 = "android.database.Cursor queryWithFactory(android.database.sqlite.SQLiteDatabase$CursorFactory,boolean,java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String,java.lang.String,android.os.CancellationSignal)";
    public static final String DATA_BASE_QUERY_10 = "void execSQL(java.lang.String)";
    public static final String DATA_BASE_QUERY_11 = "void execSQL(java.lang.String,java.lang.Object[])";

    //使用ContentResolver提供的API进行查询的
    public static final String CONTENT_RESOLVER_QUERY_0 = "android.database.Cursor query(android.net.Uri,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String)";
    public static final String CONTENT_RESOLVER_QUERY_1 = "android.database.Cursor query(android.net.Uri,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,android.os.CancellationSignal)";
    public static final String CONTENT_RESOLVER_QUERY_2 = "android.database.Cursor query(android.net.Uri,java.lang.String[],android.os.Bundle,android.os.CancellationSignal)";

    public static final HashSet<String> QUERY_API_SET = new HashSet<>();

    static {
        QUERY_API_SET.add(DATA_BASE_QUERY_0);
        QUERY_API_SET.add(DATA_BASE_QUERY_1);
        QUERY_API_SET.add(DATA_BASE_QUERY_2);
        QUERY_API_SET.add(DATA_BASE_QUERY_3);
        QUERY_API_SET.add(DATA_BASE_QUERY_4);
        QUERY_API_SET.add(DATA_BASE_QUERY_5);
        QUERY_API_SET.add(DATA_BASE_QUERY_6);
        QUERY_API_SET.add(DATA_BASE_QUERY_7);
        QUERY_API_SET.add(DATA_BASE_QUERY_8);
        QUERY_API_SET.add(DATA_BASE_QUERY_9);
        QUERY_API_SET.add(DATA_BASE_QUERY_10);
        QUERY_API_SET.add(DATA_BASE_QUERY_11);

        QUERY_API_SET.add(CONTENT_RESOLVER_QUERY_0);
        QUERY_API_SET.add(CONTENT_RESOLVER_QUERY_1);
        QUERY_API_SET.add(CONTENT_RESOLVER_QUERY_2);
    }

    public static final DatabaseUseDetector detector = new DatabaseUseDetector();




    public static void main(String[] args) throws IOException {
        String androidJar = "/home/ms/appAnalysis/AndroidHome";
//        String androidJar = "/home/ms/appAnalysis/AndroidHome";
//        String androidJar = ApkAndJavaConstants.androidJarPath;
        String apkDir = "/home/ms/targetApp";
//        String apkDir = System.getProperty("user.dir") + File.separator + "App_test";
//        String apkDir = "D:\\App\\AppIO\\app\\build\\intermediates\\apk\\debug";
        String logName="./log/targetApp.txt";
//        String logName="./social_data_use.txt";
        Log.openLog(logName, false);

        new DirTraversal() {
            @Override
            public void work(File apkFile) throws Exception {
                if (!apkFile.getName().endsWith(".apk"))
                    return;
                AnalyzeDatabase.logger.info("begin detect App:{}", apkFile);
                Log.write(Log.Mode.APP, apkFile.getName());
                SootInit.initSootForAndroid(apkFile.getPath(), androidJar);

                HashSet<AbstractBinaryAndroidComponent> components = ResolveManifest.getAllComponents(apkFile.getPath());
                HashMap<String, HashSet<SootMethod>> component2MapEntryPoint =
                        EntryPointAnalyze.getComponent2MapEntryPoint(components);

                List<SootMethod> entry=new ArrayList<>();
                for (String component : component2MapEntryPoint.keySet()) {
                    HashSet<SootMethod> entrypoints = component2MapEntryPoint.get(component);
                    for(SootMethod method:entrypoints) {
                        if(method==null)
                            continue;
                        entry.add(method);
                    }
                }
                AnalyzeDatabase.logger.info("add all clinit method as entrypoint ");
                entry.addAll(EntryPointAnalyze.getClinitMethod());
                AnalyzeDatabase.logger.info("entry point size： {}",entry.size());
                AnalyzeDatabase.logger.info("build cg ...");
                long start = System.nanoTime();
                CallGraphUtils.buildCGbyCHA(entry);
                CallGraph callGraph = Scene.v().getCallGraph();
                ReachableMethods reachableMethods = Scene.v().getReachableMethods();
                long stop = System.nanoTime();
                AnalyzeDatabase.logger.info("build cg coasts ：{} seconds",(stop-start)/1E9);
                QueueReader<MethodOrMethodContext> listener = reachableMethods.listener();
                HashSet<SootMethod> riskyMethod=new HashSet<>();
                while (listener.hasNext()){
                    SootMethod method = listener.next().method();
                    if(QUERY_API_SET.contains(method.getSubSignature())|| FileUsageDefinition.getSharedPreferencesWriteAPIList().contains(method.getSubSignature())||
                    FileUsageDefinition.getSharedPreferencesReadAPIList().contains(method.getSignature())){
                        //筛选出可能导致数据库泄漏的方法
                        Iterator<Edge> edgeIterator = callGraph.edgesInto(method);
                        while (edgeIterator.hasNext()){
                            Edge next = edgeIterator.next();
                            riskyMethod.add(next.src());
                        }
                    }
                }
                AnalyzeDatabase.logger.info("detect {} methods directly call the risky method in call graph",riskyMethod.size());
                HashSet<String> entrySubSignature=new HashSet<>();
                for(SootMethod method:entry){
                    entrySubSignature.add(method.getSubSignature());
                }
                HashMap<String,HashSet<Point>> result=new HashMap<>();
                detector.setMAX_DEPTH(2);
                int count=0;
                for(SootMethod method:riskyMethod){
                    HashSet<List<CallSite>> paths=new HashSet<>();
                    CallGraphUtils.findEntryMethod(method,entrySubSignature,"SubSignature",new ArrayList<>(),1,0,paths,false);
                    for(List<CallSite> path:paths){
                        AnalyzeDatabase.logger.info("call stack depth: {}",path.size());
                        Collections.reverse(path);
                        HashSet<Point> points = doAnalyze(method, path);
                        if(points!=null){
                            if(!result.containsKey(method.getSignature()))
                                result.put(method.getSignature(),new HashSet<>());
                            result.get(method.getSignature()).addAll(points);
                        }
                        count++;
                    }
                }
                for(String entrypoint:result.keySet()){
                    writeAnalyzeResult(result.get(entrypoint),entrypoint);
                }
                AnalyzeDatabase.logger.info("total {} called!",count);
            }
        }.traverse(new File(apkDir));
        Log.closeLog();
    }

    public static HashSet<Point> doAnalyze(SootMethod method, List<CallSite> callStack) {
        if (method == null)
            return null;
        AnalyzeDatabase.logger.info("detect entrypoint {}", method.getSignature());
        detector.setEntryPoint(method.getSignature());
        detector.inter_forward(method, 0, callStack);
        return detector.getAnalysisResultOfEntry(method.getSignature());
    }

    public static void writeAnalyzeResult(HashSet<Point> analysisResult, String entryPoint) {
        if (analysisResult == null)
            return;
        for (Point p : analysisResult)
            Log.write(Log.Mode.DATA_BASE_SINK, entryPoint, p.unit, p.method, p.type, p.otherMsg);
    }


    public static boolean isSystemClass(String clsName) {
        if (clsName.startsWith("java.") || clsName.startsWith("javax."))
            return true;
        if (clsName.startsWith("android.") || clsName.startsWith("androidx.") || clsName.startsWith("com.google.") || clsName.startsWith("com.android."))
            return true;
        if (clsName.startsWith("jdk"))
            return true;
        if (clsName.startsWith("sun."))
            return true;
        if (clsName.startsWith("org.omg") || clsName.startsWith("org.w3c.dom"))
            return true;
        return false;
    }
}
