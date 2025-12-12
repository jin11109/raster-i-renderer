// Original code (c) 2023 Alan Jian
// Licensed under MIT License
//
// Modifications (c) 2025 jin11109
// Licensed under MIT License

import chisel3._
import chisel3.util._

class Renderer extends Module {
  val io = IO(new Bundle {
    val done = Output(Bool())
    val fbId = Input(UInt(Fb.idWidth.W))
    // Uses the Flat Uppercase AXI interface to match Vram/Trinity
    val vram = Flipped(new WrAxiExtUpper(Vram.addrWidth, Vram.dataWidth))
  })

  val fbWriter = Module(new FbWriter)
  fbWriter.io.fbId := io.fbId

  /* Manually connect Flat AXI (io.vram) to Structured AXI (fbWriter) */
  /* Write Address Channel (AW) */
  io.vram.AWID    := fbWriter.io.vram.addr.bits.id
  io.vram.AWADDR  := fbWriter.io.vram.addr.bits.addr
  io.vram.AWLEN   := fbWriter.io.vram.addr.bits.len
  io.vram.AWSIZE  := fbWriter.io.vram.addr.bits.size
  io.vram.AWBURST := fbWriter.io.vram.addr.bits.burst
  io.vram.AWVALID := fbWriter.io.vram.addr.valid
  fbWriter.io.vram.addr.ready := io.vram.AWREADY

  // Drive defaults for AXI signals that FbWriter doesn't use
  io.vram.AWLOCK   := false.B
  io.vram.AWCACHE  := "b0011".U
  io.vram.AWPROT   := "b000".U
  io.vram.AWQOS    := 0.U
  io.vram.AWREGION := 0.U

  /* Write Data Channel (W) */
  io.vram.WDATA   := fbWriter.io.vram.data.bits.data
  io.vram.WSTRB   := fbWriter.io.vram.data.bits.strb
  io.vram.WLAST   := fbWriter.io.vram.data.bits.last
  io.vram.WVALID  := fbWriter.io.vram.data.valid
  fbWriter.io.vram.data.ready := io.vram.WREADY

  /* Write Response Channel (B) */
  fbWriter.io.vram.resp.bits.id   := io.vram.BID
  fbWriter.io.vram.resp.bits.resp := io.vram.BRESP
  fbWriter.io.vram.resp.valid     := io.vram.BVALID
  io.vram.BREADY := fbWriter.io.vram.resp.ready

  // Write data, Purple Pixel, as temporarily tesing way
  val purple = FbRGB(255.U, 0.U, 255.U)
  val purplePixels = VecInit(Seq.fill(Fb.nrBanks)(purple))

  // State Machine
  val sIdle :: sRunning :: Nil = Enum(2)
  // Start automatically
  val state = RegInit(sRunning)
  
  val fbIdReg = RegNext(io.fbId, 1.U)
  val start   = (io.fbId =/= fbIdReg)

  switch (state) {
    is (sIdle) {
      when (start) {
        state := sRunning
      }
    }
    is (sRunning) {
      // If don't use !RegNext(fbWriter.io.done), the current donesignal
      // remains at true(Level-sensitive), causing the second frame to read the
      // old High signal at the very beginning.
      when (fbWriter.io.done && !RegNext(fbWriter.io.done)) {
        state := sIdle
      }
    }
  }

  // Drive FbWriter request
  fbWriter.io.req.valid := (state === sRunning)
  fbWriter.io.req.bits.pix := purplePixels

  // Output done signal
  io.done := (state === sIdle)
}