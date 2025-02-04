package com.namboy.demowebrtc.webrtc.turnServer;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


import java.util.List;

public class TurnServerPojo {

    @SerializedName("s")
    @Expose
    public String s;
    @SerializedName("p")
    @Expose
    public String p;
    @SerializedName("e")
    @Expose
    public String e;
    @SerializedName("v")
    @Expose
    public IceServerList iceServerList;

    public class IceServerList {

        @SerializedName("iceServers")
        @Expose
        public List<IceServer> iceServers = null;

    }

}
