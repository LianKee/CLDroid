package component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;


public class FragementCreater {
    public static final Logger logger= LoggerFactory.getLogger(FragementCreater.class);

    public static final String FRAGMENT="androidx.fragment.app.Fragment";
    public static final String FRAGMENT_1="android.support.v4.app.Fragment";
    public static final String DIALOG_FRAGMENT="androidx.fragment.app.DialogFragment";
    public static final String LIST_FRAGMENT="androidx.fragment.app.ListFragment";


    public static final String FRAGMENT_ONCREATE = "void onCreate(android.os.Bundle)";
    public static final String FRAGMENT_ONATTACH = "void onAttach(android.app.Activity)";
    public static final String FRAGMENT_ONCREATEVIEW = "android.view.View onCreateView(android.view.LayoutInflater,android.view.ViewGroup,android.os.Bundle)";
    public static final String FRAGMENT_ONVIEWCREATED = "void onViewCreated(android.view.View,android.os.Bundle)";
    public static final String FRAGMENT_ONSTART = "void onStart()";
    public static final String FRAGMENT_ONACTIVITYCREATED = "void onActivityCreated(android.os.Bundle)";
    public static final String FRAGMENT_ONVIEWSTATERESTORED = "void onViewStateRestored(android.app.Activity)";
    public static final String FRAGMENT_ONRESUME = "void onResume()";
    public static final String FRAGMENT_ONPAUSE = "void onPause()";
    public static final String FRAGMENT_ONSTOP = "void onStop()";
    public static final String FRAGMENT_ONDESTROYVIEW = "void onDestroyView()";
    public static final String FRAGMENT_ONDESTROY = "void onDestroy()";
    public static final String FRAGMENT_ONDETACH = "void onDetach()";
    public static final String FRAGMENT_ONSAVEINSTANCESTATE = "void onSaveInstanceState(android.os.Bundle)";

    public static final String[] fragmentLifeCycleMethod={FRAGMENT_ONCREATE,FRAGMENT_ONATTACH,FRAGMENT_ONCREATEVIEW,FRAGMENT_ONVIEWCREATED,FRAGMENT_ONSTART,
    FRAGMENT_ONACTIVITYCREATED,FRAGMENT_ONSAVEINSTANCESTATE,FRAGMENT_ONVIEWSTATERESTORED,FRAGMENT_ONRESUME,FRAGMENT_ONPAUSE,FRAGMENT_ONSTOP,FRAGMENT_ONDESTROYVIEW,
    FRAGMENT_ONDESTROY,FRAGMENT_ONDETACH};

    public static final List<String>  fragmentLifeCycleMethodList= Arrays.asList(fragmentLifeCycleMethod);

    public static final String[] fragmentClasses={FRAGMENT,FRAGMENT_1,DIALOG_FRAGMENT,LIST_FRAGMENT};

    public static HashSet<SootMethod> getAllFragmentInApp(){
        HashSet<SootMethod> res=new HashSet<>();
        //我们遍历应用中的类，看这些类是不是
        for(SootClass cls:Scene.v().getClasses()){
            if(cls.isAbstract()||cls.isInterface())
                continue;
            if(!cls.isApplicationClass())
                continue;
            if(cls.isEnum())
                continue;
            if(cls.isLibraryClass())
                continue;
            for(String fragmentClas:fragmentClasses) {
                //判断是不是Fragment几个类的子类，如果是子类我们应该寻找它的生命周期函数
                if (isSubClass(cls, fragmentClas)){
                    logger.info("[Fragment Class]: {}",cls.getName());
                    for(String subSignature:fragmentLifeCycleMethodList){
                        SootMethod method = findMethodBySubSignature(cls, subSignature);
                        if(method==null)
                            continue;
                        res.add(method);
                    }
                }
            }
        }
        return res;
    }

    public static SootMethod findMethodBySubSignature(SootClass currentClass, String subsignature) {
        if (currentClass.getName().startsWith("android.") || currentClass.getName().startsWith("androidx"))
            return null;

        SootMethod m = currentClass.getMethodUnsafe(subsignature);

        if (m != null) {
            return m;
        }
        if (currentClass.hasSuperclass()) {
            return findMethodBySubSignature(currentClass.getSuperclass(), subsignature);
        }
        return null;
    }











    public static boolean isSubClass(SootClass currentCls,String cls){
        if(!currentCls.hasSuperclass())
            return false;
        SootClass superclass = currentCls.getSuperclass();
        if(superclass.getName().equals(cls))
            return true;
        return isSubClass(superclass,cls);
    }
}
