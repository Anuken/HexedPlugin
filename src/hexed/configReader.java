package hexed;

import arc.util.Log;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;

public class configReader {
    final static String userHomePath ="config/mods";

    public static JSONObject get(String fileName) {
        try {
            //String userHomePath = System.getProperty("user.home");
            File file = new File(userHomePath+"/"+fileName);
            File path = new File(userHomePath+"/");
            if (!path.isDirectory()) {
                Log.err("404 - could not find directory "+userHomePath+"/");
                return null;
            }
            if (!file.exists()) {
                Log.err("404 - "+userHomePath+"/"+fileName+" not found");
                return null;
            }
            FileReader fr = new FileReader(file);
            StringBuilder builder = new StringBuilder();
            int i;
            while((i=fr.read())!=-1) {
                builder.append((char)i);
            }
            //return null;
            return new JSONObject(new JSONTokener(builder.toString()));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
