package dataflow;

import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;


public interface RuleChecker {
    //判断数据流如到某个调用是不是sink
    boolean isSink(Unit checkedUnit, ValueBox taintValueBox);

    //判断某个依赖于控制流的调用是不是sink
    boolean isDependConditionSink(SootMethod method,SootMethod caller);
}
