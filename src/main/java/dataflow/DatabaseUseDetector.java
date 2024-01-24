package dataflow;

import constant.FileUsageDefinition;
import constant.StrawPointsDefinition;
import fj.P;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.TransitiveTargets;
import soot.toolkits.graph.BriefUnitGraph;
import util.StringUtil;

import java.util.*;


public class DatabaseUseDetector extends AbstractDataFlow {

    public static final Logger logger = LoggerFactory.getLogger(DatabaseUseDetector.class);

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

    private String entryPoint;

    public static HashSet<SootMethod> evailMethodSet=new HashSet<>();

    public void setEntryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
    }

    private HashMap<String, HashSet<Point>> res = new HashMap<>();

    public final Analyze analyze = new Analyze() {
        @Override
        public boolean caseAnalyze(Unit unit, SootMethod method, List<CallSite> callStack, HashSet<Point> points, ValueBox taintValueBox) {
            InvokeExpr invokeExpr = null;
            if (unit instanceof InvokeStmt) {
                InvokeStmt invokeStmt = (InvokeStmt) unit;
                invokeExpr = invokeStmt.getInvokeExpr();

            } else if (unit instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) unit;
                if (assignStmt.containsInvokeExpr()) {
                    invokeExpr = assignStmt.getInvokeExpr();
                }
            }

            if (invokeExpr == null)
                return false;
            if (!isCollectionSink(invokeExpr, taintValueBox))//判断是不是集合类型
                return false;
            DatabaseUseDetector.logger.info("detect risky method is used：{}", method.getSignature());
            //是相关的API,我们还要判断方法体内是否存在相关的API
            for (Unit u : method.retrieveActiveBody().getUnits()) {
                if (u.toString().contains("<android.database.Cursor: boolean moveToNext()>")) {
                    DatabaseUseDetector.logger.info("[Confirmed!]");
                    return true;
                }
            }
            return false;
        }

        boolean isCollectionSink(InvokeExpr invokeExpr, ValueBox taintValueBox) {

            int indexOf = invokeExpr.getArgs().indexOf(taintValueBox.getValue());
            if (indexOf == -1)
                return false;
            if (indexOf == 0 && StringUtil.isMatch(invokeExpr.getMethod().getSignature(), StrawPointsDefinition.COLLECTIONS_STRAWPOINT_REGEX))
                return true;
            return false;

        }
    };

    public final ForwardDataFlow forwardDetector = new ForwardDataFlow(analyze);


    public HashSet<Point> getAnalysisResultOfEntry(String entryPoint) {
        if (res.containsKey(entryPoint))
            return res.get(entryPoint);
        return null;
    }

    public void addPoint2Res(Unit unit, SootMethod method, String type, String uri) {
        if (!res.containsKey(entryPoint)) {
            res.put(entryPoint, new HashSet<Point>());
        }
        Point point = new Point(unit, method, type, uri);
        res.get(entryPoint).add(point);
    }

    class DataBaseNameConstructor {

        public final FileNameConstructor fileNameConstructor = new FileNameConstructor();

        public String construct(Unit u, SootMethod method, List<CallSite> callChain) {
            if (FileUsageDefinition.getDataBaseQueryAPIList().contains(method.getSubSignature())) {
                return databaseHandler(u, method, callChain);
            } else {
                return contentResolverHandler(u, method, callChain);
            }
        }

        public String databaseHandler(Unit u, SootMethod method, List<CallSite> callChain) {
//        logger.info("检测到ContentResolver查询语句：" + u);
            InvokeExpr invokeExpr = getInvokeExpr(u);
            String subSignature = invokeExpr.getMethod().getSubSignature();
            Value tableValue = null;
            Value sql = null;
            if (subSignature.equals(FileUsageDefinition.DATA_BASE_QUERY_0) || subSignature.equals(FileUsageDefinition.DATA_BASE_QUERY_1)) {
                tableValue = invokeExpr.getArg(0);
            } else if (subSignature.equals(FileUsageDefinition.DATA_BASE_QUERY_2) || subSignature.equals(FileUsageDefinition.DATA_BASE_QUERY_3)) {
                tableValue = invokeExpr.getArg(1);
            } else if (subSignature.equals(FileUsageDefinition.DATA_BASE_QUERY_4) || subSignature.equals(FileUsageDefinition.DATA_BASE_QUERY_5)) {
                sql = invokeExpr.getArg(0);
            } else if (subSignature.equals(FileUsageDefinition.DATA_BASE_QUERY_6) || subSignature.equals(FileUsageDefinition.DATA_BASE_QUERY_7)) {
                sql = invokeExpr.getArg(1);
            } else if (subSignature.equals(FileUsageDefinition.DATA_BASE_QUERY_8) || subSignature.equals(FileUsageDefinition.DATA_BASE_QUERY_9)) {
                tableValue = invokeExpr.getArg(2);
            } else {
                sql = invokeExpr.getArg(0);
            }
            String tableName = null;
            if (tableValue != null)
                tableName = getValueOfObject(tableValue, u, method, callChain);
            if (sql != null) {
                String sqlStmt = getValueOfObject(sql, u, method, callChain);
                if (sqlStmt == null)
                    return null;
                String[] s = sqlStmt.trim().split(" ");
                if (!s[0].toLowerCase().equals("select") || s.length < 3)
                    return null;
                tableName = s[3];
            }
            List<ValueBox> useBoxes = invokeExpr.getUseBoxes();
            String dataBaseName = fileNameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), u, method,0);
            return String.format("Database: {}, Table: {}", dataBaseName, tableName);
        }

        public String contentResolverHandler(Unit u, SootMethod method, List<CallSite> callChain) {
//        logger.info("检测到ContentResolver查询语句：" + u);
            InvokeExpr invokeExpr = getInvokeExpr(u);
            if (invokeExpr == null)
                return null;
            Value arg = invokeExpr.getArg(0);
            return getValueOfObject(arg, u, method, callChain);
        }

        private String getValueOfObject(Value value, Unit u, SootMethod method, List<CallSite> callChain) {
            if (value instanceof Local)
                return fileNameConstructor.getRef2Str(value, u, method,0);
            return value.toString();
        }
    }

    public final DataBaseNameConstructor constructor = new DataBaseNameConstructor();

    class PreferencesNameConstructor{

        public final FileNameConstructor fileNameConstructor = new FileNameConstructor();

        public String construct(Unit u, SootMethod method, List<CallSite> callChain){
            InvokeExpr invokeExpr = getInvokeExpr(u);
            if (invokeExpr == null)
                return "";
            SootMethod m = invokeExpr.getMethod();
            List<ValueBox> useBoxes = invokeExpr.getUseBoxes();
            String preferenceName="";
            if(FileUsageDefinition.getSharedPreferencesReadAPIList().contains(m.getSignature())){
                String str = fileNameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), u, method,0);
                preferenceName=String.format("READ: %s",str);
            }else if(FileUsageDefinition.getSharedPreferencesWriteAPIList().contains(m.getSubSignature())){
                String str = fileNameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), u, method,0);
                preferenceName=String.format("WRITE: %s",str);
            }
            return preferenceName;
        }
    }

    public final PreferencesNameConstructor preferencesNameConstructor=new PreferencesNameConstructor();

    @Override
    public boolean caseAnalyze(Unit unit, SootMethod method, List<CallSite> callStack, HashSet<Point> res, ValueBox taintValueBox) {
        InvokeExpr invokeExpr = getInvokeExpr(unit);
        if (invokeExpr == null)
            return false;
        SootMethod m = invokeExpr.getMethod();
        if(QUERY_API_SET.contains(m.getSubSignature())&&(unit instanceof DefinitionStmt)){
            return analyzeDataBaseOp(unit,method,callStack);
        }else if(FileUsageDefinition.getSharedPreferencesWriteAPIList().contains(m.getSubSignature())||FileUsageDefinition.getSharedPreferencesReadAPIList().contains(m.getSignature())){
            return analyzePreferences(unit,method,callStack);
        }
//        evailMethodSet.add(method);
//        DefinitionStmt definitionStmt = (DefinitionStmt) unit;
//        ValueBox valueBox = definitionStmt.getDefBoxes().get(0);
//        forwardDetector.inter_forward_dataflow(method, unit, valueBox, -1, 0, callStack);
//        if (!forwardDetector.getFindFlag())
//            return false;
//        //将找到的结果记录下来
//        String dataBaseInfo = constructor.construct(unit, method, callStack);
//        addPoint2Res(unit, method, "COLLECTIONS", dataBaseInfo);
//        //分析完需要讲前向分析的标志位设置为false
//        forwardDetector.setFindFlag(false);
        return true;
    }

    public boolean analyzeDataBaseOp(Unit unit, SootMethod method, List<CallSite> callStack){
        DefinitionStmt definitionStmt = (DefinitionStmt) unit;
        ValueBox valueBox = definitionStmt.getDefBoxes().get(0);
        boolean flag=false;
        forwardDetector.inter_forward_dataflow(method, unit, valueBox, -1, 0, callStack);
        if (!forwardDetector.getFindFlag())
            flag=true;
        //将找到的结果记录下来
        String dataBaseInfo = constructor.construct(unit, method, callStack);
        if(!flag){
            dataBaseInfo=String.format("ONLY_QUERY: %s",dataBaseInfo);
        }else {
            dataBaseInfo=String.format("DOS_EFFECT_SIDE: %s",dataBaseInfo);
        }
        addPoint2Res(unit, method, "DATA_BASE", dataBaseInfo);
        //分析完需要讲前向分析的标志位设置为false
        forwardDetector.setFindFlag(false);
        return flag;
    }

    public boolean analyzePreferences(Unit unit, SootMethod method, List<CallSite> callStack){
        String preferencesInfo = preferencesNameConstructor.construct(unit, method, callStack);
        addPoint2Res(unit,method,"SHARED_PREFERENCES",preferencesInfo);
        return true;
    }





    public void inter_forward(SootMethod method, int depth, List<CallSite> callStack) {
        if (depth > MAX_DEPTH) {
            return;
        }
        if (method == null)
            return;
        if (isSystemClass(method.getDeclaringClass()))
            return;
        if (isMethodCalled(callStack, method))
            return;
        if (!method.isConcrete())
            return;
        if(method.getName().equals("run")||method.getName().equals("call"))
            return;
        Iterator<Edge> iterator = Scene.v().getCallGraph().edgesOutOf(method);
        while (iterator.hasNext()){
            Edge edge = iterator.next();
            caseAnalyze(edge.srcUnit(), edge.src(),callStack,null,null);
            List<CallSite> temp_call_stack = new ArrayList<>(callStack);
            temp_call_stack.add(new CallSite(method, edge.srcUnit(), -1));
            inter_forward(edge.tgt(),depth+1,temp_call_stack);
        }
//        for (Unit u : method.retrieveActiveBody().getUnits()) {
//            InvokeExpr invokeExpr = getInvokeExpr(u);
//            if (invokeExpr != null) {
//                if(caseAnalyze(u, method, callStack, null, null))
//                    return;
//                HashSet<SootMethod> methods=new HashSet<>();
//                Iterator<Edge> edgeIterator = Scene.v().getCallGraph().edgesOutOf(u);
//                while (edgeIterator.hasNext()){
//                    SootMethod sootMethod = edgeIterator.next().tgt();
//                    methods.add(sootMethod);
//
//                }
//                for (SootMethod m : methods) {
//                    List<CallSite> temp_call_stack = new ArrayList<>(callStack);
//                    temp_call_stack.add(new CallSite(method, u, -1));
//                    inter_forward(m, depth + 1, temp_call_stack);
//                }
//            }
//        }
    }

    public SootMethod getConcreteMethod(SootMethod m) {
        //该方法是Abstract或者接口方法，找到该方法的实际的方法
        FastHierarchy fastHierarchy = Scene.v().getOrMakeFastHierarchy();
        Collection<SootClass> subclassesOf = fastHierarchy.getSubclassesOf(m.getDeclaringClass());
        for (SootClass sootClass : subclassesOf) {
            if (sootClass.declaresMethod(m.getSubSignature())) {
                return sootClass.getMethodUnsafe(m.getSubSignature());
            }
        }
        return m;
    }






}
