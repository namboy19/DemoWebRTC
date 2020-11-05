package com.namboy.demowebrtc;


import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.model.Document;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.namboy.demowebrtc.webrtc.CustomCameraEventsHandler;
import com.namboy.demowebrtc.webrtc.CustomPeerConnectionObserver;
import com.namboy.demowebrtc.webrtc.CustomSdpObserver;
import com.namboy.demowebrtc.webrtc.turnServer.IceServer;
import com.namboy.demowebrtc.webrtc.turnServer.TurnServerPojo;
import com.namboy.demowebrtc.webrtc.turnServer.Utils;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


/**
 * A simple {@link Fragment} subclass.
 */
public class VideoCallFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "VideoCallFragment";

    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints sdpConstraints;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;

    SurfaceViewRenderer localVideoView;
    SurfaceViewRenderer remoteVideoView;

    VideoRenderer localRenderer;
    VideoRenderer remoteRenderer;

    PeerConnection localPeer;
    Button btnstart, btncall, btnhangup,btnToggleVideo,btnToggleAudio;

    FirebaseFirestore mDB;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    List<IceServer> iceServers;
    EglBase rootEglBase;
    Gson gson = new Gson();
    //--------
    String mCallName = "";
    Boolean mIsMakeCall = false;


    public VideoCallFragment() {
        // Required empty public constructor
    }

    public static VideoCallFragment newInstance(Boolean isMakeCall, String name) {
        VideoCallFragment fragment = new VideoCallFragment();
        fragment.mCallName = name;
        fragment.mIsMakeCall = isMakeCall;
        return fragment;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_video_call, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDB = FirebaseFirestore.getInstance();

        initViews(view);
        initVideos();

        getIceServers();

        if (mIsMakeCall){
            //reset before listen new one

            mDB.collection(mCallName).document("sdpAnswer").delete();
            mDB.collection(mCallName).document("sdp").delete();
            mDB.collection(mCallName).document("hangup").delete();

            mDB.collection(mCallName).document("IceCandidate").collection("IceCandidates").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                    for (DocumentSnapshot document: task.getResult().getDocuments()){

                        mDB.collection(mCallName).document("IceCandidate").collection("IceCandidates").document(document.getId()).delete();
                    }
                    mDB.collection(mCallName).document("IceCandidate").delete();
                }
            });



        }
    }

    private void initViews(View view) {
        btnstart = (Button) view.findViewById(R.id.start_call);
        btncall = (Button) view.findViewById(R.id.init_call);
        btnhangup = (Button) view.findViewById(R.id.end_call);
        localVideoView = (SurfaceViewRenderer) view.findViewById(R.id.local_gl_surface_view);
        remoteVideoView = (SurfaceViewRenderer) view.findViewById(R.id.remote_gl_surface_view);
        btnToggleVideo = (Button) view.findViewById(R.id.btn_toggle_video);
        btnToggleAudio = (Button) view.findViewById(R.id.btn_toggle_audio);

        btnstart.setOnClickListener(this);
        btncall.setOnClickListener(this);
        btnhangup.setOnClickListener(this);
        btnToggleVideo.setOnClickListener(this);
        btnToggleAudio.setOnClickListener(this);
    }

    private void initVideos() {
        rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
        remoteVideoView.setZOrderMediaOverlay(true);
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    boolean isOnAudio=true;
    boolean isOnVideo=true;

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_call: {
                start();
                break;
            }
            case R.id.init_call: {
                call();
                break;
            }
            case R.id.end_call: {

                Map<String, String> sdpanswer = new HashMap<>();
                sdpanswer.put("hangup", "true");
                mDB.collection(mCallName).document("hangup").set(sdpanswer);
                break;
            }
            case R.id.btn_toggle_audio:{
                if (isOnAudio){
                    localAudioTrack.setEnabled(false);
                    isOnAudio=false;
                }
                else {
                    localAudioTrack.setEnabled(true);
                    isOnAudio=true;
                }
                break;

            }
            case R.id.btn_toggle_video:{
                try{
                    if (isOnVideo){
                        videoCapturerAndroid.stopCapture();
                        isOnVideo=false;
                    }
                    else {
                        videoCapturerAndroid.startCapture(1024, 720, 30);
                        isOnVideo=true;
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    VideoCapturer videoCapturerAndroid;

    public void start() {
        btnstart.setEnabled(false);
        btncall.setEnabled(true);
        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(getActivity().getApplicationContext())
                        .setEnableVideoHwAcceleration(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */true,  /* enableH264HighProfile */true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();


        //Now create a VideoCapturer instance.

        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
            localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);
        }

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }

        //create a videoRenderer based on SurfaceViewRenderer instance
        localRenderer = new VideoRenderer(localVideoView);
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addRenderer(localRenderer);


        //we already have video and audio tracks. Now create peerconnections
        //creating localPeer
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                onIceCandidateReceived(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }
        });

        //creating local mediastream
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("kkk");
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);
    }




    private void call() {
        btnstart.setEnabled(false);
        btncall.setEnabled(false);
        btnhangup.setEnabled(true);

        //create sdpConstraints
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        //creating Offer
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                //we have localOffer. Set it as local desc for localpeer and remote desc for remote peer.
                //try to create answer from the remote peer.
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);

                // Access a Cloud Firestore instance from your Activity
                // send sdp to user B

                Map<String, String> sdp = new HashMap<>();
                sdp.put("sdp", new Gson().toJson(sessionDescription));
                mDB.collection(mCallName).document("sdp").set(sdp);
            }
        }, sdpConstraints);



    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        getActivity().runOnUiThread(() -> {
            try {
                remoteRenderer = new VideoRenderer(remoteVideoView);
                videoTrack.setEnabled(true);
                videoTrack.addRenderer(remoteRenderer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        Map<String, String> ice = new HashMap<>();
        ice.put("IceCandidate", new Gson().toJson(iceCandidate));
        mDB.collection(mCallName).document("IceCandidate").collection("IceCandidates").add(ice);
    }


    private void listenerOffer() {

        Gson gson = new Gson();

        mDB.collection(mCallName).document("sdp").addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if (!documentSnapshot.exists() || documentSnapshot.get("sdp") == null || documentSnapshot.get("sdp").toString().equals(""))
                    return;

                SessionDescription sessionDescription = gson.fromJson(documentSnapshot.get("sdp").toString(), SessionDescription.class);

                localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), sessionDescription);

                localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        super.onCreateSuccess(sessionDescription);
                        localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);


                        //send answer
                        Map<String, String> sdp = new HashMap<>();
                        sdp.put("sdpAnswer", new Gson().toJson(sessionDescription));
                        mDB.collection(mCallName).document("sdpAnswer").set(sdp);
                    }
                }, new MediaConstraints());

            }
        });
    }

    private void listenerTheAnswer() {

        //reset before listen new one
        Map<String, String> sdpanswer = new HashMap<>();
        sdpanswer.put("sdpAnswer", "");
        mDB.collection(mCallName).document("sdpAnswer").set(sdpanswer);

        mDB.collection(mCallName).document("sdpAnswer").addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {

                if (!documentSnapshot.exists() || documentSnapshot.get("sdpAnswer") == null || documentSnapshot.get("sdpAnswer").toString().equals(""))
                    return;

                SessionDescription sessionDescription = gson.fromJson(documentSnapshot.get("sdpAnswer").toString(), SessionDescription.class);

                localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), sessionDescription);
            }
        });
    }

    private void listenerIceCandidate() {

        mDB.collection(mCallName).document("IceCandidate").collection("IceCandidates").addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {

                if (queryDocumentSnapshots.getDocuments()==null)
                    return;


                for (DocumentChange documentChange:queryDocumentSnapshots.getDocumentChanges()) {
                    if (documentChange.getType()==DocumentChange.Type.ADDED){
                        IceCandidate iceCandidate = gson.fromJson(documentChange.getDocument().get("IceCandidate").toString(), IceCandidate.class);
                        localPeer.addIceCandidate(iceCandidate);
                    }
                }
            }
        });
    }

    private void listenHangup(){

        mDB.collection(mCallName).document("hangup").addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {

                if (!documentSnapshot.exists() || documentSnapshot.get("hangup") == null || documentSnapshot.get("hangup").toString().equals(""))
                    return;

                if (documentSnapshot.get("hangup").equals("true")){
                    hangup();
                    getActivity().finish();
                }
            }
        });
    }


    private void hangup() {
        try{
            localPeer.close();
            localPeer = null;
            remoteRenderer.dispose();
            remoteVideoView.release();
            btncall.setEnabled(true);
            btnhangup.setEnabled(false);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        hangup();
        super.onDestroy();

    }

    private void getIceServers() {
        //get Ice servers using xirsys
        byte[] data = new byte[0];
        try {
            data = ("namboy19:446e1232-4afa-11ea-9798-0242ac110004").getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String authToken = "Basic " + Base64.encodeToString(data, Base64.NO_WRAP);
        Utils.getInstance().getRetrofitInstance().getIceCandidates(authToken).enqueue(new Callback<TurnServerPojo>() {
            @Override
            public void onResponse(@NonNull Call<TurnServerPojo> call, @NonNull Response<TurnServerPojo> response) {
                TurnServerPojo body = response.body();
                if (body != null) {
                    iceServers = body.iceServerList.iceServers;
                }
                for (IceServer iceServer : iceServers) {
                    if (iceServer.credential == null) {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url).createIceServer();
                        peerIceServers.add(peerIceServer);
                    } else {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url)
                                .setUsername(iceServer.username)
                                .setPassword(iceServer.credential)
                                .createIceServer();
                        peerIceServers.add(peerIceServer);
                    }
                }

                start();

                if (!mIsMakeCall){
                    btncall.setVisibility(View.GONE);
                    btnstart.setVisibility(View.GONE);
                    listenerOffer();
                }
                else{
                    listenerTheAnswer();
                }
                listenerIceCandidate();
                listenHangup();

                Log.d("onApiResponse", "IceServers\n" + iceServers.toString());
            }

            @Override
            public void onFailure(@NonNull Call<TurnServerPojo> call, @NonNull Throwable t) {
                t.printStackTrace();
            }
        });


       /* PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder("numb.viagenie.ca")
                .setUsername("namboyhc@gmail.com")
                .setPassword("nampro")
                .createIceServer();
        peerIceServers.add(peerIceServer);


        start();
        if (!mIsMakeCall) {
            btncall.setVisibility(View.GONE);
            btnstart.setVisibility(View.GONE);
            listenerOffer();
        } else {
            listenerTheAnswer();
        }
        listenerIceCandidate();*/
    }
}
