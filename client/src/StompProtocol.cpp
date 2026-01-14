#include "../include/StompProtocol.h"
#include "../include/event.h"
#include <iostream>
#include <fstream>
#include <sstream>

std::vector<std::string> StompProtocol::split(const std::string &str, char delimiter) {
    std::vector<std::string> tokens;
    std::string token;
    std::istringstream tokenStream(str);
    while (std::getline(tokenStream, token, delimiter)) {
        tokens.push_back(token);
    }
    return tokens;
}