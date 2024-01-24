package dataflow;

import cg.CallGraphUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


public class SensitiveActionChecker implements RuleChecker {

    private Logger logger= LoggerFactory.getLogger(SensitiveActionChecker.class);

    //指定的敏感方法API文件

    public static final String sensitiveMethodAPIFile="D:\\App资源耗尽漏洞检测\\DF_SINK_INFO.txt";


    private HashMap<String,String> sinks=new HashMap<>();

    private HashSet<SootMethod> methodsCanReachSink=new HashSet<>();

    private HashSet<SootMethod> controlFlowMethods=new HashSet<>();




    private HashSet<String> sinkMethodTrue = new HashSet<>();

    //获取可以触发的敏感行为
    public HashSet<String> getSinkMethodTrue() {
        return this.sinkMethodTrue;
    }

    public void clearSinkMethodTrue(){
        this.sinkMethodTrue.clear();
    }

    public SensitiveActionChecker(){
    }

    //这里需要修改

    public SensitiveActionChecker(HashMap<String,String> sinks){
        logger.info("Init Sensitive Checker");
        this.sinks=sinks;
        initReachableMethod();
    }

    private void initReachableMethod(){
        for(String signature:this.sinks.keySet()){
            try{
                String type = this.sinks.get(signature).trim();
                //获取指定
                if(type.equals("C5")||type.equals("C4-AD")) {
                    SootMethod method = Scene.v().getMethod(signature);
                    controlFlowMethods.add(method);
                }
            }catch (Exception e){
//                e.printStackTrace();
            }
        }

        if(sinks.isEmpty())
            return;
        //计算CG中所有可以执行到控制流的方法集合
        methodsCanReachSink=CallGraphUtils.getAllMethodsCanReachTargetMethods(controlFlowMethods);
    }



    @Override
    public boolean isSink(Unit checkedUnit, ValueBox taintValueBox) {

        InvokeExpr invokeExpr = getInvokeExpr(checkedUnit);
        if(invokeExpr==null)
            return false;
        //对于数据流的sink我们认为它的参数应该受到污染，尽管这有时
        if (sinks.keySet().contains(invokeExpr.getMethod().getSignature()) && invokeExpr.getArgs().contains(taintValueBox.getValue())) {
            logger.info("Detect sensitive data flow method {} is trigger!",invokeExpr.getMethod().getSignature());
            String info = getFormatInfo(invokeExpr.getMethod(), 0,null);
            sinkMethodTrue.add(info);
        }
        return false;
    }

    @Override
    public boolean isDependConditionSink(SootMethod method,SootMethod caller) {
        if(methodsCanReachSink.contains(method)){
            logger.info("Detect sensitive control flow method {} is trigger!",method.getSignature());
            String info = getFormatInfo(method, 1,caller);
            if(info==null)
                return false;
            sinkMethodTrue.add(info);
        }
        return false;
    }



    private InvokeExpr getInvokeExpr(Unit u) {
        if (u instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) u;
            if (assignStmt.containsInvokeExpr())
                return assignStmt.getInvokeExpr();
        }
        if (u instanceof InvokeStmt) {
            InvokeStmt invokeStmt = (InvokeStmt) u;
            return invokeStmt.getInvokeExpr();
        }
        return null;
    }

    private String getFormatInfo(SootMethod method,int mode,SootMethod caller){
        //获取标准的分析信息，恢复字符串主要是为了懒得写代码。。。。
        String msg="";
        if(mode==0){
            //如果是0则表示是数据流依赖的
            msg+="SINK TYPE: DATA_FLOW_SINK\n";
            msg+="METHOD: "+method.getSignature()+"\n";
            msg+="TYPE: "+sinks.get(method.getSignature());
        }else {
            //计算该方法可以达到的危险方法
            List<String> res=new ArrayList<>();
            CallGraphUtils.getReachableMethodInGivenSet(method,controlFlowMethods,0,res,new HashSet<>());
            if(res.isEmpty())
                return null;
            msg+="SINK TYPE: CONTROL_FLOW_SINK\n";
            msg+="DEPEND METHOD: "+method.getSignature()+'\n';
            msg+="DEPEMD METHOD CALLER: "+caller.getSignature()+"\n";
            int counter=0;
            for(String sinkMethod:res){
                msg+="SINK METHOD "+"["+counter+"]: "+sinkMethod+'\n';
                if(counter<res.size()-1){
                    msg += "TYPE: " + sinks.get(sinkMethod) + '\n';
                }else {
                    msg += "TYPE: " + sinks.get(sinkMethod);
                }
                counter++;
            }
        }
        return msg;

    }


}
