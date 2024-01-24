package dataflow;

import soot.SootMethod;
import soot.Unit;

import java.util.Objects;


public class CallSite {
    public SootMethod caller;
    public Unit invokeUnit;
    public int paramIndex=-1;

    public CallSite(SootMethod caller, Unit invokeUnit, int paramIndex) {
        this.caller = caller;
        this.invokeUnit = invokeUnit;
        this.paramIndex=paramIndex;
    }

    @Override
    public boolean equals(Object obj) {
        CallSite callSite = (CallSite) obj;
        return this.invokeUnit.toString().equals(callSite.invokeUnit.toString()) && this.caller.getSignature().equals(callSite.caller.getSignature());
    }


    @Override
    public int hashCode() {
        return Objects.hash(caller, invokeUnit);
    }

    @Override
    public String toString() {
        return "CallSite{" +
                "caller=" + caller +
                ", invokeUnit=" + invokeUnit +
                '}';
    }
}
