package dataflow;

import constant.FileUsageDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.InvokeExpr;
import util.Log;

import java.io.File;
import java.util.*;


public class FileUseDetector extends AbstractDataFlow {

    public static final Logger logger = LoggerFactory.getLogger(FileUseDetector.class);

    enum OpType {
        SHARED_PREFERENCES_READ, SHARED_PREFERENCES_WRITE, DATA_BASE_QUERY, CONTENT_RESOLVER_OP, FILE_INPUT_STREAM_INIT, FILE_OUTPUT_STREAM_INIT, NULL
    }

    private HashSet<FileInfo> fileInfos = new HashSet<>();

    private String mode;
    private String type;
    private String name;
    private String entry;

    private String packageName;

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public boolean caseAnalyze(Unit unit, SootMethod method, List<CallSite> callStack, HashSet<Point> res, ValueBox taintValueBox) {
        InvokeExpr invokeExpr = getInvokeExpr(unit);
        if (invokeExpr == null)
            return false;
        SootMethod m = invokeExpr.getMethod();
        cleanFileInfo();
        switch (getType(m)) {
            case FILE_INPUT_STREAM_INIT:
                fileHandler(unit, method, callStack, 0);
                break;
            case FILE_OUTPUT_STREAM_INIT:
                fileHandler(unit, method, callStack, 1);
                break;
            case SHARED_PREFERENCES_READ:
                sharedPreferencesHandler(unit, method, callStack, 0);
                break;
            case SHARED_PREFERENCES_WRITE:
                sharedPreferencesHandler(unit, method, callStack, 1);
                break;
            case DATA_BASE_QUERY:
                databaseHandler(unit, method, callStack);
                break;
            case CONTENT_RESOLVER_OP:
                contentResolverHandler(unit, method, callStack);
                break;
            default:
                break;
        }
        if (callStack.size() == 0) {
            this.entry = method.getSignature();
        } else {
            this.entry = callStack.get(0).caller.getSignature();
        }
        addFileInfo();
        return false;
    }

    private void cleanFileInfo() {
        this.type = null;
        this.name = null;
        this.mode = null;
        this.entry = null;
    }

    private void addFileInfo() {
        if (this.name == null)
            return;
        if (this.name.contains("package")) {
            this.name=this.name.replaceAll("package", this.packageName);
        }
        this.name=this.name.replaceAll("\"","");

        fileInfos.add(new FileInfo(type, name, mode, entry));
    }

    public void fileHandler(Unit u, SootMethod method, List<CallSite> callChain, int mode) {
//        logger.info("检测到在{} 使用了文件", u);
        InvokeExpr invokeExpr = getInvokeExpr(u);
        if (invokeExpr == null)
            return;
        String fileName;
        fileName = getValueOfObject(invokeExpr.getArg(0), u, method, callChain);
        if (fileName.contains("FAILED"))
            return;
        this.name = fileName;
        if (mode == 0) {
            this.mode = "READ";
        } else {
            this.mode = "WRITE";
        }
        this.type = "FILE";
//        logger.info("恢复的文件名是：" + fileName);
    }

    public void sharedPreferencesHandler(Unit u, SootMethod method, List<CallSite> callChain, int mode) {
        InvokeExpr invokeExpr = getInvokeExpr(u);
        if (invokeExpr == null)
            return;
        List<ValueBox> useBoxes = invokeExpr.getUseBoxes();
        this.type = "SAHREDPREFERENCES";
        String preferenceName = null;
        if (mode == 0) {
            this.mode = "READ";
            preferenceName = nameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), u, method,0);
        } else {
            this.mode = "WRITE";
            preferenceName = nameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), u, method,0);
        }
        if (preferenceName != null && preferenceName.contains("FAILED"))
            return;
        this.name = preferenceName;
    }

    public void databaseHandler(Unit u, SootMethod method, List<CallSite> callChain) {
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
                return;
            String[] s = sqlStmt.trim().split(" ");
            if (!s[0].toLowerCase().equals("select") || s.length < 3)
                return;
            tableName = s[3];
        }
        List<ValueBox> useBoxes = invokeExpr.getUseBoxes();
        String dataBaseName = nameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), u, method,0);
        if (tableName == null || dataBaseName == null)
            return;

        if (dataBaseName.contains("FAILED") || tableName.contains("FAILED"))
            return;
        this.name = String.format("DataBase: %s, Table: %s", dataBaseName, tableName);
        this.mode = "Read";
        this.type = "DATABASE";
//        logger.info("数据库：" + dataBaseName + " " + tableName);
    }

    public void contentResolverHandler(Unit u, SootMethod method, List<CallSite> callChain) {
//        logger.info("检测到ContentResolver查询语句：" + u);
        InvokeExpr invokeExpr = getInvokeExpr(u);
        if (invokeExpr == null)
            return;
        Value arg = invokeExpr.getArg(0);
        String uri = getValueOfObject(arg, u, method, callChain);

        if (uri == null)
            return;
        if (uri.contains("FAILED"))
            return;
        this.name = uri;
        this.type = "CONTENT_RESOLVER";
        this.mode = "READ";
//        logger.info("Uri信息是：" + uri);
    }


    private final FileNameConstructor nameConstructor = new FileNameConstructor();

    private String getValueOfObject(Value value, Unit u, SootMethod method, List<CallSite> callChain) {
        if (value instanceof Local)
            return nameConstructor.getRef2Str(value, u, method,0);
        return value.toString();
    }

    //判断调用语句的类型
    public static OpType getType(SootMethod method) {
        if (FileUsageDefinition.getSharedPreferencesReadAPIList().contains(method.getSignature()))
            return OpType.SHARED_PREFERENCES_READ;
        if (FileUsageDefinition.getSharedPreferencesWriteAPIList().contains(method.getSubSignature()))
            return OpType.SHARED_PREFERENCES_WRITE;
        if (FileUsageDefinition.getDataBaseQueryAPIList().contains(method.getSubSignature()))
            return OpType.DATA_BASE_QUERY;
        if (FileUsageDefinition.getContentResolverQueryAPIList().contains(method.getSubSignature()))
            return OpType.CONTENT_RESOLVER_OP;
        if (FileUsageDefinition.getInputStreamConstructorList().contains(method.getSignature()))
            return OpType.FILE_INPUT_STREAM_INIT;
        if (FileUsageDefinition.getOutputStreamConstructorList().contains(method.getSignature()))
            return OpType.FILE_OUTPUT_STREAM_INIT;
        return OpType.NULL;
    }

    public void inter_forward(SootMethod method, int depth, List<CallSite> callStack) {
        if (depth > MAX_DEPTH)
            return;
        if (isSystemClass(method.getDeclaringClass()))
            return;
        if (isMethodCalled(callStack, method))
            return;
        if (!method.isConcrete())
            return;
        for (Unit u : method.retrieveActiveBody().getUnits()) {
            InvokeExpr invokeExpr = getInvokeExpr(u);
            if (invokeExpr != null) {
                caseAnalyze(u, method, callStack, null, null);
                for (SootMethod m : getMethodFromCG(u)) {
                    List<CallSite> temp_call_stack = new ArrayList<>(callStack);
                    temp_call_stack.add(new CallSite(method, u, -1));
                    inter_forward(m, depth + 1, temp_call_stack);
                }
            }
        }
    }



    public void writeLog() {
        for (FileInfo fileInfo : fileInfos) {
            Log.write(Log.Mode.FILE_INFO, fileInfo.type, fileInfo.name, fileInfo.mode, fileInfo.entry);
        }
        fileInfos.clear();
    }
}
