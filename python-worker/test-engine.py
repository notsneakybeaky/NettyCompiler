import asyncio
import json
import websockets
import aiohttp

# ── How long to wait for the warm pool to be ready before connecting.
# Increase this if you're running the test immediately after `gradle run`.
STARTUP_WAIT_SECONDS = 5

# ── How long (seconds) to wait for the server to send the 'active' ack
# when a container has to be created on-demand (pool was empty).
CONTAINER_READY_TIMEOUT = 30

# The Python code we will inject into the containers
SCRIPT_V1 = """
def handle(payload):
    # Initialize state if it doesn't exist
    if "counter" not in __state__:
        __state__["counter"] = 0
    
    __state__["counter"] += 1
    
    return {
        "actions": [
            {
                "type": "SEND",
                "message": {
                    "type": "test_response",
                    "version": "v1",
                    "count": __state__["counter"]
                }
            }
        ]
    }
"""

SCRIPT_V2 = """
def handle(payload):
    # Notice we don't reset the counter, we just read the preserved state!
    current_count = __state__.get("counter", 0)
    
    return {
        "actions": [
            {
                "type": "SEND",
                "message": {
                    "type": "test_response",
                    "version": "HOT_FLASHED_V2",
                    "count": current_count
                }
            }
        ]
    }
"""


async def recv_with_timeout(ws, timeout: float, label: str):
    """Receive one message with a timeout; raises on timeout."""
    try:
        raw = await asyncio.wait_for(ws.recv(), timeout=timeout)
        return json.loads(raw)
    except asyncio.TimeoutError:
        raise TimeoutError(f"Timed out waiting for message: {label}")


async def wait_for_active(ws, user_id: int) -> str:
    """
    Handle the session handshake and return the session_id.

    The server sends one of three first messages:
      • {"type": "session_ack", "status": "active",       "session_id": "..."}  — pool hit
      • {"type": "session_ack", "status": "initializing", "session_id": "..."}  — pool miss,
            followed by a second {"status": "active", "session_id": "..."} when ready
      • {"type": "error", "code": "container_failed", ...}                      — hard failure

    Returns session_id on success, raises RuntimeError on failure.
    """
    msg1 = await recv_with_timeout(ws, CONTAINER_READY_TIMEOUT, "first ack")
    print(f"[User {user_id}] Received: {msg1}")

    # Hard failure — server couldn't allocate a container at all
    if msg1.get("type") == "error":
        raise RuntimeError(
            f"Server returned error: {msg1.get('code')} — {msg1.get('detail')}"
        )

    status = msg1.get("status")

    if status == "active":
        # Warm pool hit — container was ready immediately
        return msg1["session_id"]

    if status == "initializing":
        # Container is being provisioned; wait for the follow-up 'active' message
        msg2 = await recv_with_timeout(ws, CONTAINER_READY_TIMEOUT, "active ack")
        print(f"[User {user_id}] Received: {msg2}")

        if msg2.get("type") == "error":
            raise RuntimeError(
                f"Server returned error after init: {msg2.get('code')} — {msg2.get('detail')}"
            )

        if msg2.get("status") != "active":
            raise RuntimeError(f"Unexpected second message: {msg2}")

        return msg2["session_id"]

    raise RuntimeError(f"Unexpected first message from server: {msg1}")


async def simulate_user(user_id: int):
    uri = "ws://localhost:8080/ws"

    print(f"[User {user_id}] Connecting...")
    async with websockets.connect(uri) as ws:

        # ── 1. Handshake — get session_id ─────────────────────────────────────
        try:
            session_id = await wait_for_active(ws, user_id)
        except (RuntimeError, TimeoutError) as e:
            print(f"[User {user_id}] ✗ Could not get a session: {e}")
            return

        print(f"[User {user_id}] ✓ Assigned Session ID: {session_id}")

        flash_url = f"http://localhost:8080/sessions/{session_id}/hot-flash"

        # ── 2. Inject Script V1 via HTTP ──────────────────────────────────────
        async with aiohttp.ClientSession() as http:
            resp = await http.post(
                flash_url, json={"script_id": "test_node", "source": SCRIPT_V1}
            )
            print(f"[User {user_id}] Injected Script V1 (HTTP {resp.status})")

        # ── 3. Trigger hook — counter → 1 ────────────────────────────────────
        await ws.send(json.dumps({"type": "dummy_trigger"}))
        response = await recv_with_timeout(ws, 10, "hook response 1")
        print(f"[User {user_id}] Hook Response: {response}")

        # ── 4. Trigger hook — counter → 2 ────────────────────────────────────
        await ws.send(json.dumps({"type": "dummy_trigger"}))
        response = await recv_with_timeout(ws, 10, "hook response 2")
        print(f"[User {user_id}] Hook Response: {response}")

        # ── 5. Hot-flash Script V2 ────────────────────────────────────────────
        async with aiohttp.ClientSession() as http:
            resp = await http.post(
                flash_url, json={"script_id": "test_node", "source": SCRIPT_V2}
            )
            print(f"[User {user_id}] 🔥 HOT-FLASHED Script V2 (HTTP {resp.status}) 🔥")

        # ── 6. Trigger hook — expect V2 script, counter should STILL be 2 ────
        await ws.send(json.dumps({"type": "dummy_trigger"}))
        response = await recv_with_timeout(ws, 10, "hook response 3")
        print(f"[User {user_id}] Hook Response: {response}")

        version = response.get("version") or response.get("message", {}).get("version")
        count   = response.get("count")   or response.get("message", {}).get("count")

        if version == "HOT_FLASHED_V2" and count == 2:
            print(f"[User {user_id}] ✓ State preserved across hot-flash!")
        else:
            print(f"[User {user_id}] ✗ Unexpected response — version={version}, count={count}")

        print(f"[User {user_id}] Test Complete. Disconnecting.")


async def main():
    if STARTUP_WAIT_SECONDS > 0:
        print(f"Waiting {STARTUP_WAIT_SECONDS}s for warm pool to fill before connecting...")
        await asyncio.sleep(STARTUP_WAIT_SECONDS)

    # Spawn 3 concurrent users to test multi-tenancy and the Warm Pool
    users = [simulate_user(i) for i in range(1, 4)]
    results = await asyncio.gather(*users, return_exceptions=True)

    # Surface any unhandled exceptions from individual user tasks
    for i, result in enumerate(results, start=1):
        if isinstance(result, Exception):
            print(f"[User {i}] Unhandled exception: {result}")


if __name__ == "__main__":
    asyncio.run(main())