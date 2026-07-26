#ifndef AEGIS_MESH_BLE_MESH_H
#define AEGIS_MESH_BLE_MESH_H

#include <cstdint>

void buildSosPacket(
        uint32_t msg_id,
        const char* name,
        const char* condition,
        uint8_t* output_buffer);

bool processIncomingPacket(
        const uint8_t* input_buffer,
        uint8_t* output_buffer_to_forward);

#endif  // AEGIS_MESH_BLE_MESH_H
