import socket
import time

def test_subscribe():
    host = '127.0.0.1'
    port = 7777
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((host, port))
        print(f"✅ Connected to {host}:{port}")

        # 1. Send CONNECT
        connect_frame = "CONNECT\naccept-version:1.2\nhost:stomp.server\nlogin:guest\npasscode:guest\n\n\x00"
        sock.sendall(connect_frame.encode('utf-8'))
        print("📤 Sent CONNECT")

        # 2. Receive CONNECTED
        response = sock.recv(1024)
        print(f"📥 Received:\n{response.decode('utf-8', errors='replace')}")
        
        if b"CONNECTED" not in response:
            print("❌ Login failed. Stopping.")
            return

        # 3. Send SUBSCRIBE with Receipt
        # We ask for receipt-id: 77
        sub_frame = "SUBSCRIBE\ndestination:/topic/games\nid:123\nreceipt:77\n\n\x00"
        sock.sendall(sub_frame.encode('utf-8'))
        print("📤 Sent SUBSCRIBE (asking for receipt 77)...")

        # 4. Receive RECEIPT
        response = sock.recv(1024)
        print("-" * 20)
        print(f"📥 Received:\n{response.decode('utf-8', errors='replace')}")

        if b"RECEIPT" in response and b"77" in response:
            print("🏆 VICTORY: Server processed the subscription!")
        else:
            print("❌ Failed: Did not get the correct receipt.")

        sock.close()
        
    except Exception as e:
        print(f"❌ Error: {e}")

if __name__ == "__main__":
    test_subscribe()