package com.znfdev.bgfx.api;

import com.znfdev.utils.LatestVersion;
import com.znfdev.zson.annotations.SerializedName;

public class Data {

    @SerializedName("isBGFXActive")
    public boolean isBGFXActive;

    @SerializedName("bgfxLink")
    public String bgfxLink;

    @SerializedName("bgfxMgrLink")
    public String bgfxMgrLink;

    @SerializedName("bgfxApi")
    public String bgfxApi;

    @SerializedName("isApiActive")
    public boolean isApiActive;

    @SerializedName("keyUrl")
    public String keyUrl;

    @SerializedName("versionInfo")
    public LatestVersion versionInfo;

    @SerializedName("notice")
    public Notice notice;

    public static class Notice {

        @SerializedName("isActive")
        public boolean isActive;

        @SerializedName("data")
        public String data;

        @SerializedName("isMarkDown")
        public boolean isMarkDown;
    }
}
