package constant;

import java.util.Arrays;
import java.util.List;


public class IPCPointDefinition {


    //通过以下方法调用第三方应用或者自身应用的组件
    public static final String IPC_SERVICE="android.content.ComponentName startService(android.content.Intent)";
    public static final String IPC_FOREGROUND_SERVICE="android.content.ComponentName startForegroundService(android.content.Intent)";

    public static final String IPC_RECEIVER="void sendBroadcast(android.content.Intent)";
    public static final String IPC_ORDER_RECEIVER="void sendBroadcast(android.content.Intent,java.lang.String)";

    public static final String IPC_PROVIDER_BULK_INSERT="int bulkInsert(android.net.Uri,android.content.ContentValues[])";
    public static final String IPC_PROVIDER_INSERT_0="android.net.Uri insert(android.net.Uri,android.content.ContentValues)";
    public static final String IPC_PROVIDER_INSERT_1="android.net.Uri insert(android.net.Uri,android.content.ContentValues,android.os.Bundle)";
    public static final String IPC_PROVIDER_UPDATE_0="int update(android.net.Uri,android.content.ContentValues[],java.lang.String,java.lang.String[])";
    public static final String IPC_PROVIDER_UPDATA_1="int update(android.net.Uri,android.content.ContentValues[],android.os.Bundle)";


    public static final String[] ipc_method={IPC_SERVICE,IPC_FOREGROUND_SERVICE,IPC_RECEIVER,IPC_ORDER_RECEIVER,IPC_PROVIDER_BULK_INSERT,IPC_PROVIDER_INSERT_0,IPC_PROVIDER_INSERT_1,IPC_PROVIDER_UPDATE_0,
            IPC_PROVIDER_UPDATA_1
    };

    public static final List<String> ipcMethodList= Arrays.asList(ipc_method);

    public static List<String> getIpcMethodList(){
        return ipcMethodList;
    }


}
