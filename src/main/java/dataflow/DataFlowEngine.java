package dataflow;

import constant.CollectionsUsageDefinition;
import constant.StrawPointsDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.VariableBox;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.tagkit.AttributeValueException;
import soot.tagkit.Tag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.MHGPostDominatorsFinder;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import soot.util.queue.QueueReader;

import java.util.*;

public class DataFlowEngine {


    //用于检测应用中敏感数据的使用
    //主要工作是进行数据流分析
    public static final Logger logger = LoggerFactory.getLogger(DataFlowEngine.class);


    //整个程序的ICFG
    //整个数据流分析必须依赖于这个过程间控制流图
    public static JimpleBasedInterproceduralCFG icfg = null;

    //定义的一些wrapper
    //主要是对一些系统方法的总结，指导分析
    public static TaintWrapper taintWrapper = new TaintWrapper();

    //记录输入方法外的变量和它对该方法的影响
    //对已经分析过的方法的summary,避免重复分析
    //其中key是相应方法和污染变量的映射，value是污染变量在方法中传播可以传播出的污染变量
    public static HashMap<Integer, HashSet<ValueBox>> inputMapOutCache = new HashMap<>();

    //静态字段和他们的加载点之间的映射
    //对于静态字段的分析我们使用特殊的方式进行处理
    public MultiMap<SootField, Unit> staticField2MapLoadSite = new HashMultiMap<>();
    //单例类字段和他们的加载点的映射
    public MultiMap<SootField,Unit> singleTonFieldMap2LoadSite=new HashMultiMap<>();

    //从sink点向下分析的最大深度，深度越大，精确度越低
    protected int maxDepth = 10;

    //用户定义的规则检查器，针对不同的问题，用户必须定义相应的规则检查器
    public RuleChecker checker = null;//规则检查器

    //设置分析引擎的规则检查器
    public void setChecker(RuleChecker checker) {
        this.checker = checker;
    }

    //隐式流模式，默认情况下不处理隐式流，因为隐式流回造成非常大的误报以及开销
    private boolean implicitMode=false;

    public void setImplicitMode(boolean implicitMode){
        this.implicitMode=implicitMode;
    }

    //已经进行过全局追踪的污染字段
    private HashSet<SootField> tracedGlobalTaintValue=new HashSet<>();

    //分析开始的时间
    private long start_time;

    //用户定义time out时间
    private double time_out=300;

    public void setTimeOut(double time_out){
        this.time_out=time_out;
    }

    //设置time_out标志位，如果时间超时，将被设置为true

    private boolean is_time_out(){
        if((System.nanoTime()-start_time)/1E9>time_out)
            return true;
        return false;
    }

    private boolean isTrackGlobalTaintValue=false;

    public void setTracedGlobalTaintValue(boolean isTrackGlobalTaintValue){
        this.isTrackGlobalTaintValue=isTrackGlobalTaintValue;
    }

    public final Tag retTag = new Tag() {
        @Override
        public String getName() {
            return "RetValue";
        }

        @Override
        public byte[] getValue() throws AttributeValueException {
            return new byte[0];
        }
    };

    public final Tag paramTag = new Tag() {
        @Override
        public String getName() {
            return "Param";
        }

        @Override
        public byte[] getValue() throws AttributeValueException {
            return new byte[0];
        }
    };

    public final Tag staticFiledTag = new Tag() {
        @Override
        public String getName() {
            return "StaticField";
        }

        @Override
        public byte[] getValue() throws AttributeValueException {
            return new byte[0];
        }
    };

    public final Tag thisTag = new Tag() {
        @Override
        public String getName() {
            return "This";
        }

        @Override
        public byte[] getValue() throws AttributeValueException {
            return new byte[0];
        }
    };

    public final Tag instanceFieldTag = new Tag() {
        @Override
        public String getName() {
            return "InstanceField";
        }

        @Override
        public byte[] getValue() throws AttributeValueException {
            return new byte[0];
        }
    };

    static class MethodTaintOutPut {
        /*

         */
        public HashSet<ValueBox> taintValueBox;
        public SootMethod taintMethod;

        public MethodTaintOutPut(HashSet<ValueBox> taintValueBox, SootMethod taintMethod) {
            this.taintValueBox = taintValueBox;
            this.taintMethod = taintMethod;
        }

        @Override
        public boolean equals(Object obj) {
            if(!(obj instanceof MethodTaintOutPut))
                return false;
            MethodTaintOutPut taint = (MethodTaintOutPut) obj;
            //比较方法是否相同
            if(!taint.taintMethod.equals(this.taintMethod))
                return false;
            //比较方法总结的污染是否相同


            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taintMethod, taintValueBox);
        }
    }

    public void setMaxDepth(int depth){
        this.maxDepth=depth;
    }
    /*
    构造方法，参数ICFG,用户必须提供一个程序的过程间控制流图
     */
    public DataFlowEngine(JimpleBasedInterproceduralCFG icfg) {
        this.icfg = icfg;
        preAnalysis();
        start_time=System.nanoTime();
    }

    /*
    指定方法内的某条语句进行数据追踪
     */
    public void runFlowAnalysis(SootMethod method, Unit sinkUnit, String accessPath) {
        if(!(sinkUnit instanceof AssignStmt)&&!(sinkUnit instanceof IdentityStmt)) {
            logger.info("{} is not an assign unit!",sinkUnit);
            return;
        }
        ValueBox headBox = sinkUnit.getDefBoxes().get(0);
        headBox.addTag(new AccessPathTag());
        if(accessPath!=null&&!accessPath.isEmpty()){
            AccessPathTag accessPathTag = new AccessPathTag();
            accessPathTag.appendAccessPath(accessPath);
            headBox.addTag(accessPathTag);
            headBox.addTag(instanceFieldTag);
        }else {
            headBox.addTag(new AccessPathTag());
        }
        HashSet<ValueBox> outPut = forwardDataFlow(method, null, sinkUnit, headBox, -1, 0);
        //记录处理过的调用
        if(outPut==null)
            return;
        HashSet<Unit> visitedInvoke=new HashSet<>();
        MethodTaintOutPut methodTaintOutPut = new MethodTaintOutPut(outPut, method);
        Queue<MethodTaintOutPut> queue = new LinkedList<>();
        queue.add(methodTaintOutPut);
        while (!queue.isEmpty()) {
            MethodTaintOutPut poll = queue.poll();
            SootMethod callee = poll.taintMethod;
            HashSet<ValueBox> taintValueBox = poll.taintValueBox;
            for (Unit callSiteStmt : icfg.getCallersOf(callee)) {
                if(visitedInvoke.contains(callSiteStmt))
                    continue;
                visitedInvoke.add(callSiteStmt);
                SootMethod caller = icfg.getMethodOf(callSiteStmt);
                InvokeExpr invokeExpr = getInvokeExpr(callSiteStmt);
                if (invokeExpr == null)
                    continue;
                for (ValueBox valueBox : taintValueBox) {
                    //我们需要依据返回的结果类型进行分析
                    if (valueBox.getTag("Param") != null) {
                        //如果是参数返回我们应该继续传播该参数
                        int index = callee.retrieveActiveBody().getParameterLocals().indexOf((Local) valueBox.getValue());
                        //我们应该得到该参数的实参
                        if(index==-1)
                            continue;
                        Value arg = invokeExpr.getArg(index);
                        if(arg instanceof Constant)
                            continue;
                        VariableBox variableBox = new VariableBox(arg);
                        variableBox.addTag(new AccessPathTag(valueBox.getTag("AccessPath")));
                        //对于参数，其实是应该做别名分析的，但这里，我们不再做了
                        HashSet<ValueBox> valueBoxes = forwardDataFlow(caller, null, callSiteStmt, variableBox, -1, 0);
                        if (valueBoxes!=null&&valueBoxes.size() != 0) {
                            MethodTaintOutPut res = new MethodTaintOutPut(valueBoxes, caller);
                            queue.add(res);
                        }
                    } else if (valueBox.getTag("RetValue") != null) {
                        //如果是返回值，那么左值被污染
                        if (callSiteStmt instanceof AssignStmt) {
                            AssignStmt assignStmt = (AssignStmt) callSiteStmt;
                            VariableBox variableBox = new VariableBox(assignStmt.getDefBoxes().get(0).getValue());
                            variableBox.addTag(new AccessPathTag(valueBox.getTag("AccessPath")));
                            HashSet<ValueBox> valueBoxes = forwardDataFlow(caller, null, callSiteStmt, variableBox, -1, 0);

                            if (valueBoxes!=null&&valueBoxes.size() != 0) {
                                MethodTaintOutPut res = new MethodTaintOutPut(valueBoxes, caller);
                                queue.add(res);
                            }
                        }

                    } else if (valueBox.getTag("This") != null) {
                        //应该继续传播该实例
                        ValueBox baseValue = invokeExpr.getUseBoxes().get(invokeExpr.getUseBoxes().size() - 1);
                        VariableBox variableBox = new VariableBox(baseValue.getValue());
                        variableBox.addTag(new AccessPathTag());
                        HashSet<ValueBox> valueBoxes = forwardDataFlow(caller, null, callSiteStmt, variableBox, -1, 0);
                        if (valueBoxes!=null&&valueBoxes.size() != 0) {
                            MethodTaintOutPut res = new MethodTaintOutPut(valueBoxes, caller);
                            queue.add(res);
                        }
                    } else if (valueBox.getTag("InstanceField") != null&&!(invokeExpr instanceof StaticInvokeExpr)) {
                        //如果是实例字段被污染，我们应该去做别名分析
                        Value baseBox = getBaseBox(invokeExpr);
                        VariableBox variableBox = new VariableBox(baseBox);
                        variableBox.addTag(valueBox.getTag("AccessPath"));
                        //找到base的别名，可能是字段，也可能是一个引用
                        HashSet<ValueBox> valueBoxes = onDemandBackWardAliasAnalyze(caller, variableBox, callSiteStmt);
                        variableBox.addTag(instanceFieldTag);
                        valueBoxes.add(variableBox);
                        for (ValueBox alias : valueBoxes) {
                            //我们要对数据流分析
                            HashSet<ValueBox> valueBoxes1 = forwardDataFlow(caller, null, callSiteStmt, alias, -1, 0);
                            if (valueBoxes1!=null&&valueBoxes1.size() != 0) {
                                MethodTaintOutPut res = new MethodTaintOutPut(valueBoxes1, caller);
                                queue.add(res);
                            }

                        }
                    }

                }
            }
        }
    }

    //获得语句中的调用表达式
    public InvokeExpr getInvokeExpr(Unit unit) {
        if (unit instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) unit;
            if (assignStmt.containsInvokeExpr())
                return assignStmt.getInvokeExpr();
        } else if (unit instanceof InvokeStmt) {
            InvokeStmt invokeStmt = (InvokeStmt) unit;
            return invokeStmt.getInvokeExpr();
        }
        return null;
    }

    /*
    功能：该方法进行过程间的数据流分析，除了精确度的一个问题，就是它的数据流分析只能追述到source存在的方法以及相关的调用，对于source所在的方法的调用者不具有追踪能力
    注意：因为通过递归的方式，因此这部分代码是上下文敏感的，因此可以减少异步分误报
    该部分也可以写成非递归的方式进行优化
    输入：要分析的方法，污染变量，参数编号，调用深度
    污染变量是ValueBox的形式，它有一些辅助tag,我们这里必需的是AccessPath,valuebox的value是变量名，access path是它在堆上的具体值
    比如：一个污染变量value是r10,它的access path是x.y.那么它真实污染的值是r10.x.y
    一个valuebox它的access path为空，表示这个就是这个引用指向的对象被污染了
    对于静态变量，value就是这个字段本身
     */
    public HashSet<ValueBox> forwardDataFlow(SootMethod method, ValueBox valueBox, Unit taintUnit, ValueBox taintValueBox, int paramIndex, int depth) {
        /*
        ===============================================================================================
        ========================================数据流分析引擎============================================
        ===============================================================================================
       流敏感，上下文敏感，字段敏感
       导致精确度损失的主要原因有：别名（没有特别好的建模），cg的构建算法，一些系统类，native方法，Java反射等。
         */
        //超过指定的最大深度返回
        if (depth > maxDepth) {
            logger.warn("exceed the max depth ...");
            return null;
        }
        if(is_time_out()){
            logger.info("Analyze time out!");
            return null;
        }

        //todo:我们没用icfg中提供的指定的cfg,再次版本迭代的时候一定记得更新替换

        //创建CFG

        //实例字段的
        Body body;
        if(method.isPhantom()||method.isNative()||!method.isConcrete()){
            logger.info("No body for {}",method.getSignature());


            //我们需要对传入污染字段进行返回，因为我们处理不掉
            if(taintUnit!=null&&taintValueBox!=null){
                return null;
            }
            HashSet<ValueBox> ans=new HashSet<>();

            if(paramIndex==-1){
                //如果是静态字段
                valueBox.addTag(staticFiledTag);
            }else if(paramIndex==-2){
                //如果是实例字段
                valueBox.addTag(instanceFieldTag);
            }else if(paramIndex==-3){
                //如果是对象本身
                valueBox.addTag(thisTag);
            }else if(paramIndex>=0){
                //如果是参数
                valueBox.addTag(paramTag);
            }
            ans.add(valueBox);
            return ans;
        }
        body = method.retrieveActiveBody();

        BriefUnitGraph graph = new BriefUnitGraph(body);
        Unit unit = null;

        //我们用paramIndex的状态区分影响方法的是：参数，静态变量，实例字段
        if (taintUnit != null && taintValueBox != null) {
            //如果taintUnit，taintValueBox不为null,说明是指定的污染变量和污染语句
            valueBox = taintValueBox;
            unit = taintUnit;
        } else if (paramIndex >= 0) {
            //paramIndex大于等于0表示实际参数
            //我们要找到方法内具体的值
            unit = getArgumentsAssignment(method, paramIndex);//找到方法内的具体赋值类似：r1=:@Parameter0,表示将第0个参数赋值给r1
            if (unit == null)
                return null;
            ValueBox paramBox = unit.getDefBoxes().get(0);
            paramBox.addTag(new AccessPathTag(valueBox.getTag("AccessPath")));//实参对象及其对应的AccessPath,比如实参是a,它的access path是.x.y
            paramBox.addTag(paramTag);//打上参数的tag
            valueBox = paramBox;
        } else if (paramIndex == -1) {
            //等于-1,表示静态字段
            //我们要将该字段在所有方法中检索
            for (Unit head : graph.getHeads()) {
                if (!head.toString().equals("@caughtexception")) {
                    unit = head;
                    break;
                }
            }
        } else if (paramIndex == -2) {
            //等于-2，表示实例字段
            Value thisLocal =(Value) method.retrieveActiveBody().getThisLocal();
            AccessPathTag accessPath = new AccessPathTag(valueBox.getTag("AccessPath"));
            VariableBox variableBox = new VariableBox(thisLocal);
            variableBox.addTag(accessPath);
            variableBox.addTag(instanceFieldTag);
            valueBox = variableBox;
            unit = method.retrieveActiveBody().getThisUnit();
        } else if (paramIndex == -3) {
            //等于-3,表示对象本身，我们直接找到对象本身
            Unit thisUnit = method.retrieveActiveBody().getThisUnit();
            Local thisLocal = method.retrieveActiveBody().getThisLocal();
            unit=thisUnit;
            valueBox=new VariableBox(thisLocal);
            valueBox.addTag(thisTag);
            valueBox.addTag(new AccessPathTag());
        }

        //我们去缓存中查找是否对该方法的该污染变量已经处理过了
        //我们需要综合方法名+变量名+accesspath对它进行处理
        String key = method.getSignature() + valueBox.getValue().toString() + ((AccessPathTag) valueBox.getTag("AccessPath")).getFieldChain();
        int mark = key.hashCode();
        if (inputMapOutCache.containsKey(mark))
            return inputMapOutCache.get(mark);

        EventQueue workList = new EventQueue();
        workList.add(new Event(unit, valueBox));
        HashSet<Event> processedEvent = new HashSet<>();
        HashSet<ValueBox> res = new HashSet<>();//用于记录该方法可以影响的方法外的变量，如返回值，参数被污染，实例字段被污染，静态字段被污染
        //已经
        HashSet<Unit> checkedIfSet=new HashSet<>();

        //对于我们设置的深度，我们认为应该设置相应的超时，否则，会出现比较大的错误，深度根据当前的depth来决定，越深的话我们认为它的超时应该最少
        //我们认为如果不考虑过程间分析的话，一个方法分析的最小单元是5秒，因为调用，理论上分析应该是指数级别的，但这里我们用最线性时间
        //对于第k层的分析，我们给他的时间的阈值为（Max_Depth-K）*U
        while (!workList.isEmpty()) {
            if(is_time_out()){
                logger.info("Analyze time out!");
                return null;
            }
            Event poll = workList.poll();
            processedEvent.add(poll);
            Unit curUnit = poll.unit;//当前语句
            ValueBox curValueBox = poll.valueBox;//传播到当前语句中的污染变量
//            if(1==1) {
//            logger.info("[Taint Unit]: {}",curUnit);
//            logger.info("[Taint Value]: {}",curValueBox.getValue());
//            logger.info("[Method]: {}",method.getSignature());
//            AccessPathTag accessPath = (AccessPathTag) curValueBox.getTag("AccessPath");
//            logger.info("[AccessPath]: {}",accessPath.getFieldChain());
//            }
//
            //Event表示已知在当前的unit中value是污染的，想知道后续指令中污点分析的情况
            //判断下条语句类型
            //四种种情况
            //1、调用点
            //2、返回点
            //3、赋值语句
            //4、条件控制语句等

            //1、首先处理返回点的情况
            //如果当前语句是返回值被污染，例如return r,而r正是被污染的变量，我们应该将他的信息记录下来，并返回给调用点的左值
            //如果是全局变量，我们应该返回给调用点，在调用点添加全局变量
            //如果是本对象的字段，我们应该返回给调用点，然后将该字段继续传播
            //如果是局部变量的话，我们的传播就应该结束

            //我们应该考察它的后继指令中的情况
            for (Unit checkedUnit : graph.getSuccsOf(curUnit)) {
                if (!isValueDefinedInUnit(checkedUnit, curValueBox.getValue())) {
                    //1、首先处理返回点的情况
                    //如果当前语句是返回值被污染，例如return r,而r正是被污染的变量，我们应该将他的信息记录下来，并返回给调用点的左值
                    //如果是全局变量，我们应该返回给调用点
                    //如果是本对象的字段，我们应该返回给调用点
                    //如果是局部变量的话，我们的传播就应该结束
                    //如果是参数，我们也应该返回该调用点
                    if ((checkedUnit instanceof ReturnStmt) || (checkedUnit instanceof RetStmt) || (checkedUnit instanceof ReturnVoidStmt)) {

                        Value value = curValueBox.getValue();
                        if(!(value instanceof StaticFieldRef)&&!method.isStatic()){
                            //如果不是静态字段,且本方法不是静态方法
                            AccessPathTag accessPath = (AccessPathTag) curValueBox.getTag("AccessPath");
                            Local thisLocal = method.retrieveActiveBody().getThisLocal();
                            if(thisLocal.equals(value)&&accessPath.getFieldLength()>0){
                                curValueBox.addTag(instanceFieldTag);
                            }else if(thisLocal.equals(value)){
                                curValueBox.addTag(thisTag);
                            }
                        }else {
                            curValueBox.addTag(staticFiledTag);
                        }
                        if (isValueUsedInUnit(checkedUnit, curValueBox.getValue())) {
                            //如果是返回值被污染，我们要给污染变量加上tag
                            curValueBox.addTag(retTag);
                            res.add(curValueBox);
                        } else if (curValueBox.getTag("InstanceField") != null&&!method.isStatic()) {
                            //todo。这里应该考虑的两种情况，一种是传入的字段是不是应该继续传出去，另一种是产生的污染字段是不是应该传出去
                            //todo,对于新污染的字段，需要在污染时的判断加上InstanceField
                            Local thisLocal = method.retrieveActiveBody().getThisLocal();
                            //如果被标记为本实例方法的字段，并且传入到了这里，就应该继续传出去
                            //我们需要辨别哪些字段是本实例的字段
                            //判断实例字段是不是本方法的字段用比较粗糙的方法，直接查看这个字段的形式是不是r0.field
                            //其实这是不精确的，例如a=getField(),a=taint，因为饿哦们没有对getFiled进行处理，因此精度会有损失
                            String baseValue = curValueBox.getValue().getType().toString();
                            String className = method.getDeclaringClass().getName();
                            if (baseValue.equals(thisLocal.toString()) || className.equals(baseValue)) {
                                //实例字段返回
                                res.add(curValueBox);
                            }
                        } else if (curValueBox.getTag("StaticField") != null) {
                            res.add(curValueBox);
                        } else if (curValueBox.getTag("Param") != null) {
                            res.add(curValueBox);
                        } else if (curValueBox.getTag("This") != null || (!method.isStatic()&&curValueBox.getValue().equals(method.retrieveActiveBody().getThisLocal()))) {
                            res.add(curValueBox);
                        } else if (curValueBox.getValue() instanceof Local && method.retrieveActiveBody().getParameterLocals().contains((Local) curValueBox.getValue())) {
                            //如果参数在方法执行过程总被污染了
                            curValueBox.addTag(paramTag);
                            res.add(curValueBox);
                        }
                    } else if ((checkedUnit instanceof InvokeStmt) || ((checkedUnit instanceof AssignStmt) && ((AssignStmt) checkedUnit).containsInvokeExpr())) {
                        //2、接着处理调用点的情况
                        //传入进去的有四种情况：全局变量，参数，实例字段，对象本身
                        //判断是不是sink
                        checker.isSink(checkedUnit,curValueBox);

                        if (isValueUsedInUnit(checkedUnit, curValueBox.getValue()) || curValueBox.getValue() instanceof StaticFieldRef) {
                            InvokeExpr invokeExpr ;
                            if (checkedUnit instanceof InvokeStmt) {
                                InvokeStmt invokeStmt = (InvokeStmt) checkedUnit;
                                invokeExpr = invokeStmt.getInvokeExpr();
                            } else {
                                AssignStmt assignStmt = (AssignStmt) checkedUnit;
                                invokeExpr = assignStmt.getInvokeExpr();
                            }

                            //获取被调用的方法
                            SootMethod callee = invokeExpr.getMethod();
                            //用户一定要重新定义该checker

                            //我们这里是不是需要考虑下ICC
                            if (callee.isNative()) {
                                //我们不处理native方法
                                Event event = new Event(checkedUnit, curValueBox);
                                if (!processedEvent.contains(event) && !workList.contains(event))
                                    workList.add(event);
                            }
                            else if (isSystemClass(callee.getDeclaringClass().getName())) {
                                if (!invokeExpr.getArgs().contains(curValueBox.getValue())) {
                                    Event event = new Event(checkedUnit, curValueBox);
                                    if (!processedEvent.contains(event) && !workList.contains(event))
                                        workList.add(event);
                                }
                                //我们这里需要考虑调用方法是不是一些异步的操作
                                CallGraph callGraph = Scene.v().getCallGraph();
                                Iterator<Edge> edgeIterator = callGraph.edgesOutOf(checkedUnit);
                                boolean flag = false;
                                while (edgeIterator.hasNext()) {
                                    Edge edge = edgeIterator.next();
                                    Kind kind = edge.kind();
                                    if (kind.isFake() || kind.toString().equals("CALLBACK")||kind.toString().equals("SYSTEM_CALL")) {
                                        //我们要处理掉异步调用中的接口调用和系统中的回调
                                        flag = true;
                                        SootMethod calledMethod = edge.tgt();
//                                        System.out.println("实际调用方法： " + calledMethod.getSignature());
                                        if (kind.isFake()) {
                                            //如果是Soot为我们添加的一些特殊方法
                                            if (kind.isThread()) {
                                                //对于Thread其实对应的就是run方法，run方法只需要关注对应的thread实例或者实例字段被污染或者污染字段被污染即可
                                                if (curValueBox.getValue() instanceof StaticFieldRef) {
                                                    forwardDataFlow(calledMethod, curValueBox, null, null, -1, depth + 1);
                                                } else if (isValueUsedInUnit(checkedUnit, curValueBox.getValue())) {
                                                    AccessPathTag accessPath = (AccessPathTag) curValueBox.getTag("AccessPath");
                                                    //其实这里不需要这样区分是实例，还是字段，access path本身就是区分
                                                    if (accessPath.getFieldLength() == 0) {
                                                        forwardDataFlow(calledMethod, curValueBox, null, null, -3, depth + 1);
                                                    } else {
                                                        forwardDataFlow(calledMethod, curValueBox, null, null, -2, depth + 1);
                                                    }
                                                } else {
                                                    //否则的话，继续传播
                                                    Event event = new Event(checkedUnit, curValueBox);
                                                    if (!processedEvent.contains(event) && !workList.contains(event))
                                                        workList.add(event);
                                                }
                                            } else if (kind.isExecutor()) {
                                                //如果是handler post的方式
                                                if (curValueBox.getValue() instanceof StaticFieldRef) {
                                                    forwardDataFlow(calledMethod, curValueBox, null, null, -1, depth + 1);
                                                } else if (invokeExpr.getArgs().contains(curValueBox.getValue())) {
                                                    //如果是thread被污染的话，我们也应该创博下取
                                                    forwardDataFlow(calledMethod, curValueBox, null, null, -2, depth + 1);

                                                } else {
                                                    Event event = new Event(checkedUnit, curValueBox);
                                                    if (!processedEvent.contains(event) && !workList.contains(event))
                                                        workList.add(event);
                                                }
                                            } else if (kind.isAsyncTask()) {
                                                //如果是异步AsyncTask的方式，我们要考虑参数和返回值
                                                if (curValueBox.getValue() instanceof StaticFieldRef) {
                                                    //如果是静态字段
                                                    forwardDataFlow(calledMethod, curValueBox, null, null, -1, depth + 1);
                                                } else if (invokeExpr.getArgs().contains(curValueBox.getValue())) {
                                                    //如果是参数
                                                    forwardDataFlow(calledMethod, curValueBox, null, null, invokeExpr.getArgs().indexOf(curValueBox.getValue()), depth + 1);
                                                } else if (!(invokeExpr instanceof StaticInvokeExpr)) {
                                                    //如果是实例调用
                                                    Value baseBox = getBaseBox(invokeExpr);
                                                    if (baseBox.equals(curValueBox.getValue())) {
                                                        AccessPathTag accessPath = (AccessPathTag) curValueBox.getTag("AccessPath");
                                                        //其实这里不需要这样区分是实例，还是字段，access path本身就是区分
                                                        if (accessPath.getFieldLength() == 0) {
                                                            forwardDataFlow(calledMethod, curValueBox, null, null, -3, depth + 1);
                                                        } else {
                                                            forwardDataFlow(calledMethod, curValueBox, null, null, -2, depth + 1);
                                                        }
                                                    }
                                                }

                                            } else if (kind == Kind.HANDLER) {
                                                //todo
                                            }
                                        } else if (kind.toString().equals("CALLBACK")) {
                                            //r.setOnClickListener----onClickView,这个时候我们应该考虑的是r有没有被污染，如果r被污染了，那么onClick
                                            if (isValueUsedInUnit(checkedUnit, curValueBox.getValue())) {
                                                //如果是r被污染了，那么就是view受污染了
                                                forwardDataFlow(calledMethod, curValueBox, null, null, 0, depth + 1);
                                            } else if (curValueBox.getValue() instanceof StaticFieldRef) {
                                                //如果是静态字段
                                                forwardDataFlow(calledMethod, curValueBox, null, null, -1, depth + 1);
                                            }
                                        }else if(kind.toString().equals("SYSTEM_CALL")){
                                            if(calledMethod.getName().equals("onDraw")) {
                                                //传播实例字段
                                                if (isValueUsedInUnit(checkedUnit, curValueBox.getValue())) {
                                                    forwardDataFlow(calledMethod, curValueBox, null, null, -2, depth + 1);
                                                } else if (curValueBox.getValue() instanceof StaticFieldRef) {
                                                    //如果是静态字段
                                                    forwardDataFlow(calledMethod, curValueBox, null, null, -1, depth + 1);
                                                }
                                            }
                                        }

                                    }
                                }
                                if (flag)
                                    continue;
                                //判断这个调用的实例是不是用到的污染变量
                                Value baseBox = getBaseBox(invokeExpr);
                                boolean taintedInstance = false;
                                if (baseBox != null) {
                                    taintedInstance = baseBox.equals(curValueBox.getValue());
                                }
                                //判断调用参数是不是污染变量
                                int taintedParamIndex = invokeExpr.getArgs().indexOf(curValueBox.getValue());
                                //如果这个方法用户设置了相应的wrapper，直接用wrapper处理
                                if (taintWrapper.isInInTaintWrapper(callee.getSignature())) {
                                    boolean instance_flag = taintWrapper.canAffectInstance(callee.getSignature(), taintedParamIndex);
                                    //是否能够影响实例
                                    if (instance_flag) {
                                        ValueBox instanceBox = invokeExpr.getUseBoxes().get(invokeExpr.getUseBoxes().size() - 1);
                                        //只要影响到实例我们就应该去别名分析
                                        HashSet<ValueBox> valueBoxes = onDemandBackWardAliasAnalyze(method, instanceBox, checkedUnit);
                                        instanceBox.addTag(new AccessPathTag());
                                        valueBoxes.add(instanceBox);
                                        for (ValueBox alias : valueBoxes) {
                                            Value value = alias.getValue();
                                            //我们要为字段打上tag
                                            if (value instanceof InstanceFieldRef) {
                                                //如果别名是实例字段
                                                InstanceFieldRef fieldRef = (InstanceFieldRef) alias;
                                                VariableBox variableBox1 = new VariableBox(fieldRef.getBase());
                                                AccessPathTag accessPath1 = new AccessPathTag(alias.getTag("AccessPath"));
                                                accessPath1.appendAccessPath(fieldRef.getField().getName());
                                                variableBox1.addTag(accessPath1);
                                                alias = variableBox1;
                                                alias.addTag(instanceFieldTag);
                                            } else if (value instanceof StaticFieldRef) {
                                                alias.addTag(staticFiledTag);
                                            }
                                            Event event = new Event(checkedUnit, alias);
                                            if (!processedEvent.contains(event) && !workList.contains(event))
                                                workList.add(event);
                                        }
                                    }
                                    boolean retValue_flag = taintWrapper.canAffectRetValue(callee.getSignature(), taintedInstance, taintedParamIndex);
                                    //是否能影响返回值
                                    if (retValue_flag) {
                                        if (checkedUnit instanceof AssignStmt) {
                                            ValueBox retBox = checkedUnit.getDefBoxes().get(0);
                                            retBox.addTag(new AccessPathTag());
                                            Event retEvent = new Event(checkedUnit, retBox);
                                            if (!processedEvent.contains(retEvent) && !workList.contains(retEvent))
                                                workList.add(retEvent);
                                        }
                                    }
                                    int affectedParamIndex = taintWrapper.getAffectedParamIndex(callee.getSignature(), taintedInstance, taintedParamIndex);
                                    //判断是否影响相关参数，比如copy(x,y),x被污染，然后赋值给y,y也被污染
                                    if (affectedParamIndex != -1) {
                                        ValueBox argBox = invokeExpr.getArgBox(affectedParamIndex);
                                        argBox.addTag(new AccessPathTag());
                                        Event argEvent = new Event(checkedUnit, argBox);
                                        if (!processedEvent.contains(argEvent) && !workList.contains(argEvent))
                                            workList.add(argEvent);
                                    }

                                } else {
                                    //todo,对于没有给出相关wrapper的系统方法，我们认为返回值被污染，参数被污染
                                    //默认返回值被污染
                                    if (checkedUnit instanceof AssignStmt) {

                                        ValueBox retBox = checkedUnit.getDefBoxes().get(0);
                                        retBox.addTag(new AccessPathTag());
                                        Event retEvent = new Event(checkedUnit, retBox);
                                        if (!processedEvent.contains(retEvent) && !workList.contains(retEvent))
                                            workList.add(retEvent);
                                    }
                                    //默认实例被污染
                                    if (!(invokeExpr instanceof StaticInvokeExpr)) {
                                        //对于集合容器的其实应该更加细粒度的
                                        Value base = getBaseBox(invokeExpr);
                                        VariableBox instanceBox = new VariableBox(base);
                                        //我们需要根据他们的粒度判断是都影响到实例的堆上
                                        //如果影响到实例的堆，我们应该考虑后向分析
                                        if (isMethodCanAffectHeap(invokeExpr.getMethod().getName()) && invokeExpr.getArgs().contains(curValueBox.getValue())) {
                                            //如果作为集合对象中的元素的话，类似于add,put这样的操作比如像r.add()这样的操作，我们应该认为他们的r.element被污染了
                                            //在寻找他们的别名的时候，我们寻找的是r的别名，例如r=r0.field,那么应该被传染的别名应该是r0.field.element
//                                            System.out.println(checkedUnit);
                                            AccessPathTag accessPathTag = new AccessPathTag();
                                            instanceBox.addTag(accessPathTag);
                                            HashSet<ValueBox> valueBoxes = onDemandBackWardAliasAnalyze(method, instanceBox, checkedUnit);
                                            valueBoxes.add(instanceBox);

                                            for (ValueBox alias : valueBoxes) {
                                                Event event = new Event(checkedUnit, alias);
                                                if (!processedEvent.contains(event) && !workList.contains(event)) {
                                                    workList.add(event);
                                                }
                                            }
                                        } else {
                                            //对于editor.put的形式，我们默认editor不是被污染的
                                            if(!StrawPointsDefinition.getSharedPreferencesWriteMethodList().contains(invokeExpr.getMethod().getSignature())){
                                                instanceBox.addTag(new AccessPathTag());
                                                Event event = new Event(checkedUnit, instanceBox);
                                                if (!processedEvent.contains(event) && !workList.contains(event))
                                                    workList.add(event);
                                            }else {
//                                                logger.info("被检测到使用了editor的api: {}",checkedUnit);
                                            }
                                        }
                                    }
                                    //我们默认参数可以继续传播
                                    Event event = new Event(checkedUnit, curValueBox);
                                    if (!processedEvent.contains(event) && !workList.contains(event))
                                        workList.add(event);
                                }
                            } else {
                                //非系统方法
                                //todo,异步是个大问题，异步中的数据流传播？
                                CallGraph callGraph = Scene.v().getCallGraph();
                                Iterator<Edge> edgeIterator = callGraph.edgesOutOf(checkedUnit);
                                while (edgeIterator.hasNext()) {
                                    Edge next = edgeIterator.next();
                                    Kind kind = next.kind();
                                    SootMethod calledMethod = next.getTgt().method();
                                    if(calledMethod.isPhantom())
                                        continue;
                                    if (isSystemClass(calledMethod.getDeclaringClass().getName())) {
                                        Event event = new Event(checkedUnit, curValueBox);
                                        if (!processedEvent.contains(event) && !workList.contains(event))
                                            workList.add(event);
                                        continue;
                                    }
                                    HashSet<ValueBox> resultFromCallee = new HashSet<>();
                                    if (kind.isFake()) {
                                        //如果是Soot为我们添加的一些特殊方法
                                        if (kind.isThread()) {
                                            //对于Thread其实对应的就是run方法，run方法只需要关注对应的thread实例或者实例字段被污染或者污染字段被污染即可
                                            if (curValueBox.getValue() instanceof StaticFieldRef) {
                                                resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -1, depth + 1);
                                            } else if (isValueUsedInUnit(checkedUnit, curValueBox.getValue())) {
                                                AccessPathTag accessPath = (AccessPathTag) curValueBox.getTag("AccessPath");
                                                //其实这里不需要这样区分是实例，还是字段，access path本身就是区分
                                                if (accessPath.getFieldLength() == 0) {
                                                    resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -3, depth + 1);
                                                } else {
                                                    resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -2, depth + 1);
                                                }
                                            } else {
                                                //否则的话，继续传播
                                                Event event = new Event(checkedUnit, curValueBox);
                                                if (!processedEvent.contains(event) && !workList.contains(event))
                                                    workList.add(event);
                                            }
                                        } else if (kind.isExecutor()) {
                                            //如果是handler post的方式
                                            if (curValueBox.getValue() instanceof StaticFieldRef) {
                                                resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -1, depth + 1);
                                            } else if (invokeExpr.getArgs().contains(curValueBox.getValue())) {
                                                //如果是thread被污染的话，我们也应该创博下取
                                                resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -2, depth + 1);

                                            } else {
                                                Event event = new Event(checkedUnit, curValueBox);
                                                if (!processedEvent.contains(event) && !workList.contains(event))
                                                    workList.add(event);
                                            }
                                        } else if (kind.isAsyncTask()) {
                                            //如果是异步AsyncTask的方式，我们要考虑参数和返回值
                                            if (curValueBox.getValue() instanceof StaticFieldRef) {
                                                //如果是静态字段
                                                resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -1, depth + 1);
                                            } else if (invokeExpr.getArgs().contains(curValueBox.getValue())) {
                                                //如果是参数
                                                resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, invokeExpr.getArgs().indexOf(curValueBox.getValue()), depth + 1);
                                            } else if (!(invokeExpr instanceof StaticInvokeExpr)) {
                                                //如果是实例调用
                                                Value baseBox = getBaseBox(invokeExpr);
                                                if (baseBox.equals(curValueBox.getValue())) {
                                                    AccessPathTag accessPath = (AccessPathTag) curValueBox.getTag("AccessPath");
                                                    //其实这里不需要这样区分是实例，还是字段，access path本身就是区分
                                                    if (accessPath.getFieldLength() == 0) {
                                                        resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -3, depth + 1);
                                                    } else {
                                                        resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -2, depth + 1);
                                                    }
                                                }
                                            }

                                        } else if (kind == Kind.HANDLER) {
                                            //todo
                                            //我们现在认为Handler的不应该传入相关的数据
                                        }
                                    }
                                    else if (kind.toString().equals("CALLBACK")) {
                                        //如果是回调事件,callee method是onBack
                                        //r.setOnClickListener----onClickView,这个时候我们应该考虑的是r有没有被污染，如果r被污染了，那么onClick
                                        if (isValueUsedInUnit(checkedUnit, curValueBox.getValue())) {
                                            //如果是r被污染了，那么就是view受污染了
                                            resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, 0, depth + 1);
                                        } else if (curValueBox.getValue() instanceof StaticFieldRef) {
                                            //如果是静态字段
                                            resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -1, depth + 1);
                                        }
                                    } else if (kind.isExplicit()) {
                                        //我们这里需要判断是不是系统类
                                        //这里我们只考虑实例方法和静态方法，对于Soot帮助我们创建的其他方法，我们不考虑
                                        if (curValueBox.getValue() instanceof StaticFieldRef) {
                                            //如果是静态字段
                                            resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -1, depth + 1);
                                        } else if (invokeExpr.getArgs().contains(curValueBox.getValue())) {
                                            //如果是参数
                                            resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, invokeExpr.getArgs().indexOf(curValueBox.getValue()), depth + 1);
                                        } else if (!(invokeExpr instanceof StaticInvokeExpr)) {
                                            //如果是实例调用
                                            Value baseBox = getBaseBox(invokeExpr);
                                            if (baseBox.equals(curValueBox.getValue())) {
                                                AccessPathTag accessPath = (AccessPathTag) curValueBox.getTag("AccessPath");
                                                //其实这里不需要这样区分是实例，还是字段，access path本身就是区分
                                                if (accessPath.getFieldLength() == 0) {
                                                    resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -3, depth + 1);
                                                } else {
                                                    resultFromCallee = forwardDataFlow(calledMethod, curValueBox, null, null, -2, depth + 1);
                                                }
                                            }
                                        }
                                    }

                                    if (resultFromCallee == null)
                                        continue;

                                    //对于调用点返回的信息处理相当重要
                                    for (ValueBox valueMatter : resultFromCallee) {
                                        //返回来的可能是参数，可能是返回值，可能是静态字段，可能是本对象的实例字段
                                        if (valueMatter.getTag("Param") != null) {
                                            //如果参数被污染或者原来就是污染的
                                            //找到对应的实参
                                            int index = getArgumentIndex(calledMethod, valueMatter.getValue());
                                            if(index==-1)
                                                continue;
                                            ValueBox argBox = invokeExpr.getArgBox(index);
                                            argBox.addTag(new AccessPathTag(valueMatter.getTag("AccessPath")));
                                            Event event = new Event(checkedUnit, argBox);
                                            if (!processedEvent.contains(event) && !workList.contains(event))
                                                workList.add(event);
                                        } else if (valueMatter.getTag("This") != null) {
                                            Event event = new Event(checkedUnit, curValueBox);
                                            if (!processedEvent.contains(event) && !workList.contains(event))
                                                workList.add(event);
                                        } else if (valueMatter.getTag("StaticField") != null) {
                                            Event event = new Event(checkedUnit, valueMatter);
                                            if (!processedEvent.contains(event) && !workList.contains(event))
                                                workList.add(event);
                                        } else if (valueMatter.getTag("InstanceField") != null) {
                                            //如果是实例字段返回的话，我们首先要找到这个实例调用方法r.call()和其caller是否指向同一实例
                                            //对于实例字段我们要这样处理，我们首先标记r，access path加入到这个这个对象得Access path中，这个时候要升
                                            //比如，此时返回的是r0.filed,access=null,那么r的access 就应该加入filed
                                            //除此之外我们还应该找打r的别名，r=y,r=call(),r=r1.field,并将自己的access path赋值给他们
                                            //我们要去找这个实例的别名,然后向下传播这个污染的字段
                                            Value baseBox = getBaseBox(invokeExpr);
                                            if(baseBox==null)
                                                continue;
                                            VariableBox variableBox = new VariableBox(baseBox);
                                            variableBox.addTag(valueMatter.getTag("AccessPath"));
                                            //找到base的别名，可能是字段，也可能是一个引用
                                            HashSet<ValueBox> valueBoxes = onDemandBackWardAliasAnalyze(method, variableBox, checkedUnit);

                                            variableBox.addTag(instanceFieldTag);
                                            valueBoxes.add(variableBox);
                                            for (ValueBox alias : valueBoxes) {
                                                Event event = new Event(checkedUnit, alias);
                                                if (!processedEvent.contains(event) && !workList.contains(event))
                                                    workList.add(event);
                                            }
                                        } else if (valueMatter.getTag("RetValue") != null) {
                                            //对于返回值我们要处理它的左值
                                            if (checkedUnit instanceof AssignStmt) {
                                                AssignStmt assignStmt = (AssignStmt) checkedUnit;
                                                AccessPathTag accessPath = new AccessPathTag(valueMatter.getTag("AccessPath"));
                                                ValueBox retBox = assignStmt.getDefBoxes().get(0);
                                                retBox.addTag(accessPath);
                                                Event event = new Event(checkedUnit, retBox);
                                                if (!processedEvent.contains(event) && !workList.contains(event))
                                                    workList.add(event);
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            //如果传播到此的污染变量没有被使用，我们将该污染变量继续传播
                            Event transferValue = new Event(checkedUnit, curValueBox);
                            if (!processedEvent.contains(transferValue) && !workList.contains(transferValue))
                                workList.add(transferValue);
                        }
                    } else if (checkedUnit instanceof AssignStmt) {
                        //我们要求污染变量的形式一定是一个变量名，加上它的access path
                        //比如varName=r1,access path=.filedx.fieldy,表示该引用指向对象的fieldx.fieldy是被污染的
                        //3、如果是一般赋值语句
                        AssignStmt assignStmt = (AssignStmt) checkedUnit;
                        //对于赋值，有以下几种情况
                        //1、直接赋值，例如x=y，如果y污染，x也是污染的,这里其实x和y是别名关系，如果我们知道，y指向的某个字段是污染的，那么x指向的那个字段也是污染的，如果缺少别名分析，那么这种精度就会丢掉
                        //2、store，例如x.filed=y，如果y是污染的，那么x.filed是污染的，x.filed和y也是别名关系
                        //3、load,例如x=y.filed,如果y.field是污染的，那么x也是污染的，并且x和y.field也是污染的
                        //4、数组元素。比如x=a[i]，如果a中的某个元素被污染了，那么我们认为整个数组被污染了，可以把数组看成一个一个特殊的对象x.element
                        //5、调用返回值，a=b(),这种情况需要查看返回值有没有被污染，这种情况，我们在处理调用的时候进行处理
                        //6、创建对象，a=new A()

                        //我们这里需要处理别名分析的情况，但我们只处理一个方法内的别名分析
                        //只需要关注store这种形式，当遇到这种情形时进行后向的别名分析

                        Value leftOp = assignStmt.getLeftOp();
                        Value rightOp = assignStmt.getRightOp();
                        ValueBox leftBox = assignStmt.getDefBoxes().get(0);

                        //先将污染值继续传播
                        Event transferValue = new Event(checkedUnit, curValueBox);
                        if (!processedEvent.contains(transferValue) && !workList.contains(transferValue)) {
                            workList.add(transferValue);
                        }

                        //污染变量的形式如x.a.b.c.d,
                        //如果y=x.a,则它的y.a.b.c.d被污染
                        //如果y.g=x.a.b.c.d,那么记为y.g.a.b.c.d被污染


                        //对于赋值语句，有以下几种形式
                        //x=y
                        //x=y.filed
                        //x=.filed
                        //x=call()
                        //x=array[i]
                        //x=(cast)
                        //x.filed=y

                        if ((leftOp instanceof InstanceFieldRef) && isUsedInRightOp(checkedUnit, curValueBox.getValue())) {
                            //1、store
                            //如果x.a被污染了，那么如果y是x的别名，那么此时，y.a也应该是污染的
                            //我们此时应该找到方法内x在此时的所有别名信息，然后追踪他们的该字段
                            //找到别名的办法，最直接是指针分析
                            //我们采用更启发式的办法，我们找到该x.filed=taint,直接赋值语句，递归查找这些赋值语句，对于控制流以上的，我们使他在checkUnit之后有效
                            //我们向下寻找类似y=x,的赋值语句，然后在此语句处，我们保证在到达y=x的路径上，该字段没有被污染
                            //on-demand的别名分析
                            InstanceFieldRef instanceFieldRef = (InstanceFieldRef) leftOp;
                            AccessPathTag accessPath = new AccessPathTag(curValueBox.getTag("AccessPath"));
                            accessPath.appendAccessPath(instanceFieldRef.getField().getName());
                            VariableBox variableBox = new VariableBox(instanceFieldRef.getBase());
                            variableBox.addTag(accessPath);
                            variableBox.addTag(instanceFieldTag);
                            //先进性判断是不是全局变量，全局变量包括静态字段和单例的字段
                            transferGlobalValue(accessPath.getFieldChain(),instanceFieldRef.getField());
                            HashSet<ValueBox> aliasValueSet = onDemandBackWardAliasAnalyze(method, variableBox, checkedUnit);//获得变量的别名信息
                            aliasValueSet.add(variableBox);
                            //y的别名可能有y=x.filed,y=x,y=(cast)x;
                            //我们需要将它的信息传播下去
                            //y.filed=x,我们直接将y.field加入
                            //对于它的别名，我们也将其加入
                            //我们要将新被污染的变量加入进来
                            for (ValueBox alias : aliasValueSet) {
                                //需要根据他们的类型分别处理,x.filed=taint找到x的所有别名
                                Event event = new Event(checkedUnit, alias);
                                if (!processedEvent.contains(event) && !workList.contains(event))
                                    workList.add(event);

                            }
                        } else if ((leftOp instanceof StaticFieldRef) && isUsedInRightOp(checkedUnit, curValueBox.getValue())) {
                            leftBox.addTag(new AccessPathTag(curValueBox.getTag("AccessPath")));
                            leftBox.addTag(staticFiledTag);
                            Event event = new Event(checkedUnit, leftBox);
                            if (!processedEvent.contains(event) && !workList.contains(event)) {
                                workList.add(event);
                            }
                            //处理全局变量
                            transferGlobalValue(((AccessPathTag)curValueBox.getTag("AccessPath")).getFieldChain(),((StaticFieldRef)leftOp).getField());

                        } else if ((rightOp instanceof StaticFieldRef) && isUsedInRightOp(checkedUnit, curValueBox.getValue())) {
                            //2、load,继续,如果y=x.taintField,不管我们的时x被污染，还是x.taintField被污染，左值都会被污染
                            //这种情况不管是x被污染还是该字段被污染，作者都会被污染
                            leftBox.addTag(new AccessPathTag(curValueBox.getTag("AccessPath")));
                            Event event = new Event(checkedUnit, leftBox);
                            if (!processedEvent.contains(event) && !workList.contains(event)) {
                                workList.add(event);
                            }
                        } else if ((rightOp instanceof InstanceFieldRef) && isUsedInRightOp(checkedUnit, curValueBox.getValue())) {
                            //如果y=r.x,现在考察的是r,//我们要看r的access path是不是和字段x匹配
                            AccessPathTag accessPath = (AccessPathTag) curValueBox.getTag("AccessPath");
                            InstanceFieldRef fieldRef = (InstanceFieldRef) rightOp;
                            if (accessPath.match(fieldRef.getField().getName())) {
                                //y的access path等于r的access path减去相r
                                AccessPathTag accessPath1 = new AccessPathTag(accessPath);
                                accessPath1.removeAccessPath();
                                leftBox.addTag(accessPath1);
                                Event event = new Event(checkedUnit, leftBox);
                                if (!processedEvent.contains(event) && !workList.contains(event))
                                    workList.add(event);
                            }
                        } else if ((leftOp instanceof ArrayRef) && isUsedInRightOp(checkedUnit, curValueBox.getValue())) {
                            //3、array store，类似a[i]=taint
                            ArrayRef arrayRef = (ArrayRef) leftOp;
                            ValueBox baseBox = arrayRef.getBaseBox();
                            baseBox.addTag(curValueBox.getTag("AccessPath"));
                            Event event = new Event(checkedUnit, baseBox);
                            if (!processedEvent.contains(event) && !workList.contains(event))
                                workList.add(event);
                        } else if ((rightOp instanceof ArrayRef) && isUsedInRightOp(checkedUnit, curValueBox.getValue())) {
                            //4、array load,类似y=taint[i]
                            //不管操纵数组还是下标我们都认为该元素被污染
                            leftBox.addTag(curValueBox.getTag("AccessPath"));
                            Event event = new Event(checkedUnit, leftBox);
                            if (!processedEvent.contains(event) && !workList.contains(event))
                                workList.add(event);
                        } else if ((rightOp instanceof CastExpr) && isUsedInRightOp(checkedUnit, curValueBox.getValue())) {
                            //5、cast object
                            //y=(cast)x,如果x被污染，y被污染，如果是x.field被污染，那么y.filed被污染
                            leftBox.addTag(new AccessPathTag(curValueBox.getTag("AccessPath")));
                            Event event = new Event(checkedUnit, leftBox);
                            if (!processedEvent.contains(event) && !workList.contains(event))
                                workList.add(event);


                        } else if (rightOp.toString().equals(curValueBox.getValue().toString())) {
                            leftBox.addTag(new AccessPathTag(curValueBox.getTag("AccessPath")));
                            Event event = new Event(checkedUnit, leftBox);
                            if (!processedEvent.contains(event) && !workList.contains(event))
                                workList.add(event);

                        } else if (isUsedInRightOp(checkedUnit, curValueBox.getValue())) {
                            //如果是其他的类型的赋值，我们直接认为左值被污染了，比如一些算术运算，一些类似instance of,cmp的语句,我们直接认为左值被污染了
                            leftBox.addTag(new AccessPathTag());
                            Event event = new Event(checkedUnit, leftBox);
                            if (!processedEvent.contains(event) && !workList.contains(event))
                                workList.add(event);
                        }
                    } else {
                        //如果是污染变量没有在本语句中被使用，就直接传播到下条语句
                        Event event = new Event(checkedUnit, curValueBox);
                        if (!processedEvent.contains(event) && !workList.contains(event))
                            workList.add(event);
                        //我们应该还要处理控制流相关
                        //我们要看下变量是不是在污染条件中被使用
                        if(!(checkedUnit instanceof IfStmt))
                            continue;
                        AccessPathTag pathTag = (AccessPathTag) curValueBox.getTag("AccessPath");
                        if(pathTag.getFieldChain().length()>0)
                            continue;
                        IfStmt ifStmt = (IfStmt) checkedUnit;
                        List<ValueBox> useBoxes = ifStmt.getCondition().getUseBoxes();
                        boolean flag=false;
                        for(ValueBox box:useBoxes){
                            if(box.getValue().equals(curValueBox.getValue()))
                                flag=true;
                        }
                        if(!flag)
                            continue;
                        if(checkedIfSet.contains(checkedUnit))
                            continue;
                        checkedIfSet.add(checkedUnit);
                        if(implicitMode){
                            //如果是条件语句，我们需要关注的地方
                            //1、不同分支可能触发到的触发某些危险调用，仅从控制流上判断
                            //2、不同分支对不同变量的赋值的不同，我们认为这些变量应该是被污染的
                            //3、条件中的数据可能是污染的
                            //4、对于集合类的操作可能是污染的
                            HashSet<Unit> dependUnits = doWithControlFlowDependentUnit(checkedUnit, curValueBox);
                            //todo,这里有个bug没找出来
                            if(dependUnits==null||dependUnits.isEmpty())
                                continue;
                            for(Unit dependUnit:dependUnits){
                                if(dependUnit instanceof InvokeStmt){
                                    //如果是调用，就是对集合的操作，我们认为集合被污染了
                                    InvokeExpr invokeExpr = getInvokeExpr(dependUnit);
                                    if(invokeExpr.getMethod().getDeclaringClass().getName().equals("java.util.Iterator")){
                                        Value baseBox = getBaseBox(invokeExpr);
                                        //查找迭代器的直接赋值语句
                                        Unit defUnit = getDirectDefUnit(dependUnit, baseBox, method);
                                        InvokeExpr defInvoke = getInvokeExpr(defUnit);
                                        if(defInvoke==null){
                                            logger.info("Iterator is get by indirect way!");
                                            continue;
                                        }

                                        String signature = defInvoke.getMethod().getSignature();
                                        if(signature.equals("<java.util.List: java.util.Iterator iterator()>")){
                                            Value base = getBaseBox(defInvoke);
                                            VariableBox variableBox = new VariableBox(base);
                                            variableBox.addTag(new AccessPathTag());
                                            Event new_event = new Event(dependUnit, variableBox);
                                            if (!processedEvent.contains(new_event) && !workList.contains(new_event))
                                                workList.add(new_event);
                                        }else {
                                            logger.info("Iterator is not build by standard API!");
                                        }

                                    }else {
                                        Value baseBox = getBaseBox(invokeExpr);
                                        VariableBox variableBox = new VariableBox(baseBox);
                                        variableBox.addTag(new AccessPathTag());
                                        Event new_event = new Event(dependUnit, variableBox);
                                        if (!processedEvent.contains(new_event) && !workList.contains(new_event))
                                            workList.add(new_event);
                                    }

                                }else if(dependUnit instanceof AssignStmt){
                                    //如果是赋值，我们认为左值是被污染了
                                    AssignStmt assignStmt = (AssignStmt) dependUnit;
                                    Value leftOp = assignStmt.getLeftOp();
                                    if(leftOp instanceof Local){
                                        VariableBox variableBox = new VariableBox(leftOp);
                                        variableBox.addTag(new AccessPathTag());
                                        Event new_event = new Event(dependUnit, variableBox);
                                        if (!processedEvent.contains(new_event) && !workList.contains(new_event))
                                            workList.add(new_event);
                                    }else if(leftOp instanceof InstanceFieldRef){
                                        //如果是实例字段
                                        InstanceFieldRef instanceFieldRef = (InstanceFieldRef) leftOp;
                                        AccessPathTag accessPath = new AccessPathTag();
                                        accessPath.appendAccessPath(instanceFieldRef.getField().getName());
                                        VariableBox variableBox = new VariableBox(instanceFieldRef.getBase());
                                        variableBox.addTag(accessPath);
                                        variableBox.addTag(instanceFieldTag);
                                        Event new_event = new Event(dependUnit, variableBox);
                                        if (!processedEvent.contains(new_event) && !workList.contains(new_event))
                                            workList.add(new_event);

                                    }else if(leftOp instanceof StaticFieldRef){
                                        VariableBox variableBox = new VariableBox(leftOp);
                                        variableBox.addTag(new AccessPathTag());
                                        variableBox.addTag(staticFiledTag);
                                        Event new_event = new Event(dependUnit, variableBox);
                                        if (!processedEvent.contains(new_event) && !workList.contains(new_event))
                                            workList.add(new_event);
                                    }
                                }else if(dependUnit instanceof ReturnStmt){
                                    //如果是返回值
                                    ReturnStmt returnUnit = (ReturnStmt) dependUnit;
                                    ValueBox retBox = returnUnit.getOpBox();
                                    retBox.addTag(retTag);
                                    retBox.addTag(new AccessPathTag());
                                    res.add(retBox);
                                }
                            }
                        }
                    }
                }
            }
        }
        //将本方法需要返回的信息返回给caller
        inputMapOutCache.put(mark, new HashSet<>());
        inputMapOutCache.get(mark).addAll(res);//数据缓存下来
        return res;
    }


    public HashSet<ValueBox> onDemandBackWardAliasAnalyze(SootMethod method, ValueBox valueBox, Unit curUnit) {
        //后向别名分析
        //仅当store发生时进行x.field=taint,我们去寻找x的别名
        //这里我们需要将一些集合类的操作给添加进来，例如一个集合容器发生变化时，也看成store操作
        //这种别名分析算法是不精确的，因为别名分析往往需要精确的指针分析
        //我们的规则很简单
        //向上分析x的直接赋值语句，比如x=y,x=z.filed,这些变量如果在到达x.filed=taint之前是没有被refresh掉的话，那么直接添加进来
        //再往上就不再分析了，意思是不再分析y的别名了，精度确实丧失了
        HashSet<ValueBox> res = new HashSet<>();
        EventQueue workList = new EventQueue();
        workList.add(new Event(curUnit, valueBox));
        //后向分析找到相关的别名
        HashSet<Event> processedEvent = new HashSet<>();
        BriefUnitGraph graph = new BriefUnitGraph(method.retrieveActiveBody());

        while (!workList.isEmpty()) {
            Event poll = workList.poll();
            Unit unit = poll.unit;
            ValueBox defineBox = poll.valueBox;
            processedEvent.add(poll);
            for (Unit preUnit : graph.getPredsOf(unit)) {
                if (isValueDefinedOrUsedInUnit(preUnit, defineBox.getValue())) {
                    //如果变量在此语句中被使用或者定义

                    if (isValueDefinedInUnit(preUnit, defineBox.getValue())) {
                        //如果在此语句中被定义
                        //比如a=b,a=new A(),a=call(),a=r.field,a=(A)b
                        if (preUnit instanceof AssignStmt) {
//                            System.out.println("找到的直接定义语句是：" + preUnit);
                            //可能是赋值语句，也可能是IdentityStmt
                            AssignStmt assignStmt = (AssignStmt) preUnit;
                            Value rightOp = assignStmt.getRightOp();
                            ValueBox rightOpBox = assignStmt.getRightOpBox();
                            if (rightOp instanceof Local) {
                                //如果是局部变量
                                rightOpBox.addTag(new AccessPathTag(valueBox.getTag("AccessPath")));
                                res.add(rightOpBox);
                            } else if (rightOp instanceof InstanceFieldRef) {
                                InstanceFieldRef instanceFieldRef = (InstanceFieldRef) rightOp;
                                Value base = instanceFieldRef.getBase();
                                VariableBox variableBox = new VariableBox(base);
                                AccessPathTag accessPathTag = new AccessPathTag(valueBox.getTag("AccessPath"));
                                accessPathTag.appendAccessPath(instanceFieldRef.getField().getName());
                                variableBox.addTag(accessPathTag);
                                variableBox.addTag(instanceFieldTag);
                                res.add(variableBox);
                            } else if (rightOp instanceof StaticFieldRef) {
                                rightOpBox.addTag(new AccessPathTag(valueBox.getTag("AccessPath")));
                                rightOpBox.addTag(staticFiledTag);
                                res.add(rightOpBox);
                            } else if (rightOp instanceof CastExpr) {
                                CastExpr castExpr = (CastExpr) rightOp;
                                Value op = castExpr.getOp();
                                VariableBox variableBox = new VariableBox(op);
                                variableBox.addTag(new AccessPathTag(valueBox.getTag("AccessPath")));
                                res.add(variableBox);
                            }
                        }
                    } else {
                        Event event = new Event(preUnit, defineBox);
                        if (!workList.contains(event) && !processedEvent.contains(event))
                            workList.add(event);
                    }
                } else {
                    //如果该语句中未使用该变量
                    Event event = new Event(preUnit, defineBox);
                    if (!workList.contains(event) && !processedEvent.contains(event))
                        workList.add(event);
                }
            }
        }
        return res;
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

    private Unit getArgumentsAssignment(SootMethod method, int paramIndex) {
        for (Unit unit : method.retrieveActiveBody().getUnits()) {
            if (unit instanceof IdentityStmt)
                if (unit.toString().contains("@parameter" + paramIndex + ":"))
                    return unit;
        }
        return null;
    }

    private int getArgumentIndex(SootMethod method, Value value) {
        for (Unit u : method.retrieveActiveBody().getUnits()) {
            if (u instanceof IdentityStmt) {
                if (isValueDefinedInUnit(u, value)) {
                    IdentityStmt identityStmt = (IdentityStmt) u;
                    Value rightOp = identityStmt.getRightOp();
                    if(rightOp instanceof ParameterRef) {
                        ParameterRef parameterRef = (ParameterRef) rightOp;
                        return parameterRef.getIndex();
                    }
                }
            }
        }
        return -1;
    }

    private Value getBaseBox(InvokeExpr invokeExpr) {
        //找到调用的实例，比如a.b(xxx),就是找到a
        Value res = null;
        if (invokeExpr instanceof VirtualInvokeExpr)
            res = ((VirtualInvokeExpr) invokeExpr).getBaseBox().getValue();
        else if (invokeExpr instanceof SpecialInvokeExpr)
            res = ((SpecialInvokeExpr) invokeExpr).getBaseBox().getValue();
        else if (invokeExpr instanceof InterfaceInvokeExpr)
            res = ((InterfaceInvokeExpr) invokeExpr).getBaseBox().getValue();
        return res;
    }

    //判断变量在语句中被使用
    public static boolean isValueUsedInUnit(Unit unit, Value value) {
        List<String> usedValue = new ArrayList<>();
        for (ValueBox useBox : unit.getUseBoxes()) {
            usedValue.add(useBox.getValue().toString());
        }
        return usedValue.contains(value.toString());
    }

    //判断变量是否语句中被定义
    public static boolean isValueDefinedInUnit(Unit unit, Value value) {
        if(!(unit instanceof IdentityStmt)&&!(unit instanceof AssignStmt))
            return false;
        for(ValueBox useBox:unit.getUseBoxes()){
            if(useBox.getValue().equals(value))
                return false;
        }
        for (ValueBox defBox : unit.getDefBoxes()) {
            if(defBox.getValue().equals(value))
                return true;
        }
        return false;
    }

    public static boolean isValueDefinedOrUsedInUnit(Unit unit, Value value) {
        for (ValueBox valueBox : unit.getUseAndDefBoxes()) {
            if (valueBox.getValue().toString().equals(value.toString()))
                return true;
        }
        return false;
    }

    //    public static boolean isInRight
    public static boolean isUsedInRightOp(Unit unit, Value value) {
        //需要判断变量是不是在赋值语句的右侧
        if (!(unit instanceof AssignStmt))
            return false;
        AssignStmt assignStmt = (AssignStmt) unit;
        ValueBox rightOpBox = assignStmt.getRightOpBox();
        Queue<ValueBox> queue = new LinkedList<>();
        queue.add(rightOpBox);
        while (!queue.isEmpty()) {
            ValueBox poll = queue.poll();
            if (poll.getValue().equals(value))
                return true;
            queue.addAll(poll.getValue().getUseBoxes());
        }
        return false;
    }

    public boolean isMethodCanAffectHeap(String methodName) {
        String[] methodNameList = {"add", "addAll", "put", "putAll"};
        for (String name : methodNameList) {
            if (name.equals(methodName))
                return true;
        }
        return false;
    }

    public void preAnalysis() {
        //预处理搜集应用中的一些信息
        //处理应用中的静态字段和他们的加载点的映射
        //处理应用中的单例字段和他们加载点的映射
        ReachableMethods reachableMethods = Scene.v().getReachableMethods();
        QueueReader<MethodOrMethodContext> listener = reachableMethods.listener();
        //搜集应用中的所有单例类
        HashSet<SootClass> singleTonClasses=new HashSet<>();
        for(SootClass cls:Scene.v().getApplicationClasses()){
            if(isSingleTonCls(cls))
                singleTonClasses.add(cls);
        }
        while (listener.hasNext()) {
            MethodOrMethodContext method = listener.next();
            SootMethod m = method.method();
            if (m.isConcrete()) {
                try {
                    Body body = m.retrieveActiveBody();
                    for (Unit unit : body.getUnits()) {
                        if (unit instanceof AssignStmt) {
                            AssignStmt assignStmt = (AssignStmt) unit;
                            Value rightOp = assignStmt.getRightOp();
                            if (rightOp instanceof StaticFieldRef) {
                                //记录应用中的静态字段的使用地点
                                SootField field = ((StaticFieldRef) rightOp).getField();
                                if (!staticField2MapLoadSite.containsKey(field)) {
                                    staticField2MapLoadSite.put(field, unit);
                                } else {
                                    staticField2MapLoadSite.get(field).add(unit);
                                }
                            }else if(rightOp instanceof InstanceFieldRef){
                                SootField field = ((InstanceFieldRef) rightOp).getField();
                                if(singleTonClasses.contains(field.getDeclaringClass())){
                                    //如果是单例类的实例字段,我们应该
                                    if(!singleTonFieldMap2LoadSite.containsKey(field)){
                                        singleTonFieldMap2LoadSite.put(field,unit);
                                    }else {
                                        singleTonFieldMap2LoadSite.get(field).add(unit);
                                    }
                                }
                            }
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
//        logger.info("[Static Field Load Sites]: {}",staticField2MapLoadSite.toString());
//        logger.info("[SingleTon Instance Field Load Sites]: {}",singleTonFieldMap2LoadSite.toString());
    }

    private void transferGlobalValue(String accessPath,SootField field){
        if(!isTrackGlobalTaintValue)
            return;
        //传递全局污染变量
        if(tracedGlobalTaintValue.contains(field)){
            logger.info("The global value has been done!");
            return;
        }
        tracedGlobalTaintValue.add(field);

        logger.info("track for global taint value ...");
        //我们这里不追踪静态字段
        if(field.isStatic()&&staticField2MapLoadSite.containsKey(field)){
            //我们找到静态字段的使用位置，然后传播
            for(Unit loaderSite:staticField2MapLoadSite.get(field)){
                SootMethod m = icfg.getMethodOf(loaderSite);
                runFlowAnalysis(m,loaderSite,accessPath);
            }
        }else if(!field.isStatic()&&singleTonFieldMap2LoadSite.containsKey(field)) {
            //如果我们发现污染的是单例字段，我们找到他们所有加载的位置继续传播
            for (Unit loaderSite : singleTonFieldMap2LoadSite.get(field)) {
                SootMethod m = icfg.getMethodOf(loaderSite);
                logger.info("[Global Value Load Site]: {}", loaderSite);
                runFlowAnalysis(m, loaderSite, accessPath);
            }
        }
    }


    public HashSet<Unit> doWithControlFlowDependentUnit(Unit u,ValueBox curValueBox){
        //处理控制流依赖相关的问题
        SootMethod methodOf = icfg.getMethodOf(u);
        if(methodOf==null)
            return new HashSet<>();
        DirectedGraph<Unit> graph = icfg.getOrCreateUnitGraph(icfg.getMethodOf(u));
        MHGPostDominatorsFinder<Unit> postdominatorFinder = new MHGPostDominatorsFinder<Unit>(graph);
        Unit postdom = postdominatorFinder.getImmediateDominator(u);
        //如果postdom是空的话，说明这个分支没有汇聚点
        //我们寻找所有和控制流相关的调用
        HashSet<SootMethod> dependMethods = getAllCallDependCondition(u, postdom);
        //我们找到这些调用，判断他们是否可以执行危险方法，如果是可以执行到危险方法，我们认为他们的参数被污染了

        HashSet<Unit> res=new HashSet<>();
        //不同分支中的赋值
//        HashSet<Unit> dependAssignUnits = getAllValueDependCondition(u, postdom);
//        res.addAll(dependAssignUnits);
        //找到在不同分支中对集合操作的
//        HashSet<Unit> dependCollectionInvokes = getAllCollectionInvokeDependCondition(u, postdom);
//        res.addAll(dependCollectionInvokes);
        //处理可能影响的返回值
        if(postdom==null&&isPrimitiveType(icfg.getMethodOf(u).getReturnType())) {
            HashSet<Unit> returnUnit = getAllReturnUnit(graph);
            res.addAll(returnUnit);
        }
        for(SootMethod dependMethod:dependMethods){
            checker.isDependConditionSink(dependMethod,icfg.getMethodOf(u));
        }
        return res;
    }

    public void buildPaths(Unit end,Unit curUnit,DirectedGraph<Unit> graph,List<Unit> path,List<List<Unit>> res){
        //找到指定起终点间的所有语句
        if(curUnit!=null&&curUnit.equals(end)){
            ArrayList<Unit> addPath = new ArrayList<>(path);
            addPath.add(curUnit);
            res.add(addPath);
            return;
        }
        //如果不是目的语句
        if(!path.contains(curUnit)) {
            path.add(curUnit);
        }else {
            return;
        }
        for(Unit nextUnit:graph.getSuccsOf(curUnit)){
            buildPaths(end,nextUnit,graph,new ArrayList<>(path),res);
        }

    }

    public HashSet<Unit> getAllReturnUnit(DirectedGraph<Unit> graph){
        //我们应该观察返回值
        HashSet<Unit> res=new HashSet<>();
        for(Unit returnUnit:graph.getTails()){
            if(returnUnit instanceof ReturnStmt){
                //我们应该关注的是基本数据类型
                ReturnStmt returnStmt = (ReturnStmt) returnUnit;
                Value op = returnStmt.getOp();
                if(op instanceof NullConstant)
                    continue;
                res.add(returnStmt);

            }
        }
        return res;
    }


    public HashSet<SootMethod> getAllCallDependCondition(Unit beginUnit,Unit endUnit){
        HashSet<SootMethod> res=new HashSet<>();
        if(!(beginUnit instanceof IfStmt)) {
            logger.info("{} is not an IfStmt!", beginUnit);
            return res;
        }

        for(Unit unit:icfg.getSuccsOf(beginUnit)){
            HashSet<SootMethod> ans = getMethodByBFS(unit, endUnit);
            if(res.size()==0){
                res.addAll(ans);
            }else {
                makeIntersection(res,ans);
            }

        }
        return res;
    }

    public HashSet<SootMethod> getMethodByBFS(Unit start,Unit endUnit){
        Queue<Unit> queue=new LinkedList<>();
        HashSet<Unit> visit=new HashSet<>();
        HashSet<SootMethod> res=new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()){
            Unit poll = queue.poll();
            visit.add(poll);
            InvokeExpr invokeExpr = getInvokeExpr(poll);
            if(invokeExpr!=null){
                res.add(icfg.getMethodOf(poll));
            }
            if(poll.equals(endUnit))
                continue;
            for(Unit succUnit:icfg.getSuccsOf(poll)){
                if(visit.contains(succUnit))
                    continue;
                queue.add(succUnit);
            }

        }
        return res;
    }

    public HashSet<SootMethod> makeIntersection(HashSet<SootMethod> a,HashSet<SootMethod> b){
        //取两个集合的差集
        HashSet<SootMethod> temp = new HashSet<>(a);
        a.retainAll(b);
        temp.addAll(b);
        temp.removeAll(a);
        return temp;
    }

    public HashSet<Unit> makeIntersectionValue(HashSet<Unit> a,HashSet<Unit> b){
        //取两个集合的差集
        HashSet<Unit> temp = new HashSet<>(a);
        a.retainAll(b);
        temp.addAll(b);
        temp.removeAll(a);
        return temp;
    }



    public HashSet<Unit> getAllValueDependCondition(Unit beginUnit,Unit endUnit){
        HashSet<Unit> res=new HashSet<>();
        if(!(beginUnit instanceof IfStmt)) {
            logger.info("{} is not an IfStmt!", beginUnit);
            return res;
        }

        for(Unit unit:icfg.getSuccsOf(beginUnit)){
            HashSet<Unit> ans = getValueByBFS(unit, endUnit);
            if(res.size()==0){
                res.addAll(ans);
            }else {
                makeIntersectionValue(res,ans);
            }

        }
        return res;
    }

    public HashSet<Unit> getValueByBFS(Unit start,Unit endUnit){
        Queue<Unit> queue=new LinkedList<>();
        HashSet<Unit> visit=new HashSet<>();
        HashSet<Unit> res=new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()){
            Unit poll = queue.poll();
            visit.add(poll);
            if(poll instanceof AssignStmt){
                //我们应该找到所有的赋值语句
                AssignStmt assignStmt = (AssignStmt) poll;
                Value rightOp = assignStmt.getRightOp();
                if(!(rightOp instanceof InvokeExpr)&&isPrimitiveType(rightOp.getType())) {
                    res.add(poll);
                }
            }
            if(poll.equals(endUnit))
                continue;
            for(Unit succUnit:icfg.getSuccsOf(poll)){
                if(visit.contains(succUnit))
                    continue;
                queue.add(succUnit);
            }

        }
        return res;
    }



    public HashSet<Unit> getAllCollectionInvokeDependCondition(Unit beginUnit,Unit endUnit){
        HashSet<Unit> res=new HashSet<>();
        if(!(beginUnit instanceof IfStmt)) {
            logger.info("{} is not an IfStmt!", beginUnit);
            return res;
        }
        for(Unit unit:icfg.getSuccsOf(beginUnit)){
            HashSet<Unit> ans = getCollectionByBFS(unit, endUnit);
            if(res.size()==0){
                res.addAll(ans);
            }else {
                makeIntersectionValue(res,ans);
            }
        }
        return res;
    }

    public HashSet<Unit> getCollectionByBFS(Unit start,Unit endUnit){
        Queue<Unit> queue=new LinkedList<>();
        HashSet<Unit> visit=new HashSet<>();
        HashSet<Unit> res=new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()){
            Unit poll = queue.poll();
            visit.add(poll);
            if(poll instanceof InvokeStmt){
                SootMethod method = ((InvokeStmt) poll).getInvokeExpr().getMethod();
                if(method.getDeclaringClass().getName().startsWith("java.util")&&
                        CollectionsUsageDefinition.getCollectionApiList().contains(method.getSubSignature()))
                    res.add(poll);
            }
            if(poll.equals(endUnit))
                continue;
            for(Unit succUnit:icfg.getSuccsOf(poll)){
                if(visit.contains(succUnit))
                    continue;
                queue.add(succUnit);
            }

        }
        return res;
    }

    //找到所有的依赖相关方法,


    public static Unit getDirectDefUnit(Unit curUnit,Value value,SootMethod method){
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

    private boolean isPrimitiveType(Type type){
        //判断类型是不是基本类型
        if(type.toString().equals("boolean")||type.toString().equals("int")||type.toString().equals("float")||type.toString().equals("double")||type.toString().equals("long"))
            return true;
        return false;

    }

    public static boolean isSingleTonCls(SootClass cls){
        //判断一个类是不是单例类
        //如果一个类是接口、抽象类、内部类、私有类
        if(cls.isEnum()||cls.isInterface()||cls.isAbstract()||cls.isInnerClass()||cls.isPrivate())
            return false;
        //我们也不分析系统类
        if(isSystemClass(cls.getName()))
            return false;
        //否则，我们判断它的构造函数是不是私有的
        boolean flag=true;
        //如果它的所有构造函数都是私有的，我们认为它是单例类
        for(SootMethod m:cls.getMethods()){
            if(m.getName().equals("<init>")&&!m.isPrivate())
                flag=false;

        }
        return flag;



    }



}




