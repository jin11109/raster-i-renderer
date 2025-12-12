// Copyright (C) 2023 Alan Jian (alanjian85@outlook.com)
// SPDX-License-Identifier: MIT
//
// Modifications (c) 2025 jin11109
// Licensed under MIT License

import chisel3._

class Trinity extends Module {
  val io = IO(new Bundle {
    val ddr3 = new Ddr3Ext
    // val vga  = new VgaExt

    /* Debug interface */
    val debug_idx          = Input(UInt(32.W))
    val debug_data         = Output(UInt(128.W))
    val debug_graphicsFbId = Output(UInt(Fb.idWidth.W))
    val debug_graphicsDone = Output(Bool())
    // Temporarily diable dispaly component and play the role of this during simualtion
    val debug_displayVsync = Input(Bool())
  })

  val clkWiz = Module(new ClkWiz)
  val vram   = Module(new Vram)
  io.ddr3 <> vram.io.ddr3

  val fbSwapper    = Module(new FbSwapper)
  val displayVsync = WireInit(false.B)
  val graphicsDone = Wire(Bool())
   
  /* Debug */
  displayVsync := io.debug_displayVsync
  io.debug_graphicsFbId := RegNext(RegNext(fbSwapper.io.graphicsFbId))
  io.debug_graphicsDone := graphicsDone
  // Connect debug interface to Vram
  vram.io.debug_idx := io.debug_idx
  io.debug_data     := vram.io.debug_data

  fbSwapper.io.displayVsync := RegNext(RegNext(displayVsync))
  fbSwapper.io.graphicsDone := RegNext(RegNext(graphicsDone))

  // val displaySysRst = Module(new ProcSysRst)
  // displaySysRst.clock  := clkWiz.io.clkDisplay
  // vram.io.aclkDisplay  := clkWiz.io.clkDisplay
  // vram.io.arstnDisplay := displaySysRst.io.periArstn
  // withClockAndReset(clkWiz.io.clkDisplay, displaySysRst.io.periRst) {
  //   val display = Module(new Display)
  //   vram.io.axiDisplay <> display.io.vram
  //   io.vga             <> display.io.vga
  //   display.io.fbId := RegNext(RegNext(fbSwapper.io.displayFbId))
  //   displayVsync    := RegNext(display.io.vga.vsync === VgaTiming.polarity.B, false.B)
  // }

  // Renderer
  val graphicsSysRst = Module(new ProcSysRst)
  graphicsSysRst.clock  := clkWiz.io.clkGraphics
  // Keep them at the same frequency so they can transmit synchronously
  vram.io.aclkGraphics  := clkWiz.io.clkGraphics
  // Allows them to start or restart simultaneously, avoiding a crash.
  vram.io.arstnGraphics := graphicsSysRst.io.periArstn
  withClockAndReset(clkWiz.io.clkGraphics, graphicsSysRst.io.periRst) {
    val renderer = Module(new Renderer)
    vram.io.axiGraphics <> renderer.io.vram
    // 2-Stage Synchronizer to avoid metastability
    renderer.io.fbId := RegNext(RegNext(fbSwapper.io.graphicsFbId))
    graphicsDone     := RegNext(renderer.io.done, false.B)
  }
}

object Trinity extends App {
  emitVerilog(new Trinity, Array("--target-dir", "generated"))
}
