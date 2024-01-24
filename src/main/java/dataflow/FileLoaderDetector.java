package dataflow;

import constant.FileUsageDefinition;
import constant.StrawPointsDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import util.StringConvertUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;


public class FileLoaderDetector {

    public static final Logger logger = LoggerFactory.getLogger(FileLoaderDetector.class);

    //直接使用数据库提供的API进行查询的
    public static final String DATA_BASE_QUERY_0 = "android.database.Cursor query(java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String)";
    public static final String DATA_BASE_QUERY_1 = "android.database.Cursor query(java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String,java.lang.String)";
    public static final String DATA_BASE_QUERY_2 = "android.database.Cursor query(boolean,java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String,java.lang.String)";
    public static final String DATA_BASE_QUERY_3 = "android.database.Cursor query(boolean,java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String,java.lang.String,android.os.CancellationSignal)";
    public static final String DATA_BASE_QUERY_4 = "android.database.Cursor rawQuery(java.lang.String,java.lang.String[])";
    public static final String DATA_BASE_QUERY_5 = "android.database.Cursor rawQuery(java.lang.String,java.lang.String[],android.os.CancellationSignal)";
    public static final String DATA_BASE_QUERY_6 = "android.database.Cursor rawQueryWithFactory(android.database.sqlite.SQLiteDatabase$CursorFactory,java.lang.String,java.lang.String[],java.lang.String)";
    public static final String DATA_BASE_QUERY_7 = "android.database.Cursor rawQueryWithFactory(android.database.sqlite.SQLiteDatabase$CursorFactory,java.lang.String,java.lang.String[],java.lang.String,android.os.CancellationSignal)";
    public static final String DATA_BASE_QUERY_8 = "android.database.Cursor queryWithFactory(android.database.sqlite.SQLiteDatabase$CursorFactory,boolean,java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String,java.lang.String)";
    public static final String DATA_BASE_QUERY_9 = "android.database.Cursor queryWithFactory(android.database.sqlite.SQLiteDatabase$CursorFactory,boolean,java.lang.String,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,java.lang.String,java.lang.String,java.lang.String,android.os.CancellationSignal)";
    public static final String DATA_BASE_QUERY_10 = "void execSQL(java.lang.String)";
    public static final String DATA_BASE_QUERY_11 = "void execSQL(java.lang.String,java.lang.Object[])";

    //使用ContentResolver提供的API进行查询的
    public static final String CONTENT_RESOLVER_QUERY_0 = "android.database.Cursor query(android.net.Uri,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String)";
    public static final String CONTENT_RESOLVER_QUERY_1 = "android.database.Cursor query(android.net.Uri,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,android.os.CancellationSignal)";
    public static final String CONTENT_RESOLVER_QUERY_2 = "android.database.Cursor query(android.net.Uri,java.lang.String[],android.os.Bundle,android.os.CancellationSignal)";
    public static final HashSet<String> QUERY_API_SET = new HashSet<>();

    public static final String INSET = "<android.database.sqlite.SQLiteDatabase: long insert(java.lang.String,java.lang.String,android.content.ContentValues)>";
    public static final String UPDATE="";

    static {
        QUERY_API_SET.add(DATA_BASE_QUERY_0);
        QUERY_API_SET.add(DATA_BASE_QUERY_1);
        QUERY_API_SET.add(DATA_BASE_QUERY_2);
        QUERY_API_SET.add(DATA_BASE_QUERY_3);
        QUERY_API_SET.add(DATA_BASE_QUERY_4);
        QUERY_API_SET.add(DATA_BASE_QUERY_5);
        QUERY_API_SET.add(DATA_BASE_QUERY_6);
        QUERY_API_SET.add(DATA_BASE_QUERY_7);
        QUERY_API_SET.add(DATA_BASE_QUERY_8);
        QUERY_API_SET.add(DATA_BASE_QUERY_9);
        QUERY_API_SET.add(DATA_BASE_QUERY_10);
        QUERY_API_SET.add(DATA_BASE_QUERY_11);

        QUERY_API_SET.add(CONTENT_RESOLVER_QUERY_0);
        QUERY_API_SET.add(CONTENT_RESOLVER_QUERY_1);
        QUERY_API_SET.add(CONTENT_RESOLVER_QUERY_2);
    }


    public JimpleBasedInterproceduralCFG icfg = null;

    public FileNameConstructor nameConstructor = null;

    public DataBaseInfoExtractor dataBaseInfoExtractor = null;
    public SharedPreferencesInfoExtractor sharedPreferencesInfoExtractor = null;

    public FileLoaderDetector(JimpleBasedInterproceduralCFG icfg) {
        this.icfg = icfg;
        this.nameConstructor = new FileNameConstructor(icfg);
        this.dataBaseInfoExtractor = new DataBaseInfoExtractor(this.nameConstructor);
        this.sharedPreferencesInfoExtractor = new SharedPreferencesInfoExtractor(this.nameConstructor);
    }


    public DataBaseInfo getDataBaseInfo(Unit dataBaseLoaderPoint, SootMethod method) {
        //获取应用中的数据库信息
        return this.dataBaseInfoExtractor.constructDataBaseInfo(dataBaseLoaderPoint, method);

    }

    public SharedPreferencesInfo getSharedPreferencesInfo(Unit sharedPreferenceUsePoint, SootMethod method) {
        //获取sharedPreference的信息
        return this.sharedPreferencesInfoExtractor.construct(sharedPreferenceUsePoint, method);
    }


    public static class DataBaseInfo {
        //数据库信息
        public String database;
        public String table;
        public String tableInfo;
        public String uri;

        //标准返回的数据的最大数目
        private int max=20;

        public DataBaseInfo(String database, String table, String tableInfo, String uri) {
            this.database = database;
            this.table = table;
            this.tableInfo = tableInfo;
            this.uri = uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        @Override
        public boolean equals(Object obj) {
            DataBaseInfo baseInfo = (DataBaseInfo) obj;
//            return database.equals(baseInfo.database) && table.equals(baseInfo.table)
//                    && tableInfo.equals(baseInfo.tableInfo) && uri.equals(baseInfo.uri);

            return getStandardDataBaseName().toString().equals(baseInfo.getStandardDataBaseName().toString())
                    &&getStandardTableName().toString().equals(baseInfo.getStandardTableName().toString())
                    &&getStandardUri().toString().equals(baseInfo.getStandardUri().toString());
        }

        public HashSet<String> getStandardUri(){
            if(uri==null||uri.isEmpty())
                return new HashSet<>();
            StringConvertUtil convertUtil = new StringConvertUtil();
            return convertUtil.getFormatInfo(uri);
        }

        public HashSet<String> getStandardDataBaseName(){
            if(database==null||database.isEmpty())
                return new HashSet<>();
            StringConvertUtil convertUtil = new StringConvertUtil();
            return convertUtil.getFormatInfo(database);
        }

        public HashSet<String> getStandardTableName(){
            if(table==null||table.isEmpty())
                return new HashSet<>();
            StringConvertUtil convertUtil = new StringConvertUtil();
            return convertUtil.getFormatInfo(table);
        }

        public HashSet<String> getStandardTableInfo(){
            if(tableInfo==null||tableInfo.isEmpty())
                return new HashSet<>();
            HashSet<String> strings = new HashSet<>();
            strings.add(tableInfo);
            return strings;
        }

//        private void getFixedStandardData(HashSet<String> src,HashSet<String> dest ){
//            //最多收集的数据信息
//            int budget=Math.min(max,src.size());
//
//
//
//        }



        @Override
        public int hashCode() {
            return Objects.hash(getStandardDataBaseName().toString(),getStandardTableName().toString(),getStandardUri().toString());
        }

        @Override
        public String toString() {
            return "[DataBase]: " + getStandardDataBaseName() + "\n" +
                    "[Table]: " + getStandardTableName() + "\n" +
                    "[TableInfo]: " + getStandardTableInfo() + "\n" +
                    "[Uri]: " + getStandardUri() + "\n";
        }
    }

    static class DataBaseInfoExtractor {
        //数据库信息恢复器
        //负责根据调用语句恢复数据库的信息，例如：uri,数据库名，表名

        public FileNameConstructor nameConstructor = null;

        public DataBaseInfoExtractor(FileNameConstructor nameConstructor) {
            this.nameConstructor = nameConstructor;
        }

        public DataBaseInfo constructDataBaseInfo(Unit unit, SootMethod method) {
            InvokeExpr invokeExpr = getInvokeExpr(unit);
            //这个地方考虑的api缺少注入数据库的那部分需要补充上
            if (FileUsageDefinition.getDataBaseQueryAPIList().contains(invokeExpr.getMethod().getSubSignature())) {
                return databaseHandler(unit, method);
            } else if (StrawPointsDefinition.getDatabaseModifeiedMethods().contains(invokeExpr.getMethod().getSignature())) {
                //对于注入数据库的行为，我们应该将注入的数据库的名字，表明，以及该表的字段信息给提取出来
                //这里其实我们只需要关注Provider中使用标准API方式注入数据库的方式
                return dataBaseHandlerForInsertAPI(unit, method);
            } else {
                return contentResolverHandler(unit, method);
            }

        }

        private DataBaseInfo databaseHandler(Unit unit, SootMethod method) {
            //恢复标准数据库api中所使用的关键数据
            //这里需要根据数据库API的类型恢复数据
            InvokeExpr invokeExpr = getInvokeExpr(unit);
            String subSignature = invokeExpr.getMethod().getSubSignature();
            Value tableValue = null;
            Value sql = null;
            switch (subSignature) {
                case FileUsageDefinition.DATA_BASE_QUERY_0:
                case FileUsageDefinition.DATA_BASE_QUERY_1:
                    tableValue = invokeExpr.getArg(0);
                    break;
                case FileUsageDefinition.DATA_BASE_QUERY_2:
                case FileUsageDefinition.DATA_BASE_QUERY_3:
                    tableValue = invokeExpr.getArg(1);
                    break;
                case FileUsageDefinition.DATA_BASE_QUERY_4:
                case FileUsageDefinition.DATA_BASE_QUERY_5:
                    sql = invokeExpr.getArg(0);
                    break;
                case FileUsageDefinition.DATA_BASE_QUERY_6:
                case FileUsageDefinition.DATA_BASE_QUERY_7:
                    sql = invokeExpr.getArg(1);
                    break;
                case FileUsageDefinition.DATA_BASE_QUERY_8:
                case FileUsageDefinition.DATA_BASE_QUERY_9:
                    tableValue = invokeExpr.getArg(2);
                    break;
                default:
                    sql = invokeExpr.getArg(0);
                    break;
            }
            String tableName = null;
            if (tableValue != null)
                tableName = getValueOfObject(tableValue, unit, method);
            if (sql != null) {
                String sqlStmt = getValueOfObject(sql, unit, method);
                if (sqlStmt == null) {
                    tableName = "[()]";
                } else {
                    sqlStmt=sqlStmt.replaceAll("[\\[\\]()]","").toLowerCase();
//                    logger.info("处理后的sql语句是： {}",sqlStmt);
                    //我们要得到具体的表名
                    String[] s = sqlStmt.trim().split(" ");
                    if (sqlStmt.contains("select")) {
                        for(int i=0;i<s.length;i++)
                            if(s[i].equals("from")){
                                if(i+1<s.length)
                                    tableName="[("+s[i+1]+")]";
                                break;
                            }
                    } else {
                        tableName = "[()]";
                    }
                }
            }
            List<ValueBox> useBoxes = invokeExpr.getUseBoxes();
            String dataBaseName = nameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), unit, method, 0);
            DataBaseInfo res = new DataBaseInfo(dataBaseName, tableName, "", "");
            return res;
        }

        private DataBaseInfo contentResolverHandler(Unit unit, SootMethod method) {
            //恢复使用contentResolver进行数据查询的API
            //这里关注的是uri
            InvokeExpr invokeExpr = getInvokeExpr(unit);
            if (invokeExpr == null) {
                logger.warn("the unit is not a call site");
                return null;
            }
            String uri = getValueOfObject(invokeExpr.getArg(0), unit, method);
//            logger.info("construct uri: {}", uri);
            DataBaseInfo res = new DataBaseInfo("", "", "", uri);
            return res;
        }

        private DataBaseInfo dataBaseHandlerForInsertAPI(Unit unit, SootMethod method) {
            InvokeExpr invokeExpr = getInvokeExpr(unit);
            VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr) invokeExpr;
            //获取表明信息
            String tableName = getValueOfObject(invokeExpr.getArg(0), unit, method);
            //获取数据库信息
            String dataBaseName = nameConstructor.getRef2Str(virtualInvokeExpr.getBase(), unit, method, 0);
            //我们还想知道数据库的字段信息
            FileNameConstructor.EXTRACTOR_MODE = "DATABASE_FIELD";
            //todo

            String tableInfo = nameConstructor.getRef2Str(virtualInvokeExpr.getBase(), unit, method, 0);
            FileNameConstructor.EXTRACTOR_MODE = "DATABASE_NO_FIELD";

            //我们要把注入的数据库uri
            DataBaseInfo res = new DataBaseInfo(dataBaseName, tableName, tableInfo, "");
            return res;
        }

        private String getValueOfObject(Value value, Unit u, SootMethod method) {
            if (value instanceof Local)
                return nameConstructor.getRef2Str(value, u, method, 0);
            return "[("+value.toString()+")]";
        }

    }

    public static class SharedPreferencesInfo {
        public String preferencesName;
        public String key;
        //标志能否影响key值
        public boolean affectKey = false;
        //标志是否从属于一个Provider
        public String uriAuthority="";

        public SharedPreferencesInfo(String preferencesName, String key) {
            this.preferencesName = preferencesName;
            this.key = key;
        }

        public void setUriAuthority(String uriAuthority) {
            this.uriAuthority = uriAuthority;
        }

        public void setAffectKey(boolean affectKey) {
            this.affectKey = affectKey;
        }

        @Override
        public boolean equals(Object obj) {
            SharedPreferencesInfo preferencesInfo = (SharedPreferencesInfo) obj;
//            return preferencesName.equals(preferencesInfo.preferencesName) && key.equals(preferencesInfo.key) &&
//                    uriAuthority.equals(preferencesInfo.uriAuthority);
            return getStandardPreferencesName().toString().equals(preferencesInfo.getStandardPreferencesName().toString())&&getStandardKey().toString().equals(preferencesInfo.getStandardKey().toString())&&
                    (affectKey== preferencesInfo.affectKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(getStandardKey().toString(),getStandardPreferencesName().toString(),affectKey);
        }

        public HashSet<String> getStandardPreferencesName() {
            if (preferencesName == null || preferencesName.isEmpty())
                return new HashSet<>();
            StringConvertUtil convertUtil = new StringConvertUtil();
            return convertUtil.getFormatInfo(preferencesName);
        }

        public HashSet<String> getStandardKey() {
            if (key == null || key.isEmpty())
                return new HashSet<>();
            StringConvertUtil convertUtil = new StringConvertUtil();
            return convertUtil.getFormatInfo(key);
        }
        public HashSet<String> getStandardUri(){
            if(uriAuthority==null||uriAuthority.isEmpty())
                return new HashSet<>();
            StringConvertUtil convertUtil = new StringConvertUtil();
            return convertUtil.getFormatInfo(uriAuthority);
        }


        @Override
        public String toString() {
            return "[PreferencesName]: " + getStandardPreferencesName() + '\n' +
                    "[Key]: " + getStandardKey() + "\n";
        }
    }

    static class SharedPreferencesInfoExtractor {
        //SharedPreference信息提取器
        public FileNameConstructor nameConstructor = null;

        public SharedPreferencesInfoExtractor(FileNameConstructor fileNameConstructor) {
            this.nameConstructor = fileNameConstructor;
        }

        public SharedPreferencesInfo construct(Unit u, SootMethod method) {
            InvokeExpr invokeExpr = getInvokeExpr(u);
            if (invokeExpr == null)
                return null;
            SootMethod m = invokeExpr.getMethod();
            List<ValueBox> useBoxes = invokeExpr.getUseBoxes();
            String preferenceName = "";
            String key = "";
            //从SharePreference中读取数据
            if (FileUsageDefinition.getSharedPreferencesReadAPIList().contains(m.getSignature())) {
                String str = nameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), u, method, 0);
                if (!m.getName().equals("getAll")) {
                    key = getValueOfObject(invokeExpr.getArg(0), u, method);
                }
                preferenceName = str;
            } else if (FileUsageDefinition.getSharedPreferencesWriteAPIList().contains(m.getSubSignature())) {
                String str = nameConstructor.getRef2Str(useBoxes.get(useBoxes.size() - 1).getValue(), u, method, 0);
                key = getValueOfObject(invokeExpr.getArg(0), u, method);
                preferenceName = str;
            }
            SharedPreferencesInfo res = new SharedPreferencesInfo(preferenceName, key);
            return res;
        }

        private String getValueOfObject(Value value, Unit u, SootMethod method) {
            if (value instanceof Local)
                return nameConstructor.getRef2Str(value, u, method, 0);
            return "[("+value.toString()+")]";
        }

    }

    public HashSet<String> getStandardFileInfo(Unit curUnit,SootMethod method){
        //获取一个文件的名字
        InvokeExpr invokeExpr = getInvokeExpr(curUnit);
        if(invokeExpr==null)
            return new HashSet<>();
        String signature = invokeExpr.getMethod().getSignature();
        StringConvertUtil convertUtil = new StringConvertUtil();
        if(StrawPointsDefinition.getFileWriteMethodList().contains(signature)){
            //文件注入提取文件名
            InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
            String fileName = nameConstructor.getRef2Str(instanceInvokeExpr.getBase(), curUnit, method, 0);
            return convertUtil.getFormatInfo(fileName);
        }else {
            //文件加载提取文件名
            if(signature.equals(FileUsageDefinition.FILE_LOAD_0)||signature.equals(FileUsageDefinition.FILE_LOAD_3)
                    ||FileUsageDefinition.getInputStreamConstructorList().contains(signature)){
                String fileName = nameConstructor.getRef2Str(invokeExpr.getArg(0), curUnit, method, 0);
                return convertUtil.getFormatInfo(fileName);
            }else {
                String fileName = nameConstructor.getRef2Str(invokeExpr.getArg(1), curUnit, method, 0);
                return convertUtil.getFormatInfo(fileName);
            }
        }


    }

    public static InvokeExpr getInvokeExpr(Unit unit) {
        if (unit instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) unit;
            if (assignStmt.containsInvokeExpr())
                return assignStmt.getInvokeExpr();
        }
        if (unit instanceof InvokeStmt) {
            InvokeStmt invokeStmt = (InvokeStmt) unit;
            return invokeStmt.getInvokeExpr();
        }
        return null;
    }


    private static String[] stack = new String[100];
    private static int top = 0;

    public static String pop() {
        top--;
        return stack[top];
    }

    public static void push(String s) {
        stack[top] = s;
        top++;
    }

    public static HashSet<String> convertInfo(String input) {
        //将从文件中抽取的信息恢复成标准数据信息
        HashSet<String> res = new HashSet<>();
        if (!input.contains("[")) {
            String s = input.replaceAll("\"", "");
            res.add(s.toLowerCase());
            return res;
        }

        int i = 0;

        String var = "";

        for (i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '(' || input.charAt(i) == '[') {
                push(var);
                var = "";
            } else if (input.charAt(i) == ')' || input.charAt(i) == ']') {
                if (var.length() > 0) {
                    String out_str = "";
                    for (int j = 0; j < top; j++) {
                        out_str += stack[j];
                    }
                    String ans = out_str + var;
                    String s = ans.replaceAll("\"", "");
                    res.add(s.toLowerCase());
                }
                pop();
                var = "";
            } else {
                var = var + input.charAt(i);
            }
        }
        return res;
    }








}
