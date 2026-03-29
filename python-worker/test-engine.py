import asyncio
import json
import websockets
import aiohttp
import time

STARTUP_WAIT_SECONDS = 5
CONTAINER_READY_TIMEOUT = 30

# V1: Simply increments a counter and returns pong. No hook/type filtering —
# _dispatch in main.py already handles that routing.
SCRIPT_V1 = """
def handle(payload):
    if "counter" not in __state__:
        __state__["counter"] = 0
    __state__["counter"] += 1
    return {
        "actions": [
            {
                "type": "SEND",
                "message": {
                    "type": "pong",
                    "timestamp": __state__["counter"]
                }
            }
        ]
    }
"""

# V2: Reads preserved counter and adds 100
SCRIPT_V2 = """
def handle(payload):
    current_count = __state__.get("counter", 0)
    return {
        "actions": [
            {
                "type": "SEND",
                "message": {
                    "type": "pong",
                    "timestamp": current_count + 100
                }
            }
        ]
    }
"""


async def recv_with_timeout(ws, timeout: float, label: str):
    try:
        raw = await asyncio.wait_for(ws.recv(), timeout=timeout)
        return json.loads(raw)
    except asyncio.TimeoutError:
        raise TimeoutError(f"Timed out waiting for message: {label}")


async def wait_for_active(ws, user_id: int) -> str:
    msg1 = await recv_with_timeout(ws, CONTAINER_READY_TIMEOUT, "first ack")
    if msg1.get("type") == "error":
        raise RuntimeError(f"Server error: {msg1.get('code')} — {msg1.get('detail')}")

    if msg1.get("status") == "active":
        return msg1["session_id"]

    msg2 = await recv_with_timeout(ws, CONTAINER_READY_TIMEOUT, "active ack")
    if msg2.get("type") == "error":
        raise RuntimeError(f"Server error: {msg2.get('code')} — {msg2.get('detail')}")
    return msg2["session_id"]


async def simulate_user(user_id: int):
    uri = "ws://localhost:8080/ws"
    print(f"[User {user_id}] Connecting...")

    async with websockets.connect(uri) as ws:
        try:
            session_id = await wait_for_active(ws, user_id)
        except Exception as e:
            print(f"[User {user_id}] ✗ Handshake failed: {e}")
            return

        print(f"[User {user_id}] ✓ Active Session: {session_id}")
        flash_url = f"http://localhost:8080/sessions/{session_id}/hot-flash"

        async with aiohttp.ClientSession() as http:
            # 1. Inject Script V1 — check the response!
            resp = await http.post(flash_url, json={"source": SCRIPT_V1})
            body = await resp.text()
            print(f"[User {user_id}] Hot-flash V1: HTTP {resp.status} — {body}")
            if resp.status != 200:
                print(f"[User {user_id}] ✗ Hot-flash failed, aborting")
                return

            # 2. Trigger ping -> expect pong (1)
            await ws.send(json.dumps({"type": "ping", "timestamp": int(time.time())}))
            res = await recv_with_timeout(ws, 5, "pong 1")
            print(f"[User {user_id}] Pong 1: {res}")

            # 3. Trigger ping -> expect pong (2)
            await ws.send(json.dumps({"type": "ping", "timestamp": int(time.time())}))
            res = await recv_with_timeout(ws, 5, "pong 2")
            print(f"[User {user_id}] Pong 2: {res}")

            # 4. Hot-flash Script V2
            resp = await http.post(flash_url, json={"source": SCRIPT_V2})
            body = await resp.text()
            print(f"[User {user_id}] 🔥 Hot-flash V2: HTTP {resp.status} — {body}")

            # 5. Trigger ping -> expect pong (102 if state preserved)
            await ws.send(json.dumps({"type": "ping", "timestamp": int(time.time())}))
            res = await recv_with_timeout(ws, 5, "pong 3")
            val = res.get("timestamp")

            if val == 102:
                print(f"[User {user_id}] ✓ State preserved across hot-flash!")
            else:
                print(f"[User {user_id}] ✗ Expected 102, got {val}")


async def main():
    print(f"Waiting {STARTUP_WAIT_SECONDS}s for pool...")
    await asyncio.sleep(STARTUP_WAIT_SECONDS)
    results = await asyncio.gather(
        *[simulate_user(i) for i in range(1, 4)],
        return_exceptions=True,
    )
    for i, r in enumerate(results, 1):
        if isinstance(r, Exception):
            print(f"[User {i}] Unhandled error: {r}")


if __name__ == "__main__":
    asyncio.run(main())