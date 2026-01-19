#include <iostream>
#include <thread>
#include <vector>
#include <string>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"

bool parseHostAndPort(const std::string& input, std::string& host, short& port) {
    size_t colonPos = input.find(':');
    if (colonPos == std::string::npos)
		return false;
    
    host = input.substr(0, colonPos);
    try {
        port = std::stoi(input.substr(colonPos + 1));
    } catch (...) {
        return false;
    }
    return true;
}

int main(int argc, char *argv[]) {
	// TODO: implement the STOMP client

	// Pointers for dynamic management
    ConnectionHandler* connectionHandler = nullptr;
    std::thread* socketThread = nullptr;
    
    StompProtocol protocol;
    bool isConnected = false;	

	while (true)
	{
		const short bufsize = 1024;
        char buf[bufsize];
        std::cin.getline(buf, bufsize);
        std::string line(buf);

		if(!isConnected)
		{
			std::vector<std::string> parts = protocol.split(line, ' ');
			if(parts[0] == "login")
			{
				if(parts.size() < 4)
				{
					std::cout << "Usage: login {host:port} {user} {password}\n" << std::endl;
					continue;
				}

				std::string host;
				short port;
				if(!parseHostAndPort(parts[1], host, port))
				{
					std::cout << "Invalid host:port format.\n" << std::endl;
					continue;
				}

				connectionHandler = new ConnectionHandler(host, port);

				if(!connectionHandler->connect())
				{
					std::cout << "Could not connect to server.\n" << std::endl;
					delete connectionHandler;
					connectionHandler = nullptr;
					continue;
				}

				isConnected = true;
				std::cout << "-> Connected to server.\n" << std::endl;

				socketThread = new std::thread([connectionHandler, &protocol, &isConnected]() {
					std::cout << "-> Socket thread started.\n" << std::endl;
                    while (isConnected) {
                        std::string answer;
                        if (!connectionHandler->getFrameAscii(answer, '\0')) {
                            std::cout << "Disconnected from server.\n" << std::endl;
                            isConnected = false;
                            break;
                        }

						std::cout << "-> Received message from server.\n" << std::endl;
                        bool shouldContinue = protocol.processServerResponse(answer);
                        if (!shouldContinue) {
                            isConnected = false;
                            break;
                        }
                    }
                });

				// Send frame
				std::cout << "-> Sending login frame.\n" << std::endl;
				std::vector<std::string> frames = protocol.processInput(line, *connectionHandler);
				//for(auto& frame : frames) connectionHandler->sendLine(frame);
				for(auto& frame : frames) {
    				// FIX: Send with null delimiter so the server sees the end of the frame
					connectionHandler->sendFrameAscii(frame, '\0'); 
				}
			}
			else
			{
				std::cout << "You need to login first.\n" << std::endl;
			}
		}
		else
		{
			// 1. Handle Double Login Check (Required by PDF)
			std::string command = line.substr(0, line.find(' '));
			if (command == "login") {
				std::cout << "The client is already logged in, log out before trying again" << std::endl;
				continue;
			}

			// 2. Send Frame with Null Byte Fix
			std::vector<std::string> frames = protocol.processInput(line, *connectionHandler);
			for(auto& frame : frames) {
				// FIX: Use sendFrameAscii with '\0' to ensure server sees end of frame
				if (!connectionHandler->sendFrameAscii(frame, '\0')) {
					std::cout << "Error sending message. Disconnecting.\n" << std::endl;
					isConnected = false;
					break;
				}
			}
		}

		if (!isConnected && connectionHandler != nullptr) {
            // Join the thread to ensure it's finished
            if (socketThread && socketThread->joinable()) {
                socketThread->join();
                delete socketThread;
                socketThread = nullptr;
            }
			
            // Close connection
            connectionHandler->close();
            delete connectionHandler;
            connectionHandler = nullptr;
		}
	}
	return 0;
}

