#pragma once
#include "../include/ConnectionHandler.h"
#include "../include/event.h"

struct GameStats {
    std::string game_name;
    std::string team_a_name;
    std::string team_b_name;
    std::map<std::string, std::string> general_stats;
    std::map<std::string, std::string> team_a_stats;
    std::map<std::string, std::string> team_b_stats;
    std::vector<Event> events;

    GameStats() : game_name(""), team_a_name(""), team_b_name(""), general_stats(), team_a_stats(), team_b_stats(), events() {}

    GameStats(std::string game_name, std::string team_a_name, std::string team_b_name)
        : game_name(game_name), team_a_name(team_a_name), team_b_name(team_b_name),
          general_stats(), team_a_stats(), team_b_stats(), events() {}
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
    std::mutex gameLock;

public:
    StompProtocol();
    std::vector<std::string> processInput(const std::string& line, ConnectionHandler& handler);
    bool processServerResponse(std::string& frame);
    std::vector<std::string> split(const std::string &str, char delimiter);
    void parseAndSaveGameMsg(const std::string& frame);
};
