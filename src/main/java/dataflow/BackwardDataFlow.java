package dataflow;

import cfg.CfgFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.BriefUnitGraph;
import util.StringUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class BackwardDataFlow extends AbstractDataFlow {

    private static final Logger logger= LoggerFactory.getLogger(BackwardDataFlow.class);

    private boolean flag=true;

    public boolean getFlag(){
        return flag;
    }

    public void startBackward(){
        this.flag=true;
    }


    public BackwardDataFlow(Analyze analyze){
        this.analyze=analyze;
    }

    @Override
    public boolean caseAnalyze(Unit unit, SootMethod method, List<CallSite> callStack,HashSet<Point> res,ValueBox taintValueBox) {
        return analyze.caseAnalyze(unit,method,callStack,res,null);
    }

    public void run(SootMethod method,Unit endUnit,ValueBox valueBox,int depth,List<CallSite> callStack){
        inter_backward_dataflow(method,endUnit,valueBox,depth,callStack);
    }


    public void inter_backward_dataflow(SootMethod method, Unit endUnit, ValueBox endValueBox, int depth, List<CallSite> callStack){

        if(depth>MAX_DEPTH)
            return;
        if(isMethodCalled(callStack,method))
            return;

        CfgFactory cfgFactory = CfgFactory.getInstance();
        BriefUnitGraph cfg = cfgFactory.getCFG(method);
        if (cfg == null) {
            logger.warn("the cfg is null");
            return;
        }

        EventQueue waitForProcessEvent=new EventQueue();
        if(endUnit!=null&&endValueBox!=null) {
            Event event = new Event(endUnit, endValueBox);
            waitForProcessEvent.add(event);
        }else {
            for (Unit tailUnit : cfg.getTails()) {
                if (tailUnit instanceof ReturnStmt) {
                    ReturnStmt returnStmt = (ReturnStmt) tailUnit;
                    if (!returnStmt.getOp().toString().equals("null")) {
                        Event event = new Event(tailUnit, returnStmt.getOpBox());
                        waitForProcessEvent.add(event);
                    }
                }
            }
        }

        HashSet<Event> processedEvent=new HashSet<>();
        while (!waitForProcessEvent.isEmpty()&&flag){
            Event event = waitForProcessEvent.poll();
            processedEvent.add(event);
            Unit unit = event.unit;
            ValueBox valueBox = event.valueBox;
            for(Unit pred:cfg.getPredsOf(unit)){
                if(isValueDefinedInUnit(pred,valueBox.getValue())){
                    //todo:对于字段的,对于new不做处理，交给使用者
                    if (caseAnalyze(pred,method,callStack,null,null)) {
                        //如果符合条件，将标志位设为false,同时关闭查找
                        flag = false;
                    }
                    if(pred instanceof AssignStmt){
                        AssignStmt assignStmt = (AssignStmt) pred;
                        if(assignStmt.containsInvokeExpr()){
                            SootClass declaringClass = assignStmt.getInvokeExpr().getMethod().getDeclaringClass();
                            if(!isSystemClass(declaringClass)){
                                List<CallSite> new_callStack = new ArrayList<>(callStack);
                                new_callStack.add(new CallSite(method,pred,-1));
                                for(SootMethod calleeMethod:getMethodFromCG(pred)) {
                                    inter_backward_dataflow(calleeMethod, null, null, depth + 1, new_callStack);
                                }
                            }
                        }
                        else if(!assignStmt.containsFieldRef()&&!assignStmt.containsArrayRef()){
                            if(!(assignStmt.getLeftOp() instanceof NewExpr)){
                                //如果只是简单的赋值
                                Event new_event = new Event(pred, assignStmt.getUseBoxes().get(0));
                                if(processedEvent.contains(new_event))
                                    processedEvent.add(new_event);
                            }
                        }
                    }else if((pred instanceof IdentityStmt)&&pred.toString().contains("@param")) {
                        //todo:如果是参数,我们应该去上一层中进行后向的数据流分析
                        int parameterOrder = StringUtil.getParameterOrder(pred.toString());
                        //找寻本方法的调用语句
                        CallSite preCallSite = getPreCallSite(callStack);
                        if (preCallSite != null) {
                            Unit invokeUnit = preCallSite.invokeUnit;
                            ValueBox arg = null;
                            if (invokeUnit instanceof AssignStmt) {
                                arg = ((AssignStmt) invokeUnit).getInvokeExpr().getArgBox(parameterOrder);
                            } else {
                                arg = ((InvokeStmt) invokeUnit).getInvokeExpr().getArgBox(parameterOrder);
                            }
                            if (arg != null)
                                inter_backward_dataflow(preCallSite.caller, preCallSite.invokeUnit, arg, depth--, copyAndRemoveLast(callStack));
                        }else {
                            logger.info("the object from the param of the first method called");
                        }
                    }
                }else {
                    Event old_event = new Event(pred, valueBox);
                    if(!processedEvent.contains(old_event)){
                        waitForProcessEvent.add(old_event);
                    }
                }
            }
        }


    }
    public CallSite getPreCallSite(List<CallSite> callStack) {
        if(callStack.size()==0)
            return null;
        return callStack.get(callStack.size() - 1);

    }

    public List<CallSite> copyAndRemoveLast(List<CallSite> callStack){
        List<CallSite> new_callStack=new ArrayList<>(callStack);
        new_callStack.remove(callStack.size()-1);
        return new_callStack;
    }

}
