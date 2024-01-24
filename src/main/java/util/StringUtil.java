package util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {


    public static boolean isMatch(String str, String reg) {
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(str);
        return matcher.matches();
    }

    public static boolean isFind(String str,String reg){
        Pattern pattern=Pattern.compile(reg);
        Matcher matcher=pattern.matcher(str);
        return  matcher.find();

    }

    public static String findString(String str,String reg){
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(str);
        if(matcher.find())
            return matcher.group(0);
        return null;
    }

    public static int getParameterOrder(String input) {
        String[] str = input.split(":=")[1].split(":");
        Pattern pattern = Pattern.compile("[^0-9]");
        Matcher matcher = pattern.matcher(str[0]);
        return Integer.parseInt(matcher.replaceAll(""));
    }

    public static String insertDot(String str) {
        List<String> insertMark= Arrays.asList(".","$","[","]","(",")");
            StringBuilder stringBuilder=new StringBuilder();
            for(int i=0;i<str.length();i++){
                if(insertMark.contains(str.substring(i,i+1)))//如果是分割符，我们就
                    stringBuilder.append("\\");
                stringBuilder.append(str.substring(i,i+1));
            }
            str=stringBuilder.toString();
        return str;
    }

    /**
     * @classDef: E.g., class "Lcom/android/server/notification/EventConditionProvider;"
     * */
    public static String parseClassNameFromUnit(String classDef){
        if (classDef.contains("\"L")){
            classDef = classDef.substring(classDef.indexOf("\"L")+2,classDef.indexOf(";"));
            classDef = classDef.replaceAll("/",".");
            return classDef;
        }else {
            return null;
        }

    }

}
