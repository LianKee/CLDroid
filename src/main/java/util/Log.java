package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class Log {

    public enum Mode {
        APP, COMPONENT, SINK,SERVICE,INTERFACE,SENTENCE,FILE_INFO,UNIT,CLASS,DATA_BASE_SINK,VERSION,PATH
    }

    private static final Logger logger = LoggerFactory.getLogger(Log.class);
    private static File logFile = null;

    private static boolean startLog=false;//设置是否开启log

    private static BufferedWriter bufferedWriter = null;

    public static void openLog(String fileName, boolean append) {
        try {
            logFile = new File(fileName);
            FileWriter writer = new FileWriter(logFile, append);
            bufferedWriter = new BufferedWriter(writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void closeLog() {
        try {
            if (bufferedWriter != null)
                bufferedWriter.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void write(String message) {
        try {
            bufferedWriter.write(message + '\n');
        }catch (Exception e){
            e.printStackTrace();
        }
    }



    public static void write(Mode mode, String... message) {
        try {
            switch (mode) {
                case APP:
                    bufferedWriter.write("================================APP========================================\n");
                    bufferedWriter.write("APP: " + message[0] + '\n');
                    break;
                case PATH:
                    bufferedWriter.write("PATH: " + message[0] + '\n');
                    break;
                case VERSION:
                    bufferedWriter.write("VERSION: " + message[0] + '\n');
                    break;
                case SINK:
                    bufferedWriter.write("ENTRY_POINT: " + message[0] + '\n');
                    bufferedWriter.write("UNIT:" + message[1] + '\n');
                    bufferedWriter.write("METHOD:" + message[2] + '\n');
                    bufferedWriter.write("TYPE:" + message[3] + '\n');
                    if(!message[4].equals(""))
                        bufferedWriter.write(message[4]+'\n');
                    break;
                case COMPONENT:

                    bufferedWriter.write("***********************************<Component>*****************************\n");
                    bufferedWriter.write("COMPONENT:" + message[0] + '\n');
                    bufferedWriter.write('\n');
                    break;
                case SERVICE:
                    bufferedWriter.write("===================================System Service==========================\n");
                    bufferedWriter.write("System Service: "+message[0]+'\n');
                    break;
                case INTERFACE:
                    bufferedWriter.write("***************************************Interface***************************\n");
                    bufferedWriter.write("Interface: "+message[0]+'\n');
                    break;
                case SENTENCE:
                    bufferedWriter.write("method: "+message[0]+'\n');
                    break;
                case UNIT:
                    bufferedWriter.write("Unit: "+message[0]+'\n');
                    break;
                case CLASS:
                    bufferedWriter.write("Class: "+message[0]+'\n');
                    break;
                case FILE_INFO:
                    bufferedWriter.write("================================File Info=====================================\n");
                    bufferedWriter.write("Type: "+message[0]+'\n');
                    bufferedWriter.write("Name: "+message[1]+'\n');
                    bufferedWriter.write("Mode: "+message[2]+'\n');
                    bufferedWriter.write("Entry: "+message[3]+'\n');
                    break;
                case DATA_BASE_SINK:
                    bufferedWriter.write("=================================DataBase Info=====================================\n");
                    bufferedWriter.write("ENTRY_POINT: " + message[0] + '\n');
                    bufferedWriter.write("UNIT:" + message[1] + '\n');
                    bufferedWriter.write("METHOD:" + message[2] + '\n');
                    bufferedWriter.write("TYPE:" + message[3] + '\n');
                    bufferedWriter.write(message[4]+'\n');
                    break;
                default:
                    logger.warn("the mode is not define!");
            }
            bufferedWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
