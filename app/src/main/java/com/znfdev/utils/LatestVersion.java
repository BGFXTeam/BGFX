package com.znfdev.utils;

import org.json.JSONObject;
import org.json.JSONException;
import android.content.Context;
import android.graphics.Color;
import com.znfdev.zson.Zson;

public class LatestVersion {
    
    private static int NEW_VERSION_CODE = -106;
    private static String NEW_VERSION_NAME = "0.6";
    private static String NEW_VERSION_LABEL = "alpha";
    
    public int versionCode = NEW_VERSION_CODE;
    public String versionName = NEW_VERSION_NAME;
    public String versionLabel = NEW_VERSION_LABEL;

    public static int getNewVersionCode() {
        return NEW_VERSION_CODE;
    }


    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getVersionLabel() {
        return versionLabel;
    }
    
    public static boolean isOld(LatestVersion lvold, LatestVersion lvnew) {
         /*int old = lvold.getVersionCode();
         int nEW = lvnew.getVersionCode();
         String sold = ""+old;
         String snew = ""+nEW;
         
         sold.replaceFirst("-","");
         snew.replaceFirst("-","");
         
         return Integer.valueOf(sold) < Integer.valueOf(snew);*/
         /*return Integer.valueOf(String.valueOf(lvold.getVersionCode()).replaceFirst("-","")) < 
        Integer.valueOf(String.valueOf(lvold.getVersionCode()).replaceFirst("-",""));*/
        if (lvnew.getVersionLabel().equalsIgnoreCase("alpha")) {
            return Math.abs(lvold.getVersionCode()) < Math.abs(lvnew.getVersionCode());
        }
        if (lvnew.getVersionLabel().equalsIgnoreCase("beta") || lvnew.getVersionLabel().equalsIgnoreCase("release")) {
            return lvold.getVersionCode() < lvnew.getVersionCode();
        }
        
        return true;
    }
    
    public boolean isOld() {
        return versionCode < NEW_VERSION_CODE;
    }

    public boolean isNewer() {
        return versionCode > NEW_VERSION_CODE;
    }

    public boolean isLatest() {
        return versionCode == NEW_VERSION_CODE
            && versionName.equals(NEW_VERSION_NAME)
            && versionLabel.equals(NEW_VERSION_LABEL);
    }
    
    
    
    
    
    
    
    
    
    
    

    public static LatestVersion fromJson(JSONObject json) {
        /*LatestVersion v = new LatestVersion();

        v.versionCode = json.optInt("versionCode", NEW_VERSION_CODE);
        v.versionName = json.optString("versionName", NEW_VERSION_NAME);
        v.versionLabel = json.optString("versionLabel", NEW_VERSION_LABEL);

        return v;*/
        return new Zson().fromJson(json.toString(), LatestVersion.class);
    }

    public JSONObject toJson() throws JSONException {
        /*JSONObject json = new JSONObject();

        json.put("versionCode", versionCode);
        json.put("versionName", versionName);
        json.put("versionLabel", versionLabel);

        return json;*/
        
        return new JSONObject(new Zson().toJson(this, LatestVersion.class));
    }
}
