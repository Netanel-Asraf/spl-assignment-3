#include "../include/StompProtocol.h"
#include "../include/event.h"
#include <iostream>
#include <fstream>
#include <sstream>

StompProtocol::StompProtocol() 
    : subscriptionCounter(0), 
      receiptCounter(0), 
      isConnected(false),
      shouldTerminate(false)
    {}

std::vector<std::string> StompProtocol::split(const std::string &str, char delimiter) {
    std::vector<std::string> tokens;
    std::string token;
    std::istringstream tokenStream(str);
    while (std::getline(tokenStream, token, delimiter)) {
        tokens.push_back(token);
    }
    return tokens;
}

std::vector<std::string> StompProtocol::processInput(const std::string& line, ConnectionHandler& handler) {
    std::vector<std::string> frames;
    std::vector<std::string> args = split(line, ' ');
    std::string command = args[0];

    if (command == "login") {
        std::string frame = "CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:" + args[2] + "\npasscode:" + args[3] + "\n\n\0";
        frames.push_back(frame);
    }
    else if (command == "join") {
        std::string topic = args[1];
        subscriptionCounter++;
        subscriptions[topic] = subscriptionCounter;
        std::string frame = "SUBSCRIBE\ndestination:" + topic + "\nid:" + std::to_string(subscriptionCounter) + "\nreceipt:" + std::to_string(receiptCounter++) + "\n\n\0";
        frames.push_back(frame);
    }
    else if (command == "exit") {
        std::string topic = args[1];
        int id = subscriptions[topic];
        receiptCounter++;
        std::string frame = "UNSUBSCRIBE\nid:" + std::to_string(id) + "\nreceipt:" + std::to_string(receiptCounter) + "\n\n\0";
        pendingReplies[receiptCounter] = "Exited channel " + topic;
        frames.push_back(frame);
    }
    else if (command == "logout") {
        receiptCounter++;
        std::string frame = "DISCONNECT\nreceipt:" + std::to_string(receiptCounter) + "\n\n\0";
        pendingReplies[receiptCounter] = "logout";
        frames.push_back(frame);
    }
    else if (command == "report") {
        std::string filename = args[1];
        names_and_events parsed_data;
        try {
            parsed_data = parseEventsFile(filename);
        } catch (std::exception& e) {
            std::cerr << "Error parsing file: " << e.what() << std::endl;
            return frames; // Return empty vector
        }
        for (const auto& event : parsed_data.events) {
            std::string frame = "SEND\n";
            frame += "destination:/" + parsed_data.team_a_name + "_" + parsed_data.team_b_name + "\n";
            frame += "\n"; // End of headers
            
            // Body (Strict formatting required by assignment)
            frame += "user: " + args[2] + "\n"; // Assuming args[2] is username
            frame += "team a: " + parsed_data.team_a_name + "\n";
            frame += "team b: " + parsed_data.team_b_name + "\n";
            frame += "event name: " + event.get_name() + "\n";
            frame += "time: " + std::to_string(event.get_time()) + "\n";
            frame += "general game updates:\n";
            
            // Iterate general updates map
            for (const auto& pair : event.get_game_updates()) {
                frame += pair.first + ":" + pair.second + "\n";
            }
            
            frame += "team a updates:\n";
            for (const auto& pair : event.get_team_a_updates()) {
                frame += pair.first + ":" + pair.second + "\n";
            }
            
            frame += "team b updates:\n";
            for (const auto& pair : event.get_team_b_updates()) {
                frame += pair.first + ":" + pair.second + "\n";
            }

            frame += "description:\n" + event.get_description() + "\n";
            frame += "\0"; // NULL TERMINATOR

            frames.push_back(frame);
        }
    }
    else if(command == "summary") {
        std::string game_name = args[1];
        std::string user = args[2];
        std::string file_path = args[3];

        if(games.find(game_name) == games.end()) {
            std::cerr << "Game not found in memory." << std::endl;
            return frames;
        }

        GameStats& game = games[game_name];
        
        std::ofstream file(file_path);
        if (!file.is_open()) {
            std::cerr << "Could not open file: " << file_path << std::endl;
            return frames;
        }

        file << game.team_a_name << " vs " << game.team_b_name << "\n";
        file << "Game stats:\n";
        file << "General stats:\n";
        for(const auto& p : game.general_stats) file << p.first << ":" << p.second << "\n";
        
        file << game.team_a_name << " stats:\n"; // You need to save team names in GameStats
        for(const auto& p : game.team_a_stats) file << p.first << ":" << p.second << "\n";
        
        file << game.team_b_name << " stats:\n";
        for(const auto& p : game.team_b_stats) file << p.first << ":" << p.second << "\n";

        file << "Game event reports:\n";
        // Usually required to print events chronologically
        for(const auto& event : game.events) {
            file << event.get_time() << " - " << event.get_name() << ":\n";
            file << event.get_description() << "\n\n";
        }

        file.close();
    }

    return frames;
}

bool StompProtocol::processServerResponse(std::string& frame) {
    std::vector<std::string> lines = split(frame, '\n');
    std::string command = lines[0];

    if (command == "CONNECTED") {
        std::cout << "Login successful." << std::endl;
    }
    else if (command == "RECEIPT") {
        int receiptId = -1;
        for (const auto& line : lines) {
            if (line.find("receipt-id:") == 0) {
                receiptId = std::stoi(line.substr(11));
                break; // Why?
            }
        }
        if (pendingReplies.find(receiptId) != pendingReplies.end()) {
            std::string action = pendingReplies[receiptId];
            if (action == "logout") {
                std::cout << "Logout successful. Terminating client." << std::endl;
                return false;
            } else {
                std::cout << action << std::endl;
            }
            pendingReplies.erase(receiptId);
        }
    }
    else if (command == "MESSAGE") {
        parseAndSaveGameMsg(frame);
        std::cout << "Received a message from server." << std::endl;
    }
    else if (command == "ERROR") {
        std::cout << "Error from server:\n" << frame << std::endl;
        return false;
    }

    return true;
}

void StompProtocol::parseAndSaveGameMsg(const std::string& frame) {
    std::string body = frame.substr(frame.find("\n\n") + 2);
    std::vector<std::string> lines = split(body, '\n');
    std::map<std::string, std::string> updates;
    std::string current_section = "";

    int time = 0;
    std::string team_a, team_b, event_name, description, reported_by;
    std::map<std::string, std::string> general_updates, a_updates, b_updates;

    for (const auto& line : lines) {
        if (line.empty()) continue;
        
        // Identify current section
        if (line == "general game updates:") { current_section = "general"; continue; }
        else if (line == "team a updates:") { current_section = "team_a"; continue; }
        else if (line == "team b updates:") { current_section = "team_b"; continue; }
        else if (line == "description:") { current_section = "description"; continue; }

        // Parse key:value
        size_t colon = line.find(':');
        if (current_section == "description") {
            description += line + "\n";
        } else if (colon != std::string::npos) {
            std::string key = line.substr(0, colon);
            std::string val = line.substr(colon + 1);
            
            if (current_section == "") {
                if (key == "user") reported_by = val;
                else if (key == "team a") team_a = val;
                else if (key == "team b") team_b = val;
                else if (key == "event name") event_name = val;
                else if (key == "time") time = std::stoi(val);
            } else if (current_section == "general") general_updates[key] = val;
            else if (current_section == "team_a") a_updates[key] = val;
            else if (current_section == "team_b") b_updates[key] = val;
        }

        std::string game_name = team_a + "_" + team_b;

        if (games.find(game_name) == games.end()) {
            GameStats gs;
            gs.game_name = game_name;
            gs.team_a_name = team_a;
            gs.team_b_name = team_b;
            games[game_name] = gs;
        }

        GameStats& game = games[game_name];

        for(auto& p : general_updates) game.general_stats[p.first] = p.second;
        for(auto& p : a_updates) game.team_a_stats[p.first] = p.second;
        for(auto& p : b_updates) game.team_b_stats[p.first] = p.second;

        Event evt(team_a, team_b, event_name, time, general_updates, a_updates, b_updates, description);
        // reported by
        game.events.push_back(evt);
    }