package dataflow;

import java.util.Objects;


public class SpInfo {
    //sharedpreferences信息

    public String uri;
    public String spName;
    public String keyName;
    public boolean affectKey;
    public boolean affect_sp_name;


    public SpInfo(String uri,String spName,String keyName,boolean affectKey,boolean affect_sp_name){
        this.uri=uri;
        this.spName=spName;
        this.keyName=keyName;
        this.affectKey=affectKey;
        this.affect_sp_name=affect_sp_name;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof SpInfo))
            return false;
        SpInfo spInfo = (SpInfo) obj;

        return this.uri.equals(spInfo.uri)&&this.keyName.equals(spInfo.keyName)&&this.spName.equals(spInfo.spName)&&
                this.affectKey==spInfo.affectKey&&this.affect_sp_name==spInfo.affect_sp_name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri,spName,keyName,affectKey,affect_sp_name);
    }

    @Override
    public String toString() {
        return "SP_NAME: "+this.spName+'\t'+"KEY_NAME: "+this.keyName+"\t"+"URI: "+this.uri+"\t"+"AFFECT_KEY: "+this.affectKey+
                "\t"+"AFFCET_SP_NAME: "+this.affect_sp_name;
    }
}


