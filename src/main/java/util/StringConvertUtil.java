package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;


public class StringConvertUtil {

    //数据恢复工具，将从文件中恢复的数据处理成标准的样式

    //建立图
    //邻接表

    public static final Logger logger= LoggerFactory.getLogger(StringConvertUtil.class);
    static class Node{
        //邻接表
        HashSet<Integer> next;
        String var;

        public Node(){
            next=new HashSet<>();
            var="";
        }
    }


    private  Node[] node=null;

    private int top_node=0;

    private int alloc_node(){
        return top_node++;
    }
    //括号匹配
    private int[] bracket_match=new int[10000];
    //自己设置的栈
    private int[] stack=new int[10000];
    //栈顶
    private int top=0;

    //入栈操作
    private void push(int index){
        stack[top++]=index;
    }

    private int pop(){
        if(top==0)
            return -1;
        return stack[--top];
    }

    private boolean cal_bracket_match(String s){
        //计算括号匹配，哪两个括号是匹配的
        for(int i=0;i<s.length();i++){
            if(s.charAt(i)=='['||s.charAt(i)=='('){
                push(i);
            }
            if(s.charAt(i)==']'||s.charAt(i)==')'){
                int j = pop();
                if(j==-1)
                    return false;
                bracket_match[j]=i;
            }
        }
        return true;
    }

    //我们现在从这个字符串中创建这个图

    /*
    input:输入的字符串
    left:要处理的字符串的左起点
    right:要处理的字符串的右终点
    start:处理的表达式在图中状态的起点编号
    end:终点编号

     */
    private void dfs(String input,int left,int right,int start,int end){
        int i= left;

        int flag=0;

        while (i<=right){
            char c = input.charAt(i);
            if(c=='['){
                //如果我们遇到左中括号
                int next_left=i+1;
                int next_right=bracket_match[i]-1;

                int next_start=alloc_node();
                int next_end=alloc_node();

                node[start].next.add(next_start);

                start=next_end;
                dfs(input,next_left,next_right,next_start,next_end);
                i=next_right+2;

                flag=1;
            }else if(c=='('){
                int next_left=i+1;
                int next_right=bracket_match[i]-1;

                int next_start=alloc_node();
                int next_end=alloc_node();
                node[start].next.add(next_start);
                node[next_end].next.add(end);
                dfs(input,next_left,next_right,next_start,next_end);
                i=next_right+2;
            }else {
                int next_left=i;
                int next_right=i;
                while (input.charAt(next_right)!=']'&&input.charAt(next_right)!=')'&&
                input.charAt(next_right)!='['&&input.charAt(next_right)!='(')
                    next_right++;
                //创建新的节点
                int n=alloc_node();
                node[n].var=input.substring(next_left,next_right);
                node[start].next.add(n);
                node[n].next.add(end);
                start=n;
                i=next_right;
            }
        }
        if(flag==1){
            node[start].next.add(end);
        }
    }

    void dfs_print(int nid, String s,HashSet<String> res)
    {
        for(Integer i:node[nid].next){
            dfs_print(i,s+node[i].var,res);
        }
        if (node[nid].next.size() == 0)
        {
            if(!s.isEmpty()&&!s.equals("null")) {
                String ans = s.replaceAll("\"", "");
                res.add(ans.toLowerCase());
            }
        }
    }

    public StringConvertUtil(){
        node=new Node[10000];
        for(int i=0;i<10000;i++)
            node[i]=new Node();
    }


    public HashSet<String> getFormatInfo(String input){
        //得到格式化的信息
        HashSet<String> res=new HashSet<>();
        if(input.length()==0||input.length()>10000)
            return res;
        int start=alloc_node();
        int end=alloc_node();
        boolean flag = cal_bracket_match(input);
        if(!flag) {
            logger.info("括号匹配失败!");
            return new HashSet<>();
        }
        dfs(input,0,input.length()-1,start,end);
        dfs_print(0,"",res);
        res.removeIf(next -> next.contains("failed"));
        return res;
    }

}
