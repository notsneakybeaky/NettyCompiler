import asyncio
import json
import websockets
import aiohttp
import time

# Wait for pool to stabilize
STARTUP_WAIT_SECONDS = 5
# Time to wait for container provisioning
CONTAINER_READY_TIMEOUT = 30

# V1: Increments a counter on every 'ping' and returns it inside a 'pong' timestamp
SCRIPT_V1 = """
def handle(payload):
    # Java sends 'ping', which triggers the 'on_packet' hook
    if payload.get("hook") != "on_packet" or payload.get("message_type") != "ping":
        return {"actions": []}
    
    # Initialize persistent state
    if "counter" not in __state__:
        __state__["counter"] = 0
    
    __state__["counter"] += 1
    
    # Respond with 'pong' (registered in Java)
    # We use the 'timestamp' field to carry our counter value
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

# V2: Checks if state was preserved and returns counter + 100
SCRIPT_V2 = """
def handle(payload):
    if payload.get("hook") != "on_packet" or payload.get("message_type") != "ping":
        return {"actions": []}
        
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
    """Handles the session_ack sequence (initializing -> active)."""
    msg1 = await recv_with_timeout(ws, CONTAINER_READY_TIMEOUT, "first ack")
    if msg1.get("type") == "error":
        raise RuntimeError(f"Server error: {msg1.get('code')}")

    if msg1.get("status") == "active":
        return msg1["session_id"]

    # If pool miss, wait for second message
    msg2 = await recv_with_timeout(ws, CONTAINER_READY_TIMEOUT, "active ack")
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
            # 1. Inject Script V1
            await http.post(flash_url, json={"source": SCRIPT_V1})

            # 2. Trigger ping -> expect pong (1)
            await ws.send(json.dumps({"type": "ping", "timestamp": int(time.time())}))
            res = await recv_with_timeout(ws, 5, "pong 1")
            print(f"[User {user_id}] Pong 1 (count): {res.get('timestamp')}")

            # 3. Trigger ping -> expect pong (2)
            await ws.send(json.dumps({"type": "ping", "timestamp": int(time.time())}))
            res = await recv_with_timeout(ws, 5, "pong 2")
            print(f"[User {user_id}] Pong 2 (count): {res.get('timestamp')}")

            # 4. Hot-flash Script V2
            await http.post(flash_url, json={"source": SCRIPT_V2})
            print(f"[User {user_id}] 🔥 Hot-Flashed V2")

            # 5. Trigger ping -> expect pong (102 if state preserved)
            await ws.send(json.dumps({"type": "ping", "timestamp": int(time.time())}))
            res = await recv_with_timeout(ws, 5, "pong 3")
            val = res.get('timestamp')

            if val == 102:
                print(f"[User {user_id}] ✓ Success: State preserved!")
            else:
                print(f"[User {user_id}] ✗ Failed: Expected 102, got {val}")

async def main():
    print(f"Waiting {STARTUP_WAIT_SECONDS}s for pool...")
    await asyncio.sleep(STARTUP_WAIT_SECONDS)
    await asyncio.gather(*[simulate_user(i) for i in range(1, 4)])

if __name__ == "__main__":
    asyncio.run(main())