// Original code (c) 2023 Alan Jian
// Licensed under MIT License
//
// Modifications (c) 2025 jin11109
// Licensed under MIT License

#pragma once

#include <cmath>
#include <cstdint>

#include <math/vec.hpp>
#include <utils/color.hpp>

#define TEXTURE_WIDTH 256
#define TEXTURE_HEIGHT 256

extern const uint32_t TEXTURE[TEXTURE_WIDTH * TEXTURE_HEIGHT];

inline RGB8 sample_texture(Vec2f uv) {
    int x = ceil((double)uv.x * (TEXTURE_WIDTH - 1));
    int y = ceil((double)uv.y * (TEXTURE_HEIGHT - 1));
    return RGB8::decode(TEXTURE[y * TEXTURE_WIDTH + x]);
}
