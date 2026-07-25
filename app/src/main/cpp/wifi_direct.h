#ifndef AEGIS_MESH_WIFI_DIRECT_H
#define AEGIS_MESH_WIFI_DIRECT_H

#include <string>

std::string startProfileServer(int port);
bool sendFullProfile(const char* target_ip, int port, const char* profile_json);

#endif  // AEGIS_MESH_WIFI_DIRECT_H
