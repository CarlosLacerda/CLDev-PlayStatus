cmake_minimum_required(VERSION 3.22.1)

project("cldev_presence")

# Biblioteca oficial do Discord via Android Prefab
find_package(discord_partner_sdk REQUIRED CONFIG)

# Biblioteca nativa do nosso aplicativo
add_library(cldev_presence SHARED
        src/main/cpp/native-lib.cpp
)

target_link_libraries(cldev_presence
        discord_partner_sdk::discord_partner_sdk
        log
)