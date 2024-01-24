package dataflow;

import soot.Unit;
import soot.ValueBox;
import soot.tagkit.Tag;

import java.util.Objects;

public class Event {

    public Unit unit;
    public ValueBox valueBox;
    public String accessPath="";

    public Event(Unit unit, ValueBox valueBox){
        this.unit=unit;
        this.valueBox=valueBox;
        Tag accessPath = valueBox.getTag("AccessPath");
        if(accessPath!=null){
            this.accessPath=((AccessPathTag)accessPath).getFieldChain();
        }
    }



    public Event(Unit unit,ValueBox valueBox,String accessPath){
        this.unit=unit;
        this.valueBox=valueBox;
        this.accessPath=accessPath;
    }

    public boolean equals(Event event){
        return this.unit.equals(event.unit)&&
                this.valueBox.getValue().equals(event.valueBox.getValue())&&
                this.accessPath.equals(event.accessPath);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Event))
            return false;
        Event event=(Event) obj;
        return equals(event);

    }

    @Override
    public int hashCode() {
        return Objects.hash(unit,valueBox.getValue().toString(),accessPath);
    }
}
