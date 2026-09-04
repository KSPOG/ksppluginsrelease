from pathlib import Path


def replace_exact(path: str, old: str, new: str, count: int = 1):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    actual = text.count(old)
    if actual != count:
        raise RuntimeError(f"{path}: expected {count} occurrence(s), found {actual}: {old!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")


# The user's installed RuneLite/Microbot Text class does not expose unescapeJagex(String).
# Player/trade names only need formatting tags stripped here; NBSP/underscore normalization is
# already handled by normaliseName(), so using the older-compatible removeTags(String) is enough.
for path in [
    "kspmule/KspMuleWorkerService.java",
    "kspf2phighalchtrader/KspHighAlchMuleService.java",
    "kspf2phighalchtrader/KspHighAlchTradeAcceptGuard.java",
    "KSPTradeReceiver/KSPTradeReceiverScript.java",
]:
    replace_exact(
        path,
        "Text.removeTags(Text.unescapeJagex(value)).trim()",
        "Text.removeTags(value).trim()",
    )


# High Alch Trader has its own localhost mule client/service. Keep it compatible with the
# COMPLETE_ACK handshake added to Trade Receiver 0.2.4 so the receiver cannot remain latched
# waiting for an acknowledgement this worker never sends.
replace_exact(
    "kspf2phighalchtrader/KspLocalMuleClient.java",
    '''    void cancel(int port, String requestId)\n    {\n        if (requestId == null || requestId.isBlank()) return;\n        call(port, "CANCEL\\t" + requestId);\n    }\n\n    boolean ping(int port)\n''',
    '''    void cancel(int port, String requestId)\n    {\n        if (requestId == null || requestId.isBlank()) return;\n        call(port, "CANCEL\\t" + requestId);\n    }\n\n    boolean acknowledgeComplete(int port, String requestId)\n    {\n        return requestId != null && !requestId.isBlank()\n                && "ACKED".equals(call(port, "COMPLETE_ACK\\t" + requestId));\n    }\n\n    boolean ping(int port)\n''',
)

replace_exact(
    "kspf2phighalchtrader/KspHighAlchMuleService.java",
    '''            case COMPLETE:\n                state = MuleState.RESTORING;\n                status = "Transfer complete - restoring capital";\n                restoreTradingCapital();\n                finishSuccess();\n                return;\n''',
    '''            case COMPLETE:\n                state = MuleState.WAITING_COMPLETE;\n                status = "Transfer complete - acknowledging receiver";\n                if (requestId == null || !client.acknowledgeComplete(config.mulePort(), requestId))\n                {\n                    status = "Transfer complete - waiting for receiver acknowledgement";\n                    return;\n                }\n                state = MuleState.RESTORING;\n                status = "Transfer acknowledged - restoring capital";\n                restoreTradingCapital();\n                finishSuccess();\n                return;\n''',
)

# Patch versions for the two directly changed standalone plugins.
replace_exact(
    "KSPTradeReceiver/KSPTradeReceiverPlugin.java",
    'public static final String VERSION = "0.2.4";',
    'public static final String VERSION = "0.2.5";',
)
replace_exact(
    "kspf2phighalchtrader/KspF2PHighAlchTraderPlugin.java",
    'public static final String VERSION = "0.3.2";',
    'public static final String VERSION = "0.3.3";',
)

# Guard against reintroducing the exact API that failed in the user's source-loader compiler.
remaining = []
for root in [Path("KSPTradeReceiver"), Path("kspf2phighalchtrader"), Path("kspmule")]:
    for java in root.rglob("*.java"):
        if "unescapeJagex(" in java.read_text(encoding="utf-8"):
            remaining.append(str(java))
if remaining:
    raise RuntimeError("Unsupported Text.unescapeJagex remains in: " + ", ".join(remaining))

# The final branch must not retain patch machinery.
Path(".github/workflows/fix-source-loader-runtime-compatibility.yml").unlink(missing_ok=True)
Path(".github/scripts/fix_source_loader_runtime_compatibility.py").unlink(missing_ok=True)
