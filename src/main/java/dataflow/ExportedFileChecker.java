package dataflow;

import constant.EntryPointsDefinition;
import constant.StrawPointsDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.manifest.binary.BinaryManifestContentProvider;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.BriefUnitGraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class ExportedFileChecker implements RuleChecker {
    Logger logger = LoggerFactory.getLogger(ExportedFileChecker.class);

    //过程间控制流图
    private JimpleBasedInterproceduralCFG icfg ;
    //当前数据流分析的入口放啊
    private SootMethod entryPoint = null;
    //当前的组件
    private String component;
    //本app的Manifest组件信息
    private ProcessManifest manifest;
    //文件信息恢复工具
    private FileLoaderDetector fileLoaderDetector;
    //注入sharedPreference提取的元数据信息
    private HashSet<FileLoaderDetector.SharedPreferencesInfo> sharedPreferencesInfos=new HashSet<>();
    //注入数据库提取的元数据信息
    private HashSet<FileLoaderDetector.DataBaseInfo> dataBaseInfos=new HashSet<>();
    //注入集合提取的元数据信息
    private HashSet<String> collectionMetaInfos=new HashSet<>();
    //注入文件的文件元数据信息
    private HashSet<String> fileInfos=new HashSet<>();
    //注入的sp的元数据信息
    private HashSet<SpInfo> spInfos=new HashSet<>();
    //注入的db元数据信息
    private HashSet<DBInfo> dbInfos=new HashSet<>();

    private HashSet<Unit> affect_editors=new HashSet<>();


    private HashMap<String,HashSet<String>> spDB=null;

    public void setSpDB(HashMap<String,HashSet<String>> spDB){
        this.spDB=spDB;
    }

    public HashSet<SpInfo> getSpInfos(){
        return this.spInfos;
    }

    public HashSet<DBInfo> getDbInfos(){
        return this.dbInfos;
    }

    //从entryPoint的分析结果
    public HashMap<SootMethod, HashSet<Point>> res = new HashMap<>();

    public HashMap<SootMethod,HashSet<Point>> getRes(){
        return this.res;
    }

    public void setEntryPoint(SootMethod m) {
        this.entryPoint = m;
        res.clear();
    }


    public void  setComponent(String component){
        this.component=component;
    }

    public ExportedFileChecker(ProcessManifest manifest,JimpleBasedInterproceduralCFG icfg){
        this.manifest=manifest;
        this.icfg=icfg;
        this.fileLoaderDetector=new FileLoaderDetector(icfg);
    }

    public HashSet<FileLoaderDetector.SharedPreferencesInfo> getSharedPreferencesInfos(){
        return this.sharedPreferencesInfos;
    }

    public HashSet<FileLoaderDetector.DataBaseInfo> getDataBaseInfos(){
        return this.dataBaseInfos;
    }

    public HashSet<String> getFileInfos(){
        return this.fileInfos;
    }

    public HashSet<String> getCollectionMetaInfos(){
        return this.collectionMetaInfos;
    }

    private FileType getFileTYpe(Unit curUnit, ValueBox curValueBox) {
        InvokeExpr invokeExpr = getInvokeExpr(curUnit);
        if (invokeExpr == null)
            return FileType.UN_DEFINE;

        //我们这里添加一条，判断editor有没有被污染
        if(StrawPointsDefinition.getSharedPreferencesWriteMethodList().contains(invokeExpr.getMethod().getSignature())){
            //如果是editor.put的形式，我们需要看下这个传来的污染便令是不是editor
            InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
            if(instanceInvokeExpr.getBase().equals(curValueBox.getValue()))
                return FileType.EDITOR;
        }
        int index = invokeExpr.getArgs().indexOf(curValueBox.getValue());
        if (index == -1)
            return FileType.UN_DEFINE;
        String signature = invokeExpr.getMethod().getSignature();
        if (index == 0 && StrawPointsDefinition.getFileWriteMethodList().contains(signature))
            return FileType.FILE;
        if (StrawPointsDefinition.getDatabaseInsertMethodList().contains(signature))
            return FileType.DATA_BASE;
        if (StrawPointsDefinition.getSharedPreferencesWriteMethodList().contains(signature))
            return FileType.SHARED_PREFERENCES;
//        我们这里不再分析collection
//        if (index == 0 && StringUtil.isMatch(signature, StrawPointsDefinition.COLLECTIONS_STRAWPOINT_REGEX))
//            return FileType.COLLECTION;
        return FileType.UN_DEFINE;
    }

    @Override
    public boolean isSink(Unit checkedUnit, ValueBox taintValueBox) {
        //检测应用中的某条语句是不是可以影响的文件的API,并做相应的处理
        FileType fileTYpe = getFileTYpe(checkedUnit, taintValueBox);
        SootMethod method = icfg.getMethodOf(checkedUnit);
        switch (fileTYpe) {
            case DATA_BASE:
                handForDataBase(method, checkedUnit);
                break;
            case SHARED_PREFERENCES:
                handForSharedPreferences(method, checkedUnit, taintValueBox);
                break;
            case FILE:
                handForFile(method, checkedUnit);
                break;
            case EDITOR:
                handForEditor(method,checkedUnit);
                break;
            default:

        }
        return false;
    }

    @Override
    public boolean isDependConditionSink(SootMethod method,SootMethod caller) {
        return false;
    }

    private boolean handForFile(SootMethod method, Unit curUnit) {
        logger.info("Find a file sink!");
        HashSet<String> standardFileInfo = fileLoaderDetector.getStandardFileInfo(curUnit, method);
        String msg="FILE_NAME: "+standardFileInfo.toString();
        addRecord(method, curUnit, "FILE", msg);
        //记录注入数据的文件的元数据信息
        fileInfos.addAll(standardFileInfo);
        return true;

    }

    private boolean handForEditor(SootMethod method,Unit curUnit){
        //需要标记这个此处的editor对象是我们可控的
        affect_editors.add(curUnit);
        return true;
    }

    private boolean handForDataBase(SootMethod method, Unit curUnit) {
        //对于注入的数据库，我们需要搜集的信息比较多
        logger.info("Find a DataBase Sink!");
        //我们要尽可能恢复当前数据库的信息
        //我们需要判断我们是不是通过一个Provider进行注入数据库的，如果是
        //我们不仅要在这个数据库注入点，恢复的信息包括，uri,数据库名，数据表名，字段名
        FileLoaderDetector.DataBaseInfo dataBaseInfo = fileLoaderDetector.getDataBaseInfo(curUnit, method);
        try {
            //如果这个数据库需要通过ContentProvider获得，我们还需要得到这个uri，这个uri的
            //我们还要将这个uri添加到数据库信息中去
            String authority = getAuthority();
            logger.info("authority: {}",authority);
            if(authority!=null)
                dataBaseInfo.setUri(authority);
        }catch (Exception e){
        }
        HashSet<String> standardDataBaseName = dataBaseInfo.getStandardDataBaseName();
        HashSet<String> standardTableName = dataBaseInfo.getStandardTableName();
        HashSet<String> standardUri = dataBaseInfo.getStandardUri();
        HashSet<String> standardTableInfo = dataBaseInfo.getStandardTableInfo();

        HashSet<DBInfo> res=new HashSet<>();
        //我们现在处理注入数据库的元数据信息
        if(standardDataBaseName.isEmpty())
            standardDataBaseName.add("");
        if(standardTableName.isEmpty())
            standardTableName.add("");
        if(standardUri.isEmpty())
            standardUri.add("");
        if(standardTableInfo.isEmpty())
            standardTableInfo.add("");
        boolean insert=curUnit.toString().contains("insert");

        for(String dbName:standardDataBaseName)
            for(String tableName:standardTableName)
                for(String uri:standardUri)
                    for(String tableInfo:standardTableInfo){
                        DBInfo dbInfo = new DBInfo(dbName, tableName, tableInfo,uri,insert);
                        res.add(dbInfo);
                    }

        StringBuilder msg= new StringBuilder();
        for(DBInfo dbInfo:res)
            msg.append(dbInfo.toString()).append('\n');

        addRecord(method,curUnit,"DATA_BASE",msg.toString());
        dbInfos.addAll(res);
        return true;
    }

    private boolean handForSharedPreferences(SootMethod method, Unit curUnit, ValueBox curValueBox) {
        //对于注入的SharedPreference我们需要的是
        logger.info("Find a Preferences Sink!");
        InvokeExpr invokeExpr = getInvokeExpr(curUnit);
        boolean index=invokeExpr.getArgs().get(0).equals(curValueBox.getValue());
        String flag = index ? "TRUE" : "FALSE";
        //我们需要会恢复注入的SharedPreference信息
        //恢复的是该SharedPreferences的文件名以及key值
        FileLoaderDetector.SharedPreferencesInfo sharedPreferencesInfo = fileLoaderDetector.getSharedPreferencesInfo(curUnit, method);
        sharedPreferencesInfo.setAffectKey(index);
        //看这个sharedPreference是否和某个authority绑定
        String authority = getAuthority();
        //能影响文件名
        boolean affect_sp_name=affect_editors.contains(curUnit);
        if(authority!=null)
            sharedPreferencesInfo.setUriAuthority(authority);

        HashSet<SpInfo> res=new HashSet<>();

        //需要根据能否影响key判断影响的条目
        if(index){
            //如果能够影响key，我们还需要判断试否能够影响文件名
            if(affect_sp_name){
                //如果能够影响文件名，并且影响key的话，那么检索到的所有文件对应的内容都应该是可控的
                //我们要抽取所有的文件信息
                HashSet<String> standardUri = sharedPreferencesInfo.getStandardUri();
                if(standardUri.isEmpty())
                    standardUri.add("");
                for(String spName:spDB.keySet()){
                    if(spDB.get(spName).isEmpty()){
                        for(String uri:standardUri){
                            SpInfo spInfo = new SpInfo(uri, spName, "", true, true);
                            res.add(spInfo);
                        }
                    }else {
                        for (String keyName : spDB.get(spName)) {
                            for (String uri : standardUri) {
                                SpInfo spInfo = new SpInfo(uri, spName, keyName, true, true);
                                res.add(spInfo);
                            }
                        }
                    }
                }

            }else {
                //如果只能控制key的话，不能控制文件名
                HashSet<String> standardPreferencesName = sharedPreferencesInfo.getStandardPreferencesName();
                HashSet<String> standardUri = sharedPreferencesInfo.getStandardUri();
                HashSet<String> standardKey = sharedPreferencesInfo.getStandardKey();
                if(standardPreferencesName.isEmpty()){
                    //如果不能推断出名字,我们就不能从数据库中获取数据了
                    if(standardUri.isEmpty()){
                        standardUri.add("");
                    }
                    if(standardKey.isEmpty())
                        standardKey.add("");
                    for(String uri:standardUri)
                        for(String keyName:standardKey){
                            SpInfo spInfo = new SpInfo(uri, "", keyName, true, false);
                            res.add(spInfo);
                        }
                }else {
                    //如果能够推断出文件名，我们就要从能够推断出文件名，我们就需要从推断出的文件中获取他们的内容
                    if(standardUri.isEmpty())
                        standardUri.add("");
                    for(String spName:standardPreferencesName)
                        for(String uri:standardUri){
                            if(spDB.containsKey(spName)){
                                if(spDB.get(spName).isEmpty()){
                                    SpInfo spInfo = new SpInfo(uri, spName, "", true, false);
                                    res.add(spInfo);
                                }else {
                                    for(String keyName:spDB.get(spName)){
                                        SpInfo spInfo = new SpInfo(uri, spName, keyName, true, false);
                                        res.add(spInfo);
                                    }
                                }

                            }else {
                                //如果数据库中不包含这个数据,我们就需要分析
                                if(standardKey.isEmpty())
                                    standardKey.add("");
                                for(String keyName:standardKey){
                                    SpInfo spInfo = new SpInfo(uri, spName, keyName, true, false);
                                    res.add(spInfo);
                                }
                            }
                        }
                }

            }

        }else {
            //如果只能影响value
            //我们需要获取的数据有文件名，key名
            HashSet<String> standardPreferencesName = sharedPreferencesInfo.getStandardPreferencesName();
            HashSet<String> standardKey = sharedPreferencesInfo.getStandardKey();
            HashSet<String> standardUri = sharedPreferencesInfo.getStandardUri();
            //我们统计下元数据
            //他们中可能存在为空的情况（因为恢复不成功）
            if(standardPreferencesName.isEmpty())
                standardPreferencesName.add("");
            if(standardKey.isEmpty())
                standardKey.add("");
            if(standardUri.isEmpty())
                standardUri.add("");
            for(String spName:standardPreferencesName)
                for(String keyName:standardKey)
                    for(String uri:standardUri){
                        SpInfo spInfo = new SpInfo(uri, spName, keyName, false, false);
                        res.add(spInfo);
                    }
        }

        //记录分析结果
        //为了便于分析，我们把他们处理成标准格式
        StringBuilder msg= new StringBuilder();
        for(SpInfo spInfo:res)
            msg.append(spInfo.toString()).append('\n');

        //我们将相关信息展开
        addRecord(method, curUnit, "SHARED_PREFERENCES", msg.toString());

        //我们把注入的文件信息，保存
        spInfos.addAll(res);
        //此外我们还需要判断此组件不是个Provider，如果是个Provider，我们应该加按照数据库的使用进行查找
        if(authority!=null){
            DBInfo dbInfo = new DBInfo("", "", "", authority,false);
            dbInfos.add(dbInfo);
        }
        return true;
    }

//    private boolean handForCollection(SootMethod method, Unit curUnit) {
//        //判断入口是不是activity的生命周期函数，如果是就返回
//        if(EntryPointsDefinition.getActivityLifecycleMethods().contains(this.entryPoint.getSubSignature()))
//            return false;
//        //我们需要对这个注入的字段进行判断
//        InvokeExpr invokeExpr = getInvokeExpr(curUnit);
//        Value value = invokeExpr.getUseBoxes().get(invokeExpr.getUseBoxes().size() - 1).getValue();
//
//        Unit directDefUnit = getDirectDefUnit(curUnit, method, value);
//        if(directDefUnit==null)
//            return false;
//        AssignStmt as = (AssignStmt) directDefUnit;
//        Value rightOp = as.getRightOp();
//        if(rightOp instanceof FieldRef){
//            if(isLongLifeVar((FieldRef) rightOp)){
//                logger.info("Found a filed sink!");
//                FieldRef fieldRef = (FieldRef) rightOp;
//                collectionMetaInfos.add(fieldRef.getField().getSignature());
//                String msg="COLLECTION_LONG_LIVE: "+fieldRef.getField().getSignature();
//                addRecord(method,curUnit,"COLLECTION",msg);
//
//                return true;
//            }
//        }
//        return false;
//    }


    //对于字段我们应该更具体点，对于Service和Provider而言，或者单例类，或者任何类的静态字段他们的生命周期都应该孙比较长的

//    private boolean isLongLifeVar(FieldRef fieldRef){
//        if(fieldRef instanceof StaticFieldRef)
//            return true;
//        if(isOrSubClass(fieldRef.getField().getDeclaringClass(),"android.app.Service")||
//                isOrSubClass(fieldRef.getField().getDeclaringClass(),"android.content.ContentProvider"))
//            return true;
//        return isSingleTonCls(fieldRef.getField().getDeclaringClass());
//    }


    public static boolean isOrSubClass(SootClass curCls,String targetCls){
        //判断是不是指定类或者它的子类
        if(curCls.getName().equals(targetCls))
            return true;
        if(curCls.hasSuperclass()){
            return isOrSubClass(curCls.getSuperclass(),targetCls);
        }else {
            return false;
        }
    }

    public static boolean isSingleTonCls(SootClass cls){
        //判断一个类是不是单例类
        //如果一个类是接口、抽象类、内部类、私有类
        if(cls.isEnum()||cls.isInterface()||cls.isAbstract()||cls.isInnerClass()||cls.isPrivate())
            return false;
        //否则，我们判断它的构造函数是不是私有的
        boolean flag=true;
        //如果它的所有构造函数都是私有的，我们认为它是单例类
        for(SootMethod m:cls.getMethods()){
            if(m.getName().equals("<init>")&&!m.isPrivate())
                flag=false;

        }
        return flag;
    }

    private void addRecord(SootMethod method, Unit unit, String type, String msg) {
        if (!res.containsKey(entryPoint))
            res.put(entryPoint, new HashSet<>());
        Point point = new Point(unit, method, type, msg);
        res.get(entryPoint).add(point);
    }


    private InvokeExpr getInvokeExpr(Unit u) {
        if (u instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) u;
            if (assignStmt.containsInvokeExpr())
                return assignStmt.getInvokeExpr();
        }
        if (u instanceof InvokeStmt) {
            InvokeStmt invokeStmt = (InvokeStmt) u;
            return invokeStmt.getInvokeExpr();
        }
        return null;
    }

    public String getAuthority(){
        for(BinaryManifestContentProvider provider :manifest.getContentProviders()){
            String providerName = provider.getNameString();
            if(providerName.equals(component)) {
                AXmlNode xmlNode = provider.getAXmlNode();
                return "[("+xmlNode.getAttribute("authorities").getValue().toString()+")]";
            }
        }
        return null;
    }

    //对于注入的集合我们需要对他的生命周期进行判断
    //长生命周期的特征包括静态字段和服务类的实例字段

//    private Unit getDirectDefUnit(Unit unit, SootMethod m, Value curValue){
//        BriefUnitGraph graph = new BriefUnitGraph(m.retrieveActiveBody());
//        HashSet<Unit> visit=new HashSet<>();
//        Queue<Unit> queue=new LinkedList<>();
//        queue.add(unit);
//        while (!queue.isEmpty()){
//            Unit poll = queue.poll();
//            if(poll instanceof AssignStmt){
//                AssignStmt assignStmt = (AssignStmt) poll;
//                Value leftOp = assignStmt.getLeftOp();
//                if(leftOp.equals(curValue))
//                    return poll;
//            }
//            visit.add(poll);
//            for(Unit preUnit:graph.getPredsOf(poll)){
//                if(!visit.contains(preUnit))
//                    queue.add(preUnit);
//            }
//        }
//        return null;
//    }

}
