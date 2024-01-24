package analyze;

import component.EntryPointAnalyze;
import component.ResolveManifest;
import constant.ApkAndJavaConstants;
import dataflow.Point;
import dataflow.StrawDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.manifest.binary.AbstractBinaryAndroidComponent;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.util.queue.QueueReader;
import util.DirTraversal;
import util.Log;
import util.SootInit;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


public class AnalyzeNIO {

    public static final Logger logger = LoggerFactory.getLogger(AnalyzeNIO.class);


    public static final String SERVER_SOCKET_API = "<java.net.ServerSocket: java.net.Socket accept()>";
    public static final String SERVER_SOCKET_CHANNEL_API_0 = "<java.nio.channels.ServerSocketChannel: java.nio.channels.SocketChannel accept()>";
    public static final String SERVER_SOCKET_CHANNEL_API_1 = "<java.nio.channels.ServerSocketChannel: java.nio.channels.SelectionKey register(java.nio.channels.Selector,int)>";
    public static final String SERVER_SOCKET_CHANNEL_API_2 = "<java.nio.channels.ServerSocketChannel: java.nio.channels.SelectionKey register(java.nio.channels.Selector,int,java.lang.Object)>";



    public static final String UDS_API="<android.net.LocalSocket: java.io.InputStream getInputStream()>";
    public static final String SERVER_API="<java.net.Socket: java.io.InputStream getInputStream()>";
    public static final HashSet<String> NIO_API_List = new HashSet<>();


    public static final String DATA_READ_IN_PARAM_0="<java.io.FileReader: int read(char[])>";
    public static final String DATA_READ_IN_PARAM_1="<java.io.FileReader: int read(char[],int,int)>";
    public static final String DATA_READ_IN_PARAM_2="<java.io.FileReader: int read(java.nio.CharBuffer)>";
    public static final String DATA_READ_IN_PARAM_3="<java.io.FileInputStream: int read(byte[])>";
    public static final String DATA_READ_IN_PARAM_4="<java.io.FileInputStream: int read(byte[],int,int)>";
    public static final String DATA_READ_IN_PARAM_5="<java.io.InputStreamReader: int read(char[])>";
    public static final String DATA_READ_IN_PARAM_6="<java.io.InputStreamReader: int read(char[],int,int)>";
    public static final String DATA_READ_IN_PARAM_7="<java.io.InputStreamReader: int read(java.nio.CharBuffer)>";
    public static final String DATA_READ_IN_PARAM_8="<java.io.BufferedReader: int read(java.nio.CharBuffer)>";
    public static final String DATA_READ_IN_PARAM_9="<java.io.BufferedReader: int read(char[])>";
    public static final String DATA_READ_IN_PARAM_10="<java.io.BufferedReader: int read(char[],int,int)>";
    public static final String DATA_READ_IN_PARAM_11="<java.io.DataInputStream: int read(byte[])>";
    public static final String DATA_READ_IN_PARAM_12="<java.io.DataInputStream: int read(byte[],int,int)>";
    public static final String DATA_READ_IN_PARAM_13="<java.io.DataInputStream: void readFully(byte[])>";
    public static final String DATA_READ_IN_PARAM_14="<java.io.DataInputStream: void readFully(byte[],int,int)>";
    public static final String DATA_READ_IN_PARAM_15="<java.io.BufferedInputStream: int read(byte[])>";
    public static final String DATA_READ_IN_PARAM_16="<java.io.BufferedInputStream: int read(byte[],int,int)>";

    public static final HashSet<String> DATA_READ_API=new HashSet<>();

    static {
        NIO_API_List.add(UDS_API);
        NIO_API_List.add(SERVER_API);

        DATA_READ_API.add(DATA_READ_IN_PARAM_0);
        DATA_READ_API.add(DATA_READ_IN_PARAM_1);
        DATA_READ_API.add(DATA_READ_IN_PARAM_2);
        DATA_READ_API.add(DATA_READ_IN_PARAM_3);
        DATA_READ_API.add(DATA_READ_IN_PARAM_4);
        DATA_READ_API.add(DATA_READ_IN_PARAM_5);
        DATA_READ_API.add(DATA_READ_IN_PARAM_6);
        DATA_READ_API.add(DATA_READ_IN_PARAM_7);
        DATA_READ_API.add(DATA_READ_IN_PARAM_8);
        DATA_READ_API.add(DATA_READ_IN_PARAM_9);
        DATA_READ_API.add(DATA_READ_IN_PARAM_10);
        DATA_READ_API.add(DATA_READ_IN_PARAM_11);
        DATA_READ_API.add(DATA_READ_IN_PARAM_12);
        DATA_READ_API.add(DATA_READ_IN_PARAM_13);
        DATA_READ_API.add(DATA_READ_IN_PARAM_14);
        DATA_READ_API.add(DATA_READ_IN_PARAM_15);
        DATA_READ_API.add(DATA_READ_IN_PARAM_16);

    }

    public static final StrawDetector detector=new StrawDetector();

    public static void main(String[] args) throws IOException {
        String androidJar = "/home/ms/appAnalysis/AndroidHome";
//        String androidJar = ApkAndJavaConstants.androidJarPath;
        String apkDir = "/home/shared/download_apks/social";
//        String apkDir = System.getProperty("user.dir")+File.separator+"detectedApp";
//        String apkDir = "D:\\App\\AppIO\\app\\build\\intermediates\\apk\\debug";

//
        Log.openLog("/home/ms/appAnalysis/log/NIO.txt", false);
//        Log.openLog("./NIO.txt", false);

        detector.setTaintWrapper(DATA_READ_API);
        new DirTraversal() {
            @Override
            public void work(File apkFile) throws Exception {
                if (!apkFile.getName().endsWith(".apk"))
                    return;
                AnalyzeNIO.logger.info("begin detect App:{}", apkFile);
                Log.write(Log.Mode.APP, apkFile.getName());

                SootInit.initSootForAndroid(apkFile.getPath(), androidJar);
                boolean flag = false;
                for (String apiSignature : NIO_API_List) {
                    try {
                        SootMethod method = Scene.v().getMethod(apiSignature);
                        flag=true;
                    } catch (Exception e) {
                    }
                }
                if (!flag)
                    return;
                for (SootClass cls : Scene.v().getApplicationClasses()) {
                    if(isSystemClass(cls.getName()))
                        continue;
                    for (SootMethod m : cls.getMethods()) {
                        try {
                            if (m.isJavaLibraryMethod())
                                continue;
                            if (m.isAbstract())
                                continue;
                            if (m.isPhantom())
                                continue;
                            if (m.isNative())
                                continue;
                            for (Unit u : m.retrieveActiveBody().getUnits()) {
                                if (!(u instanceof AssignStmt))
                                    continue;
                                AssignStmt assignStmt = (AssignStmt) u;
                                if (!(assignStmt.containsInvokeExpr()))
                                    continue;
                                String signature = assignStmt.getInvokeExpr().getMethod().getSignature();
                                if (!NIO_API_List.contains(signature))
                                    continue;
                                doAnalysis(m, u);
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }.traverse(new File(apkDir));
        Log.closeLog();
    }

    public static void doAnalysis(SootMethod caller,Unit callSite){
        if(!(callSite instanceof DefinitionStmt))
            return;
        DefinitionStmt definitionStmt = (DefinitionStmt) callSite;
        ValueBox valueBox = definitionStmt.getDefBoxes().get(0);
        //寻找数据流中的是数据流动
        detector.setEntryPoint(caller.getSignature());
        AnalyzeNIO.logger.info("detect NIO in {}",caller.getSignature());
        detector.run(caller,callSite,valueBox,0,0,new ArrayList<>());
        HashSet<Point> result = detector.getAnalysisResultOfEntry(caller.getSignature());
        if(result!=null)
            writeAnalyzeResult(result,caller.getSignature());
    }

    public static void writeAnalyzeResult(HashSet<Point> analysisResult, String entryPoint) {
        if (analysisResult == null)
            return;
        for (Point p : analysisResult)
            Log.write(Log.Mode.SINK, entryPoint, p.unit, p.method, p.type);
    }

    public static boolean isSystemClass(String clsName){
        if(clsName.startsWith("java.")||clsName.startsWith("javax."))
            return true;
        if(clsName.startsWith("android.")||clsName.startsWith("androidx.")||clsName.startsWith("com.google.")||clsName.startsWith("com.android."))
            return true;
        if(clsName.startsWith("jdk"))
            return true;
        if(clsName.startsWith("sun."))
            return true;
        if(clsName.startsWith("org.omg")||clsName.startsWith("org.w3c.dom"))
            return true;
        return false;
    }



}
