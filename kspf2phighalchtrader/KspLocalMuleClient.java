package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Blocking, single-request localhost RPC client for KSPTradeReceiver.
 * Every call opens a short-lived loopback socket so receiver restarts are naturally
 * recovered by the next poll and the worker owns no permanent networking thread.
 */
@Slf4j
final class KspLocalMuleClient
{
    enum State
    {
        UNKNOWN,
        QUEUED,
        ACTIVE,
        COMPLETE,
        FAILED,
        CANCELLED
    }

    static final class Status
    {
        private final State state;
        private final int queuePosition;
        private final String muleName;
        private final int world;
        private final int x;
        private final int y;
        private final int plane;
        private final long coins;
        private final String message;

        private Status(State state, int queuePosition, String muleName,
                       int world, int x, int y, int plane, long coins, String message)
        {
            this.state = state;
            this.queuePosition = queuePosition;
            this.muleName = muleName;
            this.world = world;
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.coins = coins;
            this.message = message;
        }

        static Status unknown(String message)
        {
            return new Status(State.UNKNOWN, 0, "", 0, 0, 0, 0, 0, message);
        }

        State getState() { return state; }
        int getQueuePosition() { return queuePosition; }
        String getMuleName() { return muleName; }
        int getWorld() { return world; }
        int getX() { return x; }
        int getY() { return y; }
        int getPlane() { return plane; }
        long getCoins() { return coins; }
        String getMessage() { return message; }
    }

    Status ready(int port, String requestId, String accountName, long coins, int world)
    {
        return parse(call(port, "READY\t" + requestId + "\t" + encode(accountName) + "\t" + coins + "\t" + world));
    }

    Status status(int port, String requestId)
    {
        return parse(call(port, "STATUS\t" + requestId));
    }

    void cancel(int port, String requestId)
    {
        if (requestId == null || requestId.isBlank()) return;
        call(port, "CANCEL\t" + requestId);
    }

    boolean ping(int port)
    {
        return "PONG".equals(call(port, "PING"));
    }

    private String call(int port, String request)
    {
        if (port < 1024 || port > 65535) return "ERROR\tINVALID_PORT";

        try (Socket socket = new Socket())
        {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1_500);
            socket.setSoTimeout(2_500);

            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)))
            {
                out.write(request);
                out.newLine();
                out.flush();
                String response = in.readLine();
                return response == null ? "ERROR\tEMPTY_RESPONSE" : response;
            }
        }
        catch (IOException ex)
        {
            log.debug("KSP local mule RPC unavailable on 127.0.0.1:{}: {}", port, ex.getMessage());
            return "ERROR\tUNREACHABLE";
        }
    }

    private Status parse(String response)
    {
        if (response == null || response.isBlank()) return Status.unknown("Empty response");

        String[] parts = response.split("\\t", -1);
        switch (parts[0])
        {
            case "QUEUED":
                return new Status(State.QUEUED, parts.length > 1 ? parseInt(parts[1]) : 0,
                        "", 0, 0, 0, 0, 0, "");
            case "ACTIVE":
                if (parts.length < 7) return Status.unknown("Malformed ACTIVE response");
                return new Status(State.ACTIVE, 0, decode(parts[1]), parseInt(parts[2]),
                        parseInt(parts[3]), parseInt(parts[4]), parseInt(parts[5]), parseLong(parts[6]), "");
            case "COMPLETE":
                return new Status(State.COMPLETE, 0, "", 0, 0, 0, 0, 0, "");
            case "FAILED":
                return new Status(State.FAILED, 0, "", 0, 0, 0, 0, 0,
                        parts.length > 1 ? decode(parts[1]) : "Mule job failed");
            case "CANCELLED":
                return new Status(State.CANCELLED, 0, "", 0, 0, 0, 0, 0, "Cancelled");
            case "UNKNOWN":
                return Status.unknown("Unknown job");
            case "ERROR":
            default:
                return Status.unknown(parts.length > 1 ? parts[1] : response);
        }
    }

    private static int parseInt(String value)
    {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ex) { return 0; }
    }

    private static long parseLong(String value)
    {
        try { return Long.parseLong(value); }
        catch (NumberFormatException ex) { return 0L; }
    }

    private static String encode(String value)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value)
    {
        try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ex) { return ""; }
    }
}
