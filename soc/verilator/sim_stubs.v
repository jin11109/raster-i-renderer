module clk_wiz (
    input  wire clk,
    output wire clk_graphics,
    output wire clk_display
);
    assign clk_graphics = clk;
    assign clk_display  = clk;
endmodule

module proc_sys_rst (
    input  wire slowest_sync_clk,
    input  wire ext_reset_in,
    input  wire aux_reset_in,
    input  wire mb_debug_sys_rst,
    input  wire dcm_locked,
    output wire mb_reset,
    output wire bus_struct_reset,
    output wire peripheral_reset,
    output wire interconnect_aresetn,
    output wire peripheral_aresetn
);
    assign mb_reset           = ext_reset_in;
    assign bus_struct_reset   = ext_reset_in;
    assign peripheral_reset   = ext_reset_in;
    
    assign interconnect_aresetn = ~ext_reset_in;
    assign peripheral_aresetn   = ~ext_reset_in;

endmodule