import asyncio
import websockets

async def test_sandbox():
    uri = "ws://localhost:8080/ws"
    async with websockets.connect(uri) as ws:
        # 1. Wait for the Netty connection greeting
        greeting = await ws.recv()
        print(f"Server: {greeting}")

        # 2. Send raw Python code for the server to execute
        python_code = """
import sys
import math
print(f"Hello from the Sandbox! Math test: {math.sqrt(144)}")
print("Error test!", file=sys.stderr)
"""
        print("Sending code to sandbox...")
        await ws.send(python_code)

        # 3. Listen for the stdout/stderr stream coming back
        try:
            while True:
                response = await asyncio.wait_for(ws.recv(), timeout=2.0)
                print(f"Sandbox Output:\n{response}")
        except asyncio.TimeoutError:
            print("Execution finished.")

if __name__ == "__main__":
    asyncio.run(test_sandbox())