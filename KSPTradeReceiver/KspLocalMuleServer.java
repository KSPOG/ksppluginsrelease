package net.runelite.client.plugins.microbot.KSPTradeReceiver;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tiny loopback-only coordinator for KSP mule jobs.
 *
 * Protocol is intentionally dependency-free (tab-delimited UTF-8 over TCP) so any
 * Microbot plugin can talk to the receiver without pulling in a networking library.
 *
 * READY  <requestId> <base64 account name> <coin amount> <worker world>
 * STATUS <requestId>
 * CANCEL <requestId>
 * PING
 *
 * The worker account name is supplied automatically by the worker client. The mule
 * account name is discovered automatically from the receiver's local player and is
 * returned in ACTIVE responses. A job is never activated until that mule identity is
 * known, so worker plugins never have to guess which local account to trade.
 *
 * The server only binds to 127.0.0.1. It is not reachable from the LAN/Internet.
 */
@Slf4j
public final class KspLocalMuleServer implements Closeable
{
    public enum JobState
    {
        QUEUED,
        ACTIVE,
        COMPLETE,
        FAILED,
        CANCELLED
    }

    public static final class MuleJob
    {
        private final String requestId;
        private final String accountName;
        private final long coins;
        private final int world;
        private final long createdAt;
        private volatile long lastContactAt;
        private volatile JobState state;
        private volatile String failureReason;

        private MuleJob(String requestId, String accountName, long coins, int world)
        {
            this.requestId = requestId;
            this.accountName = accountName;
            this.coins = coins;
            this.world = world;
            this.createdAt = System.currentTimeMillis();
            this.lastContactAt = this.createdAt;
            this.state = JobState.QUEUED;
        }

        public String getRequestId() { return requestId; }
        public String getAccountName() { return accountName; }
        public long getCoins() { return coins; }
        public int getWorld() { return world; }
        public long getCreatedAt() { return createdAt; }
        public long getLastContactAt() { return lastContactAt; }
        public JobState getState() { return state; }
        public String getFailureReason() { return failureReason; }

        private void touch()
        {
            lastContactAt = System.currentTimeMillis();
        }
    }

    private static final int CLIENT_READ_TIMEOUT_MS = 4_000;
    private static final int MAX_LINE_LENGTH = 8_192;

    private final Map<String, MuleJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object activationLock = new Object();

    private volatile MuleJob activeJob;
    private volatile String muleName = "";
    private volatile int muleWorld;
    private volatile WorldPoint muleTile;

    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private ExecutorService clientExecutor;
    private int port;

    public synchronized void start(int port) throws IOException
    {
        if (running.get())
        {
            return;
        }

        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 32);

        this.serverSocket = socket;
        this.port = socket.getLocalPort();
        this.acceptExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ksp-mule-local-accept");
            thread.setDaemon(true);
            return thread;
        });
        this.clientExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "ksp-mule-local-client");
            thread.setDaemon(true);
            return thread;
        });
        running.set(true);
        acceptExecutor.execute(this::acceptLoop);
        log.info("KSP local mule coordinator listening on 127.0.0.1:{}", this.port);
    }

    private void acceptLoop()
    {
        while (running.get())
        {
            try
            {
                Socket client = serverSocket.accept();
                client.setSoTimeout(CLIENT_READ_TIMEOUT_MS);
                clientExecutor.execute(() -> handleClient(client));
            }
            catch (IOException ex)
            {
                if (running.get())
                {
                    log.warn("KSP local mule accept failed", ex);
                }
            }
        }
    }

    private void handleClient(Socket socket)
    {
        try (Socket client = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8)))
        {
            String line = in.readLine();
            if (line == null || line.length() > MAX_LINE_LENGTH)
            {
                write(out, "ERROR\tBAD_REQUEST");
                return;
            }

            String response = handleCommand(line);
            write(out, response);
        }
        catch (Exception ex)
        {
            log.debug("KSP local mule client request failed: {}", ex.getMessage());
        }
    }

    private String handleCommand(String line)
    {
        String[] parts = line.split("\\t", -1);
        if (parts.length == 0)
        {
            return "ERROR\tBAD_REQUEST";
        }

        switch (parts[0].trim().toUpperCase(Locale.ROOT))
        {
            case "PING":
                return "PONG";

            case "READY":
                if (parts.length < 4)
                {
                    return "ERROR\tREADY_FORMAT";
                }
                return handleReady(parts[1], decode(parts[2]), parsePositiveLong(parts[3]),
                        parts.length > 4 ? parsePositiveInt(parts[4]) : 0);

            case "STATUS":
                if (parts.length < 2)
                {
                    return "ERROR\tSTATUS_FORMAT";
                }
                return handleStatus(parts[1]);

            case "CANCEL":
                if (parts.length < 2)
                {
                    return "ERROR\tCANCEL_FORMAT";
                }
                return handleCancel(parts[1]);

            default:
                return "ERROR\tUNKNOWN_COMMAND";
        }
    }

    private String handleReady(String requestedId, String accountName, long coins, int world)
    {
        if (accountName == null || accountName.isBlank() || coins <= 0)
        {
            return "ERROR\tINVALID_JOB";
        }

        String requestId = sanitizeRequestId(requestedId);
        MuleJob job = jobs.compute(requestId, (id, existing) -> {
            if (existing != null)
            {
                existing.touch();
                return existing;
            }
            MuleJob created = new MuleJob(id, accountName.trim(), coins, world);
            queue.offer(id);
            return created;
        });

        return statusResponse(job);
    }

    private String handleStatus(String requestId)
    {
        MuleJob job = jobs.get(sanitizeRequestId(requestId));
        if (job == null)
        {
            return "UNKNOWN";
        }
        job.touch();
        return statusResponse(job);
    }

    private String handleCancel(String requestId)
    {
        MuleJob job = jobs.get(sanitizeRequestId(requestId));
        if (job == null)
        {
            return "UNKNOWN";
        }

        synchronized (activationLock)
        {
            if (job == activeJob)
            {
                job.state = JobState.CANCELLED;
                activeJob = null;
            }
            else if (job.state == JobState.QUEUED)
            {
                job.state = JobState.CANCELLED;
            }
        }
        return "CANCELLED";
    }

    private String statusResponse(MuleJob job)
    {
        switch (job.state)
        {
            case QUEUED:
                return "QUEUED\t" + queuePosition(job.requestId);

            case ACTIVE:
                int rendezvousWorld = job.world > 0 ? job.world : muleWorld;
                WorldPoint tile = muleWorld == rendezvousWorld ? muleTile : null;
                return "ACTIVE\t" + encode(muleName)
                        + "\t" + rendezvousWorld
                        + "\t" + (tile == null ? 0 : tile.getX())
                        + "\t" + (tile == null ? 0 : tile.getY())
                        + "\t" + (tile == null ? 0 : tile.getPlane())
                        + "\t" + job.coins;

            case COMPLETE:
                return "COMPLETE";

            case CANCELLED:
                return "CANCELLED";

            case FAILED:
            default:
                return "FAILED\t" + encode(job.failureReason == null ? "Unknown failure" : job.failureReason);
        }
    }

    private int queuePosition(String requestId)
    {
        int position = activeJob == null ? 0 : 1;
        for (String queuedId : queue)
        {
            MuleJob queued = jobs.get(queuedId);
            if (queued == null || queued.state != JobState.QUEUED)
            {
                continue;
            }
            position++;
            if (queuedId.equals(requestId))
            {
                return position;
            }
        }
        return Math.max(1, position);
    }

    /**
     * Activates at most one job. Only the RuneLite script thread calls this.
     *
     * Mule identity is authoritative: do not release a worker from QUEUED until the
     * receiver has discovered its own RuneScape display name. This removes the race
     * where a just-logged-in receiver could return ACTIVE with an empty player name.
     */
    public MuleJob activateNext()
    {
        synchronized (activationLock)
        {
            if (activeJob != null && activeJob.state == JobState.ACTIVE)
            {
                return activeJob;
            }

            if (muleName == null || muleName.isBlank())
            {
                return null;
            }

            String id;
            while ((id = queue.poll()) != null)
            {
                MuleJob candidate = jobs.get(id);
                if (candidate != null && candidate.state == JobState.QUEUED)
                {
                    candidate.state = JobState.ACTIVE;
                    candidate.touch();
                    activeJob = candidate;
                    return candidate;
                }
            }

            activeJob = null;
            return null;
        }
    }

    public MuleJob peekNextQueuedJob()
    {
        for (String queuedId : queue)
        {
            MuleJob queued = jobs.get(queuedId);
            if (queued != null && queued.state == JobState.QUEUED)
            {
                return queued;
            }
        }
        return null;
    }

    public MuleJob getActiveJob()
    {
        MuleJob current = activeJob;
        return current != null && current.state == JobState.ACTIVE ? current : null;
    }

    public void completeActive()
    {
        synchronized (activationLock)
        {
            if (activeJob != null)
            {
                activeJob.state = JobState.COMPLETE;
                activeJob.touch();
                activeJob = null;
            }
        }
    }

    public void failActive(String reason)
    {
        synchronized (activationLock)
        {
            if (activeJob != null)
            {
                activeJob.state = JobState.FAILED;
                activeJob.failureReason = reason;
                activeJob.touch();
                activeJob = null;
            }
        }
    }

    public void expireStaleJobs(long staleAfterMs)
    {
        if (staleAfterMs <= 0)
        {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (activationLock)
        {
            for (MuleJob job : jobs.values())
            {
                if ((job.state == JobState.QUEUED || job.state == JobState.ACTIVE)
                        && now - job.lastContactAt > staleAfterMs)
                {
                    job.state = JobState.FAILED;
                    job.failureReason = "Worker stopped contacting mule coordinator";
                    if (job == activeJob)
                    {
                        activeJob = null;
                    }
                }
            }
        }
    }

    /**
     * Refresh the receiver's live identity/location. A transient null local-player
     * snapshot during login/world hops must not erase a previously discovered mule name.
     */
    public void updateMuleSnapshot(String name, int world, WorldPoint tile)
    {
        String discoveredName = name == null ? "" : name.trim();
        if (!discoveredName.isBlank())
        {
            if (!discoveredName.equals(muleName))
            {
                log.info("KSP local mule automatically identified receiver account as '{}'", discoveredName);
            }
            this.muleName = discoveredName;
        }
        this.muleWorld = world;
        this.muleTile = tile;
    }

    public boolean hasPendingJobs()
    {
        return pendingCount() > 0;
    }

    public int pendingCount()
    {
        int count = 0;
        for (MuleJob job : jobs.values())
        {
            if (job.state == JobState.QUEUED || job.state == JobState.ACTIVE)
            {
                count++;
            }
        }
        return count;
    }

    public int queuedCount()
    {
        int count = 0;
        for (MuleJob job : jobs.values())
        {
            if (job.state == JobState.QUEUED)
            {
                count++;
            }
        }
        return count;
    }

    public int getPort()
    {
        return port;
    }

    public boolean isRunning()
    {
        return running.get();
    }

    @Override
    public synchronized void close()
    {
        if (!running.compareAndSet(true, false))
        {
            return;
        }

        try
        {
            if (serverSocket != null)
            {
                serverSocket.close();
            }
        }
        catch (IOException ignored)
        {
        }

        if (acceptExecutor != null)
        {
            acceptExecutor.shutdownNow();
        }
        if (clientExecutor != null)
        {
            clientExecutor.shutdownNow();
        }

        synchronized (activationLock)
        {
            activeJob = null;
        }
        queue.clear();
        jobs.clear();
    }

    private static void write(BufferedWriter out, String value) throws IOException
    {
        out.write(value);
        out.newLine();
        out.flush();
    }

    private static String sanitizeRequestId(String value)
    {
        if (value == null || value.isBlank())
        {
            return UUID.randomUUID().toString();
        }

        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "");
        if (cleaned.isBlank())
        {
            return UUID.randomUUID().toString();
        }
        return cleaned.substring(0, Math.min(96, cleaned.length()));
    }

    private static int parsePositiveInt(String value)
    {
        try
        {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : 0;
        }
        catch (NumberFormatException ex)
        {
            return 0;
        }
    }

    private static long parsePositiveLong(String value)
    {
        try
        {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : -1L;
        }
        catch (NumberFormatException ex)
        {
            return -1L;
        }
    }

    private static String encode(String value)
    {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value)
    {
        try
        {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException ex)
        {
            return "";
        }
    }
}
