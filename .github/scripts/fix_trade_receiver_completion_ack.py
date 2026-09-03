from pathlib import Path


def replace_exact(path, old, new):
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise RuntimeError(f'Expected text not found in {path}: {old[:120]!r}')
    text = text.replace(old, new, 1)
    p.write_text(text, encoding='utf-8')

# Worker client: add explicit COMPLETE_ACK RPC.
replace_exact(
    'kspmule/KspLocalMuleClient.java',
    '''    void cancel(int port, String requestId)\n    {\n        if (requestId != null && !requestId.isBlank()) call(port, "CANCEL\\t" + requestId);\n    }\n\n    boolean ping(int port)\n''',
    '''    void cancel(int port, String requestId)\n    {\n        if (requestId != null && !requestId.isBlank()) call(port, "CANCEL\\t" + requestId);\n    }\n\n    boolean acknowledgeComplete(int port, String requestId)\n    {\n        return requestId != null && !requestId.isBlank()\n                && "ACKED".equals(call(port, "COMPLETE_ACK\\t" + requestId));\n    }\n\n    boolean ping(int port)\n''')

# Worker: do not resume until receiver confirms it received the completion ACK.
replace_exact(
    'kspmule/KspMuleWorkerService.java',
    '''            case COMPLETE:\n                state = State.RESTORING;\n                status = "Transfer complete - restoring capital";\n                restoreTradingCapital();\n                finishSuccess();\n                return;\n''',
    '''            case COMPLETE:\n                state = State.WAITING_COMPLETE;\n                status = "Transfer complete - acknowledging receiver";\n                KspMuleConfig currentConfig = config;\n                if (currentConfig == null || requestId == null\n                        || !client.acknowledgeComplete(currentConfig.muleReceiverPort(), requestId))\n                {\n                    status = "Transfer complete - waiting for receiver acknowledgement";\n                    return;\n                }\n                state = State.RESTORING;\n                status = "Transfer acknowledged - restoring capital";\n                restoreTradingCapital();\n                finishSuccess();\n                return;\n''')

# Server protocol docs.
replace_exact(
    'KSPTradeReceiver/KspLocalMuleServer.java',
    ''' * STATUS <requestId>\n * CANCEL <requestId>\n * PING\n''',
    ''' * STATUS <requestId>\n * COMPLETE_ACK <requestId>\n * CANCEL <requestId>\n * PING\n''')

# Server command handler.
replace_exact(
    'KSPTradeReceiver/KspLocalMuleServer.java',
    '''            case "CANCEL":\n                if (parts.length < 2)\n                {\n                    return "ERROR\\tCANCEL_FORMAT";\n                }\n                return handleCancel(parts[1]);\n\n            default:\n''',
    '''            case "COMPLETE_ACK":\n                if (parts.length < 2)\n                {\n                    return "ERROR\\tCOMPLETE_ACK_FORMAT";\n                }\n                return handleCompleteAck(parts[1]);\n\n            case "CANCEL":\n                if (parts.length < 2)\n                {\n                    return "ERROR\\tCANCEL_FORMAT";\n                }\n                return handleCancel(parts[1]);\n\n            default:\n''')

# Add COMPLETE_ACK handler before cancellation handler.
replace_exact(
    'KSPTradeReceiver/KspLocalMuleServer.java',
    '''    private String handleCancel(String requestId)\n    {\n''',
    '''    private String handleCompleteAck(String requestId)\n    {\n        String id = sanitizeRequestId(requestId);\n        MuleJob job = jobs.get(id);\n        if (job == null)\n        {\n            return "UNKNOWN";\n        }\n\n        synchronized (activationLock)\n        {\n            if (job.state != JobState.COMPLETE)\n            {\n                job.touch();\n                return statusResponse(job);\n            }\n\n            job.touch();\n            if (job == activeJob)\n            {\n                activeJob = null;\n            }\n            queue.remove(id);\n            jobs.remove(id, job);\n            return "ACKED";\n        }\n    }\n\n    private String handleCancel(String requestId)\n    {\n''')

# Block activation of the next worker while a completed transfer is awaiting ACK.
replace_exact(
    'KSPTradeReceiver/KspLocalMuleServer.java',
    '''        synchronized (activationLock)\n        {\n            if (activeJob != null && activeJob.state == JobState.ACTIVE)\n            {\n                return activeJob;\n            }\n\n            if (muleName == null || muleName.isBlank())\n''',
    '''        synchronized (activationLock)\n        {\n            if (activeJob != null && activeJob.state == JobState.ACTIVE)\n            {\n                return activeJob;\n            }\n\n            if (hasUnacknowledgedCompletionUnsafe())\n            {\n                return null;\n            }\n\n            if (muleName == null || muleName.isBlank())\n''')

# completeActive leaves COMPLETE persisted in jobs; active pointer can clear, but pendingCount will latch it.
# Add helper near pending methods.
replace_exact(
    'KSPTradeReceiver/KspLocalMuleServer.java',
    '''    public boolean hasPendingJobs()\n    {\n        return pendingCount() > 0;\n    }\n\n    public int pendingCount()\n''',
    '''    public boolean hasPendingJobs()\n    {\n        return pendingCount() > 0;\n    }\n\n    public boolean hasUnacknowledgedCompletion()\n    {\n        synchronized (activationLock)\n        {\n            return hasUnacknowledgedCompletionUnsafe();\n        }\n    }\n\n    private boolean hasUnacknowledgedCompletionUnsafe()\n    {\n        for (MuleJob job : jobs.values())\n        {\n            if (job.state == JobState.COMPLETE)\n            {\n                return true;\n            }\n        }\n        return false;\n    }\n\n    public int pendingCount()\n''')

# COMPLETE remains pending until worker ack.
replace_exact(
    'KSPTradeReceiver/KspLocalMuleServer.java',
    '''            if (job.state == JobState.QUEUED || job.state == JobState.ACTIVE)\n            {\n                count++;\n            }\n''',
    '''            if (job.state == JobState.QUEUED || job.state == JobState.ACTIVE || job.state == JobState.COMPLETE)\n            {\n                count++;\n            }\n''')

# Expiration also releases an abandoned COMPLETE latch.
replace_exact(
    'KSPTradeReceiver/KspLocalMuleServer.java',
    '''                if ((job.state == JobState.QUEUED || job.state == JobState.ACTIVE)\n                        && now - job.lastContactAt > staleAfterMs)\n                {\n                    job.state = JobState.FAILED;\n                    job.failureReason = "Worker stopped contacting mule coordinator";\n''',
    '''                if ((job.state == JobState.QUEUED || job.state == JobState.ACTIVE || job.state == JobState.COMPLETE)\n                        && now - job.lastContactAt > staleAfterMs)\n                {\n                    boolean completedAwaitingAck = job.state == JobState.COMPLETE;\n                    job.state = JobState.FAILED;\n                    job.failureReason = completedAwaitingAck\n                            ? "Worker did not acknowledge completed transfer"\n                            : "Worker stopped contacting mule coordinator";\n''')

# Coordinator status: make the latched state explicit and never start logout while waiting for ACK.
replace_exact(
    'KSPTradeReceiver/KspLocalMuleCoordinatorService.java',
    '''        if (active == null)\n        {\n            restoreManualTrader();\n            activeWorker = "-";\n            activeCoins = 0L;\n            maybeLogoutWhenDone(currentServer);\n            return;\n        }\n''',
    '''        if (active == null)\n        {\n            restoreManualTrader();\n            activeWorker = "-";\n            activeCoins = 0L;\n            if (currentServer.hasUnacknowledgedCompletion())\n            {\n                status = "Transfer complete - waiting for worker acknowledgement";\n                queueEmptySince = 0L;\n                return;\n            }\n            maybeLogoutWhenDone(currentServer);\n            return;\n        }\n''')

# Version bump.
replace_exact(
    'KSPTradeReceiver/KSPTradeReceiverPlugin.java',
    'public static final String VERSION = "0.2.3";',
    'public static final String VERSION = "0.2.4";')

# Clean temporary patch artifacts from final diff.
Path('.github/workflows/fix-trade-receiver-completion-ack.yml').unlink(missing_ok=True)
Path('.github/scripts/fix_trade_receiver_completion_ack.py').unlink(missing_ok=True)
