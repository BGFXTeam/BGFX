package com.znfdev.utils;

import java.io.File;
import com.znfdev.zson.Zson;
import com.znfdev.zson.annotations.SerializedName;
import com.znfdev.zson.ZsonBuilder;

public class Settings {

    @SerializedName("script")
    public String defaultScript = "nullptr";
    public boolean autoExec = false;
    private static Settings loaded = null;
    @SerializedName("versionInfo")
    public LatestVersion version = new LatestVersion();

    public void setDefaultScript(String defaultScript) {
        this.defaultScript = defaultScript;
        saveSettings();
    }

    public String getDefaultScript() {
        return defaultScript;
    }

    public void setAutoExec(boolean autoExec) {
        this.autoExec = autoExec;
        saveSettings();
    }

    public boolean isAutoExec() {
        return autoExec;
    }

    public LatestVersion getVersion() {
        return version;
    }

    /** Forget the cached copy so the next getSettings() re-reads settings.json. */
    public static void reload() {
        loaded = null;
    }

    public static Settings getSettings() {
        if (loaded == null) {
            loaded = loadSettings();
        }

        return loaded;
    }

    private static Settings loadSettings() {
        try {
            File file = new File(
                "/storage/emulated/0/BGFX/settings.json"
            );

            String json = FileManager.FileUtils.read(file);

            return new Zson().fromJson(json, Settings.class);

        } catch (Exception e) {
            e.printStackTrace();
            return new Settings();
        }
    }

    private void saveSettings() {
        try {
            File file = new File(
                "/storage/emulated/0/BGFX/settings.json"
            );

            String json = new ZsonBuilder().setPrettyPrinting().create().toJson(this);

            FileManager.FileUtils.write(file, json);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
