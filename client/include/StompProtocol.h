#pragma once

#include "../include/ConnectionHandler.h"

// Structs used in client memory
struct Event {
    std::string team_a_name;
    std::string team_b_name;
    std::string event_name;
    int time;
    std::map<std::string, std::string> general_game_updates;
    std::map<std::string, std::string> team_a_updates;
    std::map<std::string, std::string> team_b_updates;
    std::string description;
};

struct GameStats {
    std::string game_name;
    std::string team_a_name;
    std::string team_b_name;
    std::map<std::string, std::string> general_stats;
    std::map<std::string, std::string> team_a_stats;
    std::map<std::string, std::string> team_b_stats;
    std::vector<Event> events;
};
// TODO: implement the STOMP protocol
class StompProtocol
{
private:
    int subscriptionCounter;
    int receiptCounter;
    bool isConnected;
    bool shouldTerminate;

    std::string activeUser;
    std::string userPassword;

    std::map<std::string, int> subscriptions;
    std::map<int, std::string> pendingReplies;
    std::map<std::string, GameStats> games;

public:
    StompProtocol();
    std::vector<std::string> processInput(const std::string& line, ConnectionHandler& handler);
    bool processServerResponse(std::string& frame);
    std::vector<std::string> split(const std::string &str, char delimiter);
    void parseAndSaveGameMsg(const std::string& frame);
};
