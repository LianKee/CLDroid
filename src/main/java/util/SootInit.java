package util;

import soot.G;
import soot.Scene;
import soot.options.Options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class SootInit {

    public static void initSootForJava(String programPath){
        G.reset();
        soot.options.Options.v().set_prepend_classpath(true);
        soot.options.Options.v().set_allow_phantom_refs(true);
        soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_class);
        Options.v().set_process_dir(Collections.singletonList(programPath));
        Scene.v().loadNecessaryClasses();
    }

    public static void initSootForAndroid(String apkPath,String androidJarPath){
        G.reset();
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_process_multiple_dex(true);
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_dir(Collections.singletonList(apkPath));
        Options.v().set_android_jars(androidJarPath);
        Options.v().set_whole_program(true);
        List<String> excludeList=new ArrayList<>();
        excludeList.add("java.*");
        excludeList.add("javax.*");
        excludeList.add("android.*");
        excludeList.add("androidx.*");
        excludeList.add("com.google.*");
        excludeList.add("sun.*");
        excludeList.add("io.*");
        excludeList.add("kotlin.*");
        excludeList.add("kotlinx.*");
        Options.v().set_exclude(excludeList);
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_output_format(Options.output_format_jimple);
        Options.v().set_process_multiple_dex(true);
        Options.v().set_keep_line_number(true);
        Scene.v().loadNecessaryClasses();
    }
}
