#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <string.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "AegisWiFiDirectCPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

const int BUFFER_SIZE = 4096;

// ==========================================
// SERVER: Listen for incoming profile requests
// ==========================================
extern "C" std::string startProfileServer(int port) {
    int server_fd, new_socket;
    struct sockaddr_in address;
    int opt = 1;
    int addrlen = sizeof(address);
    char buffer[BUFFER_SIZE] = {0};

    // 1. Create socket file descriptor
    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
        LOGE("Socket creation failed");
        return "";
    }

    // 2. Attach socket to the port
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt))) {
        LOGE("setsockopt failed");
        close(server_fd);
        return "";
    }

    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(port);

    // 3. Bind
    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0) {
        LOGE("Bind failed");
        close(server_fd);
        return "";
    }

    // 4. Listen
    if (listen(server_fd, 3) < 0) {
        LOGE("Listen failed");
        close(server_fd);
        return "";
    }

    LOGI("Wi-Fi Direct Server listening on port %d...", port);

    // 5. Accept connection (This blocks until someone connects!)
    if ((new_socket = accept(server_fd, (struct sockaddr *)&address, (socklen_t*)&addrlen)) < 0) {
        LOGE("Accept failed");
        close(server_fd);
        return "";
    }

    // 6. Read the incoming payload (e.g., Responder saying "SEND_PROFILE")
    read(new_socket, buffer, BUFFER_SIZE);
    LOGI("Received message from Responder: %s", buffer);

    std::string received_data(buffer);

    close(new_socket);
    close(server_fd);

    return received_data;
}


// ==========================================
// CLIENT: Send our full profile to an IP
// ==========================================
extern "C" bool sendFullProfile(const char* target_ip, int port, const char* profile_json) {
    int sock = 0;
    struct sockaddr_in serv_addr;

    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        LOGE("Socket creation error");
        return false;
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);

    // Convert IPv4 address from text to binary form
    if (inet_pton(AF_INET, target_ip, &serv_addr.sin_addr) <= 0) {
        LOGE("Invalid address / Address not supported: %s", target_ip);
        close(sock);
        return false;
    }

    LOGI("Connecting to Responder at %s:%d...", target_ip, port);

    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        LOGE("Connection Failed");
        close(sock);
        return false;
    }

    // Send the JSON profile over the TCP socket
    send(sock, profile_json, strlen(profile_json), 0);
    LOGI("Full profile sent successfully to %s", target_ip);

    close(sock);
    return true;
}