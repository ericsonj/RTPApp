package net.ericsonj.rtpapp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ShortBuffer;
import java.util.Random;

/**
 * Created by ericson on 2/14/18.
 */

public class RTPManager {

    public static final String TAG = RTPManager.class.getSimpleName();

    /**
     * AUDIO Settings
     */
    private static final int RECORDER_SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder = null;
    private AudioTrack track;
    private ShortBuffer mSamples;
    private DatagramSocket socket;
    private InetAddress addr;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    int recordingBuffSize = 160 * 1; // want to play 2048 (2K) since 2 bytes we use only 1024
    int bufferSize = 160 * 1;
    int BytesPerElement = 2; // 2 bytes in 16bit format
    private RTP rtp;

    /**
     * Wokitoki setting
     */
    private String name;
    private String id;
    private String server;
    private int serverPort;
    private boolean sendAudio;


    public RTPManager(String name, String id, String server, int serverPort) {
        this.name = name;
        this.id = id;
        this.server = server;
        this.serverPort = serverPort;
        startRecording();
        rtp = new RTP();
    }

    private void startRecording() {
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING, recordingBuffSize * BytesPerElement);

            Random r = new Random(System.currentTimeMillis());
            socket = new DatagramSocket(19000);
            socket.setSoTimeout(1);
            addr = InetAddress.getByName(server);
            byte buff2Send[] = new byte[160];
            track = new AudioTrack(AudioManager.STREAM_VOICE_CALL, 8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
            track.play();

            recorder.startRecording();

            if (AcousticEchoCanceler.isAvailable()) {
                Log.d(TAG, "  AcousticEchoCanceler available");
                AcousticEchoCanceler mAEC = AcousticEchoCanceler.create(recorder.getAudioSessionId());
                if (mAEC == null) {
                    Log.e(TAG, "AcousticEchoCanceler.create failed");
                }
                int ret = mAEC.setEnabled(true);
                if (ret != AudioEffect.SUCCESS) {
                    Log.e(TAG, "setEnabled error: " + ret);
                } else if (ret == AudioEffect.SUCCESS) {
                    Log.e(TAG, "AcousticEchoCanceler SUCCESS");
                }
            }

            isRecording = true;
            recordingThread = new Thread(new Runnable() {
                public void run() {
                    writeAudioDataToSocket();
                }
            }, "AudioRecorder Thread");
            recordingThread.start();


            Thread playbackThread = new Thread(new Runnable() {
                public void run() {
                    playAudio();
                }
            }, "Playback Thread");
            playbackThread.start();

        } catch (Exception e) {
            Log.d("AUDIO", null, e);
        }

    }


    private void writeAudioDataToSocket() {
        Log.d(TAG, "write audio data to socket");

        short audioRecorded[] = new short[recordingBuffSize];
        byte buff2Send[][] = new byte[1][160];
        byte[] rtpBuffer = new byte[172];
        byte[] data = new byte[160 + 1];

        try {

            DatagramPacket packet = new DatagramPacket(buff2Send[0], buff2Send[0].length, addr, serverPort);
            socket.send(packet);
            long cont = 0;
            long countSilence = 0;
            boolean globalSilence = true;
            sendControlMessage();

            while (isRecording) {
                cont++;
                countSilence++;

                if (cont > 600) {
                    sendControlMessage();
                    cont = 0;
                }

                int recordedSize = recorder.read(audioRecorded, 0, recordingBuffSize);

                int pos = 0;
                boolean silence = true;

                for (int i = 0; i < 160; i++) {
                    silence = silence && (-10 <= audioRecorded[pos] && audioRecorded[pos] <= 10);
                    buff2Send[0][i] = (byte) G711.linear2alaw(audioRecorded[pos]);
                    audioRecorded[pos] = 0;
                    pos++;
                }
                if (!silence) {
                    if (sendAudio) {
                        rtp.addRTPHeader(rtpBuffer);
                        System.arraycopy(buff2Send[0], 0, rtpBuffer, 12, buff2Send[0].length);
                        DatagramPacket packetSend = new DatagramPacket(rtpBuffer, rtpBuffer.length, addr, serverPort);
                        socket.send(packetSend);
                    }
                }
            }

        } catch (Exception e) {
            Log.d("AUDIO", " EXCEPTION " + e);
            e.printStackTrace();
        } finally {
        }
    }

    private void playAudio() {

        short outAudio[] = new short[bufferSize];
        byte buffRead[] = new byte[172];
        try {
            int pos = 0;
            DatagramPacket packetRead = new DatagramPacket(buffRead, buffRead.length);
            while (isRecording) {
                for (int k = 0; k < 4; k++) {
                    try {
                        socket.receive(packetRead);
                        for (int i = 12; i < buffRead.length; i++) {
                            int n = G711.alaw2linear(buffRead[i]);
                            outAudio[pos++] = (short) n;
                        }
                        if (pos >= outAudio.length) {
                            track.write(outAudio, 0, outAudio.length);
                            pos = 0;
                        }

                    } catch (SocketTimeoutException e) {
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, null, e);
        }
    }


    public void setSendAudio(boolean sendAudio) {
        this.sendAudio = sendAudio;
    }

    private void sendControlMessage() throws IOException {

        String command = id + "@ericsonj.net";

        int dataLen = command.length() + 10;
        int rtcplen = (dataLen / 4) + ((dataLen % 4) > 0 ? 1 : 0) - 1;

        byte[] dataControl = new byte[dataLen];
        dataControl[0] = (byte) 0x81;
        dataControl[1] = (byte) 0xCA; // 202

        int hight = rtcplen & 0x0FF00;
        hight >>= 8;
        dataControl[2] = (byte) hight;
        int low = rtcplen & 0x000FF;
        dataControl[3] = (byte) low;

        int ssrc = 2020;
        int b;
        b = ssrc & 0x00FF000000;
        b >>= 24;
        dataControl[4] = (byte) b;

        b = ssrc & 0x0000FF0000;
        b >>= 16;
        dataControl[5] = (byte) b;

        b = ssrc & 0x000000FF00;
        b >>= 8;
        dataControl[6] = (byte) b;

        b = ssrc & 0x00000000FF;
        dataControl[7] = (byte) b;

        dataControl[8] = (byte) 0x01; // cname = 1
        dataControl[9] = (byte) command.length(); // 2 bytes len

        System.arraycopy(command.getBytes(), 0, dataControl, 10, command.length());

        DatagramPacket packetSend = new DatagramPacket(dataControl, dataControl.length, addr, serverPort);
        Log.d(TAG, "SEND CONTROL " + new String(dataControl));
        socket.send(packetSend);
    }

}
