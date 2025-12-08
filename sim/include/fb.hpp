// Original code (c) 2023 Alan Jian
// Licensed under MIT License

#pragma once

#include <ap_int.h>
#include <cstdint>

#ifdef __SYNTHESIS__
#include <hls_burst_maxi.h>
#endif

#include <math/vec.hpp>

#define FB_WIDTH 1024
#define FB_HEIGHT 768
#define FB_SAMPLES_PER_PIXEL 4
#define FB_ID_SHIFT 18

using fb_id_t = ap_uint<1>;

#define FB_TILE_WIDTH 64
#define FB_TILE_HEIGHT 32

#ifdef __SYNTHESIS__
void fb_write_tile(Vec2i pos, const uint32_t *tile);
void fb_flush_tiles(hls::burst_maxi<ap_uint<128>> vram, fb_id_t fb_id,
                    int line);
#endif
