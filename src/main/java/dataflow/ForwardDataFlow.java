package dataflow;

import cfg.CfgFactory;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.BriefUnitGraph;

import java.util.*;


public class ForwardDataFlow extends AbstractDataFlow {

    private static final Logger logger = LoggerFactory.getLogger(ForwardDataFlow.class);

    public static HashMap<String,HashSet<String>> visitedMethod2TaintParamIndex=new HashMap<>();//用于记录哪些方法的哪些参数已经作为污染变量被访问过
    public static HashMap<Pair<String,String>,HashSet<Point>> methodParamIndexMap2StrawPoint=new HashMap<>();


    public ForwardDataFlow(Analyze analyze) {
        this.analyze = analyze;
    }

    public boolean findFlag=false;

    public void setFindFlag(boolean flag){
        findFlag=flag;
    }

    public boolean getFindFlag(){
        return findFlag;
    }

    @Override
    public boolean caseAnalyze(Unit unit, SootMethod method, List<CallSite> callStack,HashSet<Point> res,ValueBox taintValueBox) {
        return analyze.caseAnalyze(unit, method, callStack,res,taintValueBox);
    }

    public void run(SootMethod method, Unit beginUnit, ValueBox beginValueBox, int paramIndex, int depth, List<CallSite> callStack) {
        inter_forward_dataflow(method, beginUnit, beginValueBox, paramIndex, depth, callStack);
    }

    private HashSet<String> taintWrapper=new HashSet<>();

    public void setTaintWrapper(HashSet<String> taintWrapper){
        this.taintWrapper.addAll(taintWrapper);
    }


    public HashSet<String> inter_forward_dataflow(SootMethod method, Unit beginUnit, ValueBox beginValueBox, int paramIndex, int depth, List<CallSite> callStack) {
        //分析的粒度较粗，只分析参数和返回值
        //将过程间数据流分析的数据都存放在methodMap2TaintUnit中
        if (depth > MAX_DEPTH) {
            logger.info("exceed the max depth");
            return null;
        }
        if(beginUnit==null&&beginValueBox==null&&method!=null) {
            if (visitedMethod2TaintParamIndex.containsKey(method.getSignature())) {
                if (visitedMethod2TaintParamIndex.get(method.getSignature()).contains(String.valueOf(paramIndex))){
                    Pair<String,String> methodParamTuple=new Pair<>(method.getSignature(),String.valueOf(paramIndex));
                    if(methodParamIndexMap2StrawPoint.containsKey(methodParamTuple)){//如果该方法的该参数被分析过，且保有他的记录的话
                        caseAnalyze(null,null,callStack,methodParamIndexMap2StrawPoint.get(methodParamTuple),null);
                    }
                    return null;
                }else {
                    //如果该方法的这个参数未被分析过，我们将参数加入其中
                    visitedMethod2TaintParamIndex.get(method.getSignature()).add(String.valueOf(paramIndex));
                }
            } else {
                //如果是从未访问过的方法
                HashSet<String> paramSet = new HashSet<>();
                paramSet.add(String.valueOf(paramIndex));
                visitedMethod2TaintParamIndex.put(method.getSignature(),paramSet);
            }
        }

        if (method == null)
            return null;

        if (isMethodCalled(callStack, method)) //避免循环
            return null;

        BriefUnitGraph cfg = CfgFactory.getInstance().getCFG(method);
        if (cfg == null) {
            logger.warn("the cfg of {} is null", method.getSignature());
            return null;
        }
        Event beginEvent;
        HashSet<String> retTaintValue = new HashSet<>();
        if (beginUnit != null && beginValueBox != null) {
            beginEvent = new Event(beginUnit, beginValueBox);
        } else {
            Unit parmaAssignUnit = getParmaAssignUnit(cfg, paramIndex);
            assert parmaAssignUnit != null;
            ValueBox paramValue = parmaAssignUnit.getDefBoxes().get(0);
            beginEvent = new Event(parmaAssignUnit, paramValue);
        }

        EventQueue waitForProcessEvent = new EventQueue();
        waitForProcessEvent.add(beginEvent);

        HashSet<Event> processedEvent = new HashSet<>();

        while (!waitForProcessEvent.isEmpty()) {
            Event e = waitForProcessEvent.poll();
            processedEvent.add(e);
            Unit currentUnit = e.unit;
            ValueBox taintValueBox = e.valueBox;
//            logger.info("当前语句为： {}",currentUnit);
            //兄弟,确定
//            logger.info("当前污染变量为： {}",taintValueBox.getValue());
//            logger.info("当前所在方法为： {}",method.getSignature());
//            logger.info("当前待处理的请求有： {}",waitForProcessEvent.getEventQueue().size());
            if (cfg.getSuccsOf(currentUnit).size() == 0) {
                //如果本语句是return void语句,并且考察的污染变量是r0.xxx,说明是调用该方法的对象的字段被污染了，我们在返回的时候应该注意
                //这里要区分给定方法还是给定的source点
                if(beginUnit==null) {
                    if (taintValueBox.getValue().toString().startsWith("r0")) {
                        retTaintValue.add("THIS_" + taintValueBox.getValue().toString());
                    }
                    if (currentUnit instanceof ReturnStmt) {
                        //如果是返回值语句,返回值被污染
                        ReturnStmt returnStmt = (ReturnStmt) currentUnit;
                        //如果返回值是被污染的，注意这里仍然有r0.xxx的可能需要处理
                        if (!returnStmt.getOp().toString().equals("null") && isValueUsedInUnit(currentUnit, taintValueBox.getValue())) {
                            retTaintValue.add("RET_" + returnStmt.getOp().toString());
                        }
                    }
                }else {

                    //要找寻出问题的方法的begin语句，然后继续向下传播
                    //我们这里只考虑返回值的情况，我们去找栈中找对应的调用方法
                    CallSite preCallSite = getPreCallSite(callStack);//找到调用点
                    if(preCallSite==null)
                        continue;
                    if(taintValueBox.getValue().toString().startsWith("r0")){
                        //说明对象本身被污染了
                        InvokeExpr invokeExpr = getInvokeExpr(preCallSite.invokeUnit);
                        if(invokeExpr==null)
                            continue;
                        ValueBox valueBox = invokeExpr.getUseBoxes().get(invokeExpr.getUseBoxes().size() - 1);
                        inter_forward_dataflow(preCallSite.caller,preCallSite.invokeUnit,valueBox,-1,0,copyAndRemoveLast(callStack));
                    }
                    if((preCallSite.invokeUnit instanceof AssignStmt)&&(currentUnit instanceof ReturnStmt)&&isValueUsedInUnit(currentUnit,taintValueBox.getValue())) {
                        //返回值被污染
                        DefinitionStmt definitionStmt = (DefinitionStmt) preCallSite.invokeUnit;
                        ValueBox valueBox = definitionStmt.getDefBoxes().get(0);
                        inter_forward_dataflow(preCallSite.caller,preCallSite.invokeUnit,valueBox,-1,0,copyAndRemoveLast(callStack));
                    }
                }
            } else {
                for (Unit succor : cfg.getSuccsOf(currentUnit)) {
                    if (!isValueDefinedInUnit(succor, taintValueBox.getValue())) {
                        //如果污染变量没有在本语句中重新赋值，就将污染变量继续往下传博
                        Event old_event = new Event(succor, taintValueBox);
                        if (!processedEvent.contains(old_event)&& !waitForProcessEvent.contains(old_event))
                            waitForProcessEvent.add(old_event);
//                        logger.info("后继节点为：{}",succor);
                        //如果在本语句中使用了污染变量
                        if (isValueUsedInUnit(succor, taintValueBox.getValue())) {
                            if(caseAnalyze(succor, method, callStack,null,taintValueBox))
                                setFindFlag(true);//用户自定义的分析接口
                            if (succor instanceof AssignStmt) {//如果是赋值语句的话
                                //如果是赋值语句有下面几种场景
                                AssignStmt assignStmt = (AssignStmt) succor;
                                if (assignStmt.containsFieldRef()) {
                                    //如果污染变量的字段污染了左值
                                    Event new_event = new Event(succor, succor.getDefBoxes().get(0));
                                    if (!processedEvent.contains(new_event)&& !waitForProcessEvent.contains(new_event)) {
                                        waitForProcessEvent.add(new_event);
                                    }
                                } else if (assignStmt.containsInvokeExpr()) {
                                    //如果是通过方法调用，如果作为参数可能会影响返回值
                                    int index = assignStmt.getInvokeExpr().getArgs().indexOf(taintValueBox.getValue());
                                    SootClass declaringClass = assignStmt.getInvokeExpr().getMethod().getDeclaringClass();
                                    if (index != -1) {
                                        //如果污染变量是作为参数传入的话，我们需要进行过程间分析
                                        if (!isSystemClass(declaringClass)) {//如果是常规方法，进行过程件分析
                                            List<CallSite> new_callStack = new ArrayList<>(callStack);
                                            new_callStack.add(new CallSite(method, succor,index));
                                            for(SootMethod calleeMethod:getMethodFromCG(succor)) {
                                                HashSet<String> retRes = inter_forward_dataflow(calleeMethod, null, null, index, depth + 1, new_callStack);
                                                if (retRes != null && retRes.size() != 0) {
                                                    //这个地方还欠缺
                                                    for (String r : retRes) {
                                                        if (r.startsWith("RET")) {
                                                            Event new_event = new Event(succor, succor.getDefBoxes().get(0));
                                                            if (!processedEvent.contains(new_event)&& !waitForProcessEvent.contains(new_event))
                                                                waitForProcessEvent.add(new_event);
                                                        } else {
                                                            InvokeExpr invokeExpr = assignStmt.getInvokeExpr();
                                                            if (!(invokeExpr instanceof StaticInvokeExpr)) {
                                                                ValueBox thisValueBox = invokeExpr.getUseBoxes().get(invokeExpr.getUseBoxes().size() - 1);
                                                                Event event = new Event(succor, thisValueBox);
                                                                if (!processedEvent.contains(event)&&!waitForProcessEvent.contains(event)) {
                                                                    waitForProcessEvent.add(event);
                                                                }
                                                            }
                                                        }

                                                    }
                                                }
                                            }
                                        } else {
                                            //如果是污染变量传入了系统方法中，我们不再进行过程间分析，就直接认为返回值被污染了
                                            Event new_event = new Event(succor, succor.getDefBoxes().get(0));
                                            if (!processedEvent.contains(new_event)&&!waitForProcessEvent.contains(new_event))
                                                waitForProcessEvent.add(new_event);
                                            //我们这里还假设这个a=r.(xxx,xxx)中的r也被污染了
                                            InvokeExpr invokeExpr = assignStmt.getInvokeExpr();
                                            if (!(invokeExpr instanceof StaticInvokeExpr)) {
                                                //如果不是静态调用的话
                                                ValueBox ref = assignStmt.getUseBoxes().get(assignStmt.getUseBoxes().size() - 1);
                                                new_event = new Event(succor, ref);
                                                if (!processedEvent.contains(new_event)&&!waitForProcessEvent.contains(new_event))
                                                    waitForProcessEvent.add(new_event);
                                            }
                                        }
                                    } else {
                                        //如果是作为方法调用的对象，类似r.a(xxx,xxx),r是污染变量
                                        if (isSystemClass(declaringClass)) {//如果是系统类，我们就认为返回值被污染,我们不再进行过程见分析，存在漏报，也存在误报
                                            Event new_event = new Event(succor, succor.getDefBoxes().get(0));
                                            if (!processedEvent.contains(new_event)&&!waitForProcessEvent.contains(new_event))
                                                waitForProcessEvent.add(new_event);
                                        } else {//如果是一般的方法，很难判断，先不作处理,我们这里也先假设返回值被污染了，对于调用方法我们不展开进行分析，这里存在漏报也存在误报
                                            //todo，这样做其实不是很精确
                                            Event new_event = new Event(succor, succor.getDefBoxes().get(0));
                                            if (!processedEvent.contains(new_event)&&!waitForProcessEvent.contains(new_event))
                                                waitForProcessEvent.add(new_event);
                                        }

                                    }
                                } else if (assignStmt.containsArrayRef()) {//对于数组a=b[i]
                                    Event new_event = new Event(succor, succor.getDefBoxes().get(0));
                                    if (!processedEvent.contains(new_event)&&!waitForProcessEvent.contains(new_event))
                                        waitForProcessEvent.add(new_event);
                                } else {
                                    //对于一般形式的赋值a=b
                                    Event new_event = new Event(succor, succor.getDefBoxes().get(0));
                                    if (!processedEvent.contains(new_event)&& !waitForProcessEvent.contains(new_event))
                                        waitForProcessEvent.add(new_event);
                                }
                            } else if (succor instanceof InvokeStmt) {
                                //如果只是一个不含返回值的调用语句a.()
                                InvokeStmt invokeStmt = (InvokeStmt) succor;
                                int index = invokeStmt.getInvokeExpr().getArgs().indexOf(taintValueBox.getValue());
                                if (index != -1) {
                                    //如果污染变量是作为参数传入的话，我们需要进行过程间分析
                                    SootClass declaringClass = invokeStmt.getInvokeExpr().getMethod().getDeclaringClass();
                                    if (!isSystemClass(declaringClass)) {//我们将android的类这里设为可以分析的类，当然我们设计了些规则简化分析
                                        InvokeExpr expr = invokeStmt.getInvokeExpr();
                                        String methodName=expr.getMethod().getName();
                                        String className=declaringClass.getName();

                                        if(methodName.equals("<init>")){//如果构造函数的参数被污染，我们就认为这个变量被污染了
                                            InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                                            int size = invokeExpr.getUseBoxes().size();
                                            ValueBox valueBox = invokeExpr.getUseBoxes().get(size - 1);
                                            Event event = new Event(succor, valueBox);
                                            if (!processedEvent.contains(event)&&!waitForProcessEvent.contains(event)) {
                                                waitForProcessEvent.add(event);
                                            }
                                        }
                                        if(className.startsWith("android")||className.startsWith("com.android")){
                                            if(methodName.contains("put")||methodName.contains("add")||methodName.contains("insert")){
                                                InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                                                int size = invokeExpr.getUseBoxes().size();
                                                ValueBox valueBox = invokeExpr.getUseBoxes().get(size - 1);
                                                Event event = new Event(succor, valueBox);
                                                if (!processedEvent.contains(event)&&!waitForProcessEvent.contains(event)) {
                                                    waitForProcessEvent.add(event);
                                                }
                                            }
                                        }
                                        List<CallSite> new_callStack = new ArrayList<>(callStack);
                                        new_callStack.add(new CallSite(method, succor,index));
                                       for(SootMethod calleeMethod:getMethodFromCG(succor)) {
                                           HashSet<String> ret = inter_forward_dataflow(calleeMethod, null, null, index, depth + 1, new_callStack);
                                           if (ret != null && ret.size() != 0) {
                                               for (String r : ret) {
                                                   if (r.startsWith("THIS")) {
                                                       if (!(invokeStmt.getInvokeExpr() instanceof StaticInvokeExpr)) {
                                                           InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                                                           ValueBox thisValueBox = invokeExpr.getUseBoxes().get(invokeExpr.getUseBoxes().size() - 1);
                                                           Event event = new Event(succor, thisValueBox);
                                                           if (!processedEvent.contains(event)&&!waitForProcessEvent.contains(event)) {
                                                               waitForProcessEvent.add(event);
                                                               break;
                                                           }
                                                       }
                                                   }
                                               }
                                           }
                                       }
                                    }else {//如果是系统类的话，我们就根据专家知识进行判断
                                        String signature = invokeStmt.getInvokeExpr().getMethod().getSignature();
                                        String methodName = invokeStmt.getInvokeExpr().getMethod().getName();
                                        if(methodName.equals("<init>")){
                                            InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                                            int size = invokeExpr.getUseBoxes().size();
                                            ValueBox valueBox = invokeExpr.getUseBoxes().get(size - 1);
                                            Event event = new Event(succor, valueBox);
                                            if (!processedEvent.contains(event)&&!waitForProcessEvent.contains(event)) {
                                                waitForProcessEvent.add(event);
                                            }
                                        }
                                        if(signature.equals("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>")){
                                            InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                                            int size = invokeExpr.getUseBoxes().size();
                                            ValueBox valueBox = invokeExpr.getUseBoxes().get(size - 1);
                                            Event event = new Event(succor, valueBox);
                                            if (!processedEvent.contains(event)&&!waitForProcessEvent.contains(event)) {
                                                waitForProcessEvent.add(event);
                                            }
                                        }
                                    }
                                } else {//如果是a.()中的a受污染，该语句不含返回值，我们这里先不做处理，
                                    //todo
                                    //这里需要专家知识
                                    String signature = invokeStmt.getInvokeExpr().getMethod().getSignature();
                                    if(taintWrapper.contains(signature)){
                                        InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                                        ValueBox valueBox = invokeExpr.getArgBox(0);
                                        Event event = new Event(succor, valueBox);
                                        if (!processedEvent.contains(event)&&!waitForProcessEvent.contains(event)) {
                                            waitForProcessEvent.add(event);
                                        }
                                    }
                                }
                            }
                        }
                    }

                }

            }
        }
        return retTaintValue;
    }

    public static void addCheckedInfo2MethodParam2StrawPoint(List<CallSite> callStack,HashSet<Point> res){
        if(callStack.size()==0||res==null)
            return;
        for(CallSite callSite:callStack){
            int paramIndex = callSite.paramIndex;
            if(paramIndex==-1)
                continue;
            Pair<String,String> methodParamPair=new Pair<>(callSite.caller.getSignature(),String.valueOf(paramIndex));
            if(!methodParamIndexMap2StrawPoint.containsKey(methodParamPair))
                methodParamIndexMap2StrawPoint.put(methodParamPair,new HashSet<>());
            methodParamIndexMap2StrawPoint.get(methodParamPair).addAll(res);
        }
    }

    public CallSite getPreCallSite(List<CallSite> callStack) {
        if (callStack.size() == 0)
            return null;
        return callStack.get(callStack.size() - 1);

    }

    public List<CallSite> copyAndRemoveLast(List<CallSite> callStack) {
        List<CallSite> new_callStack = new ArrayList<>(callStack);
        new_callStack.remove(callStack.size() - 1);
        return new_callStack;
    }

    public List<CallSite> copyAndAddLast(List<CallSite> callStack, CallSite callSite) {
        List<CallSite> new_callStack = new ArrayList<>(callStack);
        new_callStack.add(callSite);
        return new_callStack;
    }



}
