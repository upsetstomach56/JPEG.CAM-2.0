package com.github.ma1co.pmcademo.app;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;

public class MdnsResponder {
    private static final String GROUP_ADDRESS = "224.0.0.251";
    private static final int MDNS_PORT = 5353;
    private static final int TYPE_A = 1;
    private static final int TYPE_PTR = 12;
    private static final int TYPE_TXT = 16;
    private static final int TYPE_SRV = 33;
    private static final int CLASS_IN = 1;
    private static final String SERVICE_TYPE = "_http._tcp.local";
    private static final String SERVICE_INSTANCE = "JPEG.CAM._http._tcp.local";
    private static final String FRIENDLY_ALIAS = "jpegcam.local";

    private final Context context;
    private volatile boolean running = false;
    private MulticastSocket socket;
    private Thread worker;
    private WifiManager.MulticastLock multicastLock;
    private String ipAddress;

    public MdnsResponder(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void start(String ipAddress) {
        if (ipAddress == null || ipAddress.length() == 0) return;
        if (running && ipAddress.equals(this.ipAddress)) return;
        stop();
        this.ipAddress = ipAddress;
        running = true;
        acquireMulticastLock();

        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runResponder();
            }
        }, "JPEG.CAM mDNS");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        if (socket != null) {
            try { socket.close(); } catch (Exception e) {}
            socket = null;
        }
        if (worker != null) {
            try { worker.interrupt(); } catch (Exception e) {}
            worker = null;
        }
        releaseMulticastLock();
    }

    private void runResponder() {
        InetAddress group = null;
        MulticastSocket localSocket = null;
        try {
            group = InetAddress.getByName(GROUP_ADDRESS);
            localSocket = new MulticastSocket(null);
            localSocket.setReuseAddress(true);
            localSocket.bind(new InetSocketAddress(MDNS_PORT));
            localSocket.setTimeToLive(255);
            localSocket.joinGroup(group);
            socket = localSocket;

            sendResponse(localSocket, group, MDNS_PORT);

            byte[] buffer = new byte[1500];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    localSocket.receive(packet);
                    if (shouldAnswer(packet)) {
                        sendResponse(localSocket, group, MDNS_PORT);
                        if (packet.getPort() != MDNS_PORT) {
                            sendResponse(localSocket, packet.getAddress(), packet.getPort());
                        }
                    }
                } catch (SocketException e) {
                    if (running) continue;
                    break;
                } catch (Exception e) {
                    if (!running) break;
                }
            }
        } catch (Exception e) {
            // The dashboard still works by IP if mDNS is unavailable on a camera.
        } finally {
            if (localSocket != null) {
                if (group != null) {
                    try { localSocket.leaveGroup(group); } catch (Exception e) {}
                }
                try { localSocket.close(); } catch (Exception e) {}
            }
        }
    }

    private boolean shouldAnswer(DatagramPacket packet) {
        try {
            String text = new String(packet.getData(), 0, packet.getLength(), "ISO-8859-1").toLowerCase();
            return text.indexOf("jpegcam") >= 0
                    || text.indexOf("jpeg.cam") >= 0
                    || text.indexOf("jpeg") >= 0
                    || text.indexOf("_http") >= 0
                    || text.indexOf("_services") >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void sendResponse(MulticastSocket socket, InetAddress address, int port) {
        try {
            byte[] payload = buildResponse();
            DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
            socket.send(packet);
        } catch (Exception e) {}
    }

    private byte[] buildResponse() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        writeShort(out, 0);
        writeShort(out, 0x8400);
        writeShort(out, 0);
        writeShort(out, 6);
        writeShort(out, 0);
        writeShort(out, 0);

        writePtr(out, SERVICE_TYPE, SERVICE_INSTANCE);
        writeSrv(out, SERVICE_INSTANCE, HttpServer.LOCAL_HOSTNAME, HttpServer.PORT);
        writeTxt(out, SERVICE_INSTANCE);
        writeA(out, HttpServer.LOCAL_HOSTNAME, ipAddress);
        writeA(out, FRIENDLY_ALIAS, ipAddress);
        writeA(out, "jpeg.cam", ipAddress);
        return out.toByteArray();
    }

    private void writePtr(ByteArrayOutputStream out, String name, String target) throws IOException {
        byte[] data = encodeName(target);
        writeRecordHeader(out, name, TYPE_PTR, 120, false);
        writeShort(out, data.length);
        out.write(data);
    }

    private void writeSrv(ByteArrayOutputStream out, String name, String target, int port) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream(64);
        writeShort(data, 0);
        writeShort(data, 0);
        writeShort(data, port);
        data.write(encodeName(target));
        byte[] bytes = data.toByteArray();
        writeRecordHeader(out, name, TYPE_SRV, 120, true);
        writeShort(out, bytes.length);
        out.write(bytes);
    }

    private void writeTxt(ByteArrayOutputStream out, String name) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream(64);
        writeTxtString(data, "path=/");
        writeTxtString(data, "name=JPEG.CAM");
        byte[] bytes = data.toByteArray();
        writeRecordHeader(out, name, TYPE_TXT, 120, true);
        writeShort(out, bytes.length);
        out.write(bytes);
    }

    private void writeA(ByteArrayOutputStream out, String name, String ipAddress) throws IOException {
        byte[] address = InetAddress.getByName(ipAddress).getAddress();
        if (address.length != 4) return;
        writeRecordHeader(out, name, TYPE_A, 120, true);
        writeShort(out, 4);
        out.write(address);
    }

    private void writeRecordHeader(ByteArrayOutputStream out, String name, int type, int ttl, boolean cacheFlush) throws IOException {
        writeName(out, name);
        writeShort(out, type);
        writeShort(out, cacheFlush ? (CLASS_IN | 0x8000) : CLASS_IN);
        writeInt(out, ttl);
    }

    private byte[] encodeName(String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        writeName(out, name);
        return out.toByteArray();
    }

    private void writeName(ByteArrayOutputStream out, String name) throws IOException {
        String[] labels = name.split("\\.");
        for (int i = 0; i < labels.length; i++) {
            byte[] bytes = labels[i].getBytes("US-ASCII");
            out.write(bytes.length);
            out.write(bytes);
        }
        out.write(0);
    }

    private void writeTxtString(ByteArrayOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes("US-ASCII");
        out.write(bytes.length);
        out.write(bytes);
    }

    private void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xff);
        out.write(value & 0xff);
    }

    private void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 8) & 0xff);
        out.write(value & 0xff);
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                multicastLock = wifiManager.createMulticastLock("jpegcam_mdns");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (Exception e) {}
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Exception e) {
        } finally {
            multicastLock = null;
        }
    }
}
