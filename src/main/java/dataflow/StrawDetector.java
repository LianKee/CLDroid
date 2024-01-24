package dataflow;

import constant.StrawPointsDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import util.StringUtil;

import java.io.File;
import java.io.FileWriter;
import java.util.*;

public class StrawDetector {
    //本类主要为检测程序代码中的资源耗尽漏洞

    private static final Logger logger = LoggerFactory.getLogger(StrawDetector.class);

    public static final String FILE = "FILE";
    public static final String DATA_BASE = "DATA_BASE";
    public static final String COLLECTIONS = "COLLECTIONS";
    public static final String SHARED_PREFERENCES = "SHARED_PREFERENCE";
    public static final String NOT = "NOT";

    private String entryPoint;

    public static FileNameConstructor nameConstructor=new FileNameConstructor();

    public void setEntryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
    }

    private HashMap<String, HashSet<Point>> res = new HashMap<>();


    private String getStrawPointType(Unit unit,ValueBox taintValueBox) {
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

        if(invokeExpr==null)
            return NOT;
        int index = invokeExpr.getArgs().indexOf(taintValueBox.getValue());
        if(index==-1)
            return NOT;
        String signature = invokeExpr.getMethod().getSignature();
        if (index==0&&StrawPointsDefinition.getFileWriteMethodList().contains(signature))
            return FILE;
        if (StrawPointsDefinition.getDatabaseInsertMethodList().contains(signature))
            return DATA_BASE;
        if (StrawPointsDefinition.getSharedPreferencesWriteMethodList().contains(signature))
            return SHARED_PREFERENCES;
        if (index==0&&StringUtil.isMatch(signature, StrawPointsDefinition.COLLECTIONS_STRAWPOINT_REGEX))
            return COLLECTIONS;
        return NOT;
    }


    private BackwardDataFlow backwardForObjectType = new BackwardDataFlow(new Analyze() {
        @Override
        public boolean caseAnalyze(Unit unit, SootMethod method, List<CallSite> callStack, HashSet<Point> res,ValueBox taintValueBox) {
            if (unit instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) unit;
                if (assignStmt.containsFieldRef()) {
                    if (assignStmt.getFieldRef().getField().isStatic()) {
                        logger.info("the unit: {} in method: {} tell this a static field", unit, method.getSignature());
                        return true;
                    } else {
                        //对于实例字段还需要进一步判断，这里我们仅认为属于service或者单例类的字段是长生命周期的
                        logger.info("the unit: {} in method: {} is a instance field", unit, method.getSignature());
                        return isInstanceFiledLongLife(unit);

                    }
                } else if (assignStmt.getLeftOp() instanceof NewExpr) {
                    logger.info("unit: {}, this a local...", unit);
                }
            } else if (unit instanceof IdentityStmt) {
                logger.info("the unit: {} is a Identity stmt", unit);
            }
            return false;
        }

        public boolean isInstanceFiledLongLife(Unit u){
            AssignStmt assignStmt = (AssignStmt) u;
            SootClass declaringClass = assignStmt.getFieldRef().getField().getDeclaringClass();
            while (declaringClass.hasSuperclass()){//判断是不是服务的字段
                declaringClass=declaringClass.getSuperclass();
                if(declaringClass.getName().equals("android.app.Service"))
                    return true;
            }

            boolean flag=true;
            for(SootMethod method:declaringClass.getMethods()){
                if(method.getName().equals("<init>")&&method.isPublic()){
                    flag=false;
                }
            }
            return flag;
        }
    });

    private FileNameConstructor findFileName = new FileNameConstructor();

    private ForwardDataFlow forwardForStrawDetect = new ForwardDataFlow(new Analyze() {

        @Override
        public boolean caseAnalyze(Unit unit, SootMethod method, List<CallSite> callStack, HashSet<Point> points,ValueBox taintValueBox) {

            if (points != null) {
                if (!res.containsKey(entryPoint))
                    res.put(entryPoint, new HashSet<>());
                res.get(entryPoint).addAll(points);

            } else {
                String type = getStrawPointType(unit,taintValueBox);
                boolean flag=false;
                switch (type) {
                    case COLLECTIONS:
                        flag=handleForCollections(unit, method, callStack);
                        break;
                    case SHARED_PREFERENCES:
                        flag=handleForSharePreferences(unit,method,callStack,taintValueBox);
                        break;
                    case FILE:
                        flag=handleForFile(unit, method, callStack);
                        break;
                    case DATA_BASE:
                        flag=handleForDataBase(unit,method,callStack);
                        break;
                    default:
                }

                if(flag){
                    ForwardDataFlow.addCheckedInfo2MethodParam2StrawPoint(callStack,getAnalysisResultOfEntry(entryPoint));
                }
            }
            return true;
        }

        public boolean handleForSharePreferences(Unit unit,SootMethod method,List<CallSite> callStack,ValueBox taintValueBox){
            //需要判断可以影响的这个Preference是影响的Key,还是Value
            InvokeExpr invokeExpr = AbstractDataFlow.getInvokeExpr(unit);
            if(invokeExpr==null)
                return false;
            int index = invokeExpr.getArgs().indexOf(taintValueBox.getValue());
            if(index==-1)
                return false;
//            List<ValueBox> useBoxes = invokeExpr.getUseBoxes();
//            String preferencesName = nameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), unit, method, callStack,0);
            String mark=index==0?"true":"false";
            String msg=String.format("Affect Key: %s",mark);
            return handleInsertActions(unit, method, "SHARED_PREFERENCES",msg);
        }

        public boolean handleForDataBase(Unit unit,SootMethod method,List<CallSite> callStack){
            InvokeExpr invokeExpr = AbstractDataFlow.getInvokeExpr(unit);
            if(invokeExpr==null)
                return false;

//            //获取数据库的名字
//            List<ValueBox> useBoxes = invokeExpr.getUseBoxes();
//            String dataBaseName = nameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), unit, method, callStack,0);
//            //获取表的名字
//            String tableName="";
//            if(invokeExpr.getMethod().getSignature().equals(StrawPointsDefinition.DATABASE_EXECSQL)){
//                String sqlStmt = getValueOfObject(invokeExpr.getArg(0), unit, method, callStack);
//                if(sqlStmt!=null){
//                    String[] s = sqlStmt.toLowerCase().trim().split(" ");
//                    if(s[0].contains("update")&&s.length>1){
//                        tableName=s[1];
//                    }else if(s[0].length()>2){
//                        tableName=s[2];
//                    }
//                }
//            }else {
//                tableName = getValueOfObject(invokeExpr.getArg(0), unit, method, callStack);
//            }
//            String otherMsg=String.format("DataBase: %s\nTable: %s",dataBaseName,tableName);
            return handleInsertActions(unit, method, "DATA_BASE","");
        }

        public boolean handleForCollections(Unit unit, SootMethod method, List<CallSite> callStack) {
            InvokeExpr invokeExpr = null;
            if (unit instanceof InvokeStmt) {
                invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
            } else if (unit instanceof AssignStmt) {
                invokeExpr = ((AssignStmt) unit).getInvokeExpr();
            }
            assert invokeExpr != null;
            ValueBox refValueBox = invokeExpr.getUseBoxes().get(invokeExpr.getUseBoxes().size() - 1);

            //开启检测
            if (!backwardForObjectType.getFlag())
                backwardForObjectType.startBackward();

            backwardForObjectType.run(method, unit, refValueBox, 0, callStack);
            //判断检测结果
            if (backwardForObjectType.getFlag()) {
                logger.info("unit: {} is not a real straw point", unit);
                return false;
            } else {
                addPoint2Res(unit, method, "COLLECTION","");
                logger.info("STRAW_POINT! unit:{} in method:{}", unit, method.getSignature());
                return true;
            }
        }

        public boolean handleInsertActions(Unit unit, SootMethod method, String type,String otherMsg) {
            addPoint2Res(unit, method, type,otherMsg);
            logger.info("STRAW_POINT! unit:{} in method:{}", unit, method.getSignature());
            return true;

        }

        public boolean handleForFile(Unit unit, SootMethod method, List<CallSite> callStack) {
            //这里是不完备的
            addPoint2Res(unit, method, "FILE","");
            return true;
        }

        private String getValueOfObject(Value value, Unit u, SootMethod method, List<CallSite> callChain) {
            if (value instanceof Local)
                return nameConstructor.getRef2Str(value, u, method,0);
            return value.toString();
        }

    });

    public void setTaintWrapper(HashSet<String> taintWrapper){
        forwardForStrawDetect.setTaintWrapper(taintWrapper);
    }


    public void run(SootMethod method, Unit unit, ValueBox valueBox, int index, int depth, List<CallSite> callStack) {
        forwardForStrawDetect.run(method, unit, valueBox, index, depth, callStack);
    }

    public void addPoint2Res(Unit unit, SootMethod method, String type,String otherMsg) {
        if (!res.containsKey(entryPoint)) {
            res.put(entryPoint, new HashSet<Point>());
        }
        Point point = new Point(unit, method, type,otherMsg);
        res.get(entryPoint).add(point);
    }

    public HashSet<Point> getAnalysisResultOfEntry(String entryPoint){
        if(res.containsKey(entryPoint))
            return res.get(entryPoint);
        return null;
    }

    public static void recordCallSite(List<CallSite> callStack){
        File file = new File("CallStack.log");
        try {
            FileWriter fileWriter = new FileWriter(file, true);
            if(!callStack.isEmpty()) {
                fileWriter.write(callStack.toString() + "\n");
            }else {
                fileWriter.write("调用栈为空");
            }
            fileWriter.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    public void setMaxDepth(int forward_maxDepth,int backward_maxDepth){
        forwardForStrawDetect.setMAX_DEPTH(forward_maxDepth);
        backwardForObjectType.setMAX_DEPTH(backward_maxDepth);
    }
}
