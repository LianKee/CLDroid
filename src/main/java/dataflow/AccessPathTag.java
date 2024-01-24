package dataflow;

import soot.tagkit.AttributeValueException;
import soot.tagkit.Tag;


//access path长度为0，表示当前变量就是污染，当前变量可以是局部变量，this，实例字段或者是静态字段
//一个taint,如果它的access path为0，表示该引用所指的对象是污染的
//如果access path不为0，表示这个引用所指的对象是污染的，比如引用是taint,access path是.x.y.z,那么taint.x.y.z是污染的
public class AccessPathTag implements Tag {

//    private byte[] accessPath = new byte[100];//表示当前的域名，规定最大长度为5,如果有本tag并且它特curIndex为0，表示就是本变量被污染了
    //规定任何污染的变量都需要带本属性

    private int fieldLength = 0;

    private String fieldChain = "";
    int accessPathIndex=0;

    @Override
    public String getName() {
        return "AccessPath";
    }

    public AccessPathTag(Tag tag) {
//        accessPath = tag.getValue().clone();
        AccessPathTag accessPathTag = (AccessPathTag) tag;
        fieldChain = accessPathTag.fieldChain;
        fieldLength=accessPathTag.fieldLength;
    }

    public AccessPathTag() {

    }

//    public AccessPathTag(String acc)

    @Override
    public byte[] getValue() throws AttributeValueException {
        return null;
    }

    public boolean appendAccessPath(String fieldName) {
        if (fieldLength == 5) {//如果当前的access path超过规定的最大长度，我们认为这个比如r.a.b.c.d.f,此时我们认为r是没有被污染的
            return false;
        } else {
            fieldChain = fieldName + "." + fieldChain;
//            System.out.println("升");
//            System.out.println(fieldChain);
//            int cur = 0;
//            for (byte c : fieldChain.getBytes()) {
//                accessPath[cur] = c;
//                cur++;
//            }
            fieldLength += 1;
            return true;
        }
    }

    public boolean removeAccessPath() {
        if (fieldLength == 0)
            return true;
//        System.out.println("降");
        byte[] bytes = fieldChain.getBytes();
        for (int i = 0; i < fieldChain.length(); i++) {
            if ((char) bytes[i] == '.') {
                fieldChain = fieldChain.substring(i+1);
//                accessPath = fieldChain.getBytes();
                return true;
            }
        }
        return false;

    }

    public void setValue(byte[] value) {
//        accessPath = value.clone();
    }

    public boolean match(String fieldName) {
        if (fieldChain.isEmpty())
            return true;
        //查看filed是否匹配
//        System.out.println(fieldChain);
        String[] split = fieldChain.split("/.");
//        System.out.println("匹配字段");
//        System.out.println(split[0]);
//        System.out.println(fieldName);
        return split[0].replace(".","").equals(fieldName);
    }

    public int getFieldLength() {
        return fieldLength;
    }

    public String getFieldChain(){
        return fieldChain;
    }
}
