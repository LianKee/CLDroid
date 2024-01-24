package dataflow;

import constant.FileUsageDefinition;

import java.util.Objects;


public class FileInfo {

    public String type;
    public String name;
    public String mode;
    public String entry;

    public FileInfo(String type,String name,String mode,String entry){
        this.type=type;
        this.name=name;
        this.mode=mode;
        this.entry=entry;

    }

    @Override
    public boolean equals(Object obj) {
        if(obj==null)
            return false;
        FileInfo fileInfo = (FileInfo) obj;
        if(!fileInfo.name.equals(name))
            return false;
        if(!fileInfo.type.equals(type))
            return false;
        if(!fileInfo.mode.equals(mode))
            return false;
        if(!fileInfo.entry.equals(entry))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type,name,mode,entry);
    }
}
