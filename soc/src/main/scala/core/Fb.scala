// Copyright (C) 2023 Alan Jian (alanjian85@outlook.com)
// SPDX-License-Identifier: MIT
//
// Modifications (c) 2025 jin11109
// Licensed under MIT License

import chisel3._
import chisel3.util._
import chisel3.experimental._

class Ddr3Ext extends Bundle {
  val addr    = Output(UInt(14.W))
  val ba      = Output(UInt(3.W))
  val cas_n   = Output(Bool())
  val ck_n    = Output(UInt(1.W))
  val ck_p    = Output(UInt(1.W))
  val cke     = Output(UInt(1.W))
  val cs_n    = Output(UInt(1.W))
  val dm      = Output(UInt(2.W))
  val dq      = Analog(16.W)
  val dqs_n   = Analog(2.W)
  val dqs_p   = Analog(2.W)
  val odt     = Output(UInt(1.W))
  val ras_n   = Output(Bool())
  val reset_n = Output(Bool())
  val we_n    = Output(Bool())
}

object Vram {
  val addrWidth = 32
  val dataWidth = 128

  val dataBytes = dataWidth / 8
  val beatsSize = Axi.size(dataBytes)

  val dataBytesWidth = log2Up(dataBytes)
  
  // Vram size 256 MB
  val depth = 256 * 1024 * 1024 / 16
}

class Vram extends Module {
  val io = IO(new Bundle {
    val axiGraphics   = new WrAxiExtUpper(Vram.addrWidth, Vram.dataWidth)
    val aclkGraphics  = Input(Clock())
    val arstnGraphics = Input(Reset())
    
    /* Temporarily disable display logic */
    // val axiDisplay   = Flipped(new RdAxi(Vram.addrWidth, Vram.dataWidth))
    // val aclkDisplay  = Input(Clock())
    // val arstnDisplay = Input(Reset())

    val ddr3 = new Ddr3Ext

    /* Debig interface */
    val debug_idx  = Input(UInt(32.W))
    val debug_data = Output(UInt(128.W))
  })

  val mem = Mem(Vram.depth, UInt(Vram.dataWidth.W))

  // Directly access to momory for debug
  io.debug_data := mem.read(io.debug_idx)

  /* AXI Status Registers */
  // Stores the current write address (auto-increments during Burst)
  val addrReg = RegInit(0.U(Vram.addrWidth.W))
  // Indicates if a Write Transaction is in progress (from receiving AW until WLAST)
  val busy    = RegInit(false.B)
  // Response
  val b_valid = RegInit(false.B)

  /* Address Channel (AW Channel) */
  // Only accept new address when not busy and response channel is idle
  io.axiGraphics.AWREADY := !busy && !b_valid
  
  when(io.axiGraphics.AWVALID && io.axiGraphics.AWREADY) {
    busy    := true.B
    addrReg := io.axiGraphics.AWADDR
  }

  /* Data Channel (W Channel) */
  // Ready to receive data as long as we are in busy state
  io.axiGraphics.WREADY := busy
  
  when(io.axiGraphics.WVALID && io.axiGraphics.WREADY) {
    /* 
     * AXI protocol uses Byte Addressing
     * Chisel Memstores data in units of words (a complete line of data).
     * mem definition means UInt(128.W) that each line stores 128 bits, which
     * means 1 word = 128 bits
     * So, convert the "Byte Address" into the "Word Index"
     *     qual to convert 8 bits index into 128 bits index 
     */
    mem.write(addrReg >> Vram.dataBytesWidth, io.axiGraphics.WDATA)
    
    // Auto-increment address (prepare for the next Burst data)
    addrReg := addrReg + (Vram.dataWidth / 8).U 

    // If this is the last data beat (WLAST), end busy state and prepare to
    // send response
    when(io.axiGraphics.WLAST) {
      busy    := false.B
      b_valid := true.B
    }
  }

  /* Response Channel (B Channel) */
  io.axiGraphics.BVALID := b_valid
  io.axiGraphics.BRESP  := 0.U // OKAY
  io.axiGraphics.BID    := 0.U 

  // When Master acknowledges the response, pull down BVALID to complete the transaction
  when(io.axiGraphics.BVALID && io.axiGraphics.BREADY) {
    b_valid := false.B
  }

  /* DDR3 Stub */
  io.ddr3.addr    := 0.U
  io.ddr3.ba      := 0.U
  io.ddr3.cas_n   := true.B
  io.ddr3.ck_n    := 0.U
  io.ddr3.ck_p    := 0.U
  io.ddr3.cke     := 0.U
  io.ddr3.cs_n    := true.B
  io.ddr3.dm      := 0.U
  io.ddr3.odt     := 0.U
  io.ddr3.ras_n   := true.B
  io.ddr3.reset_n := true.B
  io.ddr3.we_n    := true.B
}

object FbRGB extends RGBFactory(8, 8, 8)

object Fb {
  val idWidth   = 1
  val nrBanks   = Vram.dataWidth / FbRGB.alignedWidth
  val width     = (VgaTiming.width + nrBanks - 1) / nrBanks * nrBanks
  val height    = VgaTiming.height

  val addrWidth = log2Up(Fb.width * Fb.height)
  val lastLine  = width * (height - 1)

  val nrIndices = width / nrBanks
  val maxIdx    = nrIndices - 1
}

class FbSwapper extends Module {
  val io = IO(new Bundle {
    val displayVsync = Input(Bool())
    val graphicsDone = Input(Bool())
    val displayFbId  = Output(UInt(Fb.idWidth.W))
    val graphicsFbId = Output(UInt(Fb.idWidth.W))
  })

  val swapped      = RegInit(false.B)
  val displayFbId  = RegInit(0.U(Fb.idWidth.W))
  val graphicsFbId = RegInit(1.U(Fb.idWidth.W))
  io.displayFbId  := displayFbId
  io.graphicsFbId := graphicsFbId
  when (io.displayVsync && io.graphicsDone) {
    when (!swapped) {
      swapped      := true.B
      displayFbId  := ~displayFbId
      graphicsFbId := ~graphicsFbId
    }
  } .otherwise {
    swapped := false.B
  }
}

class FbRdRes extends Bundle {
  val idx = UInt(log2Up(Fb.nrIndices).W)
  val pix = Vec(Fb.nrBanks, FbRGB())
}

class FbReader extends Module {
  val io = IO(new Bundle {
    val vram  = new RdAxi(Vram.addrWidth, Vram.dataWidth)
    val fbId  = Input(UInt(Fb.idWidth.W))
    val req   = Input(Bool())
    val res   = Valid(new FbRdRes)
  })

  val addrValid = RegInit(true.B)
  val addr      = RegInit(0.U(Fb.addrWidth.W))
  io.vram.addr.valid      := addrValid
  io.vram.addr.bits.id    := DontCare
  io.vram.addr.bits.addr  := (io.fbId ## addr) << log2Up(FbRGB.nrBytes)
  io.vram.addr.bits.len   := Fb.maxIdx.U
  io.vram.addr.bits.size  := Vram.beatsSize
  io.vram.addr.bits.burst := Axi.Burst.incr
  when (io.req) {
    addrValid := true.B
    addr      := addr + Fb.width.U
    when (addr === Fb.lastLine.U) {
      addr := 0.U
    }
  }
  when (addrValid && io.vram.addr.ready) {
    addrValid := false.B
  }

  val idx = RegInit(0.U(log2Up(Fb.nrIndices).W))
  io.vram.data.bits.id := DontCare
  io.vram.data.ready   := true.B
  io.res.valid    := io.vram.data.valid
  io.res.bits.idx := idx
  io.res.bits.pix := VecInit(Seq.tabulate(Fb.nrBanks)(
    i => FbRGB.decode(io.vram.data.bits.data(
      (i + 1) * FbRGB.alignedWidth - 1,
      i * FbRGB.alignedWidth
    ))
  ))
  when (io.vram.data.valid) {
    idx := idx + 1.U
    when (idx === Fb.maxIdx.U) {
      idx  := 0.U
    }
  }
}

class FbWrReq extends Bundle {
  val pix = Vec(Fb.nrBanks, FbRGB())
}

class FbWriter extends Module {
  val io = IO(new Bundle {
    val vram = new WrAxi(Vram.addrWidth, Vram.dataWidth)
    val fbId = Input(UInt(Fb.idWidth.W))
    val req  = Flipped(Irrevocable(new FbWrReq))
    val done = Output(Bool())
  })

  /* Address Channel (AW Channel) */
  val addrValid = RegInit(false.B)
  val addr      = RegInit(0.U(Fb.addrWidth.W))
  val addrBegan = RegInit(false.B) // 用來控制是否允許發送地址
  val done      = RegInit(false.B)

  /* Data Channel (W Channel) */
  val idx      = RegInit(0.U(log2Up(Fb.nrIndices).W))
  val dataAddr = RegInit(0.U(Fb.addrWidth.W))
  val last     = idx === Fb.maxIdx.U

  /* Address channel logic */
  io.vram.addr.valid      := addrValid
  io.vram.addr.bits.id    := DontCare
  // Calculate physical address: Combine FrameBuffer ID with Offset, shifted by pixel size
  io.vram.addr.bits.addr  := (io.fbId ## addr) << log2Up(FbRGB.nrBytes)
  io.vram.addr.bits.len   := Fb.maxIdx.U
  io.vram.addr.bits.size  := Vram.beatsSize
  io.vram.addr.bits.burst := Axi.Burst.incr

  // Start Trigger: Only trigger if "not started" (!addrBegan) and a request exists
  when (io.req.valid && !addrBegan) {
    addrBegan := true.B
    addrValid := true.B
    done      := false.B
  }

  // Address handshake
  when (addrValid && io.vram.addr.ready) {
    addrValid := false.B
    addr      := addr + Fb.width.U
    when (addr === Fb.lastLine.U) {
      addr := 0.U
    }
  }

  /* Data channel logic */
  io.vram.data.valid     := io.req.valid
  io.vram.data.bits.data := io.req.bits.pix.reverse.map(_.encodeAligned()).reduce(_ ## _)
  io.vram.data.bits.strb := Fill(Vram.dataBytes, 1.U)
  io.vram.data.bits.last := last
  io.req.ready := io.vram.data.ready

  // Data handshake
  when (io.req.valid && io.vram.data.ready) {
    idx := idx + 1.U
    
    // End of Burst (Row Finished)
    when (last) {
      idx := 0.U
      
      dataAddr := dataAddr + Fb.width.U
      
      // Distinguish between "End of Frame" and "End of Row"
      when (dataAddr === Fb.lastLine.U) {
        // Frame Completed
        dataAddr := 0.U
        done     := true.B
        /*
         * IMPORTANT: Keep 'addrBegan' true here.
         * This "locks" the address logic, preventing the generation of the 
         * address for the next frame until the Renderer stops its request.
         */
        addrBegan := true.B 
      } .otherwise {
        // Just a Row Completed, allow the next row address to be sent
        addrBegan := false.B 
      }
    }
  }

  /*
   * Force a state reset when the Renderer enters Idle (req goes low)
   * This ensures that when Frame 1 starts, all states are clean.
   */
  when (!io.req.valid) {
    addrBegan := false.B
    done      := false.B
    addrValid := false.B
    addr      := 0.U
    dataAddr  := 0.U
    idx       := 0.U
  }
  
  io.done := done
  io.vram.resp.ready := true.B
}