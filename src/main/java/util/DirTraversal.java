package util;

import java.io.File;
import java.util.logging.Logger;


public interface DirTraversal {
    void work(File f) throws Exception;

    Logger logger = Logger.getLogger(DirTraversal.class.getName());

    default void traverse(File dir){
        File[] files = dir.listFiles();
        assert files != null;
        for(File f : files){
            if(f.isDirectory())traverse(f);
            if(f.isFile()){
                try {

                    work(f);
                } catch (Exception e) {
                    logger.warning("[DIRECTORY TRAVERSAL ERROR] " + f + " (This may cause unexpected skipping the file.)");
                    e.printStackTrace();
                }
            }
        }
    }
}
