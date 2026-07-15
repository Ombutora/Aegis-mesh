#include <stdint.h>
#include <string.h>
#include <vector>
#include <android/log.h>

#define LOG_TAG "AegisMeshCPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

const int MAX_HOPS = 5; // Maximum multi-hop jumps

// 31 Byte strict payload structure
#pragma pack(push, 1)
struct MeshPayload {
    uint32_t message_id;    // Unique ID for the SOS
    uint8_t hop_count;      // Time-To-Live
    char name[10];          // e.g., "John"
    char condition[16];     // e.g., "Seizures"
};
#pragma pack(pop)

// Routing Table (keeps track of seen messages so we don't repeat them)
std::vector<uint32_t> seen_messages;

bool hasSeenMessage(uint32_t msg_id) {
    for (uint32_t id : seen_messages) {
        if (id == msg_id) return true;
    }
    return false;
}

// Function to construct a new SOS packet (Called when THIS phone triggers SOS)
extern "C" void buildSosPacket(uint32_t msg_id, const char* name, const char* condition, uint8_t* output_buffer) {
    MeshPayload payload = {0};
    payload.message_id = msg_id;
    payload.hop_count = 0; // 0 hops so far
    
    strncpy(payload.name, name, 9);
    strncpy(payload.condition, condition, 15);
    
    seen_messages.push_back(msg_id); // Remember our own message
    memcpy(output_buffer, &payload, sizeof(MeshPayload));
    
    LOGI("Built outgoing SOS Packet for %s. Condition: %s", payload.name, payload.condition);
}

// Function to handle incoming packets from other phones (Multi-hop logic)
// Returns true if the message should be rebroadcast (forwarded)
extern "C" bool processIncomingPacket(const uint8_t* input_buffer, uint8_t* output_buffer_to_forward) {
    MeshPayload incoming;
    memcpy(&incoming, input_buffer, sizeof(MeshPayload));

    if (hasSeenMessage(incoming.message_id)) {
        LOGI("Message %d already seen. Dropping to prevent loops.", incoming.message_id);
        return false; 
    }

    // Add to routing table
    seen_messages.push_back(incoming.message_id);
    LOGI("Received SOS from %s (Condition: %s) at Hop %d", incoming.name, incoming.condition, incoming.hop_count);

    // Multi-hop Check
    if (incoming.hop_count < MAX_HOPS) {
        incoming.hop_count++; // Increment hop count
        memcpy(output_buffer_to_forward, &incoming, sizeof(MeshPayload));
        LOGI("Forwarding message %d. New Hop Count: %d", incoming.message_id, incoming.hop_count);
        return true; // Tell Java to broadcast this new packet
    }

    LOGI("Max hops reached. Not forwarding.");
    return false;
}