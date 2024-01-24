package dataflow;

import cfg.CfgFactory;
import cfg.Path;
import cg.CallGraphUtils;
import constant.FileUsageDefinition;
import heros.flowfunc.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.BriefUnitGraph;
import util.StringUtil;

import java.util.*;


public class FileNameConstructor extends AbstractDataFlow {


    public JimpleBasedInterproceduralCFG icfg = null;

    public static final Logger logger = LoggerFactory.getLogger(FileNameConstructor.class);

    public FileNameConstructor(JimpleBasedInterproceduralCFG icfg){
        this.icfg=icfg;
    }

    private final int max_search_depth=10;

    public static String EXTRACTOR_MODE="DATABASE_NO_FIELD";

    public static String tableName="";

    public FileNameConstructor(){

    }

    private Unit getDirectDefUnit(Value obj, Unit unit, SootMethod method) {
        if (!method.isConcrete())
            return null;
        if (unit instanceof IdentityStmt)//如果本身就是一个参数直接返回当前语句
            return unit;
        Queue<Unit> queue = new LinkedList<>();
        HashSet<Unit> visit = new HashSet<>();
        BriefUnitGraph graph = new BriefUnitGraph(method.retrieveActiveBody());
        queue.addAll(graph.getPredsOf(unit));
        while (!queue.isEmpty()) {
            Unit poll = queue.poll();
            visit.add(poll);
            if (isValueDefinedInUnit(poll, obj)) {
                if (poll instanceof IdentityStmt)
                    return poll;
                AssignStmt assignStmt = (AssignStmt) poll;
                if (assignStmt.getRightOp().toString().equals("null")) {
                    continue;
                } else {
                    return poll;
                }
            }
            for (Unit pre : graph.getPredsOf(poll)) {
                if (!visit.contains(pre))
                    queue.add(pre);
            }
        }

        for (Unit u : method.retrieveActiveBody().getUnits()) {
            if (isValueDefinedInUnit(u, obj)) {
                if (u instanceof IdentityStmt)
                    return u;
                AssignStmt assignStmt = (AssignStmt) u;
                if (assignStmt.getRightOp().toString().equals("null")) {
                    continue;
                } else {
                    return u;
                }
            }
        }
        return null;
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

    /*
    得到当前的变量值，如果是变量就恢复，如果是常量就直接返回
     */
    private String getValueOfObject(Value value, Unit u, SootMethod method,int depth) {
        if (value instanceof Local)
            return getRef2Str(value, u, method,depth);
        return "[("+value.toString()+")]";
    }

    /*
    参数：要恢复信息的变量，当前变量被使用的语句，当前变量所在的方法
     */
    public String getRef2Str(Value obj, Unit unit, SootMethod method,int depth) {
        if (!method.isConcrete())
            return "";
        if(depth>max_search_depth){
            logger.warn("Exceed max search depth!");
            return "";
        }

        HashSet<Unit> allDirectUnit = getAllDirectUnit(obj, unit, method);
        if (allDirectUnit.isEmpty()) {
            logger.warn("No definition is for {}!", obj);
            return "";
        }
        String ans="[";
        for(Unit directDefUnit:allDirectUnit) {
            String concreteValue = getConcreteValue(directDefUnit, depth, method);
            ans=ans+"("+concreteValue+")";
        }
        ans+="]";
        return ans;
    }

    public String getConcreteValue(Unit directDefUnit,int depth,SootMethod method){
        String res="";

//        logger.info("找到的直接定义语句是： {}",directDefUnit);
//        logger.info("当前方法是： {}",method.getSignature());
        if (directDefUnit instanceof IdentityStmt) {
            Value rightOp = ((IdentityStmt) directDefUnit).getRightOp();
            if (rightOp instanceof ThisRef) {
                return "";
            }
            if(!(rightOp instanceof ParameterRef))
                return "";
            ParameterRef parameterRef = (ParameterRef) rightOp;
            int index = parameterRef.getIndex();
            Collection<Unit> callersOf = icfg.getCallersOf(method);
            if (callersOf == null || callersOf.isEmpty()) {
                logger.warn("no caller is found for {}", method.getSignature());
                return "";
            }
            res = "[";
            int count = 0;

            //在向上寻找他的实际调用出
            for (Unit callSite : callersOf) {
                SootMethod caller = icfg.getMethodOf(callSite);
                InvokeExpr invokeExpr = getInvokeExpr(callSite);
                //我们要得到相应的实参
                Value arg = invokeExpr.getArg(index);
//                logger.info("找到的调用点为： {}",callSite);
//                logger.info("找到的实参是： {}",arg);
                String valueOfObject = getValueOfObject(arg, callSite, caller, depth + 1);
                if (valueOfObject.isEmpty() || valueOfObject.startsWith("FAILED"))
                    continue;
                res += "(" + valueOfObject + ")";
                count++;
                if (count > 10)
                    break;
            }
            res += "]";
            return res;
        } else {
            //如果直接初始化语句是程序中的普通语句
            //其实我们这里查询的变量也就两种String
            AssignStmt assignStmt = (AssignStmt) directDefUnit;
            //1.如果要查询的对象的File
            //2.如果是字符串或者其他类型的变量
            //2.1如果变量通过调用方法返回的结果，我们要对不同的方法进行区分，因为字符传串的操作实在太多
            if (assignStmt.containsInvokeExpr()) {
                InvokeExpr invokeExpr = assignStmt.getInvokeExpr();
                String signature = invokeExpr.getMethod().getSignature();
                String subSignature = invokeExpr.getMethod().getSubSignature();
                List<ValueBox> useBoxes = directDefUnit.getUseBoxes();
                //如果来自android或者是java的标准方法，我们需要进一步区分对待
                if (signature.startsWith("<android") || signature.startsWith("<java")) {
                    //如果一个返回值是通过andoird或者通过java标准库获取的，我们直接返回该方法名
                    if (signature.equals("<java.lang.String: java.lang.String format(java.lang.String,java.lang.Object[])>")) {
                        return recoverInfoFromFormat(directDefUnit, method,depth);
                    } else if (signature.equals("<java.lang.StringBuilder: java.lang.String toString()>")) {
                        return recoverInfoFromStringBuilder(directDefUnit, method,depth);
                    } else if (signature.equals("<android.content.Context: java.lang.String getPackageName()>")
                            || signature.equals("<android.content.ContextWrapper: java.lang.String getPackageName()>")) {
                        return "package";
                    } else if (signature.equals("<android.net.Uri$Builder: android.net.Uri build()>")) {
                        //处理build方法建立uri的方式
                        return constructStringFromBuild(directDefUnit, method);
                    } else if (signature.equals("<java.lang.String: java.lang.String concat(java.lang.String)>")) {
                        String str1 = getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), directDefUnit, method, depth + 1);
                        String str2 = getValueOfObject(invokeExpr.getArg(0), directDefUnit, method, depth + 1);
                        return String.format("%s%s", str1, str2);
                    } else if (signature.equals("<java.io.File: java.lang.String toString()>")) {
                        return getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), directDefUnit, method, depth + 1);
                    } else if (signature.equals("<java.lang.Integer: java.lang.Integer valueOf(int)>")
                            || signature.equals("<java.lang.String: java.lang.String valueOf(java.lang.Object)>")) {
                        return getValueOfObject(invokeExpr.getArg(0), directDefUnit, method, depth + 1);
                    } else if (signature.equals("<android.net.Uri: android.net.Uri parse(java.lang.String)>")) {
                        return getValueOfObject(invokeExpr.getArg(0), directDefUnit, method, depth + 1);
                    } else if (signature.equals("<android.net.Uri: android.net.Uri withAppendedPath(android.net.Uri,java.lang.String)>")) {
                        String pre = getValueOfObject(invokeExpr.getArg(0), directDefUnit, method, depth + 1);
                        String succor = getValueOfObject(invokeExpr.getArg(1), directDefUnit, method, depth + 1);
                        return String.format("%s/%s", pre, succor);
                    } else if (signature.equals("<android.net.Uri: android.net.Uri fromParts(java.lang.String,java.lang.String,java.lang.String)>")) {
                        String str0 = getRef2Str(invokeExpr.getArg(0), directDefUnit, method, depth + 1);
                        String str1 = getRef2Str(invokeExpr.getArg(1), directDefUnit, method, depth + 1);
                        String str2 = getRef2Str(invokeExpr.getArg(2), directDefUnit, method, depth + 1);
                        return String.format("%s:%s#%s", str0, str1, str2);
                    } else if (signature.equals("<android.content.SharedPreferences: android.content.SharedPreferences$Editor edit()>")) {
                        return getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), directDefUnit, method, depth + 1);
                    } else if (subSignature.equals(FileUsageDefinition.SHARED_PREFERENCES_0)) {
                        return getValueOfObject(invokeExpr.getArg(0), directDefUnit, method, depth + 1);
                    } else if (subSignature.equals(FileUsageDefinition.SHARED_PREFERENCES_1)) {
                        return method.getDeclaringClass().getName();
                    } else if (subSignature.equals(FileUsageDefinition.SHARED_PREFERENCES_2)) {
                        return "package_preferences";
                    } else if (signature.equals("<android.database.sqlite.SQLiteOpenHelper: android.database.sqlite.SQLiteDatabase getWritableDatabase()>") ||
                            signature.equals("<android.database.sqlite.SQLiteOpenHelper: android.database.sqlite.SQLiteDatabase getReadableDatabase()>")) {
                        //todo
                        AssignStmt directDefUnit1 = (AssignStmt) directDefUnit;
                        InvokeExpr invokeExpr1 = directDefUnit1.getInvokeExpr();
                        ValueBox valueBox = invokeExpr1.getUseBoxes().get(invokeExpr1.getUseBoxes().size() - 1);
                        Value value = valueBox.getValue();
                        if (value.getType().toString().equals("android.database.sqlite.SQLiteOpenHelper")) {
                            //如果对数据库提查询的方式是通过接口的方式，我们应该去找这个实例的初始化位置，
                            return getRef2Str(value, directDefUnit, method, depth + 1);
                        }
                        SootClass dataBaseHelper = Scene.v().getSootClass(valueBox.getValue().getType().toString());
                        if (FileNameConstructor.EXTRACTOR_MODE.equals("DATABASE_NO_FIELD")) {
                            for (SootMethod mm : dataBaseHelper.getMethods()) {
                                if (mm.getName().equals("<init>") || mm.getName().equals("<clinit>")) {
                                    if (!mm.isConcrete())
                                        continue;
                                    for (Unit uu : mm.retrieveActiveBody().getUnits()) {
                                        if (uu instanceof InvokeStmt) {
                                            InvokeExpr expr = ((InvokeStmt) uu).getInvokeExpr();
                                            if (expr.getMethod().getSignature().equals("<android.database.sqlite.SQLiteOpenHelper: void <init>(android.content.Context,java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,int)>")) {
                                                return getValueOfObject(expr.getArg(1), uu, mm, depth + 1);
                                            }
                                        }
                                    }

                                }
                            }
                        } else {
                            //否则则的话我们要提取所有
                            StringBuilder tableInfo = new StringBuilder("{");
                            for (SootMethod mm : dataBaseHelper.getMethods()) {
                                if (!mm.isConcrete())
                                    continue;
                                for (Unit u : mm.retrieveActiveBody().getUnits()) {
                                    String s = u.toString().toLowerCase();
                                    if(!s.contains("create table"))
                                        continue;
                                    for(ValueBox box:u.getUseBoxes()){
                                        Value v = box.getValue();
                                        if(!(v instanceof StringConstant))
                                            continue;
                                        logger.info("[CR]: {}",v.toString());

                                        String sentence = v.toString().toLowerCase();
                                        String reg="create table.*\\(.*\\)";
                                        String string = StringUtil.findString(sentence, reg);
                                        if(string!=null){
                                            //如果这是条create table 语句，我们把他抽取出来
                                            tableInfo.append(string);
                                            tableInfo.append("MS");
                                        }
                                    }
                                }
                            }
                            logger.info("[TABLE_INFO]: {}",tableInfo.toString());
                            tableInfo.append("}");
                            FileNameConstructor.EXTRACTOR_MODE = "DATABASE_NO_FIELD";
                            return tableInfo.toString();
                        }

                    } else if (signature.equals("<android.database.sqlite.SQLiteDatabase: android.database.sqlite.SQLiteDatabase openDatabase(java.io.File,android.database.sqlite.SQLiteDatabase$OpenParams)>") ||
                            signature.equals("<android.database.sqlite.SQLiteDatabase: android.database.sqlite.SQLiteDatabase openDatabase(java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,int)>") ||
                            signature.equals("<android.database.sqlite.SQLiteDatabase: android.database.sqlite.SQLiteDatabase openDatabase(java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,int,android.database.DatabaseErrorHandler)>") ||
                            signature.equals("<android.database.sqlite.SQLiteDatabase: android.database.sqlite.SQLiteDatabase openOrCreateDatabase(java.io.File,android.database.sqlite.SQLiteDatabase$CursorFactory)>") ||
                            signature.equals("<android.database.sqlite.SQLiteDatabase: android.database.sqlite.SQLiteDatabase openOrCreateDatabase(java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,android.database.DatabaseErrorHandler)>") ||
                            signature.equals("<android.database.sqlite.SQLiteDatabase: android.database.sqlite.SQLiteDatabase openOrCreateDatabase(java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory)>") ||
                            signature.equals("<android.content.Context: android.database.sqlite.SQLiteDatabase openOrCreateDatabase(java.lang.String,int,android.database.sqlite.SQLiteDatabase$CursorFactory)>")) {
//                         logger.info("进行数据库信息恢复");
                        return getValueOfObject(invokeExpr.getArg(0), directDefUnit, method, depth + 1);
                    } else {
                        //对于我们还不知到如何处理的，我们直接返回他们的方法签名
                        logger.info("Can't copy with {}",signature);
                        return "FAILED: Not Copy With";
                    }

                } else {
                    //如果通过其他方法的返回值获取的,完全取决于开发者,因为这里设计到控制流，因此结果并不精确
                    SootMethod m = invokeExpr.getMethod();
                    if (!m.isConcrete())
                        return "FAILED: Abstract Method";
                    if (subSignature.equals(FileUsageDefinition.DATA_BASE_0) || subSignature.equals(FileUsageDefinition.DATA_BASE_1) ||
                            subSignature.equals(FileUsageDefinition.DATA_BASE_2)) {
                        return getValueOfObject(invokeExpr.getArg(0), directDefUnit, method, depth + 1);
                    } else if (subSignature.equals(FileUsageDefinition.DATA_BASE_3) || subSignature.equals(FileUsageDefinition.DATA_BASE_4)) {
                        SootClass declaringClass = m.getDeclaringClass();
                        if (declaringClass.getName().equals("android.database.sqlite.SQLiteOpenHelper") ||
                                (declaringClass.hasSuperclass() && declaringClass.getSuperclass().getName().equals("android.database.sqlite.SQLiteOpenHelper"))) {
                            //我们去看这个数据库的构造函数去检查这个数据库的名字
                            if (FileNameConstructor.EXTRACTOR_MODE.equals("DATABASE_NO_FIELD")) {
                                for (SootMethod mm : declaringClass.getMethods()) {
                                    if (mm.getName().equals("<init>") || mm.getName().equals("<clinit>")) {
                                        for (Unit uu : mm.retrieveActiveBody().getUnits()) {
                                            if (uu instanceof InvokeStmt) {
                                                InvokeExpr expr = ((InvokeStmt) uu).getInvokeExpr();
                                                if (expr.getMethod().getSignature().equals("<android.database.sqlite.SQLiteOpenHelper: void <init>(android.content.Context,java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,int)>")) {
                                                    return getValueOfObject(expr.getArg(1), uu, mm, depth + 1);
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                StringBuilder tableInfo = new StringBuilder("{");
                                for (SootMethod mm : declaringClass.getMethods()) {
                                    if (!mm.isConcrete())
                                        continue;
                                    for (Unit u : mm.retrieveActiveBody().getUnits()) {
                                        String s = u.toString().toLowerCase();
                                        if(!s.contains("create table"))
                                            continue;
                                        for(ValueBox valueBox:u.getUseBoxes()){
                                            Value value = valueBox.getValue();
                                            if(!(value instanceof StringConstant))
                                                continue;
                                            logger.info("[CR]: {}",value.toString());
                                            String sentence = value.toString().toLowerCase();
                                            String reg="create table.*\\(.*\\)";
                                            String string = StringUtil.findString(sentence, reg);
                                            if(string!=null){
                                                //如果这是条create table 语句，我们把他抽取出来
                                                tableInfo.append(string);
                                                tableInfo.append("MS");
                                            }
                                        }
                                    }
                                }
                                tableInfo.append("}");
                                FileNameConstructor.EXTRACTOR_MODE = "DATABASE_NO_FIELD";
                                return tableInfo.toString();

                            }
                        } else {
                            for (SootMethod callee : icfg.getCalleesOfCallAt(directDefUnit)) {
                                for (Unit uu : callee.retrieveActiveBody().getUnits()) {
                                    if (!(uu instanceof ReturnStmt))
                                        continue;
                                    ReturnStmt returnStmt = (ReturnStmt) uu;
                                    Value op = returnStmt.getOp();
                                    if (op instanceof NullConstant)
                                        continue;
                                    return getValueOfObject(op, uu, callee, depth + 1);
                                }
                            }

                        }
                        return "FAILED: NotFound DataBase Init Unit";
                    } else if (subSignature.equals(FileUsageDefinition.SHARED_PREFERENCES_0)) {
                        return getValueOfObject(invokeExpr.getArg(0), directDefUnit, method, depth + 1);
                    } else if (subSignature.equals(FileUsageDefinition.SHARED_PREFERENCES_1)) {
                        return method.getDeclaringClass().getName();
                    } else if (subSignature.equals(FileUsageDefinition.SHARED_PREFERENCES_2)) {
                        return "package_preferences";
                    }
                    //对于返回值，我们需要找到他们的所有
                    String ans = "[";
                    for (SootMethod callee : icfg.getCalleesOfCallAt(directDefUnit)) {
                        BriefUnitGraph graph = new BriefUnitGraph(callee.retrieveActiveBody());
                        for (Unit tail : graph.getTails()) {
                            if (tail instanceof ReturnStmt) {
                                ReturnStmt returnStmt = (ReturnStmt) tail;
                                Value op = returnStmt.getOp();
                                if (op instanceof NullConstant)
                                    continue;
                                //获取到字符串的名字
                                String str = getValueOfObject(op, tail, callee, depth + 1);
                                ans = ans + "(" + str + ")";
                            }
                        }
                        ans += "]";
                        return ans;

                    }
                }

            } else if (assignStmt.containsFieldRef()) {
                SootField field = assignStmt.getFieldRef().getField();
                if (field.toString().startsWith("<java") || field.toString().startsWith("<android")) {
                    if (field.toString().equals("<java.io.File: java.lang.String separator>")) {
                        return "/";
                    } else if (field.toString().equals("<android.provider.MediaStore$Audio$Media: android.net.Uri EXTERNAL_CONTENT_URI>")) {
                        return "EXTERNAL_CONTENT_URI";
                    } else if (field.toString().equals("<android.provider.MediaStore$Video$Thumbnails: android.net.Uri EXTERNAL_CONTENT_URI>")) {
                        return "EXTERNAL_CONTENT_URI";
                    } else if (field.toString().equals("<android.provider.MediaStore$Images$Media: android.net.Uri EXTERNAL_CONTENT_URI>")) {
                        return "EXTERNAL_CONTENT_URI";
                    } else if (field.toString().equals("<android.provider.MediaStore$Images$Thumbnails: android.net.Uri EXTERNAL_CONTENT_URI>")) {
                        return "EXTERNAL_CONTENT_URI";
                    } else {
                        return String.format("FAILED: Android/Java Field %s", field.toString());
                    }
                } else {
                    //如果是其他类或者实例的字段的话
                    return getFieldInfo(field, depth + 1);
                }
            } else if (assignStmt.getRightOp() instanceof NewExpr) {
                Value rightOp = assignStmt.getRightOp();
                NewExpr newExpr = (NewExpr) rightOp;
                Type type = newExpr.getType();
                SootClass sootClass = Scene.v().getSootClass(type.toString());
                if (sootClass.hasSuperclass()&&sootClass.getSuperclass().getName().equals("android.database.sqlite.SQLiteOpenHelper")) {
                    //我们要找到数据库的帮助类
                    if (FileNameConstructor.EXTRACTOR_MODE.equals("DATABASE_NO_FIELD")) {
                        for (SootMethod mm : sootClass.getMethods()) {
                            if (mm.getName().equals("<init>") || mm.getName().equals("<clinit>")) {
//                                    logger.info("method: {}", mm.getSignature());
                                if (!mm.isConcrete())
                                    continue;
                                for (Unit uu : mm.retrieveActiveBody().getUnits()) {
//                                        logger.info("Unit: {}", uu);
                                    if (uu instanceof InvokeStmt) {
                                        InvokeExpr expr = ((InvokeStmt) uu).getInvokeExpr();
                                        if (expr.getMethod().getSignature().equals("<android.database.sqlite.SQLiteOpenHelper: void <init>(android.content.Context,java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,int)>")) {
                                            String valueOfObject = getValueOfObject(expr.getArg(1), uu, mm, depth + 1);
//                                            logger.info("恢复的数据库信息： " + valueOfObject);
                                            return valueOfObject;
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        //我们恢复左右的数据库信息
                        StringBuilder tableInfo = new StringBuilder("{");
                        for (SootMethod mm : sootClass.getMethods()) {
                            if (!mm.isConcrete())
                                continue;
//                            logger.info("检测方法： {}", mm.getSignature());
                            for (Unit u : mm.retrieveActiveBody().getUnits()) {
                                InvokeExpr expr = getInvokeExpr(u);
                                if (expr == null)
                                    continue;
                                if (expr.getMethod().getSubSignature().equals("void execSQL(java.lang.String)")) {
                                    String valueOfObject = getValueOfObject(expr.getArg(0), u, mm, 0);
                                    if (valueOfObject.toLowerCase().toString().contains("create"))
                                        tableInfo.append("[").append(valueOfObject).append("],");
                                }
                            }

                        }
                        tableInfo.append("}");
                        FileNameConstructor.EXTRACTOR_MODE = "DATABASE_NO_FIELD";
                        return tableInfo.toString();
                    }
                }else if(sootClass.getName().startsWith("java.io")){
                    //我们对流进行分析
                    //首先要找到流被初始化的地方
                    Unit initUnit = findStreamInitUnit(method, sootClass, assignStmt.getLeftOp());
                    if(initUnit==null)
                        return "";

                    InvokeStmt invokeStmt = (InvokeStmt) initUnit;
                    String signature = invokeStmt.getInvokeExpr().getMethod().getSignature();
                    if(!FileUsageDefinition.getJavaIOAPIList().contains(signature))
                        return "";
                    if(signature.equals(FileUsageDefinition.FILE_LOAD_1)||signature.equals(FileUsageDefinition.FILE_LOAD_2))
                        return getValueOfObject(invokeStmt.getInvokeExpr().getArg(1),initUnit,method,depth+1);
                    return getValueOfObject(invokeStmt.getInvokeExpr().getArg(0),initUnit,method,depth+1);
                }


            } else if (assignStmt.containsArrayRef()) {
                return "FAILED: Array Element";
            } else {
                //如果a=b的形式
                Value value = directDefUnit.getUseBoxes().get(0).getValue();
                Value rightOp = assignStmt.getRightOp();
                List<ValueBox> useBoxes = rightOp.getUseBoxes();
                if (useBoxes.size() != 0)
                    value = useBoxes.get(0).getValue();
                return getValueOfObject(value, directDefUnit, method, depth + 1);
            }

        }
        return "FAILED: OtherException";
    }

    public Unit findStreamInitUnit(SootMethod curMethod,SootClass cls,Value value){
        //找到流的初始化语句
        for(Unit u:curMethod.retrieveActiveBody().getUnits()){
            if(!(u instanceof InvokeStmt))
                continue;
            InvokeStmt invokeStmt = (InvokeStmt) u;
            InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
            if(!(invokeExpr instanceof SpecialInvokeExpr))
                continue;
            SpecialInvokeExpr specialInvokeExpr = (SpecialInvokeExpr) invokeExpr;
            Value base = specialInvokeExpr.getBase();
            if(!base.equals(value))
                continue;
            String name = specialInvokeExpr.getMethod().getName();
            if(!name.equals("<init>"))
                continue;
            boolean equals = specialInvokeExpr.getMethod().getDeclaringClass().getName().equals(cls.getName());
            if(!equals)
                continue;
            return u;
        }
        return null;
    }

    private String recoverInfoFromFormat(Unit directDefUnit, SootMethod method,int depth) {
        //从format方法中获取完整的字符串信息
        AssignStmt assignStmt = (AssignStmt) directDefUnit;
        InvokeExpr invokeExpr = assignStmt.getInvokeExpr();
        String format = invokeExpr.getArg(0).toString();
        Unit defUnit = getDirectDefUnit(invokeExpr.getArg(1), directDefUnit, method);//得到数组的初始化语句: rxx=newarray
        AssignStmt arrayInitUint = (AssignStmt) defUnit;
        HashSet<Path> paths = CfgFactory.getInstance().getPaths(method);
        for (Path path : paths) {
            int i = path.indexOf(arrayInitUint);
            if (i != -1) {
                HashMap<Integer, String> res = new HashMap<>();
                for (; i < path.list.size(); i++) {
                    //我们遍历执行路径得到一条完整的路径信息
                    Unit unit = path.get(i);
                    if (unit instanceof AssignStmt) {
                        AssignStmt assignUnit = (AssignStmt) unit;
                        if (assignUnit.containsArrayRef()) {
                            ArrayRef arrayRef = assignUnit.getArrayRef();
                            if (arrayRef.getBase().toString().equals(invokeExpr.getArg(1).toString())) {
                                Value index = arrayRef.getIndex();//得到索引号
                                Value value = assignUnit.getRightOp();
                                String ref2Str = getValueOfObject(value, assignUnit, method,depth+1);
                                res.put(Integer.getInteger(index.toString()), ref2Str);
                            }
                        }
                    }
                }
                String[] split = format.split("%[scd]");
                StringBuilder sb = new StringBuilder();
                for (int index = 0; index < split.length; index++) {
                    sb.append(split[index]);
                    if (index < split.length - 1) {
                        sb.append(res.get(Integer.getInteger(String.valueOf(index))));
                    }
                }
                return sb.toString().replaceAll("\"", "");
            }
        }
        return "FAILED: Format Construction";
    }

    private String getFieldInfo(SootField field,int depth) {
        //恢复字段信息
//        logger.info("当前要寻找的字段是：{}",field.toString());
        String fieldInfo="[";

        SootClass declaringClass = field.getDeclaringClass();
        if (!field.getTags().isEmpty()) {
            String fieldMap = field.getTags().get(0).toString();
            if (fieldMap.startsWith("ConstantValue:")) {
                fieldInfo=fieldInfo+"("+fieldMap.replaceAll("ConstantValue:", "").trim()+")";
            }
        }

        //对于其他变量，他们初始化得地方太多，我们理论应该收集他们所有assign的地方
        for(SootMethod method:declaringClass.getMethods()){
            if(!method.isConcrete())
                continue;
            for(Unit unit:method.retrieveActiveBody().getUnits()){
                if(unit instanceof AssignStmt){
                    AssignStmt assignStmt = (AssignStmt) unit;
                    Value leftOp = assignStmt.getLeftOp();
                    if(leftOp instanceof FieldRef){
                        FieldRef fieldRef = (FieldRef) leftOp;
                        if(fieldRef.getField().getSignature().equals(field.getSignature())){
                            //如果他们的字段签名是一样的，我们就认为是同一个变量
                            Value rightOp = assignStmt.getRightOp();
//                            logger.info("找到直接定义语句是： {}",unit);
                            String ref2Str=getValueOfObject(rightOp, unit, method, depth);
                            fieldInfo=fieldInfo+"("+ref2Str+")";
                        }
                    }
                }
            }
        }
        fieldInfo+="]";
        return fieldInfo;

    }

    private String recoverInfoFromStringBuilder(Unit unit, SootMethod method,int depth) {
        if (!method.isConcrete())
            return "FAILED: Abstract Method";
        Queue<Unit> queue = new LinkedList<>();
        HashSet<Unit> visit = new HashSet<>();
        BriefUnitGraph graph = new BriefUnitGraph(method.retrieveActiveBody());
        queue.addAll(graph.getPredsOf(unit));
        List<String> res = new ArrayList<>();
        while (!queue.isEmpty()) {
            Unit poll = queue.poll();
            visit.add(poll);
            InvokeExpr invokeExpr = getInvokeExpr(poll);
            if (invokeExpr != null) {
                String signature = invokeExpr.getMethod().getSignature();
                if (signature.equals("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>")) {
                    res.add(getValueOfObject(invokeExpr.getArg(0), poll, method,depth+1));
                } else if (signature.equals("<java.lang.StringBuilder: void <init>(java.lang.String)>")) {
                    res.add(getValueOfObject(invokeExpr.getArg(0), poll, method,depth+1));
                    break;
                } else if (signature.equals("<java.lang.StringBuilder: void <init>()>")) {
                    break;
                }
            }
            for (Unit pre : graph.getPredsOf(poll))
                if (!visit.contains(pre))
                    queue.add(pre);
        }
        Collections.reverse(res);
        StringBuilder ans = new StringBuilder();
        for (String str : res)
            ans.append(str);
        if (ans.length() != 0)
            return ans.toString().replaceAll("\"", "");
        return "FAILED: String Construction";
    }

    @Override
    public boolean caseAnalyze(Unit unit, SootMethod method, List<CallSite> callStack, HashSet<Point> res, ValueBox taintValueBox) {
        return false;
    }

    public String constructStringFromBuild(Unit unit,SootMethod method){
        String scheme="xxxx";
        String authority="xxxx";
        String path="xxxx";
        VirtualInvokeExpr expr = (VirtualInvokeExpr) getInvokeExpr(unit);
        Value value = expr.getBase();
        Unit beginUnit = null;
        for(Unit u:method.retrieveActiveBody().getUnits())
            if(u.toString().contains("<android.net.Uri: android.net.Uri$Builder buildUpon()>")) {
                beginUnit = u;
                break;
            }
        if(beginUnit==null) {
            for (Unit u : method.retrieveActiveBody().getUnits()) {
                if (u instanceof AssignStmt) {
                    InvokeExpr invokeExpr = getInvokeExpr(u);
                    if (invokeExpr instanceof VirtualInvokeExpr) {
                        VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr) invokeExpr;
                        Value base = virtualInvokeExpr.getBase();
                        if (base.equals(value)) {
                            String signature = invokeExpr.getMethod().getSignature();
                            if (signature.equals("<android.net.Uri$Builder: android.net.Uri$Builder scheme(java.lang.String)>")) {
                                Value arg = invokeExpr.getArg(0);
                                scheme = getValueOfObject(arg, u, method, 0);
                            } else if (signature.equals("<android.net.Uri$Builder: android.net.Uri$Builder authority(java.lang.String)>")) {
                                Value arg = invokeExpr.getArg(0);
                                authority = getValueOfObject(arg, u, method, 0);
                            } else if (signature.equals("<android.net.Uri$Builder: android.net.Uri$Builder path(java.lang.String)>")) {
                                Value arg = invokeExpr.getArg(0);
                                path = getValueOfObject(arg, u, method, 0);
                            }
                        }
                    }

                }
            }
            return scheme+"[(://)]"+authority+path;

        }else {
            List<List<Unit>> paths=new ArrayList<>();
            CallGraphUtils.getPathBackWard(unit,beginUnit,0,paths,new BriefUnitGraph(method.retrieveActiveBody()),new ArrayList<>());
            for(List<Unit> p:paths){
                if(p.contains(beginUnit)){
                    Collections.reverse(p);
                    String ans = null;
                    for(Unit stmt:p){
                        InvokeExpr invokeExpr = getInvokeExpr(stmt);
                        if(invokeExpr==null)
                            continue;
                        String signature = invokeExpr.getMethod().getSignature();
                        if(signature.equals("<android.net.Uri: android.net.Uri$Builder buildUpon()>")){
                            Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
                            String str = getRef2Str(base, stmt, method, 0);
                            ans="["+str+"]";
                        }else if(signature.equals("<android.net.Uri$Builder: android.net.Uri$Builder appendPath(java.lang.String)>")){
                            Value arg = invokeExpr.getArg(0);
                            String str = getRef2Str(arg, stmt, method, 0);
                            ans=ans+"[(/)]"+"["+str+"]";
                        }
                    }
                    return ans;
                }
            }
        }
        return "[()]";
    }


    public static HashSet<Unit> getAllDirectUnit(Value value,Unit curUnit,SootMethod method){
        //找到指定标量的所有直接赋值语句
        HashSet<Unit> res=new HashSet<>();
        BriefUnitGraph graph = new BriefUnitGraph(method.retrieveActiveBody());
        Queue<Unit> queue=new LinkedList<>();
        queue.add(curUnit);
        HashSet<Unit> visited=new HashSet<>();
        while (!queue.isEmpty()){
            Unit poll = queue.poll();
            visited.add(poll);
            for(Unit preUnit:graph.getPredsOf(poll)){
                if(visited.contains(preUnit))
                    continue;
                if(isValueDefinedInUnit(value,preUnit)){
                    res.add(preUnit);
                    continue;
                }
                queue.add(preUnit);
            }
        }
        return res;
    }

    public static boolean isValueDefinedInUnit(Value value,Unit u){
        if (u instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) u;
            if(assignStmt.getLeftOp().equals(value))
                return true;
        }
        if(u instanceof IdentityStmt){
            IdentityStmt identityStmt = (IdentityStmt) u;
            if(identityStmt.getLeftOp().equals(value))
                return true;
        }
        return false;

    }


}
