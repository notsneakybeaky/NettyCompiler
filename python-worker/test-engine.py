import asyncio
import json
import websockets
import aiohttp

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

async def simulate_user(user_id: int):
    uri = "ws://localhost:8080/ws"

    print(f"[User {user_id}] Connecting...")
    async with websockets.connect(uri) as ws:
        # 1. Wait for Session Ack
        ack1 = json.loads(await ws.recv())
        print(f"[User {user_id}] Received: {ack1}")

        # If the warm pool is working, this might be 'active' immediately.
        # If it's 'initializing', we wait for the 'active' message.
        if ack1.get("status") == "initializing":
            ack2 = json.loads(await ws.recv())
            print(f"[User {user_id}] Received: {ack2}")
            session_id = ack2["session_id"]
        else:
            session_id = ack1["session_id"]

        print(f"[User {user_id}] Assigned Session ID: {session_id}")

        # 2. Hot-Flash the initial script (V1) via HTTP API
        async with aiohttp.ClientSession() as http:
            flash_url = f"http://localhost:8080/sessions/{session_id}/hot-flash"
            await http.post(flash_url, json={"script_id": "test_node", "source": SCRIPT_V1})
            print(f"[User {user_id}] Injected Script V1")

        # 3. Trigger the hook over WebSocket (increments counter to 1)
        await ws.send(json.dumps({"type": "dummy_trigger"}))
        response = json.loads(await ws.recv())
        print(f"[User {user_id}] Hook Response: {response}")

        # 4. Trigger again (increments counter to 2)
        await ws.send(json.dumps({"type": "dummy_trigger"}))
        response = json.loads(await ws.recv())
        print(f"[User {user_id}] Hook Response: {response}")

        # 5. Hot-Flash the new script (V2) - Testing State Preservation
        async with aiohttp.ClientSession() as http:
            await http.post(flash_url, json={"script_id": "test_node", "source": SCRIPT_V2})
            print(f"[User {user_id}] 🔥 HOT-FLASHED Script V2 🔥")

        # 6. Trigger the hook again. We expect Version V2, but the count should STILL BE 2!
        await ws.send(json.dumps({"type": "dummy_trigger"}))
        response = json.loads(await ws.recv())
        print(f"[User {user_id}] Hook Response: {response}")

        print(f"[User {user_id}] Test Complete. Disconnecting.")

async def main():
    # Let's spawn 3 concurrent users to test multi-tenancy and the Warm Pool
    users = [simulate_user(i) for i in range(1, 4)]
    await asyncio.gather(*users)

if __name__ == "__main__":
    asyncio.run(main())