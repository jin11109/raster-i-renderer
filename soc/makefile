# Generate Verilog and C++ simulation model
BUILD_DIR = generated
VERILATOR_DIR = verilator
OBJ_DIR = obj_dir
VERILATOR_FLAGS = --cc -j 4 --top-module Trinity -Mdir $(OBJ_DIR) --public -Wno-WIDTH

all: verilog verilate

verilog:
	sbt "runMain Trinity"

# Generate C++ model and compile it into a static library (.a)
verilate: verilog
	cd $(VERILATOR_DIR) && verilator $(VERILATOR_FLAGS) \
	../$(BUILD_DIR)/Trinity.sv sim_stubs.v \
	&& make -C $(OBJ_DIR) -f VTrinity.mk

sim: verilog
	cd $(VERILATOR_DIR) && verilator $(VERILATOR_FLAGS) --build --exe \
	../$(BUILD_DIR)/Trinity.sv sim_stubs.v sim.cpp \
	&& make -C $(OBJ_DIR) -f VTrinity.mk \
	&& $(OBJ_DIR)/VTrinity

clean:
	rm -rf project target $(BUILD_DIR) $(VERILATOR_DIR)/$(OBJ_DIR)