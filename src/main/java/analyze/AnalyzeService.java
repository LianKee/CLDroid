package analyze;

import dataflow.Point;
import dataflow.StrawDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.options.Options;
import util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;


public class AnalyzeService {

    public static final Logger logger = LoggerFactory.getLogger(AnalyzeService.class);

    public static final String service_path = "/home/lkk/AndroidStudy_StaticAnalysis/DeviceData/Android11_Pixel3XL/InputData/Jimple";
    public static final String entry_point_path = "/home/ms/appAnalysis/entrypoint.txt";

    private static final StrawDetector detector = new StrawDetector();

    private static String logFileName = "serviceLog.txt";

    public static void initSoot() {

        G.reset();



        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_src_prec(Options.src_prec_jimple);
        Options.v().set_process_dir(Collections.singletonList(service_path));
        Options.v().set_output_format(Options.output_format_none);
        ArrayList<String> excludeList = new ArrayList<>();
        excludeList.add("java.*");
        excludeList.add("sun.*");
        Options.v().set_exclude(excludeList);
        Options.v().set_drop_bodies_after_load(false);
        Options.v().set_no_bodies_for_excluded(true);
        Scene.v().loadNecessaryClasses();
    }

    public static void main(String[] args) {
        initSoot();
        logger.info("detector begin running ...");
        HashMap<String, HashSet<String>> entryFromText = getEntryFromText();//获取所有的系统服务
        Log.openLog(logFileName, true);
        ArrayList<SootMethod> entrypointList = new ArrayList<>();

        for (String service : entryFromText.keySet()) {
            for (String interfaceName : entryFromText.get(service)) {
                try {
                    SootMethod method = Scene.v().getMethod(interfaceName);
                    entrypointList.add(method);
                } catch (Exception e) {
                    logger.info("the method don't exist");
                }
            }
        }

        logger.info("begin building call graph ...");
        Scene.v().setEntryPoints(entrypointList);
        long start = System.nanoTime();
        PackManager.v().getPack("cg").apply();
        long end = System.nanoTime();
        logger.info("building call graph for {} entry points costs {} seconds", entrypointList.size(), (end - start) / 1e9);


        for (String service : entryFromText.keySet()) {

            logger.info("detect service {}", service);
            Log.write(Log.Mode.SERVICE, service);
            for (String interfaceName : entryFromText.get(service)) {
                SootMethod method = null;
                try {
                    method = Scene.v().getMethod(interfaceName);
                    Log.write(Log.Mode.INTERFACE, interfaceName);
                    for (int i = 0; i < method.getParameterCount(); i++) {
                        Type type = method.getParameterTypes().get(i);
                        if(!isPrimativeType(type.toString())) {
                            logger.info("detect interface {}", interfaceName);
                            detector.setEntryPoint(interfaceName);
                            detector.run(method, null, null, i, 0, new ArrayList<>());
                        }
                    }
                    HashSet<Point> analysisResultOfEntry = detector.getAnalysisResultOfEntry(interfaceName);
                    writeAnalyzeResult(analysisResultOfEntry,interfaceName);
                } catch (Exception e) {
                    logger.info("the method don't exist");
                }
            }
        }
        Log.closeLog();
    }

    public static HashMap<String, HashSet<String>> getEntryFromText() {
        HashMap<String, HashSet<String>> res = new HashMap<>();

        try {
            FileReader fileReader = new FileReader(entry_point_path);
            BufferedReader reader = new BufferedReader(fileReader);
            String line = null;
            String service = null;
            HashSet<String> serviceInterface = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                if (line.contains("系统服务")) {
                    if (service != null) {
                        res.put(service, (HashSet<String>) serviceInterface.clone());
                        serviceInterface.clear();
                    }
                    service = line.substring(6);
                } else {
                    serviceInterface.add(line.substring(6));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    public static void writeAnalyzeResult(HashSet<Point> analysisResult, String entryPoint) {
        if (analysisResult == null)
            return;
        for (Point p : analysisResult)
            Log.write(Log.Mode.SINK, entryPoint, p.unit, p.method, p.type);
    }

    public static boolean isPrimativeType(String type){
        if(type.equals("int")||type.equals("long")||type.equals("float")||type.equals("double")||type.equals("boolean"))
            return true;
        return false;
    }





}
