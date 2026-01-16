import socket
import threading
import time

def read_responses(sock, name):
    """Helper to listen for messages continuously"""
    sock.settimeout(2) # Don't wait forever
    try:
        while True:
            data = sock.recv(4096)
            if not data:
                break
            print(f"[{name}] 📥 RECEIVED:\n{data.decode('utf-8', errors='replace')}\n")
    except socket.timeout:
        pass # Expected when no more messages
    except Exception as e:
        print(f"[{name}] ❌ Error reading: {e}")

def run_test():
    host = '127.0.0.1'
    port = 7777

    print("--- STARTING MASTER TEST ---")

    # --- ALICE CONNECTS ---
    print("\n👩 ALICE: Connecting...")
    alice = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    alice.connect((host, port))
    alice.sendall(b"CONNECT\naccept-version:1.2\nhost:stomp\nlogin:alice\npasscode:alice\n\n\x00")
    read_responses(alice, "Alice") # Read CONNECTED frame

    # --- BOB CONNECTS ---
    print("\n👨 BOB: Connecting...")
    bob = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    bob.connect((host, port))
    bob.sendall(b"CONNECT\naccept-version:1.2\nhost:stomp\nlogin:bob\npasscode:bob\n\n\x00")
    read_responses(bob, "Bob") # Read CONNECTED frame

    # --- ALICE SUBSCRIBES ---
    print("\n👩 ALICE: Subscribing to /topic/games...")
    alice.sendall(b"SUBSCRIBE\ndestination:/topic/games\nid:77\nreceipt:100\n\n\x00")
    read_responses(alice, "Alice") # Read RECEIPT

    # --- BOB SUBSCRIBES (REQUIRED TO SEND) ---
    print("\n👨 BOB: Subscribing to /topic/games...")
    bob.sendall(b"SUBSCRIBE\ndestination:/topic/games\nid:99\nreceipt:bob_sub\n\n\x00")
    read_responses(bob, "Bob") # Read RECEIPT

    # --- BOB SENDS MESSAGE 1 ---
    print("\n👨 BOB: Sending 'Goal for Brazil!'...")
    bob.sendall(b"SEND\ndestination:/topic/games\n\nGoal for Brazil!\x00")
    time.sleep(0.5)

    # Check if Alice got the message
    print("\n👀 CHECKING ALICE'S INBOX (Expect Message):")
    read_responses(alice, "Alice")

    # --- ALICE UNSUBSCRIBES ---
    print("\n👩 ALICE: Unsubscribing...")
    alice.sendall(b"UNSUBSCRIBE\nid:77\nreceipt:101\n\n\x00")
    read_responses(alice, "Alice") # Read RECEIPT

    # --- BOB SENDS MESSAGE 2 (Alice shouldn't get this) ---
    print("\n👨 BOB: Sending 'Goal for Germany!' (Alice should ignore this)...")
    bob.sendall(b"SEND\ndestination:/topic/games\n\nGoal for Germany!\x00")
    time.sleep(0.5)

    print("\n👀 CHECKING ALICE'S INBOX (Should be EMPTY or Timeout):")
    read_responses(alice, "Alice")

    # --- DISCONNECT ---
    print("\n👋 Disconnecting everyone...")
    alice.sendall(b"DISCONNECT\nreceipt:bye_alice\n\n\x00")
    bob.sendall(b"DISCONNECT\nreceipt:bye_bob\n\n\x00")
    
    read_responses(alice, "Alice")
    read_responses(bob, "Bob")

    alice.close()
    bob.close()
    print("\n--- TEST FINISHED ---")

if __name__ == "__main__":
    run_test()