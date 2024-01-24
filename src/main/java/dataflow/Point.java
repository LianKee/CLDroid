package dataflow;

import soot.SootMethod;
import soot.Unit;

import java.util.Objects;


public class Point {
    public String unit;
    public String method;
    public String type;
    public String otherMsg;

    public Point(Unit unit, SootMethod method, String type) {
        this.unit = unit.toString();
        this.method = method.getSignature();
        this.type = type;
    }

    public Point(Unit unit,SootMethod method,String type,String otherMsg){
        this.unit = unit.toString();
        this.method = method.getSignature();
        this.type = type;
        this.otherMsg=otherMsg;


    }


    @Override
    public boolean equals(Object obj) {
        Point point = (Point) obj;
        return point.method.equals(this.method) && point.unit.equals(this.unit) && point.type.equals(this.type)&&point.otherMsg.equals(this.otherMsg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, unit, type,otherMsg);
    }
}
