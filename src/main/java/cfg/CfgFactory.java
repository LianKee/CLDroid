package cfg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.toolkits.graph.BriefUnitGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


public class CfgFactory {

    private static final Logger logger = LoggerFactory.getLogger(CfgFactory.class);

    private static CfgFactory cfgFactory = null;
    private  HashMap<SootMethod, BriefUnitGraph> methodMapCfg = new HashMap<>();

    private HashMap<SootMethod,HashSet<Path>> methodMapPath=new HashMap<>();

    private CfgFactory() {
    }

    public static CfgFactory getInstance() {
        if (cfgFactory == null)
            cfgFactory = new CfgFactory();
        return cfgFactory;
    }


    public BriefUnitGraph getCFG(SootMethod sootMethod) {
        if(methodMapCfg.keySet().contains(sootMethod))
            return methodMapCfg.get(sootMethod);
        if(!sootMethod.isConcrete())
            return null;
        Body body = sootMethod.retrieveActiveBody();
        if(body==null){
            logger.warn("can't get the body of {}",sootMethod.getSignature());
            return null;
        }
        BriefUnitGraph graph = new BriefUnitGraph(body);
        methodMapCfg.put(sootMethod,graph);
        return graph;
    }



    public void buildPaths(SootMethod method,Unit unit,BriefUnitGraph graph,HashSet<Unit> visit,Path path){

        Path newPath = new Path();
        newPath.addAll(path);
        newPath.add(unit);

        HashSet<Unit> newVisit=new HashSet<>();
        newVisit.addAll(visit);
        newVisit.add(unit);

        boolean f=true;
        for(Unit succor:graph.getSuccsOf(unit)){
            if(!newVisit.contains(succor)){
                f=false;
                buildPaths(method,succor,graph,newVisit,newPath);
            }
        }

        if(f){
            if(!methodMapPath.containsKey(method))
                methodMapPath.put(method,new HashSet<>());
            methodMapPath.get(method).add(newPath);
        }

    }

    public HashSet<Path> getPaths(SootMethod method){
        if(!methodMapPath.containsKey(method)){
            BriefUnitGraph cfg = getCFG(method);
            if(cfg==null)
                return null;
            for(Unit head:cfg.getHeads()){
                buildPaths(method,head,cfg,new HashSet<>(),new Path());
            }
        }
        return methodMapPath.get(method);
    }
}
