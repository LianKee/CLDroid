package dataflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.StringUtil;

import java.awt.*;
import java.util.HashSet;
import java.util.Objects;


public class DBInfo {
    //数据库信息描述

    public static final Logger logger= LoggerFactory.getLogger(DBInfo.class);


    public String dbName;
    public String tableName;
    public String tableInfo;
    public String uri;
    public boolean insert;

    public DBInfo(String dbName,String tableName,String tableInfo,String uri,boolean insert){
        this.dbName=dbName;
        this.tableName=tableName;
        //需要对table info进行处理
        this.tableInfo=getStandardTableInfo(tableInfo,tableName);

        this.uri=uri;
        this.insert=insert;
    }

    private String getStandardTableInfo(String tableInfo,String tableName){
        //我们需要对tableinfo的信息进行处理
        if(tableInfo=="")
            return "";
        String reg="\\{.*\\}";
        String string = StringUtil.findString(tableInfo, reg);
        if(string==null)
            return "";

        String replace = string.replace("{", "");
        String s = replace.replace("}", "");
        String[] split = s.split("MS");
        HashSet<String> tableInfos=new HashSet<>();
        for(String table_format:split) {
            if(tableName==""){
                tableInfos.add(table_format);
            }else {
                if(table_format.contains(tableName))
                    tableInfos.add(table_format);
            }
        }
        return tableInfos.toString();


    }

    @Override
    public int hashCode() {
        return Objects.hash(dbName,tableName,tableInfo,uri);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof DBInfo))
            return false;
        DBInfo dbInfo = (DBInfo) obj;
        return dbInfo.tableName.equals(this.dbName)&&dbInfo.tableName.equals(this.tableName)&&dbInfo.tableInfo.equals(this.tableInfo)&&
                dbInfo.uri.equals(this.uri);
    }

    @Override
    public String toString() {
        String msg="DB_NAME: "+dbName+'\t'+"TABLE_NAME: "+tableName+'\t'+"TABLE_INFO: "+tableInfo+"\t"+"URI: "+uri;
        return msg;
    }
}
