#include "../include/StompProtocol.h"
#include "../include/event.h"
#include <iostream>
#include <fstream>
#include <sstream>
#include <algorithm> 

// --- HELPER: TRIM FUNCTION ---
std::string trim(const std::string& str) {
    size_t first = str.find_first_not_of(" \t\r\n");
    if (std::string::npos == first) {
        return "";
    }
    size_t last = str.find_last_not_of(" \t\r\n");
    return str.substr(first, (last - first + 1));
}

StompProtocol::StompProtocol() 
    : subscriptionCounter(0), receiptCounter(0), isConnected(false), shouldTerminate(false),
      activeUser(""), userPassword(""), subscriptions(), pendingReplies(), games(), gameLock() {}

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
    std::vector<std::string> args = split(trim(line), ' ');
    if (args.empty()) return frames;

    std::string command = args[0];

    if (command == "login") {
        activeUser = args[2];
        userPassword = args[3];
        std::string frame = "CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:" + activeUser + "\npasscode:" + userPassword + "\n\n\0";
        frames.push_back(frame);
    }
    else if (command == "join") {
        std::string topic = "/" + args[1]; 
        receiptCounter++; subscriptionCounter++;
        subscriptions[topic] = subscriptionCounter;
        pendingReplies[receiptCounter] = "Joined channel " + args[1]; 
        std::string frame = "SUBSCRIBE\ndestination:" + topic + "\nid:" + std::to_string(subscriptionCounter) + "\nreceipt:" + std::to_string(receiptCounter) + "\n\n\0";
        frames.push_back(frame);
    }
    else if (command == "exit") {
        std::string topic = "/" + args[1]; 
        if (subscriptions.find(topic) == subscriptions.end()) {
            std::cout << "You are not subscribed to " << topic << std::endl;
            return frames;
        }
        int id = subscriptions[topic];
        receiptCounter++;
        std::string frame = "UNSUBSCRIBE\nid:" + std::to_string(id) + "\nreceipt:" + std::to_string(receiptCounter) + "\n\n\0";
        pendingReplies[receiptCounter] = "Exited channel " + args[1];
        frames.push_back(frame);
        subscriptions.erase(topic); 
    }
    else if (command == "logout") {
        receiptCounter++;
        std::string frame = "DISCONNECT\nreceipt:" + std::to_string(receiptCounter) + "\n\n\0";
        pendingReplies[receiptCounter] = "logout";
        std::cout << "-> Sending logout frame.\n" << std::endl;
        frames.push_back(frame);
    }
    else if (command == "report") {
        std::string filename = args[1];
        names_and_events parsed_data;
        try {
            parsed_data = parseEventsFile(filename);
        } catch (std::exception& e) {
            std::cerr << "Error parsing file: " << e.what() << std::endl;
            return frames;
        }
        std::string game_name = parsed_data.team_a_name + "_" + parsed_data.team_b_name;
        std::string topic_name = "/" + game_name;

        if (subscriptions.find(topic_name) == subscriptions.end()) {
            std::cout << "Error: You are not subscribed to " << game_name << ". Please join the channel first!" << std::endl;
            return frames; 
        }
        for (const auto& event : parsed_data.events) {
            std::string frame = "SEND\ndestination:/" + parsed_data.team_a_name + "_" + parsed_data.team_b_name + "\n";
            frame += "filename:" + filename + "\n\n"; 
            frame += "user:" + activeUser + "\n";
            frame += "team a:" + parsed_data.team_a_name + "\n";
            frame += "team b:" + parsed_data.team_b_name + "\n";
            frame += "event name:" + event.get_name() + "\n";
            frame += "time:" + std::to_string(event.get_time()) + "\n";
            frame += "general game updates:\n";
            for (const auto& pair : event.get_game_updates()) frame += pair.first + ":" + pair.second + "\n";
            frame += "team a updates:\n";
            for (const auto& pair : event.get_team_a_updates()) frame += pair.first + ":" + pair.second + "\n";
            frame += "team b updates:\n";
            for (const auto& pair : event.get_team_b_updates()) frame += pair.first + ":" + pair.second + "\n";
            frame += "description:\n" + event.get_description() + "\n\0";
            frames.push_back(frame);
        }
    }
    else if(command == "summary") {
        std::string game_name = args[1];
        std::string user_filter = args[2];
        std::string file_path = args[3];

        std::lock_guard<std::mutex> lock(gameLock); 

        if(games.find(game_name) == games.end()) {
            std::cerr << "Error: Game '" << game_name << "' not found in memory." << std::endl;
            std::cerr << "Available games:" << std::endl;
            for(const auto& pair : games) {
                std::cerr << " - '" << pair.first << "'" << std::endl;
            }
            return frames;
        }

        GameStats& game = games[game_name];
        std::vector<Event> summary_events;
        for (const auto& evt : game.events) {
            if (evt.get_reported_by() == user_filter) {
                summary_events.push_back(evt);
            }
        }
        std::sort(summary_events.begin(), summary_events.end(), [](const Event& a, const Event& b) {
            return a.get_time() < b.get_time();
        });
        std::ofstream file(file_path);
        if (!file.is_open()) {
            std::cerr << "Could not open file: " << file_path << std::endl;
            return frames;
        }
        file << game.team_a_name << " vs " << game.team_b_name << "\n";
        file << "Game stats:\n";
        file << "General stats:\n";
        for(const auto& p : game.general_stats) file << p.first << ":" << p.second << "\n";
        file << game.team_a_name << " stats:\n"; 
        for(const auto& p : game.team_a_stats) file << p.first << ":" << p.second << "\n";
        file << game.team_b_name << " stats:\n";
        for(const auto& p : game.team_b_stats) file << p.first << ":" << p.second << "\n";
        file << "Game event reports:\n";
        for(const auto& event : summary_events) {
            file << event.get_time() << " - " << event.get_name() << ":\n";
            file << event.get_description() << "\n\n";
        }
        file.close();
        std::cout << "Summary created at " << file_path << std::endl;
    }
    return frames;
}

bool StompProtocol::processServerResponse(std::string& frame) {
    std::vector<std::string> lines = split(frame, '\n');
    
    // FIX IS HERE: We MUST trim the command!
    std::string command = trim(lines[0]); 

    if (command == "CONNECTED") {
        std::cout << "Login successful." << std::endl;
        isConnected = true;
    }
    else if (command == "RECEIPT") {
        int receiptId = -1;
        for (const auto& line : lines) {
            if (line.find("receipt-id:") == 0) {
                receiptId = std::stoi(line.substr(11));
                break; 
            }
        }
        if (pendingReplies.find(receiptId) != pendingReplies.end()) {
            std::string action = pendingReplies[receiptId];
            if (action == "logout") {
                std::cout << "Logout successful. Terminating client." << std::endl;
                
                isConnected = false;
                shouldTerminate = true;
                
                subscriptions.clear();
                games.clear();
                activeUser = "";
                userPassword = "";
                
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
        if(frame.find("User already logged in") != std::string::npos) {
            shouldTerminate = true;
        }
        return false;
    }
    return true;
}

void StompProtocol::parseAndSaveGameMsg(const std::string& frame) {
    size_t bodyStart = frame.find("\n\n");
    if (bodyStart == std::string::npos) return; 
    std::string body = frame.substr(bodyStart + 2);
    
    std::vector<std::string> lines = split(body, '\n');
    std::string current_section = "";
    int time = 0;
    std::string team_a, team_b, event_name, description, reported_by = "";
    std::map<std::string, std::string> general_updates, a_updates, b_updates;

    // DEBUG: Prove the function is running
    std::cout << "[DEBUG] Processing Body Lines: " << lines.size() << std::endl;

    for (const auto& line : lines) {
        if (line.empty() || line == "\0") continue;
        std::string cleanLine = trim(line); 
        if (cleanLine.empty()) continue;

        if (cleanLine == "general game updates:") { current_section = "general"; continue; }
        else if (cleanLine == "team a updates:") { current_section = "team_a"; continue; }
        else if (cleanLine == "team b updates:") { current_section = "team_b"; continue; }
        else if (cleanLine == "description:") { current_section = "description"; continue; }

        size_t colon = cleanLine.find(':');
        if (current_section == "description") description += line + "\n"; 
        else if (colon != std::string::npos) {
            std::string key = trim(cleanLine.substr(0, colon));
            std::string value = trim(cleanLine.substr(colon + 1));
            
            if (current_section == "") {
                if (key == "user") reported_by = value;
                else if (key == "team a") team_a = value;
                else if (key == "team b") team_b = value;
                else if (key == "event name") event_name = value;
                else if (key == "time") time = std::stoi(value);
            } 
            else if (current_section == "general") general_updates[key] = value;
            else if (current_section == "team a") a_updates[key] = value;
            else if (current_section == "team b") b_updates[key] = value;
        }
    }

    if (team_a.empty() || team_b.empty()) {
        std::cout << "[DEBUG] ERROR: Could not find team names!" << std::endl;
        return; 
    }

    std::string game_name = team_a + "_" + team_b;
    std::cout << "[DEBUG] Saving game: '" << game_name << "'" << std::endl;

    std::lock_guard<std::mutex> lock(gameLock); 
    if (games.find(game_name) == games.end()) {
        GameStats gs;
        gs.game_name = game_name;
        gs.team_a_name = team_a;
        gs.team_b_name = team_b;
        games[game_name] = gs;
    }
    GameStats& game = games[game_name];
    for(auto& pair : general_updates) game.general_stats[pair.first] = pair.second;
    for(auto& pair : a_updates) game.team_a_stats[pair.first] = pair.second;
    for(auto& pair : b_updates) game.team_b_stats[pair.first] = pair.second;
    Event evt(team_a, team_b, event_name, time, general_updates, a_updates, b_updates, description, reported_by);
    game.events.push_back(evt);
}