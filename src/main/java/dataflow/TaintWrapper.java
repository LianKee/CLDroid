package dataflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;

import java.util.HashMap;
import java.util.HashSet;


public class TaintWrapper {
    //本类负责处理一些基本方法的wrapper


    //要求输入相应的文本，根据文本的规则,对一些方法的数据流分析进行总结
    //文件的格式
    //方法名，(污染实例，污染参数编号)， 返回值被污染，参数被污染编号,实例被污染
    //举例，getIntent,True,_,True,_:表示该方法需要实例被污染，它的返回值才会被污染


    public static final Logger logger= LoggerFactory.getLogger(TaintWrapper.class);


    class SummeryEffect{
        //前两条是条件，默认是不需要任何条件的
        boolean instanceTaint=false;
        int taintedParamIndex=-1;

        //后面是结果，默认是无条件可以影响到返回值和实例的
        boolean taintRetValue=true;
        int taintParamIndex=-1;
        boolean taintInstance=true;
    }

    HashMap<String,SummeryEffect> methodToEffect=new HashMap<>();




    boolean isInInTaintWrapper(String signature){
        return methodToEffect.containsKey(signature);
    }

    public boolean canAffectRetValue(String signature,boolean instanceTaint,int taintedParamIndex){
        if(!isInInTaintWrapper(signature)) {
            logger.warn("No taint wrapper for the method");
            return false;
        }
        SummeryEffect summeryEffect = methodToEffect.get(signature);
        if(summeryEffect.instanceTaint&&!instanceTaint)
            return false;
        if(summeryEffect.taintedParamIndex!=-1&&taintedParamIndex!= summeryEffect.taintedParamIndex)
            return false;
        return summeryEffect.instanceTaint;


    }

    public boolean canAffectInstance(String signature,int taintedParamIndex){
        if(!isInInTaintWrapper(signature)) {
            logger.warn("No taint wrapper for the method");
            return false;
        }
        SummeryEffect summeryEffect = methodToEffect.get(signature);
        if(summeryEffect.taintedParamIndex!=-1&&taintedParamIndex!= summeryEffect.taintedParamIndex)
            return false;
        return summeryEffect.taintRetValue;
    }

    public int getAffectedParamIndex(String signature,boolean instanceTaint,int taintedParamIndex){
        if(!isInInTaintWrapper(signature)) {
            logger.warn("No taint wrapper for the method");
            return -1;
        }
        SummeryEffect summeryEffect = methodToEffect.get(signature);
        if(summeryEffect.instanceTaint&&!instanceTaint)
            return -1;
        if(summeryEffect.taintedParamIndex!=-1&&taintedParamIndex!= summeryEffect.taintedParamIndex)
            return -1;
        return summeryEffect.taintParamIndex;
    }

    public void setTaintWrapper(String filePath){

        if(filePath==null)
            filePath="";
        //根据指定文件创建wrapper
    }


}
