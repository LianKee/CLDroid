package component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import soot.jimple.infoflow.android.axml.AXmlAttribute;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.manifest.binary.*;
import util.DirTraversal;

import java.io.File;
import java.io.IOException;
import java.util.*;


/**
 * 解析AndroidManifest.xml的类
 * <br>可以通过实例化具体的对象来使用它，此时它将与某一个特定的apk绑定，内部的field对应特定apk内的解析结果
 * <br><b>示例</b>
 * <pre>
 * {@code
 * ResolveManifest resolveManifest = new ResolveManifest(Common.apkPath);
 * resolveManifest.getReachableComponentNames();
 * for (Component component : resolveManifest.reachableComponentList) {;}//遍历所有开放组件
 * }
 * </pre>
 *
 * <br>也可以作为静态工具类来使用它的部分方法，此时不需要实例化，会根据入参得到对应的解析结果
 * <br><b>示例</b>
 * <pre>
 * {@code
 * protectedBroadcasts = ResolveManifest.getAllProtectedBroadcast(apkDirPath);
 * permissionMap = ResolveManifest.getAllPermissionMap(apkDirPath);
 * }
 * </pre>
 *
 * @since 2.0
 */
public class ResolveManifest {
//    查询apk中的方法有哪些可以被外部组件进行调用



    public Map<String,Integer> permissionMap;

    public ProcessManifest manifest;

    private final static Logger logger = LoggerFactory.getLogger(ResolveManifest.class.getName());


    /**
     * 得到本apk中注册的permission的保护等级。
     */
    public void getPermissionMap(){//获得自自定义权限和申明的permission level
        for(AXmlNode permission: manifest.getManifest().getChildrenWithTag("permission")) {
            if(permission.getAttribute("protectionLevel") == null) permissionMap.put(permission.getAttribute("name").getValue().toString(), 0);
            else permissionMap.put(permission.getAttribute("name").getValue().toString(), (Integer) permission.getAttribute("protectionLevel").getValue());
        }
    }

    /**
     * 得到apk中所申请/使用的权限
     * @return
     */
    public HashSet<String> getUsedPermissions(){
        HashSet<String> usedPermissions = new HashSet<>();
        AXmlNode application = manifest.getManifest().getChildrenWithTag("application").get(0);
        for (AXmlNode node:application.getChildren()){
            if (node.hasAttribute("permission"))
                usedPermissions.add(node.getAttribute("permission").getValue().toString());
            if (node.hasAttribute("readPermission"))
                usedPermissions.add(node.getAttribute("readPermission").getValue().toString());
            if (node.hasAttribute("writePermission"))
                usedPermissions.add(node.getAttribute("writePermission").getValue().toString());
        }
        return usedPermissions;
    }

    /**
     * 得到本apk（定义在Common中）内注册为&lt;pretected-broadcast&gt;的受保护广播。
     * @return {@link HashSet}&lt;{@link String}&gt;
     */
    public HashSet<String> getProtectedBroadcast() throws XmlPullParserException, IOException {
        HashSet<String> protectedBroadcasts = new HashSet<>();
        for(AXmlNode node: manifest.getManifest().getChildrenWithTag("protected-broadcast")){
            protectedBroadcasts.add(node.getAttribute("name").getValue().toString());
        }
        return protectedBroadcasts;
    }



    private static boolean permissionIsProtected(String permission, HashMap<String, Integer> permissionMap){
        if(!permissionMap.containsKey(permission)){
            logger.warn("A permission without definition, marked as low level as default");
            return false;
        }
        if(permissionMap.get(permission) == 2 || permissionMap.get(permission) == 3)
            return true;
        else return false;
    }

    private static boolean isReachableActivity(AXmlNode aXmlNode, HashMap<String, Integer> allPermissionMap){
        if(isCalledBySelf(aXmlNode))
            return false;
        AXmlAttribute<?> attrEnabled = aXmlNode.getAttribute("enabled");
        boolean enabled = attrEnabled == null || !attrEnabled.getValue().equals(Boolean.FALSE);
        if(!enabled)
            return false;

        if(aXmlNode.hasAttribute("exported")){
            if(aXmlNode.getAttribute("exported").getValue().equals(false)) return false;
            else{
                if(!aXmlNode.hasAttribute("permission")) return true;
                else{
                    String permission = (String) aXmlNode.getAttribute("permission").getValue();
                    return !permissionIsProtected(permission, allPermissionMap);
                }
            }
        }
        else {
            // before android 12 , having intent-filter and no exported flag is allowed.
            // When having intent-filter, set Exported = true as default, otherwise false.
            if(aXmlNode.getChildrenWithTag("intent-filter").size() > 0) {
                if(!aXmlNode.hasAttribute("permission")) return true;
                else{
                    String permission = (String) aXmlNode.getAttribute("permission").getValue();
                    return !permissionIsProtected(permission, allPermissionMap);
                }
            }
            else return false;
        }
    }

    private static boolean isReachableService(AXmlNode aXmlNode, HashMap<String, Integer> allPermissionMap){
        if(isCalledBySelf(aXmlNode))
            return false;
        AXmlAttribute<?> attrEnabled = aXmlNode.getAttribute("enabled");
        boolean enabled = attrEnabled == null || !attrEnabled.getValue().equals(Boolean.FALSE);
        if(!enabled)
            return false;
        if(aXmlNode.hasAttribute("exported")){
            if(aXmlNode.getAttribute("exported").getValue().equals(false)) return false;
            else{
                if(!aXmlNode.hasAttribute("permission")) return true;
                else{
                    String permission = (String) aXmlNode.getAttribute("permission").getValue();
                    return !permissionIsProtected(permission, allPermissionMap);
                }
            }
        }
        else {
            // before android 12 , having intent-filter and no exported flag is allowed.
            // When having intent-filter, set Exported = true as default, otherwise false.
            if(aXmlNode.getChildrenWithTag("intent-filter").size() > 0) {
                if(!aXmlNode.hasAttribute("permission")) return true;
                else{
                    String permission = (String) aXmlNode.getAttribute("permission").getValue();
                    return !permissionIsProtected(permission, allPermissionMap);
                }
            }
            else return false;
        }
    }



    private static boolean isReachableProvider(AXmlNode aXmlNode, HashMap<String, Integer> allPermissionMap, String mode){
        if(isCalledBySelf(aXmlNode))
            return false;
        assert Arrays.asList("wr", "r", "w", "w|r").contains(mode);
//        if(aXmlNode.hasAttribute("grantUriPermissions")&&aXmlNode.getAttribute("grantUriPermissions").getValue().equals(true))
//            return true;
        AXmlAttribute<?> attrEnabled = aXmlNode.getAttribute("enabled");
        boolean enabled = attrEnabled == null || !attrEnabled.getValue().equals(Boolean.FALSE);
        if(!enabled)
            return false;
        if(aXmlNode.hasAttribute("exported")){
            if(aXmlNode.getAttribute("exported").getValue().equals(false)) return false;
            else{
                boolean flag, readFlag, writeFlag;
                if(!aXmlNode.hasAttribute("permission")) flag = true;
                else{
                    String permission = (String) aXmlNode.getAttribute("permission").getValue();
                    flag = !permissionIsProtected(permission, allPermissionMap);
                }
                if(!aXmlNode.hasAttribute("writePermission")) writeFlag = true;
                else{
                    String permission = (String) aXmlNode.getAttribute("writePermission").getValue();
                    writeFlag = !permissionIsProtected(permission, allPermissionMap);
                }
                if(!aXmlNode.hasAttribute("readPermission")) readFlag = true;
                else{
                    String permission = (String) aXmlNode.getAttribute("readPermission").getValue();
                    readFlag = !permissionIsProtected(permission, allPermissionMap);
                }
                if(mode.equals("r")) return flag && readFlag;
                if(mode.equals("w")) return flag && writeFlag;
                if(mode.equals("wr")) return flag && writeFlag && readFlag;
                if(mode.equals("w|r")) return flag && (writeFlag || readFlag);
                logger.warn("Using isReachableProvider() with wrong mode");
                return flag && (writeFlag || readFlag);
            }
        }
        else {
            return false;
        }
    }

    private static boolean isReachableReceiver(AXmlNode aXmlNode, HashMap<String, Integer> allPermissionMap, HashSet<String> protectedBroadcastActions,HashSet<String> systemAction){
        if(isCalledBySelf(aXmlNode))
            return false;
        if(isSystemAction(aXmlNode,systemAction))
            return false;
        AXmlAttribute<?> attrEnabled = aXmlNode.getAttribute("enabled");
        boolean enabled = attrEnabled == null || !attrEnabled.getValue().equals(Boolean.FALSE);
        if(!enabled)
            return false;
        if(aXmlNode.hasAttribute("exported")){
            if(aXmlNode.getAttribute("exported").getValue().equals(false)) return false;
            else{
                if(!aXmlNode.hasAttribute("permission")) return true;
                else{
                    String permission = (String) aXmlNode.getAttribute("permission").getValue();
                    if(permissionIsProtected(permission, allPermissionMap))return false;
                    for(AXmlNode filterAXmlNode : aXmlNode.getChildrenWithTag("intent-filter")){
                        for(AXmlNode actionAXmlNode : filterAXmlNode.getChildrenWithTag("action")) {
                            String actionName = actionAXmlNode.getAttribute("name").getValue().toString();
                            if(!protectedBroadcastActions.contains(actionName)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            }
        }
        else {
            // before android 12 , having intent-filter and no exported flag is allowed.
            // When having intent-filter, set Exported = true as default, otherwise false.
            if(aXmlNode.hasAttribute("permission")) {
                String permission = (String) aXmlNode.getAttribute("permission").getValue();
                if(permissionIsProtected(permission, allPermissionMap)) return false;
            }
            for(AXmlNode filterAXmlNode : aXmlNode.getChildrenWithTag("intent-filter")){
                for(AXmlNode actionAXmlNode : filterAXmlNode.getChildrenWithTag("action")) {
                    String actionName = actionAXmlNode.getAttribute("name").getValue().toString();
                    if(!protectedBroadcastActions.contains(actionName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * 得到一个目录下注册的所有permission的保护等级。输入一个绝对路径，会检索这个目录下所有apk中的AndroidManifest.xml。
     *
     * @param apkDirPath 所有apk文件所在的绝对路径
     * @return {@link HashMap}&lt;{@link String}, {@link Integer}&gt;
     */
    public static HashMap<String, Integer> getAllPermissionMap(String apkDirPath) throws XmlPullParserException, IOException {

        HashMap<String, Integer> allPermissionMap = new HashMap<String, Integer>();

        new DirTraversal(){
            @Override
            public void work(File f) throws XmlPullParserException, IOException {
                if(!f.toString().endsWith("apk"))return;
                ProcessManifest manifest = new ProcessManifest(f.toString());
                for(AXmlNode permission:manifest.getManifest().getChildrenWithTag("permission")) {
                    if(permission.getAttribute("protectionLevel") == null) allPermissionMap.put(permission.getAttribute("name").getValue().toString(), 0);
                    else {
                        allPermissionMap.put(permission.getAttribute("name").getValue().toString(),(Integer) permission.getAttribute("protectionLevel").getValue());
                    }
                }
            }
        }.traverse(new File(apkDirPath));
        return  allPermissionMap;
    }


    /**
     * 得到一个目录下所有注册为&lt;pretected-broadcast&gt;的受保护广播。输入一个绝对路径，会检索这个目录下所有apk中的AndroidManifest.xml。
     *
     * @param apkDirPath 所有apk文件所在的绝对路径
     * @return {@link HashSet}&lt;{@link String}&gt;
     */
    public static HashSet<String> getAllProtectedBroadcast(String apkDirPath) throws XmlPullParserException, IOException {
        File file = new File(apkDirPath);
        HashSet<String> protectedBroadcasts = new HashSet<>();

        new DirTraversal() {
            @Override
            public void work(File f) throws Exception {
                if(!f.toString().endsWith("apk"))return;
                ProcessManifest manifest = new ProcessManifest(f.toString());
                for(AXmlNode node : manifest.getManifest().getChildrenWithTag("protected-broadcast")){
                    protectedBroadcasts.add(node.getAttribute("name").getValue().toString());
                }
            }
        }.traverse(file);

        return protectedBroadcasts;

    }

//    /**
//     * 获取路径下所有开放组件和它们申请的权限。
//     *
//     * @param apkDirPath apk路径
//     */
//    public static HashMap<String,HashSet<AbstractBinaryAndroidComponent>> getAllReachableComponents(String apkDirPath) throws XmlPullParserException, IOException {
//        HashMap<String,HashSet<AbstractBinaryAndroidComponent>> apkMapReachableComponents = new HashMap<>();
//        HashMap<String, Integer> allPermissionMap = getAllPermissionMap(apkDirPath);
//        HashSet<String> allProtectedBroadcastActions = getAllProtectedBroadcast(apkDirPath);
//
//        new DirTraversal() {
//            @Override
//            public void work(File f) throws Exception {
//                if(!f.toString().endsWith("apk"))return;
//
//                ProcessManifest processManifest =new ProcessManifest(f.toString());
//                HashSet<AbstractBinaryAndroidComponent> reachableComponents = new HashSet<>();
//
//                for(BinaryManifestService service:processManifest.getServices()){
//                    AXmlNode aXmlNode=service.getAXmlNode();
//                    if(isReachableService(aXmlNode, allPermissionMap)) {
//                        reachableComponents.add(service);
//                    }
//                }
//                for(BinaryManifestActivity activity:processManifest.getActivities()){
//                    AXmlNode aXmlNode=activity.getAXmlNode();
//                    if(isReachableActivity(aXmlNode, allPermissionMap)) {
//                        reachableComponents.add(activity);
//                    }
//                }
//                for(BinaryManifestBroadcastReceiver receiver:processManifest.getBroadcastReceivers()){
//                    AXmlNode aXmlNode=receiver.getAXmlNode();
//                    if(isReachableReceiver(aXmlNode, allPermissionMap, allProtectedBroadcastActions)) {
//                        reachableComponents.add(receiver);
//                    }
//                }
//                for(BinaryManifestContentProvider provider: processManifest.getContentProviders()){
//                    AXmlNode aXmlNode=provider.getAXmlNode();
//                    // if we have write or read permission, we mark it as reachable
//                    if(isReachableProvider(aXmlNode, allPermissionMap, "w|r")) {
//                        reachableComponents.add(provider);
//                    }
//                }
//
//                apkMapReachableComponents.put(f.getName(),reachableComponents);
//            }
//        }.traverse(new File(apkDirPath));
//
//        return apkMapReachableComponents;
//    }

    //判断组件是不是只能
    private static boolean isCalledBySelf(AXmlNode aXmlNode){
        if(!aXmlNode.hasAttribute("process"))
            return false;
        return aXmlNode.getAttribute("process").getValue().toString().contains(":");
    }
    //todo,如果声明的action
    private static boolean isSystemAction(AXmlNode aXmlNode,HashSet<String> systemActions){
        List<AXmlNode> children = aXmlNode.getChildrenWithTag("intent-filter");
        //如果不含有intent-filter，则不是系统广播
        if(children.isEmpty())
            return false;
        //如果有一个action是我们可以访问的那他就不是系统的
        for(AXmlNode child:children){
            List<AXmlNode> actionAxmlNode = child.getChildrenWithTag("action");
            for(AXmlNode action:actionAxmlNode){
                if(action.hasAttribute("name")){
                    String actionName = action.getAttribute("name").getValue().toString();
                    if(!systemActions.contains(actionName))
                        return false;
                }
            }
        }
        //如果全是则是必须由系统调用的组件
        return true;
    }


    public static HashSet<AbstractBinaryAndroidComponent> getReachableComponents(String apkPath,HashMap<String,Integer> permissionInfo,HashSet<String> systemAction){

        if(!apkPath.endsWith(".apk"))
            return null;

        try {
            ProcessManifest manifest = new ProcessManifest(apkPath);
            HashMap<String,Integer> permissionMap=new HashMap<>(permissionInfo);
            HashSet<String> protectedBroadcasts=new HashSet<>();
            HashSet<AbstractBinaryAndroidComponent> reachableComponents = new HashSet<>();
            for(AXmlNode permission:manifest.getManifest().getChildrenWithTag("permission")) {
                if(permission.getAttribute("protectionLevel") == null) permissionMap.put(permission.getAttribute("name").getValue().toString(), 0);
                else {
                    permissionMap.put(permission.getAttribute("name").getValue().toString(),(Integer) permission.getAttribute("protectionLevel").getValue());
                }
            }
            for(AXmlNode node : manifest.getManifest().getChildrenWithTag("protected-broadcast")){
                protectedBroadcasts.add(node.getAttribute("name").getValue().toString());
            }

            for(BinaryManifestService service: manifest.getServices()){
                AXmlNode aXmlNode=service.getAXmlNode();
                if(isReachableService(aXmlNode, permissionMap)) {
                    reachableComponents.add(service);
                }
            }
            for(BinaryManifestActivity activity: manifest.getActivities()){
                AXmlNode aXmlNode=activity.getAXmlNode();
                if(isReachableActivity(aXmlNode, permissionMap)) {
                    reachableComponents.add(activity);
                }
            }
            for(BinaryManifestBroadcastReceiver receiver: manifest.getBroadcastReceivers()){
                AXmlNode aXmlNode=receiver.getAXmlNode();
                if(isReachableReceiver(aXmlNode, permissionMap, protectedBroadcasts,systemAction)) {
                    reachableComponents.add(receiver);
                }
            }
            for(BinaryManifestContentProvider provider: manifest.getContentProviders()){
                AXmlNode aXmlNode=provider.getAXmlNode();
                // if we have write permission, we mark it as reachable
                if(isReachableProvider(aXmlNode, permissionMap, "w")) {
                    reachableComponents.add(provider);
                }
            }
            return reachableComponents;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static HashSet<AbstractBinaryAndroidComponent> getAllComponents(String apkPath){
        if(!apkPath.endsWith(".apk"))
            return null;
        try {
            ProcessManifest manifest = new ProcessManifest(apkPath);
            HashSet<AbstractBinaryAndroidComponent> reachableComponents = new HashSet<>();

            for(BinaryManifestService service: manifest.getServices()){
                    reachableComponents.add(service);
            }
            for(BinaryManifestActivity activity: manifest.getActivities()){
                    reachableComponents.add(activity);
            }
            for(BinaryManifestBroadcastReceiver receiver: manifest.getBroadcastReceivers()){
                    reachableComponents.add(receiver);
            }

            for(BinaryManifestContentProvider provider:manifest.getContentProviders()){
                reachableComponents.add(provider);
            }
            return reachableComponents;

        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static String getPackageName(File file){
        try {
            ProcessManifest processManifest = new ProcessManifest(file);
            return processManifest.getPackageName();
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static String getVersionName(File file){
        try {
            ProcessManifest processManifest = new ProcessManifest(file);
            return processManifest.getVersionName();
        }catch (Exception e){
            e.printStackTrace();

        }
        return "";
    }
}
