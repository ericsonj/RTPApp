package net.ericsonj.rtpapp;

import java.util.Random;

public class RTP {

    private int sequence;
    private int timestamp;
    private int ssrc;

    public RTP() {
        this.sequence = 0;
        this.timestamp = 0;
        Random r = new Random(System.currentTimeMillis());
        this.ssrc = r.nextInt();
    }

    public void addRTPHeader(byte[] buff2Send) {

        sequence++;
        timestamp += 20;

        buff2Send[0] = (byte) 0x80;
        buff2Send[1] = (byte) 0x08; // ALAW

        int hight = sequence & 0x0FF00;
        hight >>= 8;
        buff2Send[2] = (byte) hight;
        int low = sequence & 0x000FF;
        buff2Send[3] = (byte) low;

        int a;
        a = timestamp & 0x00FF000000;
        a >>= 24;
        buff2Send[4] = (byte) a;

        a = timestamp & 0x0000FF0000;
        a >>= 16;
        buff2Send[5] = (byte) a;

        a = timestamp & 0x000000FF00;
        a >>= 8;
        buff2Send[6] = (byte) a;

        a = timestamp & 0x00000000FF;
        //d >>= 0;
        buff2Send[7] = (byte) a;

        int b;
        b = ssrc & 0x00FF000000;
        b >>= 24;
        buff2Send[8] = (byte) b;

        b = ssrc & 0x0000FF0000;
        b >>= 16;
        buff2Send[9] = (byte) b;

        b = ssrc & 0x000000FF00;
        b >>= 8;
        buff2Send[10] = (byte) b;

        b = ssrc & 0x00000000FF;
        buff2Send[11] = (byte) b;

    }

}
