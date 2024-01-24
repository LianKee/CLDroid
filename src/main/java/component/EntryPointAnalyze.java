package component;

import constant.EntryPointsDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.manifest.binary.AbstractBinaryAndroidComponent;
import soot.toolkits.graph.BriefUnitGraph;

import java.util.*;


public class EntryPointAnalyze {

    private static final Logger logger = LoggerFactory.getLogger(EntryPointAnalyze.class);

    public static HashMap<String, HashSet<SootMethod>> getComponent2MapEntryPoint(HashSet<AbstractBinaryAndroidComponent> components) {
        HashMap<String, HashSet<SootMethod>> res = new HashMap<>();
        for (AbstractBinaryAndroidComponent component : components) {
            HashSet<SootMethod> entryPoints = new HashSet<>();
            String componentType = component.getAXmlNode().getTag();
            String componentName = component.getNameString();
            if (componentName == null)
                componentName = "null_component";
            if (componentName.startsWith(".")) {
                logger.warn("TODO: handle component name concat:" + componentName);
            } else {
                switch (componentType) {
                    case "activity":
                        entryPoints.addAll(getLifeCycleMethodByComponent(componentName, EntryPointsDefinition.getActivityLifecycleMethods()));
                        break;
                    case "service":
                        entryPoints.addAll(getLifeCycleMethodByComponent(componentName, EntryPointsDefinition.getServiceLifecycleMethods()));
                        entryPoints.addAll(getEntryPointByOnBindForService(componentName));
                        break;
                    case "receiver":
                        entryPoints.addAll(getLifeCycleMethodByComponent(componentName, EntryPointsDefinition.getBroadcastLifecycleMethods()));
                        break;
                    case "provider":
                        entryPoints.addAll(getLifeCycleMethodByComponent(componentName, EntryPointsDefinition.getContentproviderLifecycleMethods()));
                        break;
                    default:
                        logger.warn("{} is an unsupport component type", componentType);
                }
            }
            res.put(componentName, entryPoints);
        }
        return res;
    }

    private static SootMethod findMethodBySubSignature(SootClass currentClass, String subsignature) {
        if (currentClass.getName().startsWith("android.") || currentClass.getName().startsWith("androidx"))
            return null;

        SootMethod m = currentClass.getMethodUnsafe(subsignature);

        if (m != null) {
            return m;
        }
        if (currentClass.hasSuperclass()) {
            return findMethodBySubSignature(currentClass.getSuperclass(), subsignature);
        }
        return null;
    }

    private static SootMethod findMethodByName(SootClass currentClass, String methodName) {
        if (currentClass.getName().startsWith("android.") || currentClass.getName().startsWith("androidx"))
            return null;

        SootMethod m = currentClass.getMethodByNameUnsafe(methodName);
        if (m != null) {
            return m;
        }
        if (currentClass.hasSuperclass()) {
            return findMethodByName(currentClass.getSuperclass(), methodName);
        }
        return null;
    }


    public static HashSet<SootMethod> getLifeCycleMethodByComponent(String componentName, List<String> lifeCycleMethods) {
        HashSet<SootMethod> res = new HashSet<>();
        SootClass currentClass = Scene.v().getSootClassUnsafe(componentName);
        if (currentClass == null) {
            logger.warn("Can't find the {} class", componentName);
        } else {
            for (String method : lifeCycleMethods) {
                SootMethod lifeCycleMenthod = findMethodBySubSignature(currentClass, method);
                if (lifeCycleMenthod != null)
                    res.add(lifeCycleMenthod);
            }
        }
        return res;
    }

    public static HashSet<SootMethod> getEntryPointByOnBindForService(String componentName) {
        HashSet<SootMethod> res = new HashSet<>();
        SootClass currentClass = Scene.v().getSootClass(componentName);
        if (currentClass == null) {
            logger.warn("Can't find the {} class", componentName);
        } else {
            SootMethod method = findMethodBySubSignature(currentClass, EntryPointsDefinition.SERVICE_ONBIND);
            if (method == null) {
                logger.info("onBind is not override by app developer");
            } else {

                HashSet<String> returnValueType = getReturnValueType(method);
                if (returnValueType.size() == 0) {
                    logger.info("onBind return null");
                } else {
                    for (String type : returnValueType) {
                        if (type.startsWith("STUB_")) {
                            String stubClassName = type.substring(5);
                            res.addAll(getStubPublicMethod(stubClassName));
                        } else {
                            String handlerClassName = type.substring(8);
                            res.addAll(getHandlerPublicMethod(handlerClassName));
                        }
                    }
                }

            }

        }
        return res;
    }

    private static HashSet<SootMethod> getHandlerPublicMethod(String className) {
        HashSet<SootMethod> res = new HashSet<>();
        SootClass handlerClass = Scene.v().getSootClass(className);
        SootMethod onHandleMessage = handlerClass.getMethodByNameUnsafe("handleMessage");
        res.add(onHandleMessage);
        return res;
    }

    private static HashSet<SootMethod> getStubPublicMethod(String className) {
        HashSet<SootMethod> res = new HashSet<>();
        SootClass currentClass = Scene.v().getSootClass(className);
        //寻找Stub类
        while (currentClass.hasSuperclass() && !currentClass.getSuperclass().getName().equals("android.os.Binder")) {
            currentClass = currentClass.getSuperclass();
        }

        //过滤掉只继承了Binder类的一些service
        if (currentClass.getInterfaceCount() != 1)
            return res;
        if (!currentClass.getInterfaces().getFirst().implementsInterface("android.os.IInterface"))
            return res;

        HashSet<String> publicMethodName = new HashSet<>();
        for (SootField sootField : currentClass.getFields()) {
            if (sootField.getName().startsWith("TRANSACTION")) {
                publicMethodName.add(sootField.getName().substring(12));
            }
        }

        currentClass = Scene.v().getSootClass(className);
        for (String methodName : publicMethodName) {
            SootMethod method = findMethodByName(currentClass, methodName);
            if (method != null && method.isPublic())
                res.add(method);
        }
        return res;
    }


    private static HashSet<String> getReturnValueType(SootMethod sootMethod) {
        HashSet<String> res = new HashSet<>();
        Body body;
        try {
            body = sootMethod.retrieveActiveBody();
        }catch (Exception e){
            return res;
        }
        if (body == null)
            return res;
        BriefUnitGraph graph = new BriefUnitGraph(body);
        for (Unit unit : graph.getTails()) {
            if (unit instanceof ReturnStmt) {
                Value retValue = ((ReturnStmt) unit).getOp();//返回值可能
                if (!retValue.toString().equals("null")) {
                    HashSet<Unit> defUnit = findLastDefUnit(graph, unit, retValue);//获取返回值的最终定义语句
                    //我们要检查它们的类型以便作处理
                    deterBinderType(defUnit, res);
                }
            }
        }
        return res;
    }

    private static void deterBinderType(HashSet<Unit> unitSet, HashSet<String> res) {
        for (Unit unit : unitSet) {
            String type = null;
            //判断每一个语句的类型
            if (unit instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) unit;
                if (assignStmt.containsFieldRef()) {
                    //1如果是一般的字段，这里可能是Stub字段和Messenger字段，对于Messenger字段我们要去寻找它的初始化方法中的Handler参数
                    type = assignStmt.getFieldRef().getField().getType().toString();
                    if (type.equals("android.os.Messenger")) {
                        //如果发现Messenger字段，我们需要在<init>方法中查看它的实例化方法
                        SootClass declaringClass = assignStmt.getFieldRef().getFieldRef().declaringClass();//作为字段
                        boolean flag = false;
                        for (SootMethod method : declaringClass.getMethods()) {
                            if (method.getName().equals("<init>") && !flag) {
                                for (Unit u : method.retrieveActiveBody().getUnits()) {
                                    if (u instanceof InvokeStmt) {
                                        InvokeStmt invokeStmt = (InvokeStmt) u;
                                        if (invokeStmt.getInvokeExpr().getMethod().getSignature().equals("<android.os.Messenger: void <init>(android.os.Handler)>")) {
                                            type = "HANDLER_" + invokeStmt.getInvokeExpr().getArgs().get(0).getType();
                                            flag = true;
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (type.equals("android.os.IBinder")) {
                            //如果是IBinder字段的话
                            SootClass declaringClass = assignStmt.getFieldRef().getFieldRef().declaringClass();
                            SootMethod init = declaringClass.getMethodByName("<init>");
                            for (Unit u : init.retrieveActiveBody().getUnits()) {
                                if (u instanceof AssignStmt) {
                                    AssignStmt assign = (AssignStmt) u;
                                    if (assign.getLeftOp().toString().equals(assignStmt.getRightOp().toString()))
                                        type = assign.getUseBoxes().get(0).getValue().getType().toString();

                                }
                            }
                            if (type.equals("android.os.IBinder")) {
                                SootMethod onBind = declaringClass.getMethodByName("onBind");
                                //如果在init中没有找到,我们就在onBind方法中找

                            }
                        }
                        type = "STUB_" + type;
                    }
                } else if (assignStmt.getRightOp() instanceof NewExpr) {
                    //2、如果是new表达式，就是Stub类
                    type = "STUB_" + assignStmt.getRightOp().getType().toString();
                }
            } else if (unit instanceof InvokeStmt) {
                InvokeStmt invokeStmt = (InvokeStmt) unit;
                InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                type = "HANDLER_" + invokeExpr.getArgs().get(0).getType().toString();

            }
            if (type != null)
                res.add(type);
        }
    }

    private static HashSet<Unit> findLastDefUnit(BriefUnitGraph graph, Unit unit, Value value) {
        //这不是一个通用的方法,只用于判断IBinder的类型
        //进行比较粗糙的数据流分析，结束的方式只有两种，一种是发现该对象是字段，一种发现是new出来的对象
        //对于前一种这里不再进行区分，对于第二中要进行特殊处理，如果是Messenger对象，需要找到它的初始化为之的语句
        HashSet<Unit> res = new HashSet<>();
        HashSet<Unit> visit = new HashSet<>();//用于标记该语句是否访问过避免有环
        Queue<Unit> queue = new LinkedList<>();
        queue.add(unit);
        while (!queue.isEmpty()) {
            Unit currentUnit = queue.poll();
            visit.add(currentUnit);

            if (isValueDefInUnit(currentUnit, value)) {//如果变量在本语句中被赋值，就需要判断本语句的类型
                if (currentUnit instanceof AssignStmt) {
                    AssignStmt assignStmt = (AssignStmt) currentUnit;
                    if (assignStmt.containsFieldRef()) {//1、如果发现Binder是某个对象或者类的字段
                        res.add(currentUnit);
                    } else if (assignStmt.containsInvokeExpr()) {//2、如果是某个调用语句的返回值
                        //2.1如果是messenger的方式进行getBinder的
                        SootMethod method = assignStmt.getInvokeExpr().getMethod();
                        if (method.getSignature().equals("<android.os.Messenger: android.os.IBinder getBinder()>")) {
                            Value messengerValue = assignStmt.getUseBoxes().get(assignStmt.getUseBoxes().size() - 1).getValue();
                            res.addAll(findLastDefUnit(graph, currentUnit, messengerValue));
                            continue;
                        }
                        //2.2
                        if (!method.isConcrete())
                            continue;
                        Body body = method.retrieveActiveBody();
                        if (body == null) {
                            logger.info("Can't get body of {}", method.getSignature());
                        } else {
                            BriefUnitGraph callerGraph = new BriefUnitGraph(body);
                            for (Unit retUnit : callerGraph.getTails()) {
                                if (retUnit instanceof ReturnStmt) {
                                    ReturnStmt returnStmt = (ReturnStmt) retUnit;
                                    Value retValue = returnStmt.getOp();
                                    if (!retValue.toString().equals("null")) {
                                        res.addAll(findLastDefUnit(callerGraph, retUnit, retValue));
                                    }
                                }
                            }
                        }
                    } else if (!assignStmt.containsArrayRef()) {
                        if (assignStmt.getRightOp() instanceof NewExpr) {
                            String instanceType = ((NewExpr) assignStmt.getRightOp()).getBaseType().toString();
                            if (instanceType.equals("android.os.Messenger")) {
                                //如果实例化的对象是android.os.Messenger我们要找的是它的实例化的地方
                                HashSet<Unit> isVisit = new HashSet<>();
                                Queue<Unit> queue_temp = new LinkedList<>();
                                queue_temp.add(currentUnit);
                                while (!queue_temp.isEmpty()) {
                                    Unit poll = queue_temp.poll();
                                    isVisit.add(poll);
                                    if (poll instanceof InvokeStmt) {
                                        InvokeStmt invokeStmt = (InvokeStmt) poll;
                                        InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                                        if (invokeExpr.getMethod().getSignature().equals("<android.os.Messenger: void <init>(android.os.Handler)>")) {
                                            res.add(poll);//将初始化Messenger对象的这个语句加入队列
                                        } else {
                                            queue_temp.addAll(graph.getSuccsOf(poll));
                                        }
                                    } else {
                                        for (Unit succor : graph.getSuccsOf(poll)) {
                                            if (!isVisit.contains(succor))
                                                queue_temp.add(succor);
                                        }
                                    }
                                }
                            } else {
                                //如果是Stub类的实现类
                                res.add(currentUnit);
                            }
                        } else {
                            value = currentUnit.getUseBoxes().get(0).getValue();
                            for (Unit prrUnit : graph.getPredsOf(currentUnit)) {
                                if (!visit.contains(prrUnit))
                                    queue.add(prrUnit);
                            }
                        }
                    }
                } else if (currentUnit instanceof IdentityStmt) {
                    //todo
                }

            } else {
                for (Unit prrUnit : graph.getPredsOf(currentUnit)) {
                    if (!visit.contains(prrUnit))
                        queue.add(prrUnit);
                }
            }
        }
        return res;
    }

    private static HashSet<Unit> findDirectDefUnit(BriefUnitGraph graph, Unit unit, Value value) {
        HashSet<Unit> res = new HashSet<>();
        Queue<Unit> queue = new LinkedList<>();
        queue.add(unit);
        while (!queue.isEmpty()) {
            Unit currentUnit = queue.poll();
            if ((Stmt) currentUnit instanceof AssignStmt) {
                if (isValueDefInUnit(currentUnit, value)) {
                    res.add(currentUnit);
                }
            } else {
                queue.addAll(graph.getPredsOf(currentUnit));
            }
        }
        return res;
    }

    private static boolean isValueDefInUnit(Unit unit, Value value) {
        for (ValueBox valueBox : unit.getDefBoxes()) {
            if (valueBox.getValue().toString().equals(value.toString()))
                return true;
        }
        return false;
    }

    //以所有的方法为入口
    public static HashSet<SootMethod> getEntryPointByTargetMethodIsCalled(HashSet<String> targetMethodSigSet,String mode) {
        HashSet<SootMethod> entrypoint = new HashSet<>();
        //这里不存在构造调用图的过程
        for (SootClass cls : Scene.v().getApplicationClasses()) {
            if (isSystemClass(cls.getName()))
                continue;
            for (SootMethod m : cls.getMethods()) {
                if (m.isJavaLibraryMethod())
                    continue;
                if (m.isAbstract())
                    continue;
                if (m.isPhantom())
                    continue;
                if (m.isNative())
                    continue;
                for (Unit u : m.retrieveActiveBody().getUnits()) {

                    InvokeExpr invokeExpr=null;
                    if(u instanceof InvokeStmt){
                        InvokeStmt invokeStmt = (InvokeStmt) u;
                        invokeExpr=invokeStmt.getInvokeExpr();
                    }else if(u instanceof AssignStmt){
                        AssignStmt assignStmt = (AssignStmt) u;
                        if(assignStmt.containsInvokeExpr()){
                            invokeExpr= assignStmt.getInvokeExpr();
                        }
                    }
                    if(invokeExpr==null)
                        continue;
                    if(mode.equals("Signature")) {
                        if (!targetMethodSigSet.contains(invokeExpr.getMethod().getSignature()))
                            continue;
                    }else if(mode.equals("SubSignature")){
                        if (!targetMethodSigSet.contains(invokeExpr.getMethod().getSubSignature()))
                            continue;
                    }
                    entrypoint.add(m);
                }

            }
        }
        return entrypoint;
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


    public static HashSet<SootMethod> getClinitMethod(){
        //获取环境所有的静态方法
        HashSet<SootMethod> clinitSet=new HashSet<>();
        for(SootClass cls:Scene.v().getApplicationClasses()){
            if(isSystemClass(cls.getName()))
                continue;
            SootMethod clintMethod = cls.getMethodUnsafe("void <clinit>()");
            if(clintMethod!=null)
                clinitSet.add(clintMethod);
        }
        return clinitSet;
    }

    public static HashSet<SootMethod> getApplicationLifeCycleMethod(SootClass applicationClass){
        //获取应用中的application类中生命周期函数，application是最先被调用的应用组件
        HashSet<SootMethod> res=new HashSet<>();
        for(String methodSubsig:EntryPointsDefinition.getAppliactionMethods()){
            SootMethod method = findMethodBySubSignature(applicationClass, methodSubsig);
            if(method!=null){
                res.add(method);
            }
        }
        return res;

    }
}
