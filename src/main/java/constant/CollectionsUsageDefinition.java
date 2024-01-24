package constant;

import java.util.Arrays;
import java.util.List;


public class CollectionsUsageDefinition {

    //和集合相关的api

    //方法特别多,这里只关注他们的subSignature

    public static final String ADD_0="boolean add(java.lang.Object)";
    public static final String ADD_1="void add(int,java.lang.Object)";
    public static final String ADD_ALL="boolean addAll(java.util.Collection)";
    public static final String REMOVE_0="java.lang.Object remove(int)";
    public static final String REMOVE_1="boolean remove(java.lang.Object)";
    public static final String REMOVE_2="void remove()";
    public static final String REMOVE_ALL="boolean removeAll(java.util.Collection)";
    public static final String CLEAR="void clear()";
    public static final String PUT="java.lang.Object put(java.lang.Object,java.lang.Object)";
    public static final String PUT_ALL="java.lang.Object remove(java.lang.Object)";

    public static final String[] collectionApi={
            ADD_0,ADD_1,ADD_ALL,REMOVE_0,REMOVE_1,REMOVE_2,REMOVE_ALL,CLEAR,PUT,PUT_ALL
    };

    public static final List<String> collectionApiList= Arrays.asList(collectionApi);

    public static List<String> getCollectionApiList(){
        return collectionApiList;
    }

}
