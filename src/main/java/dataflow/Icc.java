package dataflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.BriefUnitGraph;

import java.util.*;


public class Icc {
    /*
    model组件间通信,目的是给定一条icc指令，如startActivity,我们的目的是，跳转到哪一个组件
     */

    public static final Logger logger= LoggerFactory.getLogger(Icc.class);


    //过程间分析Intent的来源

    private JimpleBasedInterproceduralCFG icfg=null;

    public static final String STRAT_ACTIVITY="void startActivity(android.content.Intent)";
    public static final String STRAT_ACTIVITY_IF_NEED="boolean startActivityIfNeeded(android.content.Intent,int)";

    public static final String START_ACTIVITY_FRAGMENT_0="void startActivityFromFragment(androidx.fragment.app.Fragment,android.content.Intent,int)";
    public static final String START_ACTIVITY_FRAGMENT_1="void startActivityFromFragment(androidx.fragment.app.Fragment,android.content.Intent,int,android.os.Bundle)";

    public static final String START_ACTIVITY_FOR_RESULT="void startActivityForResult(android.content.Intent,int,android.os.Bundle)";

    public static final String START_SERVICE="android.content.ComponentName startService(android.content.Intent)";

    public static final String SEND_BROADCAST_0="void sendBroadcast(android.content.Intent)";
    public static final String SEND_BROADCAST_1="void sendBroadcast(android.content.Intent,java.lang.String)";

    public static final String[] iccAPI={STRAT_ACTIVITY,STRAT_ACTIVITY_IF_NEED,START_ACTIVITY_FRAGMENT_0,START_ACTIVITY_FRAGMENT_1,START_ACTIVITY_FOR_RESULT,
    START_SERVICE,SEND_BROADCAST_0,SEND_BROADCAST_1};

    public static final List<String> iccAPIList= Arrays.asList(iccAPI);

    private static final String GET_INTENT = "android.content.Intent getIntent()";


    public static final String INTENT_INIT_0="<android.content.Intent: void <init>()>";
    public static final String INTENT_INIT_1="<android.content.Intent: void <init>(java.lang.String)>";
    public static final String INTENT_INIT_2="<android.content.Intent: void <init>(java.lang.String,android.net.Uri)>";
    public static final String INTENT_INIT_3="<android.content.Intent: void <init>(android.content.Intent)>";
    public static final String INTENT_INIT_4="<android.content.Intent: void <init>(android.content.Context,java.lang.Class)>";
    public static final String INTENT_INIT_5="<android.content.Intent: void <init>(java.lang.String,android.net.Uri,android.content.Context,java.lang.Class)>";



    public static final String COMPONENT_INIT_0="<android.content.ComponentName: void <init>(java.lang.String,java.lang.String)>";
    public static final String COMPONENT_INIT_1="<android.content.ComponentName: void <init>(android.content.Context,java.lang.Class)>";

    public static final String INTENT_SET_1="<android.content.Intent: android.content.Intent setComponent(android.content.ComponentName)>";
    public static final String INTENT_SET_2="<android.content.Intent: android.content.Intent setClassName(java.lang.String,java.lang.String)>";
    public static final String INTENT_SET_3="<android.content.Intent: android.content.Intent setClassName(android.content.Context,java.lang.String)>";
    public static final String INTENT_SET_4="<android.content.Intent: android.content.Intent setClass(android.content.Context,java.lang.Class)>";

    public static final String[] INTENT_CONSTRUCT_API={INTENT_INIT_4,INTENT_INIT_5,INTENT_SET_1,INTENT_SET_2,INTENT_SET_3,INTENT_SET_4};

    public static final List<String> INTENT_CONSTRUCT_API_LIST=Arrays.asList(INTENT_CONSTRUCT_API);


    public Icc(JimpleBasedInterproceduralCFG icfg){
        this.icfg=icfg;
    }

    public HashSet<String> getJumpComponentInfo(Unit jumpUnit){
        InvokeExpr invokeExpr = getInvokeExpr(jumpUnit);
        //我们需要确定是那种类型的组件间通信
        String subSignature = invokeExpr.getMethod().getSubSignature();
        if(iccAPIList.contains(subSignature)){
            if(START_ACTIVITY_FRAGMENT_1.equals(subSignature)||START_ACTIVITY_FRAGMENT_0.equals(subSignature)){
                return getComponentInfo(jumpUnit,invokeExpr.getArg(1));
            }else {
                return getComponentInfo(jumpUnit,invokeExpr.getArg(0));
            }
        }else {
            logger.warn("This is not an ICC API Invoke!");
        }

        return null;
    }

    public HashSet<String> getComponentInfo(Unit curUnit, Value curValue){

        HashSet<String> res=new HashSet<>();
        //该方法返回找到的Intent的

        HashSet<Unit> intentInitUnit = getIntentInitUnit(curUnit, curValue, 0);
        if(intentInitUnit.isEmpty()){
            logger.info("Can't find the intent Created");
            return res;
        }

        //为所有的new出来的Intent,寻找他们的初始化方法

        for(Unit initUnit:intentInitUnit){
            res.addAll(getJumpClassName(initUnit));
        }

        return res;

    }

    public HashSet<String> getJumpClassName(Unit initUnit){
        HashSet<String> res=new HashSet<>();
        Value leftOp = ((AssignStmt) initUnit).getLeftOp();
        //我们要找到intent所有可能指定它要跳转的组件的信息地方
        SootMethod curMethod = icfg.getMethodOf(initUnit);
        for(Unit u:curMethod.retrieveActiveBody().getUnits()){
            if(u instanceof InvokeStmt){
                InvokeExpr invokeExpr = ((InvokeStmt) u).getInvokeExpr();
                String signature = invokeExpr.getMethod().getSignature();
                if((invokeExpr instanceof InstanceInvokeExpr)&&INTENT_CONSTRUCT_API_LIST.contains(signature)
                        &&((InstanceInvokeExpr)invokeExpr).getBase().equals(leftOp)){
                    if(signature.equals(INTENT_INIT_4)||signature.equals(INTENT_SET_4)){
                        Value classValue = invokeExpr.getArg(1);
                        if(classValue instanceof ClassConstant){
                            String className = ((ClassConstant) classValue).getValue();
                            String s = className.substring(1,className.length()-1).replaceAll("/", ".");
                            res.add(s);
                        }
                    }else if(signature.equals(INTENT_INIT_5)){
                        Value classValue = invokeExpr.getArg(3);
                        if(classValue instanceof ClassConstant){
                            String className = ((ClassConstant) classValue).getValue();
                            String s = className.substring(1,className.length()-1).replaceAll("/", ".");
                            res.add(s);
                        }
                    }else if(signature.equals(INTENT_SET_2)||signature.equals(INTENT_SET_3)){
                        Value classValue = invokeExpr.getArg(1);
                        if(classValue instanceof StringConstant){
                            res.add(classValue.toString().replaceAll("\"",""));
                        }

                    }else {
                        Value componentValue = invokeExpr.getArg(0);
                        //我们假定component是在本方法中创建的
                        //因此这里可能存在漏报
                        res.addAll(getComponentInitInfo(componentValue,curMethod));

                    }
                }
            }
        }
        return res;
    }

    private HashSet<String > getComponentInitInfo(Value component,SootMethod method){
        HashSet<String> res=new HashSet<>();
        for(Unit u:method.retrieveActiveBody().getUnits()){
            if(u instanceof InvokeStmt){
                InvokeStmt invokeStmt = (InvokeStmt) u;
                InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                String signature = invokeExpr.getMethod().getSignature();
                if(!(invokeExpr instanceof InstanceInvokeExpr))
                    continue;
                Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
                if(!base.equals(component))
                    continue;
                if(signature.equals(COMPONENT_INIT_0)){
                    Value classValue = invokeExpr.getArg(1);
                    if(classValue instanceof StringConstant){
                        res.add(classValue.toString().replaceAll("\"",""));
                    }
                }else if(signature.equals(COMPONENT_INIT_1)){
                    Value classValue = invokeExpr.getArg(1);
                    if(classValue instanceof ClassConstant){
                        String className = ((ClassConstant) classValue).getValue();
                        String s = className.substring(1,className.length()-1).replaceAll("/", ".");
                        res.add(s);
                    }
                }
            }
        }
        return res;
    }



    //该方法是去寻找所有intent被new的初始位置
    //Intent这个对象可能来自参数，可能来自返回值，也可能来自new
    //但是我们这里认为最终所有的Intent都是来自new出来的，因此我们去追踪他们new出来的地方
    public HashSet<Unit> getIntentInitUnit(Unit curUnit,Value value,int depth){
        HashSet<Unit> res=new HashSet<>();

        if(depth>10){
            logger.info("Exceed the max depth");
            return res;
        }
        SootMethod curMethod = icfg.getMethodOf(curUnit);

        HashSet<Unit> defUnits = getAllDirectDefUnit(value, curUnit, curMethod);
        if(defUnits.isEmpty()){
            logger.info("Can't find definition for {}",value);
            return res;
        }

        for(Unit defUnit:defUnits){
            //找到所有的定义语句，
            if(defUnit instanceof IdentityStmt){
                //如果是参数的话，我们直接找他的调用者
                Collection<Unit> callers = icfg.getCallersOf(curMethod);
                if(callers.isEmpty()){
                    logger.info("Can't find caller for {}",curMethod.getSignature());
                    continue;
                }
                Value rightOp = ((IdentityStmt) defUnit).getRightOp();
                if (rightOp instanceof ThisRef)
                    continue;
                ParameterRef parameterRef = (ParameterRef) rightOp;
                int index = parameterRef.getIndex();
                for(Unit callSite:callers){
                    //找到调用点对应的实参
                    InvokeExpr invokeExpr = getInvokeExpr(callSite);
                    res.addAll(getIntentInitUnit(callSite,invokeExpr.getArg(index),depth+1));
                }
            }else{
                //如果是来自返回值，new,或者变量赋值
                AssignStmt assignStmt = (AssignStmt) defUnit;
                Value rightOp = assignStmt.getRightOp();
                if(rightOp instanceof NewExpr){
                    //如果是new出来的记下来
                    res.add(defUnit);
                }else if(rightOp instanceof InvokeExpr){
                    //如果是调用返回值
                    Collection<SootMethod> callees = icfg.getCalleesOfCallAt(defUnit);
                    for(SootMethod callee:callees){
                        String subSignature = callee.getSubSignature();
                        if(subSignature.equals(GET_INTENT)) {
                            logger.info("Intent is get by ICC");
                            continue;
                        }
                        if(isSystemClass(callee.getDeclaringClass().getName())){
                            //判断是不是系统类
                            logger.info("Intent is get by android api: {}",callee.getSignature());
                            continue;
                        }

                        if(!callee.isConcrete()||callee.isPhantom())
                            continue;
                        for(Unit u:callee.retrieveActiveBody().getUnits()){
                            if(u instanceof ReturnStmt){
                                ReturnStmt returnStmt = (ReturnStmt) u;
                                Value op = returnStmt.getOp();
                                if(op instanceof NullConstant)
                                    continue;
                                res.addAll(getIntentInitUnit(returnStmt,op,depth+1));
                            }
                        }
                    }
                }else if(rightOp instanceof Local){
                    //如果是局部变量赋值
                    res.addAll(getIntentInitUnit(defUnit,rightOp,depth+1));
                }

            }

        }




        return res;

    }


    public static HashSet<Unit> getAllDirectDefUnit(Value value,Unit curUnit,SootMethod method){
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



    private InvokeExpr getInvokeExpr(Unit u){
        if(u instanceof InvokeStmt){
            InvokeStmt invokeStmt = (InvokeStmt) u;
            return invokeStmt.getInvokeExpr();
        }
        if(u instanceof AssignStmt){
            AssignStmt assignStmt = (AssignStmt) u;
            if(assignStmt.containsInvokeExpr())
                return assignStmt.getInvokeExpr();
        }
        return null;
    }

    public static boolean isSystemClass(String clsName) {
        if (clsName.startsWith("java.") || clsName.startsWith("javax."))
            return true;
        if (clsName.startsWith("android.") || clsName.startsWith("androidx.") || clsName.startsWith("com.google."))
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
