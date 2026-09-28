import os
import httpx
from mcp.server.fastmcp import FastMCP

MICROBOT_URL = os.getenv("MICROBOT_URL", "http://127.0.0.1:8081")
MICROBOT_TOKEN = os.getenv("MICROBOT_TOKEN", "")

mcp = FastMCP("microbot")

def _headers() -> dict:
    if not MICROBOT_TOKEN:
        raise RuntimeError("MICROBOT_TOKEN is not set")
    return {"X-Agent-Token": MICROBOT_TOKEN}

@mcp.tool()
def get_microbot_state() -> dict:
    """
    Read the current Microbot Agent Server state.
    """
    response = httpx.get(
        f"{MICROBOT_URL}/state",
        headers=_headers(),
        timeout=5.0,
    )
    response.raise_for_status()
    return response.json()

if __name__ == "__main__":
    mcp.run()