// Loads the recompiled game (st_game.dll / libst_game.so, built from the player's ROM) and connects it to the runtime.
#pragma once

#include <filesystem>
#include <string>

#include "ntr/runtime.hpp"

namespace st {

// The module's file name, looked for next to the program.
std::filesystem::path default_module_path();

// Loads the module, gives it the runtime's services and Spirit Tracks's hooks, and fills `code` with its
// function and overlay tables, and `rom_sha1` with the SHA-1 of the ROM it was recompiled from. False with `error` set
// if that fails. The module stays loaded until the program exits.
bool load_game_module(const std::filesystem::path& path, ntr::GameCode& code, std::string& rom_sha1, std::string& error);

}  // namespace st
