package edu.ufp.cm.arduinoserialmonitor.usingjserialcomm;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import javax.swing.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ArduinoSerialMonitorJSerialComm implements WindowListener, ActionListener, SerialPortDataListener {

    // ====== CONFIG ======
    private static final int BAUD = 9600;

    // If Arduino sends only "cm=NN;" and Java decides occupied:
    private static final int THRESHOLD_CM = 30;

    // ThingSpeak (channel update)
    private static final String THINGSPEAK_WRITE_KEY = "3XDQJ2U7HUUIBGFN";
    private static final int FIELD_OCCUPIED = 1; // field1
    private static final int FIELD_CM       = 2; // field2
    private static final int FIELD_TS       = 3; // field3

    // ThingSpeak free: minimum ~15 seconds per update per channel
    private static final long MIN_UPDATE_MS = 15_000;

    // TalkBack (commands)
    private static final long TALKBACK_ID = 56147L;
    private static final String TALKBACK_API_KEY = "9IZ8NWEQ87VDL5PT";
    private static final long POLL_TALKBACK_MS = 3000; // 3s

    // Optional IDs just for logs
    private static final String LOT  = "lotA";
    private static final String SPOT = "spot1";
    // ====================

    private final StringBuilder rx = new StringBuilder();
    private SerialPort port;

    // --- ThingSpeak rate limit queue (coalescing) ---
    private volatile String pendingBody = null;                 // keep last event while waiting
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean sendScheduled = new AtomicBoolean(false);
    private volatile long lastUpdateMs = 0;

    // --- TalkBack polling ---
    private final ScheduledExecutorService talkbackScheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile String lastSentCmd = null; // opcional: evita enviar repetido

    public static void main(String[] args) { new ArduinoSerialMonitorJSerialComm().run(); }

    private void run() {
        // 1) Pick the FT232R (XBee Explorer) port
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) { System.err.println("No serial ports found."); return; }

        String[] items = new String[ports.length];
        for (int i = 0; i < ports.length; i++) {
            items[i] = ports[i].getDescriptivePortName() + " | " + ports[i].getSystemPortName();
            System.out.println(items[i]);
        }

        String choice = (String) JOptionPane.showInputDialog(
                null, "Select the XBee Explorer (FT232R) port:",
                "Serial Port", JOptionPane.PLAIN_MESSAGE, null, items, items[0]);

        if (choice == null) return;
        String com = choice.substring(choice.indexOf('|') + 1).trim();

        // 2) Open serial
        port = SerialPort.getCommPort(com);
        port.setComPortParameters(BAUD, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

        if (!port.openPort()) { System.err.println("Failed to open " + com); return; }
        port.setDTR(); port.setRTS();
        System.out.println("Opened: " + com);

        // ✅ Start TalkBack polling now that serial is open
        startTalkBackPolling();

        // 3) Read loop (Arduino -> Java)
        new Thread(() -> {
            byte[] buf = new byte[1024];
            while (port.isOpen()) {
                int n = port.readBytes(buf, buf.length);
                if (n > 0) {
                    String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                    System.out.print("[RAW] " + chunk);
                    feed(chunk);
                }
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
        }, "serial-reader").start();

        JOptionPane.showMessageDialog(null, "Receiver running.\nClose this window to exit.");

        // cleanup
        try { if (port.isOpen()) port.closePort(); } catch (Exception ignored) {}
        scheduler.shutdownNow();
        talkbackScheduler.shutdownNow();
    }

    private void feed(String chunk) {
        rx.append(chunk);
        int end;
        while ((end = rx.indexOf(";")) >= 0) {
            String frame = rx.substring(0, end + 1).trim();
            rx.delete(0, end + 1);
            if (!frame.isEmpty()) handleFrame(frame);
        }
    }

    private void handleFrame(String f) {
        // Accepts either:
        //   "cm=386;"
        //   "spot1=0,cm=386;"
        // Also may receive ACK:LED_ON; from Arduino
        try {
            String msg = f.trim();
            if (msg.endsWith(";")) msg = msg.substring(0, msg.length() - 1);

            // ignore ACK frames (only log)
            if (msg.startsWith("ACK:")) {
                System.out.println("[ARDUINO] " + msg);
                return;
            }

            Integer cm = null;
            Integer spotState = null;

            for (String part : msg.split(",")) {
                String[] kv = part.split("=");
                if (kv.length != 2) continue;
                String k = kv[0].trim();
                String v = kv[1].trim();
                if (k.equalsIgnoreCase("cm")) cm = Integer.parseInt(v);
                if (k.equalsIgnoreCase("spot1")) spotState = Integer.parseInt(v);
            }

            boolean occupied;
            if (spotState != null) {
                occupied = (spotState == 1);
            } else {
                // fallback: if Arduino only sends cm, decide here
                if (cm == null) { System.out.println("[SKIP] " + f); return; }
                occupied = (cm > 0 && cm < THRESHOLD_CM);
            }

            if (cm == null) cm = -1;
            long ts = System.currentTimeMillis() / 1000;

            System.out.printf("[EVENT] lot=%s spot=%s cm=%d occupied=%s ts=%d%n",
                    LOT, SPOT, cm, occupied ? "true" : "false", ts);

            enqueueThingSpeak(occupied, cm, ts);

        } catch (Exception e) {
            System.out.println("[SKIP] " + f);
        }
    }

    // --- Queue logic: never "skip", just delay and send the latest event ---
    private void enqueueThingSpeak(boolean occupied, int cm, long ts) {
        try {
            String body =
                    "api_key=" + URLEncoder.encode(THINGSPEAK_WRITE_KEY, "UTF-8")
                            + "&field" + FIELD_OCCUPIED + "=" + (occupied ? "1" : "0")
                            + "&field" + FIELD_CM + "=" + cm
                            + "&field" + FIELD_TS + "=" + ts;

            long now = System.currentTimeMillis();
            long wait = (lastUpdateMs == 0) ? 0 : (MIN_UPDATE_MS - (now - lastUpdateMs));

            if (wait <= 0) {
                // can send immediately
                sendBodyToThingSpeak(body);
                return;
            }

            // store latest (coalesce)
            pendingBody = body;

            // schedule only once
            if (sendScheduled.compareAndSet(false, true)) {
                System.out.println("[ThingSpeak] Rate-limited. Will send in " + wait + " ms");
                scheduler.schedule(() -> {
                    try {
                        String b = pendingBody;
                        pendingBody = null;
                        if (b != null) sendBodyToThingSpeak(b);
                    } finally {
                        sendScheduled.set(false);
                    }
                }, wait, TimeUnit.MILLISECONDS);
            } else {
                System.out.println("[ThingSpeak] Rate-limited. Updated pending event.");
            }

        } catch (Exception e) {
            System.err.println("[ThingSpeak ERROR] " + e.getMessage());
        }
    }

    private void sendBodyToThingSpeak(String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.thingspeak.com/update.json");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);

            try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }

            int code = conn.getResponseCode();
            String resp = readAll((code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream());
            System.out.println("[ThingSpeak] HTTP " + code + " response: " + resp);

            // mark send time only on success
            if (code == 200 && resp != null && !resp.trim().equals("0")) {
                lastUpdateMs = System.currentTimeMillis();
            }

        } catch (Exception ex) {
            System.err.println("[ThingSpeak ERROR] " + ex.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ===== TalkBack polling (Android -> TalkBack -> Java -> Arduino) =====
    private void startTalkBackPolling() {
        talkbackScheduler.scheduleAtFixedRate(() -> {
            try {
                if (port == null || !port.isOpen()) return;

                String cmd = executeNextTalkBackCommand();
                if (cmd == null || cmd.trim().isEmpty()) return;

                cmd = cmd.trim();
                System.out.println("[TalkBack] Next command: " + cmd);

                // opcional: evita mandar o mesmo comando repetido (remove se quiseres permitir repetidos)
                if (lastSentCmd != null && cmd.equalsIgnoreCase(lastSentCmd)) return;

                if (cmd.equalsIgnoreCase("LED_ON") || cmd.equalsIgnoreCase("LED_OFF")) {
                    sendToArduino(cmd + "\n");
                    lastSentCmd = cmd.toUpperCase();
                }
            } catch (Exception e) {
                System.err.println("[TalkBack ERROR] " + e.getMessage());
            }
        }, 0, POLL_TALKBACK_MS, TimeUnit.MILLISECONDS);
    }

    private String executeNextTalkBackCommand() throws IOException {
        // POST https://api.thingspeak.com/talkbacks/{id}/commands/execute.json
        URL url = new URL("https://api.thingspeak.com/talkbacks/" + TALKBACK_ID + "/commands/execute.json");

        String body = "api_key=" + URLEncoder.encode(TALKBACK_API_KEY, "UTF-8");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);

        try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }

        int code = conn.getResponseCode();
        String resp = readAll((code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();

        if (code != 200 || resp == null || resp.trim().isEmpty()) return null;

        // Extrair "command_string":"LED_ON" sem biblioteca JSON
        String key = "\"command_string\":\"";
        int i = resp.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        int end = resp.indexOf("\"", start);
        if (end < 0) return null;

        return resp.substring(start, end);
    }

    private void sendToArduino(String text) {
        try {
            OutputStream os = port.getOutputStream();
            os.write(text.getBytes(StandardCharsets.UTF_8));
            os.flush();
            System.out.println("[SERIAL->ARDUINO] " + text.trim());
        } catch (Exception e) {
            System.err.println("[SERIAL WRITE ERROR] " + e.getMessage());
        }
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    // --- Unused interfaces (kept to match your implements) ---
    @Override public int getListeningEvents() { return 0; }
    @Override public void serialEvent(SerialPortEvent serialPortEvent) { }
    @Override public void actionPerformed(ActionEvent e) { }

    @Override public void windowOpened(WindowEvent e) { }
    @Override public void windowClosing(WindowEvent e) { }
    @Override public void windowClosed(WindowEvent e) { }
    @Override public void windowIconified(WindowEvent e) { }
    @Override public void windowDeiconified(WindowEvent e) { }
    @Override public void windowActivated(WindowEvent e) { }
    @Override public void windowDeactivated(WindowEvent e) { }
}
