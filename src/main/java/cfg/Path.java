package cfg;

import soot.Unit;

import java.util.ArrayList;
import java.util.List;


public class Path {

    public List<Unit> list=new ArrayList<>();

    public void add(Unit unit){
        list.add(unit);
    }

    public void addAll(Path path){
        list.addAll(path.list);
    }

    public boolean contains(Unit unit){
        return list.contains(unit);
    }

    public Unit get(int index){
        return list.get(index);
    }



    public int indexOf(Unit unit){
        return list.indexOf(unit);
    }

}
