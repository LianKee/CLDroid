package cg;

import constant.StrawPointsDefinition;
import dataflow.CallSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.util.queue.QueueReader;
import util.StringUtil;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.lang.reflect.Constructor;
import java.util.*;

public class CallGraphUtils {

    private static final Logger logger = LoggerFactory.getLogger(CallGraphUtils.class);

    public static final String SPARK = "spark";
    public static final String CHA = "cha";
    public static final String RTA = "rta";
    public static final String VTA = "vta";


    private static void resetCallGraph() {
        Scene.v().releaseCallGraph();
        Scene.v().releasePointsToAnalysis();
        Scene.v().releaseReachableMethods();
        G.v().resetSpark();
    }

    public static void constructCallGraphConfig(String callgraphAlgorithm) {
        switch (callgraphAlgorithm) {
            case SPARK:
                soot.options.Options.v().setPhaseOption("cg.spark", "on");
                break;
            case CHA:
                soot.options.Options.v().setPhaseOption("cg.cha", "on");
                break;
            case RTA:
                soot.options.Options.v().setPhaseOption("cg.spark", "on");
                soot.options.Options.v().setPhaseOption("cg.spark", "rta:true");
                soot.options.Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
                break;
            case VTA:
                soot.options.Options.v().setPhaseOption("cg.spark", "on");
                Options.v().setPhaseOption("cg.spark", "vta:true");
                break;
            default:
                throw new RuntimeException("Invalid callgraph algorithm");
        }
    }

    public static void constructCallGraph(String callgraphAlgorithm) {
        resetCallGraph();
        constructCallGraphConfig(callgraphAlgorithm);
        PackManager.v().getPack("cg").apply();
    }

    public static boolean reachable(SootMethod method) {
        ReachableMethods reachableMethods = Scene.v().getReachableMethods();
        return reachableMethods.contains(method);
    }

    public static boolean isMethodReachable2Target(SootMethod begin, List<String> targetMethodList) {
        QueueReader<MethodOrMethodContext> listener =
                Scene.v().getReachableMethods().listener();
        while (listener.hasNext()) {
            String signature = listener.next().method().getSignature();
            if (targetMethodList.contains(signature) || StringUtil.isMatch(signature, StrawPointsDefinition.COLLECTIONS_STRAWPOINT_REGEX))
                return true;

        }
        return false;

    }

    public static boolean isMethodReachable2Target(SootMethod begin, String targetMethod) {
        QueueReader<MethodOrMethodContext> listener =
                Scene.v().getReachableMethods().listener();
        while (listener.hasNext()) {
            String subSignature = listener.next().method().getSubSignature();
            if (targetMethod.equals(subSignature))
                return true;
        }
        return false;
    }

    public static HashMap<SootMethod, Unit> findTargetMethodInvokeInICFG(SootMethod method, String targetMethod) {
        //寻找ICFG中满足条件的语句
        HashMap<SootMethod, Unit> res = new HashMap<>();
        QueueReader<MethodOrMethodContext> listener =
                Scene.v().getReachableMethods().listener();
        SootMethod target = null;
        while (listener.hasNext()) {
            SootMethod sootMethod = listener.next().method();
            if (sootMethod.getSubSignature().equals(targetMethod)) {
                target = sootMethod;
                break;
            }
        }
        Iterator<Edge> edges = Scene.v().getCallGraph().edgesInto(target);
        while (edges.hasNext()) {
            SootMethod m = edges.next().getTgt().method();
            if (m.isConcrete()) {
                for (Unit unit : m.retrieveActiveBody().getUnits()) {
                    if (unit instanceof AssignStmt) {
                        AssignStmt assignStmt = (AssignStmt) unit;
                        if (assignStmt.containsInvokeExpr()) {
                            if (assignStmt.getInvokeExpr().getMethod().getSubSignature().equals(targetMethod)) {
                                res.put(m, unit);
                            }
                        }
                    }
                }
            }
        }
        return res;

    }


    //用于找到从entrypoint到达目标方法的所有调用路径
    public static void findTargetMethod(SootMethod method, HashSet<String> targetMethod, String mode, List<CallSite> callStack, int max_depth, int depth, HashSet<List<CallSite>> paths) {
        if (!Scene.v().hasCallGraph())
            throw new RuntimeException("No CallGraph in Scence");
        if (depth > max_depth)
            return;
        for (CallSite callSite : callStack) {
            if (callSite.caller.getSignature().equals(method.getSignature()))
                return;
        }
        if (mode.equals("Signature") && targetMethod.contains(method.getSignature())) {
            paths.add(callStack);
        } else if (mode.equals("SubSignature") && targetMethod.contains(method.getSubSignature())) {
            logger.info("找到相关调用");
            paths.add(callStack);
        }
        CallGraph callGraph = Scene.v().getCallGraph();
        Iterator<Edge> edgeIterator = callGraph.edgesOutOf(method);
        while (edgeIterator.hasNext()) {

            Edge next = edgeIterator.next();
            List<CallSite> addedCallStack = new ArrayList<>(callStack);
            addedCallStack.add(new CallSite(next.src(), next.srcUnit(), -1));
            findTargetMethod(next.tgt(), targetMethod, mode, addedCallStack, max_depth, depth + 1, paths);
        }
    }

    //用于找到从targetMethod到entrypoint的所有调用路径
    public static void findEntryMethod(SootMethod method, HashSet<String> entryMethod, String mode, List<CallSite> callStack, int max_depth, int depth, HashSet<List<CallSite>> paths, boolean isMustFoundEntry) {
        //剪枝
        if (!Scene.v().hasCallGraph())
            throw new RuntimeException("No CallGraph in Scene");
        if (method.getSubSignature().equals("void run()") && method.getDeclaringClass().getSuperclass().getName().equals("java.lang.Object"))
            return;
        if (depth > max_depth) {
            if (!isMustFoundEntry)
                paths.add(callStack);
            return;
        }
        if (mode.equals("Signature") && entryMethod.contains(method.getSignature())) {
            paths.add(callStack);
            return;
        } else if (mode.equals("SubSignature") && entryMethod.contains(method.getSubSignature())) {
            paths.add(callStack);
            return;
        }
        CallGraph callGraph = Scene.v().getCallGraph();
        Iterator<Edge> edgeIterator = callGraph.edgesInto(method);
        while (edgeIterator.hasNext()) {
            Edge next = edgeIterator.next();
            List<CallSite> addedCallStack = new ArrayList<>(callStack);
            //检查是否有环
            if (callStack.contains(new CallSite(next.src(), next.srcUnit(), -1)))
                continue;
            addedCallStack.add(new CallSite(next.src(), next.srcUnit(), -1));
            findEntryMethod(next.src(), entryMethod, mode, addedCallStack, max_depth, depth + 1, paths, isMustFoundEntry);
        }
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

    public static CallGraph cg=null;

    public static void buildCGbyCHA(List<SootMethod> entrypoints) {
        Queue<SootMethod> worklist = new LinkedList<>(entrypoints);
        HashSet<SootMethod> reachableMethod = new HashSet<>();
        cg = new CallGraph();
        Scene.v().setEntryPoints(entrypoints);
        while (!worklist.isEmpty()) {
            SootMethod poll = worklist.poll();
            if (reachableMethod.contains(poll))
                continue;
            reachableMethod.add(poll);

            if (isSystemClass(poll.getDeclaringClass().getName())||isThirdPartyLibrary(poll.getDeclaringClass().getName()))
                continue;
            if (poll.isPhantom())
                continue;
            if (poll.isNative())
                continue;
            if (!poll.isConcrete())
                continue;
            try {
                for (Unit u : poll.retrieveActiveBody().getUnits()) {
                    InvokeExpr invokeExpr = null;
                    if (u instanceof InvokeStmt) {
                        InvokeStmt invokeStmt = (InvokeStmt) u;
                        invokeExpr = invokeStmt.getInvokeExpr();
                    }

                    if (u instanceof AssignStmt) {
                        AssignStmt assignStmt = (AssignStmt) u;
                        if (assignStmt.containsInvokeExpr())
                            invokeExpr = assignStmt.getInvokeExpr();
                    }

                    if (invokeExpr == null)
                        continue;

                    Kind kind = Kind.STATIC;

                    HashSet<SootMethod> targetMethods = new HashSet<>();

                    if (invokeExpr instanceof StaticInvokeExpr) {
                        targetMethods.add(invokeExpr.getMethod());
                        kind = Kind.STATIC;
                    } else {
                        int size = invokeExpr.getUseBoxes().size();
                        Type type = invokeExpr.getUseBoxes().get(size - 1).getValue().getType();
                        SootClass cls = Scene.v().getSootClass(type.toString());

                        if (invokeExpr instanceof SpecialInvokeExpr) {
                            //处理本类的实例方法，私有方法或者父类方法
//                        System.out.println("spacial invoke: "+invokeExpr.toString());
                            SootMethod method = invokeExpr.getMethod();
                            if (method != null) {
                                targetMethods.add(method);
                                kind = Kind.SPECIAL;
                            }
                        } else if ((invokeExpr instanceof InterfaceInvokeExpr) || (invokeExpr instanceof VirtualInvokeExpr)) {
                            String name = invokeExpr.getMethod().getName();
                            Type instancType = invokeExpr.getUseBoxes().get(invokeExpr.getUseBoxes().size() - 1).getValue().getType();
                            SootClass sootClass = Scene.v().getSootClass(instancType.toString());
                            if (!sootClass.hasSuperclass())
                                continue;
                            String superClassName = sootClass.getSuperclass().getName();
                            //需要处理cg中的一些特殊的调用，这里比较关心的是异步
                            if ((name.equals("run") && sootClass.implementsInterface("java.lang.Runnable")) || (name.equals("start") && (superClassName.equals("java.lang.Thread") || sootClass.getName().equals("java.lang.Thread"))) ||
                                    (name.equals("execute")) || (name.equals("post") && superClassName.equals("android.os.Handler"))||name.equals("postDelayed")) {
                                //我们要处理异步的问题
                                HashMap<SootMethod, Kind> asyncMethod = getAsyncMethod(poll, invokeExpr,u);
                                if (asyncMethod != null) {
                                    for (SootMethod method : asyncMethod.keySet()) {
                                        if (method == null)
                                            continue;
                                        targetMethods.add(method);
                                        kind = asyncMethod.get(method);
                                    }
                                }
                            } else if (name.equals("setOnClickListener")) {
                                //处理回调
                                Value arg = invokeExpr.getArg(0);
                                Type listener = arg.getType();
                                SootClass listenerClass = Scene.v().getSootClass(listener.toString());

                                SootMethod onClick = findMethod(listenerClass, "void onClick(android.view.View)");
                                if (onClick != null) {
                                    Class<Kind> kindClass = Kind.class;
                                    try {
                                        Constructor<Kind> constructors = kindClass.getDeclaredConstructor(String.class);
                                        constructors.setAccessible(true);
                                        kind = constructors.newInstance("CALLBACK");
                                        targetMethods.add(onClick);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            } else if (invokeExpr.getMethod().getSignature().equals("<android.content.ContentResolver: android.os.Bundle call(android.net.Uri,java.lang.String,java.lang.String,android.os.Bundle)>")) {
                                //我们处理call与实际call方法的映射
                                SootMethod method = getCallToRealCallMethod(poll, invokeExpr);
                                if (method != null) {
                                    //这个地方要处理下因为参数是不一致的
                                    Class<Kind> kindClass = Kind.class;
                                    try {
                                        Constructor<Kind> constructors = kindClass.getDeclaredConstructor(String.class);
                                        constructors.setAccessible(true);
                                        kind = constructors.newInstance("SYSTEM_CALL");
                                        targetMethods.add(method);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            } else if (invokeExpr.getMethod().getSignature().equals("<android.os.Handler: boolean sendMessage(android.os.Message)>")) {
                                //处理handler
                                //我们要找到具体的handler的实现

                                HashSet<Unit> handlerNewSiteSet = getNewSite(u, ((InstanceInvokeExpr)invokeExpr).getBase(),poll);
                                if(handlerNewSiteSet.isEmpty()){
                                    logger.info("Can't find handler init site!");
//                                    logger.info("current method: {}",poll.getSignature());
                                    continue;
                                }
                                for(Unit handlerNewSite:handlerNewSiteSet) {
                                    SootClass handler;
                                    if(handlerNewSite instanceof IdentityStmt) {
                                        IdentityStmt identityStmt = (IdentityStmt) handlerNewSite;
                                        ThisRef rightOp = (ThisRef) identityStmt.getRightOp();
                                        handler=Scene.v().getSootClass(rightOp.getType().toString());

                                    }else {
                                        Value rightOp = ((AssignStmt) handlerNewSite).getRightOp();
                                        NewExpr newExpr = (NewExpr) rightOp;
                                        handler = Scene.v().getSootClass(newExpr.getType().toString());
                                    }//                                logger.info("[Handler Impl]: {}",handler.getName());
                                    SootMethod handleMessage = findMethod(handler,"void handleMessage(android.os.Message)");
//                                logger.info("[handleMessage]: {}",handleMessage.getSignature());
//                                logger.info("[Method]: {}",poll.getSignature());
                                    if(handleMessage==null)
                                        continue;
                                    targetMethods.add(handleMessage);
                                    kind = Kind.HANDLER;
                                }


                            }else if(invokeExpr.getMethod().getSignature().equals("<android.view.View: void invalidate()>")){
                                //处理系统中刷新调用
                                VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr) invokeExpr;
                                String baseClsName = virtualInvokeExpr.getBase().getType().toString();
                                SootClass baseCls = Scene.v().getSootClass(baseClsName);
                                SootMethod method = findMethod(baseCls, "void onDraw(android.graphics.Canvas)");
                                if (method != null) {
                                    //这个地方要处理下因为参数是不一致的
                                    Class<Kind> kindClass = Kind.class;
                                    try {
                                        Constructor<Kind> constructors = kindClass.getDeclaredConstructor(String.class);
                                        constructors.setAccessible(true);
                                        kind = constructors.newInstance("SYSTEM_CALL");
                                        targetMethods.add(method);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }


                            }else {
                                if (invokeExpr instanceof VirtualInvokeExpr) {
                                    //virtual对应的是实例方法，对应的方法是子类中实现的相关方法，或者如果本类中实现了该方法，本类中的方法也要添加
//                                System.out.println("Virtual invoke: "+invokeExpr.toString());
                                    HashSet<SootMethod> methods = dispatchAbstract(cls, invokeExpr.getMethod());
                                    if (!methods.isEmpty()) {
                                        targetMethods.addAll(methods);
                                        kind = Kind.VIRTUAL;

                                    }
                                } else {
                                    //如果是interface invoke，我们要去找实现该接口的所有类中相应的方法
//                                System.out.println("Interface invoke: "+invokeExpr.toString());
                                    //对于接口调用，我们只关注哪些用户自定义的接口，对于java提供的一些接口，我们不去寻找真正实现相关接口的方法
                                    if (isSystemClass(cls.getName())&&!isUserKeepClass(cls.getName())) {
                                        //我们不返回具体的方法
                                        targetMethods.add(invokeExpr.getMethod());
                                    } else {
                                        targetMethods.addAll(dispatchAbstract(cls, invokeExpr.getMethod()));
                                        if(isUserKeepClass(cls.getName()))
                                            targetMethods.add(invokeExpr.getMethod());
                                    }
                                    kind = Kind.INTERFACE;

                                }
                            }
                        }
                    }

                    for (SootMethod target : targetMethods) {
                        Edge edge = new Edge(poll, u, target, kind);
                        cg.addEdge(edge);
                    }
                    worklist.addAll(targetMethods);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //更新Call Graph
        //更新ReachableMethod
        ReachableMethods reachableMethods = new ReachableMethods(cg, entrypoints);
        Scene.v().setReachableMethods(reachableMethods);
        Scene.v().setCallGraph(cg);
        cg=null;
    }


    public static SootMethod getCallToRealCallMethod(SootMethod method, InvokeExpr invokeExpr) {
        //对于call的处理，我们默认为call调用了自身重写的call方法
        SootClass declaringClass = method.getDeclaringClass();
        return declaringClass.getMethodUnsafe("android.os.Bundle call(java.lang.String,java.lang.String,android.os.Bundle)");


    }

    public static HashMap<SootMethod, Kind> getAsyncMethod(SootMethod method, InvokeExpr invokeExpr,Unit callSite) {
        /*
        处理异步首先要判断实例的类型
         */
        HashMap<SootMethod, Kind> res = new HashMap<>();
        SootMethod apiMethod = invokeExpr.getMethod();
        String apiName = apiMethod.getName();
        String apiSig = apiMethod.getSignature();
        Value value = invokeExpr.getUseBoxes().get(invokeExpr.getUseBoxes().size() - 1).getValue();
        Type type = value.getType();
        SootClass sc = Scene.v().getSootClass(type.toString());
        if (apiSig.equals("<java.lang.Thread: void start()>")) {
            //1、Thread.start形式启动的,这里包含两种情况，一种是继承了Thead类，实现了runnable接口
            //2、就是直接为Thread的初始化中传入一个Runnable对象
            //处理第二种方式
            if (sc.getName().equals("java.lang.Thread")) {
                //通过接口调用
                for (Unit u : method.retrieveActiveBody().getUnits()) {
                    if (u instanceof InvokeStmt) {
                        InvokeExpr invokeExpr1 = ((InvokeStmt) u).getInvokeExpr();
                        if (invokeExpr1.getMethod().getName().equals("<init>") && invokeExpr1.getUseBoxes().get(invokeExpr1.getUseBoxes().size() - 1).getValue().equals(value)) {
                            Value runArg = invokeExpr1.getArg(0);
                            HashSet<Unit> newSite = getNewSite(callSite, runArg, method);
                            for(Unit uu:newSite){
                                SootClass cls;
                                if( uu instanceof IdentityStmt){
                                    String clsName = ((ThisRef) ((IdentityStmt) uu).getRightOp()).getType().toString();
                                    cls = Scene.v().getSootClass(clsName);
                                }else {
                                    Value rightOp = ((AssignStmt) uu).getRightOp();
                                    NewExpr newExpr = (NewExpr) rightOp;
                                    cls = Scene.v().getSootClass(newExpr.getType().toString());
                                }
                                SootMethod run = findMethod(cls, "void run()");
                                if (run != null) {
                                    res.put(run, Kind.THREAD);
                                }
                            }
//                            logger.info("for Thread  find run ...");
//                            logger.info(res.toString());
//                            logger.info(method.getSignature());
//                            logger.info("-----------------------------------------------------");
                            return res;
                        }
                    }
                }
            } else {
                //处理第一种方式
                //这个类是Thread的类，但不一定是直接子类，只需要在自身或者最近的父类中找到这个方法就可以了
                //属于virtual invoke,
                SootClass threadClass = Scene.v().getSootClass("java.lang.Thread");
                if (isSubClass(sc, threadClass)) {
                    //如果是Thread的子类
                    //我们应该递归的
                    SootMethod run = findMethod(sc, "void run()");
                    if (run == null)
                        return null;
                    res.put(run, Kind.THREAD);
                    return res;
                }
            }
        } else if (apiName.equals("run") && isImplementInterface(sc,"java.lang.Runnable")) {
            //如果实现了Runnable接口
            SootMethod run = sc.getMethodByNameUnsafe("run");
            if (run == null)
                return null;
            res.put(run, Kind.THREAD);
        }else if((apiName.equals("postDelayed")||apiName.equals("post"))&&isOrSubClass(sc,"android.os.Handler")){
            //获取Runnable接口
            Value runArg = invokeExpr.getArg(0);
            HashSet<Unit> newSite = getNewSite(callSite, runArg, method);
            for(Unit u:newSite){
                SootClass cls;
                if( u instanceof IdentityStmt){
                    String clsName = ((ThisRef) ((IdentityStmt) u).getRightOp()).getType().toString();
                    cls = Scene.v().getSootClass(clsName);
                }else {
                    Value rightOp = ((AssignStmt) u).getRightOp();
                    NewExpr newExpr = (NewExpr) rightOp;
                    cls = Scene.v().getSootClass(newExpr.getType().toString());
                }
                SootMethod run = findMethod(cls,"void run()");
                if(run!=null){
                    res.put(run,Kind.THREAD);
                }
            }
//            logger.info("for Handler post/postDelayed find run ...");
//            logger.info(res.toString());
//            logger.info(method.getSignature());
//            logger.info("-----------------------------------------------------");
            return res;


        } else if (apiName.equals("execute") && isOrSubClass(sc,"android.os.AsyncTask")&&!sc.getName().equals("android.os.AsyncTask")) {
            for (SootMethod m : sc.getMethods()) {
                if (m.getName().equals("doInBackground")) {
                    boolean flag = true;
                    for (Unit uu : m.retrieveActiveBody().getUnits())
                        if (uu.toString().contains("doInBackground")) {
                            flag = false;
                            break;
                        }
                    if (flag) {
                        res.put(m, Kind.ASYNCTASK);
                        return res;
                    }
                }

            }
        } else if (apiName.equals("execute") && (isImplementInterface(sc,"java.util.concurrent.Executor")||isOrSubClass(sc,"java.util.concurrent.Executor"))) {
            //execute对应的参数就是要执行的线程
            Value runArg = invokeExpr.getArg(0);
            HashSet<Unit> newSite = getNewSite(callSite, runArg, method);
            for(Unit u:newSite){
                SootClass cls;
                if( u instanceof IdentityStmt){
                    String clsName = ((ThisRef) ((IdentityStmt) u).getRightOp()).getType().toString();
                    cls = Scene.v().getSootClass(clsName);
                }else {
                    Value rightOp = ((AssignStmt) u).getRightOp();
                    NewExpr newExpr = (NewExpr) rightOp;
                    cls = Scene.v().getSootClass(newExpr.getType().toString());
                }
                SootMethod run = findMethod(cls,"void run()");
                if(run!=null){
                    res.put(run,Kind.EXECUTOR);
                }
            }
//            logger.info("for Executor find run ...");
//            logger.info(res.toString());
//            logger.info(method.getSignature());
//            logger.info("-----------------------------------------------------");
            return res;
        }
        return null;
    }

    //判断m是不是对于cls对象可见
    public static boolean isVisible(SootMethod m, SootClass cls) {
        FastHierarchy hierarchy = Scene.v().getOrMakeFastHierarchy();
        if (m.isPublic()) {
            //如果是public对所有子类都是可见的
            return true;
        } else if (m.isPrivate()) {
            //如果是private则只有自己的类可见
            return m.getDeclaringClass().getName().equals(cls.getName());
        } else {
            return m.isProtected() ? hierarchy.canStoreClass(cls, m.getDeclaringClass()) : cls.getJavaPackageName().equals(m.getDeclaringClass().getJavaPackageName());
        }
    }


    public static SootMethod dispatchConcrete(SootClass cls, SootMethod method) {
        //在本类中获取给方法，如果本类中没有该方法就在父类中寻找
        String subSignature = method.getSubSignature();
        SootClass temp = cls;
        do {
            SootMethod m = cls.getMethodUnsafe(subSignature);
            if (m != null) {
                //后面一项是因为系统库没有解析
                if ((m.isConcrete() && isVisible(method, temp)) || isSystemClass(cls.getName())) {
                    return m;
                } else {
                    return null;
                }
            }
            //如果本类中没有实现则来自于父类，在父类中寻找
            cls = cls.getSuperclassUnsafe();
        } while (cls != null);
        return null;
    }

    //
    public static HashSet<SootMethod> dispatchAbstract(SootClass cls, SootMethod method) {
        //找到需调用或者几口调用对应的方法
        HashSet<SootMethod> targetMethod = new HashSet<>();
        Queue<SootClass> worklist = new LinkedList<>();
        worklist.add(cls);
        FastHierarchy hierarchy = Scene.v().getOrMakeFastHierarchy();
        while (!worklist.isEmpty()) {
            SootClass currentClass = worklist.poll();
            if (currentClass == null)
                continue;
            if (currentClass.isInterface()) {
                worklist.addAll(hierarchy.getAllImplementersOfInterface(currentClass));
            } else {
                //在本类中寻找方法的实现
                SootMethod m = dispatchConcrete(currentClass, method);
                if (m != null)
                    targetMethod.add(m);
                //找到本类的子类加入
                Collection<SootClass> subclassesOf = hierarchy.getSubclassesOf(currentClass);
                worklist.addAll(subclassesOf);
            }
        }
        return targetMethod;
    }

    public static HashSet<SootMethod> getMethod(Unit u) {
        //根据语句获取它的
        HashSet<SootMethod> res = new HashSet<>();
        InvokeExpr invokeExpr = null;
        if (u instanceof InvokeStmt) {
            InvokeStmt invokeStmt = (InvokeStmt) u;
            invokeExpr = invokeStmt.getInvokeExpr();
        }

        if (u instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) u;
            if (assignStmt.containsInvokeExpr())
                invokeExpr = assignStmt.getInvokeExpr();
        }
        if (invokeExpr == null)
            return res;
        if (invokeExpr instanceof StaticInvokeExpr) {
            res.add(invokeExpr.getMethod());
        } else {
            int size = invokeExpr.getUseBoxes().size();
            Type type = invokeExpr.getUseBoxes().get(size - 1).getValue().getType();
            SootClass cls = Scene.v().getSootClass(type.toString());

            if (invokeExpr instanceof SpecialInvokeExpr) {
                SootMethod method = dispatchConcrete(cls, invokeExpr.getMethod());
                if (method != null)
                    res.add(method);
            }

            if ((invokeExpr instanceof InterfaceInvokeExpr) || (invokeExpr instanceof VirtualInvokeExpr)) {
                res.addAll(dispatchAbstract(cls, invokeExpr.getMethod()));
            }
        }
        return res;
    }

    public static boolean isSubClass(SootClass child, SootClass fatherClass) {
        SootClass superclass = child.getSuperclass();
        while (!(superclass.getName().equals(fatherClass.getName()) || superclass.getName().equals("java.lang.Object"))) {
            superclass = superclass.getSuperclass();
        }
        return !superclass.getName().equals("java.lang.Object");
    }

    public static boolean isImplementInterface(SootClass cls,String interfaceName){
        if(cls.implementsInterface(interfaceName))
            return true;
        if(!cls.hasSuperclass())
            return false;
        return isImplementInterface(cls.getSuperclass(),interfaceName);
    }

    public static boolean isOrSubClass(SootClass curCls,String targetCls){
        //判断是不是指定类或者它的子类
        if(curCls.getName().equals(targetCls))
            return true;
        if(curCls.hasSuperclass()){
            return isOrSubClass(curCls.getSuperclass(),targetCls);
        }else {
            return false;
        }
    }


    public static boolean isUserKeepClass(String clsName){
        //定义用户需要追踪的一些方法
        if(clsName.equals("android.content.SharedPreferences"))
            return true;
        return false;
    }

    public static boolean isThirdPartyLibrary(String clsName){
        if(clsName.startsWith("com.facebook."))
            return true;
        return false;

    }
    public static void insertCallBackInCallGraph() {
        if (!Scene.v().hasCallGraph()) {
            throw new RuntimeException("No Call graph!");
        }


    }

    public static SootMethod findMethod(SootClass cls, String subsignature) {
        if (cls == null)
            return null;
        SootMethod method = cls.getMethodUnsafe(subsignature);
        if (method != null)
            return method;
        if (!cls.hasSuperclass())
            return null;
        return findMethod(cls.getSuperclass(), subsignature);
    }

    public static HashSet<SootMethod> getAllMethodsCanReachTargetMethods(HashSet<SootMethod> targetMethods) {
        //找到所有可以到达目标方法的方法
        HashSet<SootMethod> reachableMethods=new HashSet<>();
       for(SootMethod targetMethod:targetMethods){
           getReachableMethodInDepth(reachableMethods,targetMethod,0);
       }
        logger.info("[Reachable Method Size]: {}",reachableMethods.size());
        return reachableMethods;
    }

    public static void getAllPathsTotTargetMethod(SootMethod targetMethod,SootMethod curMethod,int depth,List<String> path){
        //找到所有可达目标方法的并打印输出
//        logger.info(path.toString());
        if(curMethod.equals(targetMethod)){
            path.add(targetMethod.getSignature());
            logger.info(path.toString());

            try{

                FileWriter fileWriter = new FileWriter("./path_info.txt", true);
                BufferedWriter writer = new BufferedWriter(fileWriter);
                writer.write("path info:\n");
                int counter=0;
                for(String m:path){
                    writer.write("["+counter+"]: "+m+"\n");
                    counter++;
                }
                writer.close();

            }catch (Exception e){
                e.printStackTrace();
            }
            path.remove(path.size()-1);
            return;
        }
        //避免出现循环
        if(path.contains(curMethod.getSignature())){
            return;
        }
        //超过最大深度
        if(depth>10){
            return;
        }

        CallGraph cg = Scene.v().getCallGraph();
        Iterator<Edge> edgeIterator = cg.edgesOutOf(curMethod);
        //将当前方法加入路径
        path.add(curMethod.getSignature());
        while (edgeIterator.hasNext()){
            SootMethod tgt = edgeIterator.next().tgt();
            getAllPathsTotTargetMethod(targetMethod,tgt,depth+1,path);
        }
        //将当前方法删除路径
        path.remove(curMethod.getSignature());
    }

    //找到某个变量被初始化的地方
    public static HashSet<Unit> getNewSite(Unit unit, Value value, SootMethod curMethod) {

        //我们应该找到指定的语句

        HashSet<Unit> res=new HashSet<>();

        HashSet<Trace> traces=new HashSet<>();
        Queue<Trace> queue=new LinkedList<>();
        //要追寻的变量
        queue.add(new Trace(unit,curMethod,value));
        while (!queue.isEmpty()){
            Trace curTrace = queue.poll();
            traces.add(curTrace);
            //我们该追寻该变量的直接赋值语句
            HashSet<Unit> allDirectUnit = getAllDirectUnit(curTrace.v, curTrace.u, curTrace.m);
            for(Unit defUnit:allDirectUnit){
                if(defUnit instanceof AssignStmt){
                    AssignStmt assignStmt = (AssignStmt) defUnit;
                    Value rightOP = assignStmt.getRightOp();
                    if(rightOP instanceof NewExpr){
                        res.add(defUnit);
                        continue;
                    }
                    //如果是返回值,我们应该考察返回值
                    if(rightOP instanceof InvokeExpr){
                        SootMethod method = ((InvokeExpr) rightOP).getMethod();
                        if(method.isConcrete()){
                            BriefUnitGraph graph = new BriefUnitGraph(method.retrieveActiveBody());
                            for(Unit tail:graph.getTails()){
                                if(tail instanceof ReturnStmt){
                                    ReturnStmt returnStmt = (ReturnStmt) tail;
                                    Value op = returnStmt.getOp();
                                    if(op instanceof NullConstant)
                                        continue;
                                    Trace trace = new Trace(tail, method, op);
                                    if(!traces.contains(trace)){
                                        queue.add(trace);
                                    }
                                }
                            }
                        }

                    }else if(rightOP instanceof Local){
                        //如果是局部变量，我们应该继续传播
                        Trace trace = new Trace(defUnit, curTrace.m, rightOP);
                        if(!traces.contains(trace)){
                            queue.add(trace);
                        }
                    }else if(rightOP instanceof InstanceFieldRef){
                        //如果是实例字段的话，我们应该找到本类中所有给定字段被定义的地方然后去追踪
                        SootClass declaringClass = curTrace.m.getDeclaringClass();
                        //实例字段
                        InstanceFieldRef instanceFieldRef = (InstanceFieldRef) rightOP;
                        for(SootMethod m:declaringClass.getMethods()){
                            if(!m.isConcrete())
                                continue;
                            for(Unit u:m.retrieveActiveBody().getUnits()){
                                if(u instanceof AssignStmt){
                                    AssignStmt assignOp= (AssignStmt) u;
                                    Value assignOpLeftOp = assignOp.getLeftOp();
                                    if(assignOpLeftOp instanceof InstanceFieldRef){
                                        InstanceFieldRef filedRef = (InstanceFieldRef) assignOpLeftOp;
                                        if(filedRef.getField().equals(instanceFieldRef.getField())){
                                            //找到实例字段被赋值的地方
                                            Value rightOp = assignOp.getRightOp();
                                            //避免出现右值未null的情况
                                            if(rightOp instanceof NullConstant)
                                                continue;
                                            //我们应该继续寻找右值
                                            Trace trace = new Trace(u, m, rightOp);
                                            if(!traces.contains(trace)){
                                                queue.add(trace);
                                            }
                                        }
                                    }

                                }
                            }
                        }

                    }else if(rightOP instanceof StaticFieldRef){
                        //对于静态字段，我们同样是找到所有的实现
                        SootClass declaringClass = curTrace.m.getDeclaringClass();
                        for(SootMethod m:declaringClass.getMethods()){
                            if(!m.isConcrete())
                                continue;
                            for(Unit u:m.retrieveActiveBody().getUnits()){
                                if(u instanceof AssignStmt){
                                    AssignStmt assignOp = (AssignStmt) u;
                                    Value leftOp = assignOp.getLeftOp();
                                    if(leftOp.equals(rightOP)){
                                        //找到静态变量的直接赋值语句
                                        Value rightOp = assignOp.getRightOp();
                                        //避免空指针
                                        if(rightOp instanceof NullConstant)
                                            continue;
                                        Trace trace = new Trace(u, m, rightOp);
                                        if(!traces.contains(trace))
                                            queue.add(trace);

                                    }
                                }
                            }
                        }
                    }
                }else {
                    //如果是作为参数，我们应该根据现有的CG去追踪这个变量被初始化的地方，当然参数可能来自多个地方
                    //找到所有的调用方法
                    Iterator<Edge> callers = cg.edgesInto(curTrace.m);
                    IdentityStmt identityStmt = (IdentityStmt) defUnit;
                    Value rightOp = identityStmt.getRightOp();
                    if(rightOp instanceof ThisRef) {
                        res.add(defUnit);
                        continue;
                    }
                    ParameterRef pr = (ParameterRef) rightOp;
                    //得到参数的索引号
                    int index = pr.getIndex();
                    while (callers.hasNext()){
                        Edge next = callers.next();
                        SootMethod src = next.src();
                        Unit callSite = next.srcUnit();
                        //得到实参
                        InvokeExpr invokeExpr = getInvokeExpr(callSite);
                        if(invokeExpr==null){
                            logger.warn("Error:{} is not a call Site!",callSite.toString());
                            continue;
                        }
                        Value v = invokeExpr.getArg(index);
                        Trace trace = new Trace(callSite, src, v);
                        if(!traces.contains(trace))
                            queue.add(trace);
                    }

                }
            }
        }
        return res;
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
                if(isValueDefinedInUnit(preUnit,value)){
                    res.add(preUnit);
                    continue;
                }
                queue.add(preUnit);
            }
        }
        return res;
    }

    public static Unit getDirectDefUnit(Unit curUnit, Value value, SootMethod method) {
        if (!method.isConcrete())
            return null;
        BriefUnitGraph graph = new BriefUnitGraph(method.retrieveActiveBody());
        Queue<Unit> queue = new LinkedList<>();
        queue.add(curUnit);
        HashSet<Unit> visit = new HashSet<>();
        while (!queue.isEmpty()) {
            Unit poll = queue.poll();
            visit.add(poll);
            if (isValueDefinedInUnit(poll, value))
                return poll;
            for (Unit pre : graph.getPredsOf(poll)) {
                if (!visit.contains(pre))
                    queue.add(pre);
            }
        }
        return null;
    }

    public static boolean isValueDefinedInUnit(Unit unit, Value value) {
        //判断是value是不是在当前语句中被定义的
        if(unit instanceof AssignStmt){
            AssignStmt assignStmt = (AssignStmt) unit;
            return assignStmt.getLeftOp().equals(value);
        }

        if(unit instanceof IdentityStmt){
            IdentityStmt identityStmt = (IdentityStmt) unit;
            return identityStmt.getLeftOp().equals(value);
        }
        return false;
    }


    static class Trace{
        Unit u;
        SootMethod m;
        Value v;

        public Trace(Unit u,SootMethod m,Value v){
            this.m=m;
            this.u=u;
            this.v=v;
        }

        @Override
        public boolean equals(Object obj) {
            if(!(obj instanceof Trace))
                return false;
            Trace trace = (Trace) obj;
            return this.v.equals(trace.v)&&this.m.equals(trace.m)&&this.u.equals(trace.u);
        }

        @Override
        public int hashCode() {
            return Objects.hash(m,u,v);
        }

        @Override
        public String toString() {
            return "[Unit]:"+u+'\n'+
                    "[Method]:"+m.getSignature()+'\n'+
                    "[Value]:"+v;
        }
    }

    public static void getPath(SootMethod curMethod,SootMethod targetMethod,List<SootMethod> path,int depth){
        if(depth>40) {
//            showPath(path);
            return;
        }
        if(curMethod.equals(targetMethod)){
            showPath(path);
            return;
        }
        CallGraph callGraph = Scene.v().getCallGraph();
        Iterator<Edge> edgeIterator = callGraph.edgesInto(curMethod);
        while (edgeIterator.hasNext()){
            Edge next = edgeIterator.next();
            SootMethod src = next.src();
            if(path.contains(src))
                continue;
            List<SootMethod> newPath=new ArrayList<>(path);
            newPath.add(curMethod);
            getPath(src,targetMethod,newPath,depth+1);
        }
    }

    public static void showPath(List<SootMethod> path){
        Collections.reverse(path);
        logger.info(path.toString());
    }


    public static void getReachableMethodInDepth(HashSet<SootMethod> reachableMethods,SootMethod curMethod,int depth){
        //找到指定深度的可达性方法
        if(depth>10)
            return;
        CallGraph callGraph = Scene.v().getCallGraph();
        reachableMethods.add(curMethod);
        Iterator<Edge> edgeIterator = callGraph.edgesInto(curMethod);
        while (edgeIterator.hasNext()){
            SootMethod src = edgeIterator.next().src();
            if(reachableMethods.contains(src))
                continue;
            getReachableMethodInDepth(reachableMethods,src,depth+1);
        }
    }

    public static void getPathBackWard(Unit curUnit, Unit beginUnit, int depth, List<List<Unit>> res, DirectedGraph<Unit> graph,List<Unit> path){
        if(depth>20)
            return;
        if(curUnit.equals(beginUnit)){
            //如果找到了本语句
            res.add(path);
        }
        //我们接着找到所有
        for(Unit preUnit:graph.getPredsOf(curUnit)){
            if(path.contains(preUnit))
                continue;
            List<Unit> newPath=new ArrayList<>(path);
            newPath.add(preUnit);
            getPathBackWard(preUnit,beginUnit,depth+1,res,graph,newPath);
        }
    }

    public static void getReachableMethodInGivenSet(SootMethod curMethod,HashSet<SootMethod> givenMethods,int depth,List<List<String>> paths,List<String> path){
        if(depth>5)
            return;
        if(givenMethods.contains(curMethod)) {
            paths.add(path);
        }
        CallGraph callGraph = Scene.v().getCallGraph();
        Iterator<Edge> edgeIterator = callGraph.edgesOutOf(curMethod);
        while (edgeIterator.hasNext()){
            SootMethod tgt = edgeIterator.next().tgt();
            List<String> newPath=new ArrayList<>(path);
            newPath.add(tgt.getSignature());
            getReachableMethodInGivenSet(tgt,givenMethods,depth+1,paths,newPath);
        }
    }

    public static void getReachableMethodInGivenSet(SootMethod curMethod,HashSet<SootMethod> givenMethods,int depth,List<String> res,HashSet<SootMethod> visited){
        if(depth>5)
            return;
        if(visited.contains(curMethod))
            return;
        visited.add(curMethod);
        if(givenMethods.contains(curMethod))
            res.add(curMethod.getSignature());
        CallGraph callGraph = Scene.v().getCallGraph();
        Iterator<Edge> edgeIterator = callGraph.edgesOutOf(curMethod);
        while (edgeIterator.hasNext()){
            SootMethod tgt = edgeIterator.next().tgt();
            getReachableMethodInGivenSet(tgt,givenMethods,depth+1,res,visited);
        }

    }


    public static InvokeExpr getInvokeExpr(Unit u){
        if(u instanceof AssignStmt){
            AssignStmt assignStmt = (AssignStmt) u;
            if(assignStmt.containsInvokeExpr())
                return assignStmt.getInvokeExpr();
        }
        if(u instanceof InvokeStmt){
            InvokeStmt invokeStmt = (InvokeStmt) u;
            return invokeStmt.getInvokeExpr();
        }

        return null;

    }








}
