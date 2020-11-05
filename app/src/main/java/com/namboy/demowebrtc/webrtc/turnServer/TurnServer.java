package com.namboy.demowebrtc.webrtc.turnServer;

import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.PUT;

public interface TurnServer {
    @PUT("/_turn/DemoWebRTC")
    Call<TurnServerPojo> getIceCandidates(@Header("Authorization") String authkey);
}
