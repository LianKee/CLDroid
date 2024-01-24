package constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;


public class FileUsageDefinition {


    //SharedPreferences的载入操作
    public static final String SHARED_PREFERENCES_0 = "android.content.SharedPreferences getSharedPreferences(java.lang.String,int)";
    public static final String SHARED_PREFERENCES_1 = "android.content.SharedPreferences getPreferences(int)";
    public static final String SHARED_PREFERENCES_2 = "android.content.SharedPreferences getDefaultSharedPreferences(android.content.Context)";

    //SharedPreferences的读
    public static final String SHARED_PREFERENCE_REDA_0 = "<android.content.SharedPreferences: int getInt(java.lang.String,int)>";
    public static final String SHARED_PREFERENCE_REDA_1 = "<android.content.SharedPreferences: long getLong(java.lang.String,long)>";
    public static final String SHARED_PREFERENCE_REDA_2 = "<android.content.SharedPreferences: float getFloat(java.lang.String,float)>";
    public static final String SHARED_PREFERENCE_REDA_3 = "<android.content.SharedPreferences: java.lang.String getString(java.lang.String,java.lang.String)>";
    public static final String SHARED_PREFERENCE_REDA_4 = "<android.content.SharedPreferences: boolean getBoolean(java.lang.String,boolean)>";
    public static final String SHARED_PREFERENCE_REDA_5 = "<android.content.SharedPreferences: java.util.Set getStringSet(java.lang.String,java.util.Set)>";
    public static final String SHARED_PREFERENCE_REDA_6 = "<android.content.SharedPreferences: java.util.Map getAll()>";
    //SharedPreference的写
    public static final String SHARED_PREFERENCE_WRITE_0 = "android.content.SharedPreferences$Editor putInt(java.lang.String,int)";
    public static final String SHARED_PREFERENCE_WRITE_1 = "android.content.SharedPreferences$Editor putBoolean(java.lang.String,boolean)";
    public static final String SHARED_PREFERENCE_WRITE_2 = "android.content.SharedPreferences$Editor putLong(java.lang.String,long)";
    public static final String SHARED_PREFERENCE_WRITE_3 = "android.content.SharedPreferences$Editor putFloat(java.lang.String,float)";
    public static final String SHARED_PREFERENCE_WRITE_4 = "android.content.SharedPreferences$Editor putString(java.lang.String,java.lang.String)";
    public static final String SHARED_PREFERENCE_WRITE_5 = "android.content.SharedPreferences$Editor putStringSet(java.lang.String,java.util.Set)";

    //数据库对象的创建方式
    public static final String DATA_BASE_0 = "android.database.sqlite.SQLiteDatabase openOrCreateDatabase(java.lang.String,int,android.database.sqlite.SQLiteDatabase$CursorFactory,android.database.DatabaseErrorHandler)";
    public static final String DATA_BASE_1 = "android.database.sqlite.SQLiteDatabase openOrCreateDatabase(java.lang.String,int,android.database.sqlite.SQLiteDatabase$CursorFactory)";
    public static final String DATA_BASE_2 = "android.database.sqlite.SQLiteDatabase openOrCreateDatabase(java.io.File,android.database.sqlite.SQLiteDatabase$CursorFactory)";
    public static final String DATA_BASE_3 = "android.database.sqlite.SQLiteDatabase getReadableDatabase()";
    public static final String DATA_BASE_4 = "android.database.sqlite.SQLiteDatabase getWritableDatabase()";
    public static final String DATA_BASE_5 = "android.database.sqlite.SQLiteDatabase openDatabase(java.io.File,android.database.sqlite.SQLiteDatabase$OpenParams)";
    public static final String DATA_BASE_6 = "android.database.sqlite.SQLiteDatabase openDatabase(java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,int)";
    public static final String DATA_BASE_7 = "android.database.sqlite.SQLiteDatabase openDatabase(java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,int,android.database.DatabaseErrorHandler)";

    //数据库的查询
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

    //ContentResolver查询
    public static final String CONTENT_RESOLVER_QUERY_0 = "android.database.Cursor query(android.net.Uri,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String)";
    public static final String CONTENT_RESOLVER_QUERY_1 = "android.database.Cursor query(android.net.Uri,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,android.os.CancellationSignal)";
//    public static final String CONTENT_RESOLVER_LOAD_THUMBNAIL = "android.graphics.Bitmap loadThumbnail(android.net.Uri,android.util.Size,android.os.CancellationSignal)";
//    public static final String CONTENT_RESOLVER_OPEN_ASSET_FILE = "android.content.res.AssetFileDescriptor openAssetFile(android.net.Uri,java.lang.String,android.os.CancellationSignal)";
//    public static final String CONTENT_RESOLVER_OPEN_FILE = "android.os.ParcelFileDescriptor openFile(android.net.Uri,java.lang.String,android.os.CancellationSignal)";
//    public static final String CONTENT_RESOLVER_OPEN_INPUT_STREAM = "java.io.InputStream openInputStream(android.net.Uri)";

    //文件加载构造方法，文件创建
    public static final String FILE_LOAD_0 = "<java.io.File: void <init>(java.lang.String)>";
    public static final String FILE_LOAD_1 = "<java.io.File: void <init>(java.lang.String,java.lang.String)>";
    public static final String FILE_LOAD_2 = "<java.io.File: void <init>(java.io.File,java.lang.String)>";
    public static final String FILE_LOAD_3 = "<java.io.File: void <init>(java.net.URI)>";
    //输入流构造方法,这些输入流的参数为file或者file_name,这些API可以直接判定文件名称
    public static final String FILE_INPUT_STREAM_0 = "<java.io.FileInputStream: void <init>(java.io.File)>";
    public static final String FILE_INPUT_STREAM_1 = "<java.io.FileInputStream: void <init>(java.lang.String)>";
    public static final String FILE_INPUT_STREAM_2 = "<java.io.FileReader: void <init>(java.lang.String)>";
    public static final String FILE_INPUT_STREAM_3 = "<java.io.FileReader: void <init>(java.io.File)>";
    //输出流构造方法，这些输出流的参数为file或者file_name,这些API可以直接判定文件名称
    public static final String FILE_OUTPUT_STREAM_0 = "<java.io.FileOutputStream: void <init>(java.lang.String)>";
    public static final String FILE_OUTPUT_STREAM_1 = "<java.io.FileOutputStream: void <init>(java.lang.String,boolean)>";
    public static final String FILE_OUTPUT_STREAM_2 = "<java.io.FileOutputStream: void <init>(java.io.File)>";
    public static final String FILE_OUTPUT_STREAM_3 = "<java.io.FileOutputStream: void <init>(java.io.File,boolean)>";
    public static final String FILE_OUTPUT_STREAM_4 = "<java.io.FileWriter: void <init>(java.lang.String)>";
    public static final String FILE_OUTPUT_STREAM_5 = "<java.io.FileWriter: void <init>(java.lang.String,boolean)>";
    public static final String FILE_OUTPUT_STREAM_6 = "<java.io.FileWriter: void <init>(java.io.File)>";
    public static final String FILE_OUTPUT_STREAM_7 = "<java.io.FileWriter: void <init>(java.io.File,boolean)>";
    public static final String FILE_OUTPUT_STREAM_8 = "<java.io.OutputStreamWriter: void <init>(java.io.OutputStream)>";
    public static final String FILE_OUTPUT_STREAM_9 = "<java.io.OutputStreamWriter: void <init>(java.io.OutputStream,java.lang.String)>";
    public static final String FILE_OUTPUT_STREAM_10 = "<java.io.BufferedWriter: void <init>(java.io.Writer)>";
    public static final String FILE_OUTPUT_STREAM_11 = "<java.io.BufferedWriter: void <init>(java.io.Writer,int)>";
    public static final String FILE_OUTPUT_STREAM_12 = "<java.io.DataOutputStream: void <init>(java.io.OutputStream)>";
    public static final String FILE_OUTPUT_STREAM_13 = "<java.io.BufferedOutputStream: void <init>(java.io.OutputStream)>";
    public static final String FILE_OUTPUT_STREAM_14 = "<java.io.BufferedOutputStream: void <init>(java.io.OutputStream,int)>";
    public static final String FILE_OUTPUT_STREAM_15 = "<java.io.FilterOutputStream: void <init>(java.io.OutputStream)>";

    //Uri创建API
    public static final String URI_CREATE_0 = "<android.net.Uri: android.net.Uri parse(java.lang.String)>";
    public static final String URI_CREATE_1 = "<android.net.Uri: android.net.Uri withAppendedPath(android.net.Uri,java.lang.String)>";
    public static final String URI_CREATE_2 = "<android.net.Uri: android.net.Uri fromFile(java.io.File)>";
    public static final String URI_CREATE_3 = "<android.net.Uri: android.net.Uri fromParts(java.lang.String,java.lang.String,java.lang.String)>";

    public static final String[] sharedPreferencesLoadAPI = {SHARED_PREFERENCES_0, SHARED_PREFERENCES_1, SHARED_PREFERENCES_2};

    public static final List<String> sharedPreferencesLoadAPIList = Arrays.asList(sharedPreferencesLoadAPI);

    public static final String[] sharedPreferencesReadAPI = {SHARED_PREFERENCE_REDA_0, SHARED_PREFERENCE_REDA_1, SHARED_PREFERENCE_REDA_2, SHARED_PREFERENCE_REDA_3,
            SHARED_PREFERENCE_REDA_4, SHARED_PREFERENCE_REDA_5, SHARED_PREFERENCE_REDA_6};

    public static final List<String> sharedPreferencesReadAPIList = Arrays.asList(sharedPreferencesReadAPI);

    public static final String[] sharedPreferencesWriteAPI = {SHARED_PREFERENCE_WRITE_0, SHARED_PREFERENCE_WRITE_1, SHARED_PREFERENCE_WRITE_2, SHARED_PREFERENCE_WRITE_3,
            SHARED_PREFERENCE_WRITE_3, SHARED_PREFERENCE_WRITE_4, SHARED_PREFERENCE_WRITE_5};

    public static final List<String> sharedPreferencesWriteAPIList = Arrays.asList(sharedPreferencesWriteAPI);

    public static final String[] dataBaseOpenOrCreateAPI = {DATA_BASE_0, DATA_BASE_1, DATA_BASE_2, DATA_BASE_3, DATA_BASE_4, DATA_BASE_5, DATA_BASE_6, DATA_BASE_7};

    public static final List<String> dataBaseOpenOrCreateAPIList = Arrays.asList(dataBaseOpenOrCreateAPI);

    public static final String[] dataBaseQueryAPI = {DATA_BASE_QUERY_0, DATA_BASE_QUERY_1, DATA_BASE_QUERY_2, DATA_BASE_QUERY_3, DATA_BASE_QUERY_4, DATA_BASE_QUERY_5,
            DATA_BASE_QUERY_6, DATA_BASE_QUERY_7, DATA_BASE_QUERY_8, DATA_BASE_QUERY_9};

    public static final List<String> dataBaseQueryAPIList = Arrays.asList(dataBaseQueryAPI);

    public static final String[] contentResolverQueryAPI = {CONTENT_RESOLVER_QUERY_0, CONTENT_RESOLVER_QUERY_1,
//            CONTENT_RESOLVER_LOAD_THUMBNAIL, CONTENT_RESOLVER_OPEN_ASSET_FILE,
//            CONTENT_RESOLVER_OPEN_FILE, CONTENT_RESOLVER_OPEN_INPUT_STREAM
    };

    public static final List<String> contentResolverQueryAPIList = Arrays.asList(contentResolverQueryAPI);

    public static final String[] inputStreamConstructor = {FILE_INPUT_STREAM_0, FILE_INPUT_STREAM_1, FILE_INPUT_STREAM_2, FILE_INPUT_STREAM_3};

    public static final List<String> inputStreamConstructorList = Arrays.asList(inputStreamConstructor);

    public static final String[] outputStreamConstructor = {FILE_OUTPUT_STREAM_0, FILE_OUTPUT_STREAM_1, FILE_OUTPUT_STREAM_2, FILE_OUTPUT_STREAM_3, FILE_OUTPUT_STREAM_4, FILE_OUTPUT_STREAM_5,
            FILE_OUTPUT_STREAM_5, FILE_OUTPUT_STREAM_6, FILE_OUTPUT_STREAM_7, FILE_OUTPUT_STREAM_8, FILE_OUTPUT_STREAM_9, FILE_OUTPUT_STREAM_10,
            FILE_OUTPUT_STREAM_11, FILE_OUTPUT_STREAM_12, FILE_OUTPUT_STREAM_12, FILE_OUTPUT_STREAM_13, FILE_OUTPUT_STREAM_14, FILE_OUTPUT_STREAM_15};

    public static final List<String> outputStreamConstructorList = Arrays.asList(outputStreamConstructor);

    public static final String[] uriCreateAPI = {URI_CREATE_0, URI_CREATE_1, URI_CREATE_2, URI_CREATE_3};

    public static final List<String> uriCreateAPIList = Arrays.asList(uriCreateAPI);

    public static final String[] javaIOAPI = {
            FILE_LOAD_0,
            FILE_LOAD_1,
            FILE_LOAD_2,
            FILE_LOAD_3,
            FILE_INPUT_STREAM_0,
            FILE_INPUT_STREAM_1,
            FILE_INPUT_STREAM_2,
            FILE_INPUT_STREAM_3,
            FILE_OUTPUT_STREAM_0,
            FILE_OUTPUT_STREAM_1,
            FILE_OUTPUT_STREAM_2,
            FILE_OUTPUT_STREAM_3,
            FILE_OUTPUT_STREAM_4,
            FILE_OUTPUT_STREAM_5,
            FILE_OUTPUT_STREAM_6,
            FILE_OUTPUT_STREAM_7,
            FILE_OUTPUT_STREAM_8,
            FILE_OUTPUT_STREAM_9,
            FILE_OUTPUT_STREAM_10,
            FILE_OUTPUT_STREAM_11,
            FILE_OUTPUT_STREAM_12,
            FILE_OUTPUT_STREAM_13,
            FILE_OUTPUT_STREAM_14,
            FILE_OUTPUT_STREAM_15
    };



    private static List<String> javaIOAPIList = Arrays.asList(javaIOAPI);

    public static final String[] fileLoadApis={FILE_LOAD_0,FILE_LOAD_1,FILE_LOAD_2,FILE_LOAD_3};

    private static List<String> fileLoadAPIList=Arrays.asList(fileLoadApis);




    public static List<String> getSharedPreferencesLoadAPIList() {
        return sharedPreferencesLoadAPIList;
    }

    public static List<String> getSharedPreferencesReadAPIList() {
        return sharedPreferencesReadAPIList;
    }

    public static List<String> getSharedPreferencesWriteAPIList() {
        return sharedPreferencesWriteAPIList;
    }

    public static List<String> getDataBaseOpenOrCreateAPIList() {
        return dataBaseOpenOrCreateAPIList;
    }

    public static List<String> getDataBaseQueryAPIList() {
        return dataBaseQueryAPIList;
    }

    public static List<String> getContentResolverQueryAPIList() {
        return contentResolverQueryAPIList;
    }

    public static List<String> getInputStreamConstructorList() {
        return inputStreamConstructorList;
    }

    public static List<String> getOutputStreamConstructorList() {
        return outputStreamConstructorList;
    }

    public static List<String> getUriCreateAPIList() {
        return uriCreateAPIList;
    }

    public static List<String> getJavaIOAPIList(){
        return javaIOAPIList;
    }

    public static List<String> getFileLoadAPIList(){ return fileLoadAPIList;}




}