#include <iomanip>
#include <iostream>

#include "VTrinity.h"
#include "verilated.h"

int err_cnt = 0;
void verify_pixel(int global_x, int global_y, uint32_t pixel_val) {
    uint32_t expected = 0xff00ff;

    if (pixel_val != expected) {
        if (err_cnt < 5) {
            std::cerr << "[ERROR] Mismatch at (" << global_x << ", " << global_y
                      << ") Got: 0x" << std::hex << pixel_val << " Exp: 0x"
                      << expected << std::dec << "\n";
            if (err_cnt == 4) {
                std::cerr << "[ERROR] ...\n";
            }
        }
        err_cnt++;
    }
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    VTrinity* top = new VTrinity;

    int total_cycles = 0;

    top->clock = 0;
    top->reset = 1;
    top->io_debug_displayVsync = 0;
    top->io_debug_idx = 0;

    // Wait for reset
    for (int i = 0; i < 50; i++) {
        top->clock = !top->clock;
        top->eval();
        top->clock = !top->clock;
        top->eval();
        total_cycles++;
    }
    top->reset = 0;

    // Start verification
    for (int frame = 0; frame < 4; frame++) {
        std::cout << "[INFO] Rendering Frame " << frame << "..." << std::endl;

        int timeout = 200000000;
        int cycles = 0;

        /* Wait util renderer draw a frame into frame buffer */
        while (!top->io_debug_graphicsDone && cycles < timeout) {
            top->clock = !top->clock; // Toggle Clock
            top->eval();              // Update Logic
            top->clock = !top->clock;
            top->eval();
            cycles++;
            total_cycles++;
        }
        if (cycles >= timeout) {
            std::cerr << "[ERROR] Timeout! Renderer did not finish."
                      << std::endl;
            break;
        }

        uint32_t base_addr = 0;
        if (top->Trinity__DOT__io_debug_graphicsFbId == 1) {
            base_addr = (1 << 20);
        }

        std::cout << "[INFO] Frame " << frame << " finished at cycle "
                  << total_cycles << std::endl;
        std::cout << "[INFO] Verifying data via IO interface..." << std::endl;

        /* Verify data framebuffer */
        const int WIDTH = 1024;
        const int HEIGHT = 768;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x += 4) {
                uint32_t word_idx = (y * WIDTH + x + base_addr) / 4;
                top->io_debug_idx = word_idx;

                top->eval();

                uint32_t p0 = top->io_debug_data[0];
                uint32_t p1 = top->io_debug_data[1];
                uint32_t p2 = top->io_debug_data[2];
                uint32_t p3 = top->io_debug_data[3];

                verify_pixel(x + 0, y, p0);
                verify_pixel(x + 1, y, p1);
                verify_pixel(x + 2, y, p2);
                verify_pixel(x + 3, y, p3);
            }
        }
        std::cout << "[INFO] Frame " << frame << " error count " << err_cnt
                  << "\n";
        std::cout << "[INFO] Frame " << frame << " verification done.\n";
        err_cnt = 0;

        /*
         * Pretending to be the valid signal of display component. Because we
         * disable display comonent temporarily, it need to play a role of
         * trigger enable renderer to start process next frame.
         */
        // Renderer Done && Display Vsync -> Swap Buffer -> Start New Frame
        top->io_debug_displayVsync = 1;

        for (int i = 0; i < 10; i++) {
            top->clock = !top->clock;
            top->eval();
            top->clock = !top->clock;
            top->eval();
            total_cycles++;
        }

        top->io_debug_displayVsync = 0;

        for (int i = 0; i < 10; i++) {
            top->clock = !top->clock;
            top->eval();
            top->clock = !top->clock;
            top->eval();
            total_cycles++;
        }
    }

    top->final();
    delete top;

    return 0;
}