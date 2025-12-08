# Makefile for simulation

# Download HLS arbitrary precision types
INCLUDE_DIR := include/types
HLS_MARKER := $(INCLUDE_DIR)/ap_fixed.h
HLS_URL := https://github.com/Xilinx/HLS_arbitrary_Precision_Types/archive/refs/heads/master.zip
TMP_DIR := tmp_hls

# Build configuration
SRC_DIR := src
OUT_DIR := build
EXE := sim
CXX := g++
CXXFLAGS := -std=c++17 -Iinclude -I$(INCLUDE_DIR) -Wno-unknown-pragmas -g
LDFLAGS := -lSDL2

# Find all cpp files recursively
SRCS := $(shell find $(SRC_DIR) -name "*.cpp")
OBJS := $(patsubst $(SRC_DIR)/%.cpp, $(OUT_DIR)/%.o, $(SRCS))

.PHONY: all download_types clean

all: download $(OUT_DIR)/$(EXE)

# Download HLS headers
download_types:
	@if [ -f "$(HLS_MARKER)" ]; then \
		echo "[INFO] HLS headers already present. Skipping download."; \
	else \
		echo "[INFO] Downloading Xilinx HLS Arbitrary Precision Types..."; \
		mkdir -p $(TMP_DIR); \
		wget -O $(TMP_DIR)/hls.zip $(HLS_URL); \
		unzip -q $(TMP_DIR)/hls.zip -d $(TMP_DIR); \
		mkdir -p $(INCLUDE_DIR); \
		cp -r $(TMP_DIR)/HLS_arbitrary_Precision_Types-master/include/* $(INCLUDE_DIR)/; \
		rm -rf $(TMP_DIR); \
		echo "[INFO] HLS headers downloaded into $(INCLUDE_DIR)"; \
	fi

$(OUT_DIR)/%.o: $(SRC_DIR)/%.cpp
	@mkdir -p $(dir $@)
	$(CXX) $(CXXFLAGS) -c $< -o $@

$(OUT_DIR)/$(EXE): $(OBJS)
	@mkdir -p $(OUT_DIR)
	$(CXX) $(CXXFLAGS) $^ -o $@ $(LDFLAGS)
	@echo "[INFO] Executable generated at $(OUT_DIR)/$(EXE)"

clean:
	@rm -rf $(OUT_DIR)
	@rm -rf $(INCLUDE_DIR)/*
	@echo "[INFO] Cleaned build directory and downloaded headers"
