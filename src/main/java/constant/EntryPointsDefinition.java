package constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class EntryPointsDefinition {


    public static final String ACTIVITY_ONCREATE = "void onCreate(android.os.Bundle)";
    public static final String ACTIVITY_ONSTART = "void onStart()";
    public static final String ACTIVITY_ONRESTOREINSTANCESTATE = "void onRestoreInstanceState(android.os.Bundle)";
    public static final String ACTIVITY_ONPOSTCREATE = "void onPostCreate(android.os.Bundle)";
    public static final String ACTIVITY_ONRESUME = "void onResume()";
    public static final String ACTIVITY_ONPOSTRESUME = "void onPostResume()";
    public static final String ACTIVITY_ONCREATEDESCRIPTION = "java.lang.CharSequence onCreateDescription()";
    public static final String ACTIVITY_ONSAVEINSTANCESTATE = "void onSaveInstanceState(android.os.Bundle)";
    public static final String ACTIVITY_ONPAUSE = "void onPause()";
    public static final String ACTIVITY_ONSTOP = "void onStop()";
    public static final String ACTIVITY_ONRESTART = "void onRestart()";
    public static final String ACTIVITY_ONDESTROY = "void onDestroy()";
    public static final String ACTIVITY_ONATTACHFRAGMENT = "void onAttachFragment(android.app.Fragment)";


    public static final String SERVICE_ONCREATE = "void onCreate()";
    public static final String SERVICE_ONSTART1 = "void onStart(android.content.Intent,int)";
    public static final String SERVICE_ONSTART2 = "int onStartCommand(android.content.Intent,int,int)";
    public static final String SERVICE_ONBIND = "android.os.IBinder onBind(android.content.Intent)";
    public static final String SERVICE_ONREBIND = "void onRebind(android.content.Intent)";
    public static final String SERVICE_ONUNBIND = "boolean onUnbind(android.content.Intent)";
    public static final String SERVICE_ONDESTROY = "void onDestroy()";
    public static final String SERVICE_ONHANDLEINTENT="void onHandleIntent(android.content.Intent)";

    public static final String BROADCAST_ONRECEIVE = "void onReceive(android.content.Context,android.content.Intent)";

    public static final String CONTENTPROVIDER_ONCREATE = "boolean onCreate()";
    public static final String CONTENTPROVIDER_INSERT = "android.net.Uri insert(android.net.Uri,android.content.ContentValues)";
    public static final String CONTENTPROVIDER_QUERY =
            "android.database.Cursor query(android.net.Uri,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String)";
    public static final String CONTENTPROVIDER_UPDATE = "int update(android.net.Uri,android.content.ContentValues,java.lang.String,java.lang.String[])";
    public static final String CONTENTPROVIDER_DELETE = "int delete(android.net.Uri,java.lang.String,java.lang.String[])";
    public static final String CONTENTPROVIDER_GETTYPE = "java.lang.String getType(android.net.Uri)";


    public static final String APPLICATION_ONCREATE = "void onCreate()";
    public static final String APPLICATION_ONTERMINATE = "void onTerminate()";

    public static List<String> allLifeCycleMethodList = null;

    private static final String[] activityMethods = {ACTIVITY_ONCREATE, ACTIVITY_ONDESTROY, ACTIVITY_ONPAUSE,
            ACTIVITY_ONRESTART, ACTIVITY_ONRESUME, ACTIVITY_ONSTART, ACTIVITY_ONSTOP, ACTIVITY_ONSAVEINSTANCESTATE,
            ACTIVITY_ONRESTOREINSTANCESTATE, ACTIVITY_ONCREATEDESCRIPTION, ACTIVITY_ONPOSTCREATE, ACTIVITY_ONPOSTRESUME,
            ACTIVITY_ONATTACHFRAGMENT};
    private static final List<String> activityMethodList = Arrays.asList(activityMethods);

    private static final String[] serviceMethods = {SERVICE_ONCREATE, SERVICE_ONDESTROY, SERVICE_ONSTART1,
            SERVICE_ONSTART2, SERVICE_ONBIND, SERVICE_ONREBIND, SERVICE_ONUNBIND,SERVICE_ONHANDLEINTENT};
    private static final List<String> serviceMethodList = Arrays.asList(serviceMethods);

    private static final String[] broadcastMethods = {BROADCAST_ONRECEIVE};
    private static final List<String> broadcastMethodList = Arrays.asList(broadcastMethods);

    private static final String[] contentproviderMethods = {CONTENTPROVIDER_ONCREATE, CONTENTPROVIDER_DELETE,
            CONTENTPROVIDER_GETTYPE, CONTENTPROVIDER_INSERT, CONTENTPROVIDER_QUERY, CONTENTPROVIDER_UPDATE};
    private static final List<String> contentProviderMethodList = Arrays.asList(contentproviderMethods);

    private static final String[] applicationMethods={APPLICATION_ONCREATE,APPLICATION_ONTERMINATE};

    private static final List<String> applicationMethodList =Arrays.asList(applicationMethods);

    public static List<String> getActivityLifecycleMethods() {
        return activityMethodList;
    }

    public static List<String> getServiceLifecycleMethods() {
        return serviceMethodList;
    }

    public static List<String> getBroadcastLifecycleMethods() {
        return broadcastMethodList;
    }

    public static List<String> getContentproviderLifecycleMethods() {
        return contentProviderMethodList;
    }

    public static List<String> getAppliactionMethods(){
        return applicationMethodList;
    }

    public static List<String> getAllLifeCycleMethodList() {

        if (allLifeCycleMethodList == null) {
            allLifeCycleMethodList = new ArrayList<>();
            allLifeCycleMethodList.addAll(activityMethodList);
            allLifeCycleMethodList.addAll(serviceMethodList);
            allLifeCycleMethodList.addAll(broadcastMethodList);
            allLifeCycleMethodList.addAll(contentProviderMethodList);
        }

        return allLifeCycleMethodList;
    }




}
