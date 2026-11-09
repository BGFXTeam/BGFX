package com.znfdev.utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import com.znfdev.models.PluginItem;
import org.json.JSONObject;

public class FileManager {
    
    public static FileManager INSTANCE = new FileManager();
    
    
    public List<File> getFiles(String path) {
        List<File> files = new ArrayList<>();
        File dir = new File(path);
        File[] children = dir.listFiles();
        if (children == null) return files; // missing dir or no storage access
        for (File f : children) {
            files.add(f);
        }
        
        return files;
    }
    
    public List<File> getScripts() {
        List<File> scripts = new ArrayList<>();
        List<File> files = getFiles("/storage/emulated/0/BGFX/Scripts/");
        for(File f : files) {
            if(f.isFile()) {
                if(f.getName().endsWith(".lua") || f.getName().endsWith(".txt")) {
                    scripts.add(f);
                } else continue;
            } else continue;
        }
        
        return scripts;
    }
    
    public List<PluginItem> getPlugins() {
        List<PluginItem> plugins = new ArrayList<>();
        List<File> files = getFiles("/storage/emulated/0/BGFX/plugins/");
        for(File f : files) {
            if(f.isFile()) {
                if(f.getName().endsWith(".json")) {
                    try {
                    String c = FileUtils.read(f);
                    JSONObject j = new JSONObject(c);
                    PluginItem p = new PluginItem();
                    p.setName(j.getString("name"));
                    p.setMain(j.getString("main"));
                    plugins.add(p);
                    } catch (Exception e) {
                        
                    }
                } else continue;
            } else continue;
        }

        return plugins;
    }
    
    public static class FileUtils {
        
        public static String read(File file) throws Exception {
            BufferedReader br = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            return sb.toString();
        }

        public static void write(File file, String data) {
            try {
                FileWriter fw = new FileWriter(file);
                fw.write(data);
                fw.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        
    }
    
}
