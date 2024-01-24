package constant;

import java.io.File;


public class ApkAndJavaConstants {

    public static final String apkDir = System.getProperty("user.dir") + File.separator + "app";
    public static final String androidJarPath = System.getenv("ANDROID_HOME") + File.separator + "platforms";


    public static final String programPath = System.getProperty("user.dir") + File.separator + "program";
}
