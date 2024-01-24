package constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class StrawPointsDefinition {

    public static final String COLLECTIONS_STRAWPOINT_REGEX = "(<(java|android)\\.util\\..*(boolean|void) add.*>)|(<(java|android)\\.util\\..*(java.lang.Object|void) put.*>)|(<android\\.util\\.Sparse.*void (append|put).*>)";

    public static final String FILE_WRITE_0="<java.io.FileOutputStream: void write(byte[])>";
    public static final String FILE_WRITE_1="<java.io.FileOutputStream: void write(byte[],int,int)>";
    public static final String FILE_WRITE_2="<java.io.FileWriter: void write(char[])>";
    public static final String FILE_WRITE_3="<java.io.FileWriter: void write(char[],int,int)>";
    public static final String FILE_WRITE_4="<java.io.FileWriter: void write(java.lang.String)>";
    public static final String FILE_WRITE_5="<java.io.FileWriter: void write(java.lang.String,int,int)>";
    public static final String FILE_WRITE_6="<java.io.FileWriter: java.io.Writer append(java.lang.CharSequence)>";
    public static final String FILE_WRITE_7="<java.io.FileWriter: java.io.Writer append(java.lang.CharSequence,int,int)>";
    public static final String FILE_WRITE_8="<java.io.OutputStreamWriter: void write(java.lang.String)>";
    public static final String FILE_WRITE_9="<java.io.OutputStreamWriter: void write(java.lang.String,int,int)>";
    public static final String FILE_WRITE_10="<java.io.OutputStreamWriter: void write(char[])>";
    public static final String FILE_WRITE_11="<java.io.OutputStreamWriter: void write(char[],int,int)>";
    public static final String FILE_WRITE_12="<java.io.OutputStreamWriter: java.io.Writer append(java.lang.CharSequence)>";
    public static final String FILE_WRITE_13="<java.io.OutputStreamWriter: java.io.Writer append(java.lang.CharSequence,int,int)>";
    public static final String FILE_WRITE_14="<java.io.BufferedWriter: void write(java.lang.String)>";
    public static final String FILE_WRITE_15="<java.io.BufferedWriter: void write(java.lang.String,int,int)>";
    public static final String FILE_WRITE_16="<java.io.BufferedWriter: void write(char[])>";
    public static final String FILE_WRITE_17="<java.io.BufferedWriter: void write(char[],int,int)>";
    public static final String FILE_WRITE_18="<java.io.BufferedWriter: java.io.Writer append(java.lang.CharSequence)>";
    public static final String FILE_WRITE_19="<java.io.BufferedWriter: java.io.Writer append(java.lang.CharSequence,int,int)>";
    public static final String FILE_WRITE_20="<java.io.DataOutputStream: void write(byte[])>";
    public static final String FILE_WRITE_21="<java.io.DataOutputStream: void write(byte[],int,int)>";
    public static final String FILE_WRITE_22="<java.io.DataOutputStream: void writeBytes(java.lang.String)>";
    public static final String FILE_WRITE_23="<java.io.DataOutputStream: void writeChars(java.lang.String)>";
    public static final String FILE_WRITE_24="<java.io.DataOutputStream: void writeUTF(java.lang.String)>";
    public static final String FILE_WRITE_25="<java.io.BufferedOutputStream: void write(byte[])>";
    public static final String FILE_WRITE_26="<java.io.BufferedOutputStream: void write(byte[],int,int)>";
    public static final String FILE_WRITE_27="<java.io.FilterOutputStream: void write(byte[])>";
    public static final String FILE_WRITE_28="<java.io.FilterOutputStream: void write(byte[],int,int)>";

    public static final String SHAREDPREFERENCES_PUT_STRING="<android.content.SharedPreferences$Editor: android.content.SharedPreferences$Editor putString(java.lang.String,java.lang.String)>";
    public static final String SHAREDPREFERENCES_PUT_BOOLEAN="<android.content.SharedPreferences$Editor: android.content.SharedPreferences$Editor putBoolean(java.lang.String,boolean)>";
    public static final String SHAREDPREFERENCES_PUT_STRINGSET="<android.content.SharedPreferences$Editor: android.content.SharedPreferences$Editor putStringSet(java.lang.String,java.util.Set)>";
    public static final String SHAREDPREFERENCES_PUT_FLOAT="<android.content.SharedPreferences$Editor: android.content.SharedPreferences$Editor putFloat(java.lang.String,float)>";
    public static final String SHAREDPREFERENCES_PUT_LONG="<android.content.SharedPreferences$Editor: android.content.SharedPreferences$Editor putLong(java.lang.String,long)>";
    public static final String SHARED_PREFERENCE_PUT_INT = "<android.content.SharedPreferences: int getInt(java.lang.String,int)>";


    public static final String DATABASE_INSERT="<android.database.sqlite.SQLiteDatabase: long insert(java.lang.String,java.lang.String,android.content.ContentValues)>";
    public static final String DATABASE_INSERTORTHROW="<android.database.sqlite.SQLiteDatabase: long insertOrThrow(java.lang.String,java.lang.String,android.content.ContentValues)>";
    public static final String DATABASE_INSERTWITHONCONFLICT="<android.database.sqlite.SQLiteDatabase: long insertWithOnConflict(java.lang.String,java.lang.String,android.content.ContentValues,int)>";
    public static final String DATABASE_EXECSQL="<android.database.sqlite.SQLiteDatabase: void execSQL(java.lang.String)>";
    public static final String DATABASE_UPDATE_0="<android.database.sqlite.SQLiteDatabase: int update(java.lang.String,android.content.ContentValues,java.lang.String,java.lang.String[])>";
    public static final String DATABASE_UPDATE_1="<android.database.sqlite.SQLiteDatabase: int updateWithOnConflict(java.lang.String,android.content.ContentValues,java.lang.String,java.lang.String[],int)>";
//    public static final String DATABASE_DELETE="<android.database.sqlite.SQLiteDatabase: int delete(java.lang.String,java.lang.String,java.lang.String[])>";

    public static final String MEDIA_BULK_INSERT="<android.content.ContentResolver: int bulkInsert(android.net.Uri,android.content.ContentValues[])>";
    public static final String MEDIA_INSERT_0="<android.content.ContentResolver: android.net.Uri insert(android.net.Uri,android.content.ContentValues)>";
    public static final String MEDIA_INSERT_1="<android.content.ContentResolver: android.net.Uri insert(android.net.Uri,android.content.ContentValues,android.os.Bundle)>";

    public static final String IPC_SERVICE="<android.content.ContextWrapper: android.content.ComponentName startService(android.content.Intent)>";
    public static final String IPC_FOREGROUND_SERVICE="<android.content.ContextWrapper: android.content.ComponentName startForegroundService(android.content.Intent)>";
    public static final String IPC_RECEIVER="<android.content.ContextWrapper: void sendBroadcast(android.content.Intent)>";
    public static final String IPC_ORDER_RECEIVER="<android.content.ContextWrapper: void sendBroadcast(android.content.Intent,java.lang.String)>";
    public static final String IPC_PROVIDER_BULK_INSERT="<android.content.ContentResolver: int bulkInsert(android.net.Uri,android.content.ContentValues[])>";
    public static final String IPC_PROVIDER_INSERT_0="<android.content.ContentResolver: android.net.Uri insert(android.net.Uri,android.content.ContentValues)>";
    public static final String IPC_PROVIDER_INSERT_1="<android.content.ContentResolver: android.net.Uri insert(android.net.Uri,android.content.ContentValues,android.os.Bundle)>";
    public static final String IPC_PROVIDER_UPDATE_0="<android.content.ContentResolver: int update(android.net.Uri,android.content.ContentValues[],java.lang.String,java.lang.String[])>";
    public static final String IPC_PROVIDER_UPDATA_1="<android.content.ContentResolver: int update(android.net.Uri,android.content.ContentValues[],android.os.Bundle)>";

    private static final String[] fileWriteMethod={FILE_WRITE_0,FILE_WRITE_1,FILE_WRITE_2,FILE_WRITE_3,FILE_WRITE_4,FILE_WRITE_5,
    FILE_WRITE_6,FILE_WRITE_7,FILE_WRITE_8,FILE_WRITE_9,FILE_WRITE_10,FILE_WRITE_11,FILE_WRITE_12,FILE_WRITE_13,FILE_WRITE_14,FILE_WRITE_15,
    FILE_WRITE_16,FILE_WRITE_17,FILE_WRITE_18,FILE_WRITE_19,FILE_WRITE_20,FILE_WRITE_21,FILE_WRITE_22,FILE_WRITE_23,FILE_WRITE_24,FILE_WRITE_25,
    FILE_WRITE_26,FILE_WRITE_27,FILE_WRITE_28};
    private static final List<String> fileWriteMethodList=Arrays.asList(fileWriteMethod);

    private static final String[] sharedPreferencesWriteMethod={SHAREDPREFERENCES_PUT_STRING,SHAREDPREFERENCES_PUT_STRINGSET,SHAREDPREFERENCES_PUT_BOOLEAN,SHAREDPREFERENCES_PUT_FLOAT,
    SHAREDPREFERENCES_PUT_LONG,SHARED_PREFERENCE_PUT_INT};
    private static final List<String> sharedPreferencesWriteMethodList=Arrays.asList(sharedPreferencesWriteMethod);

    private static final String[] databaseInsertMethod={DATABASE_INSERT,DATABASE_INSERTORTHROW,DATABASE_INSERTWITHONCONFLICT,DATABASE_EXECSQL,DATABASE_UPDATE_0,DATABASE_UPDATE_1
            ,IPC_PROVIDER_BULK_INSERT,IPC_PROVIDER_INSERT_0,IPC_PROVIDER_UPDATA_1,IPC_PROVIDER_UPDATE_0,IPC_PROVIDER_INSERT_1};
    private static final List<String> databaseInsertMethodList=Arrays.asList(databaseInsertMethod);

    private static final String[] mediaInsertMethod={MEDIA_BULK_INSERT,MEDIA_INSERT_0,MEDIA_INSERT_1};
    private static final List<String> mediaInsertMethodList=Arrays.asList(mediaInsertMethod);

    private static final String[] databaseModifiedMethods={DATABASE_INSERT,DATABASE_UPDATE_1,DATABASE_UPDATE_0,DATABASE_INSERTORTHROW,DATABASE_INSERTWITHONCONFLICT};
    private static final List<String> databaseModifiedMethodsList=Arrays.asList(databaseModifiedMethods);

    public static List<String> allInsertMethodList=null;

    /*
    =======================================================================================================
     */
    public static List<String> getFileWriteMethodList(){
        return fileWriteMethodList;
    }

    public static List<String> getSharedPreferencesWriteMethodList(){
        return sharedPreferencesWriteMethodList;
    }

    public static List<String> getDatabaseInsertMethodList(){
        return databaseInsertMethodList;
    }

    public static List<String> getMediaInsertMethodList(){
        return mediaInsertMethodList;
    }

    public static List<String> getAllInsertMethodList(){
        if(allInsertMethodList==null){
            allInsertMethodList=new ArrayList<>();
            allInsertMethodList.addAll(fileWriteMethodList);
            allInsertMethodList.addAll(sharedPreferencesWriteMethodList);
            allInsertMethodList.addAll(databaseInsertMethodList);
            allInsertMethodList.addAll(mediaInsertMethodList);
        }
        return allInsertMethodList;
    }

    public static List<String> getDatabaseModifeiedMethods(){
        return databaseModifiedMethodsList;
    }
}
