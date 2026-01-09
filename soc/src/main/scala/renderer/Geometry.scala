import chisel3._
import chisel3.util._
import implicits._

class Geometry extends Module {
  val io = IO(new Bundle {
    val angle = Input(UInt(9.W))
    val done = Output(Bool())
    // Internediate buffer memory Write Ports
    val vtxWritePort  = new MemWriter(new ScreenVertex, 2048)
    val posWritePort  = new MemWriter(new TransFormedPos, 2048)
    val normWritePort = new MemWriter(new TransFormedNorm, 4096)
    val bbWritePort   = new MemWriter(new BoundingBox, 4096)
    // Internediate buffer memory Read Ports
    val VtxReadPort =  new MemReader(new ScreenVertex, 2048)
  })

  // Rom data
  /* TODO: Move dataloader to renderer.scala */
  val dataloader = Module(new DataLoader)
  // State machine
  val sVtx :: sNorm :: sIdx :: sDone :: Nil = Enum(4)
  val state = RegInit(sVtx)
  val done = RegInit(false.B)
  io.done := done
  
  // Math signals
  dataloader.io.math.angle := io.angle
  val sine   = RegNext(dataloader.io.math.sine)
  val cosine = RegNext(dataloader.io.math.cosine)

  // Submodules
  val procVtx  = Module(new PreprocVertices)
  val procNorm = Module(new PreprocNormals)
  val procIdx  = Module(new PreprocTriangles)

  // PreprocVertices Defaults
  procVtx.io.sine    := sine
  procVtx.io.cosine  := cosine
  procVtx.io.vtxData := dataloader.io.geo.vtxData
  procVtx.io.start   := false.B
 
  // PreprocNormals Defaults
  procNorm.io.sine     := sine
  procNorm.io.cosine   := cosine
  procNorm.io.normData := dataloader.io.geo.normData
  procNorm.io.start    := false.B

  // PreprocTriangles Defaults
  procIdx.io.idxData := dataloader.io.geo.idxData
  procIdx.io.VtxReadPort.resp.valid := io.VtxReadPort.resp.valid
  procIdx.io.VtxReadPort.resp.bits  := io.VtxReadPort.resp.bits
  procIdx.io.start   := false.B
  
  // Disable read request to processed vertex buffer  
  procIdx.io.VtxReadPort.req.ready  := false.B

  // Geometry Outputs Defaults
  io.vtxWritePort.valid := false.B;  io.vtxWritePort.index := 0.U;  io.vtxWritePort.data := DontCare
  io.posWritePort.valid := false.B;  io.posWritePort.index := 0.U;  io.posWritePort.data := DontCare
  io.normWritePort.valid := false.B; io.normWritePort.index := 0.U; io.normWritePort.data := DontCare
  io.bbWritePort.valid := false.B;   io.bbWritePort.index := 0.U;   io.bbWritePort.data := DontCare
  
  io.VtxReadPort.req.valid := false.B; io.VtxReadPort.req.bits := 0.U

  // DataLoader Defaults
  dataloader.io.geo.vtxAddr  := 0.U
  dataloader.io.geo.normAddr := 0.U
  dataloader.io.geo.idxAddr  := 0.U

  // State Machine
  switch (state) {
    is (sVtx) {
      procVtx.io.start := true.B
      dataloader.io.geo.vtxAddr := procVtx.io.vtxAddr
      
      io.vtxWritePort <> procVtx.io.vtxWritePort
      io.posWritePort <> procVtx.io.posWritePort
      
      when (procVtx.io.done) {
        state := sNorm 
        procVtx.io.start := false.B
      }
    }
    is (sNorm) {
      dataloader.io.geo.normAddr := procNorm.io.normAddr
      procNorm.io.start := true.B
      
      io.normWritePort <> procNorm.io.normWritePort 
      
      when (procNorm.io.done) {
        procNorm.io.start := false.B
        state := sIdx 
      }
    }
    is (sIdx) {
      dataloader.io.geo.idxAddr := procIdx.io.idxAddr
      procIdx.io.start := true.B

      io.bbWritePort <> procIdx.io.bbWritePort
      io.VtxReadPort <> procIdx.io.VtxReadPort
      
      when (procIdx.io.done) {
        state := sDone 
        procIdx.io.start := false.B
      }
    }
    is (sDone) { done := true.B }
  }
}

class PreprocVertices extends Module {
  val io = IO(new Bundle {
    val done   = Output(Bool())
    val start  = Input(Bool())

    /* Rom data form dataloader */
    val sine   = Input(Fixed24_13())
    val cosine = Input(Fixed24_13())
    val vtxAddr = Output(UInt(32.W))
    val vtxData = Input(NumVec(3, Fixed24_13()))

    /* Write out port to intermediate memory buffer */
    val vtxWritePort = new MemWriter(new ScreenVertex, 2048)
    val posWritePort = new MemWriter(new TransFormedPos, 2048)
  })

  val rIdx = RegInit(0.U(32.W))
  val wIdx = RegNext(rIdx)
  
  val axis   = NumVec(Fixed24_13(0.U), Fixed24_13(1.U), Fixed24_13(0.U))
  val vtxWritePort = Wire(new ScreenVertex)

  io.vtxAddr := rIdx
  rIdx := rIdx + 1.U 
  
  io.vtxWritePort.valid := false.B; io.vtxWritePort.index := wIdx; io.vtxWritePort.data := vtxWritePort
  io.posWritePort.valid := false.B; io.posWritePort.index := wIdx; io.posWritePort.data := DontCare
  
  /* Rotate vec */
  val vc = axis * io.vtxData.dot(axis)
  val v1 = io.vtxData - vc
  val v2 = v1.cross(axis)
  val pos = vc + v1 * io.cosine + v2 * io.sine
  
  val posZ = pos(2) + Fixed24_13(2.U)

  /* TODO: Consider more efficiency calculate way like:
   * vtxWritePort.pos(0) := ((Fixed24_13(1.U) + (pos(0) / posZ * Fixed24_13.fromRaw(0x1800.S))) * Fixed24_13((1024 / 2).U)).bits >> 11
   */
  vtxWritePort.pos(0) := ((Fixed24_13(1.U) + (pos(0) / posZ * Fixed24_13(3.U) / Fixed24_13(4.U))) * Fixed24_13((1024 / 2).U)).bits >> 11
  vtxWritePort.pos(1) := ((Fixed24_13(1.U) - (pos(1) / posZ)) * Fixed24_13((768 / 2).U)).bits >> 11
  vtxWritePort.z := posZ

  when (RegNext(rIdx < RomData.nrMeshVertices.U, false.B)) {
    io.vtxWritePort.valid := true.B
    io.vtxWritePort.data := vtxWritePort
    io.posWritePort.valid := true.B
    io.posWritePort.data.pos := pos
    io.posWritePort.data.pos(2) := posZ    
  }

  io.done := RegNext((rIdx >= RomData.nrMeshVertices.U), false.B)
}

class PreprocNormals extends Module {
  val io = IO(new Bundle {
    val done   = Output(Bool())
    val start  = Input(Bool())

    /* Rom data form dataloader */
    val sine   = Input(Fixed24_13())
    val cosine = Input(Fixed24_13())

    /* Write out port to intermediate memory buffer */
    val normAddr = Output(UInt(32.W))
    val normData = Input(NumVec(3, Fixed24_13()))
    val normWritePort = new MemWriter(new TransFormedNorm, 4096)
  })

  val rIdx = RegInit(0.U(32.W))
  val wIdx = RegNext(rIdx, 0.U)
  val axis = NumVec(Fixed24_13(0.U), Fixed24_13(1.U), Fixed24_13(0.U))

  io.normAddr := rIdx
  when (io.start) {
    rIdx := rIdx + 1.U
  }

  io.normWritePort.valid := false.B; io.normWritePort.index := wIdx; io.normWritePort.data := DontCare
  
  /* Rotate vec */
  val vc = axis * io.normData.dot(axis)
  val v1 = io.normData - vc
  val v2 = v1.cross(axis)
  val norm = vc + v1 * io.cosine + v2 * io.sine
  io.normWritePort.data.norm := norm

  when (RegNext((rIdx < RomData.nrMeshNormals.U) && io.start, false.B)) {
    io.normWritePort.valid := true.B
  }

  io.done := RegNext((rIdx >= RomData.nrMeshNormals.U), false.B)
}

class PreprocTriangles extends Module {
  val io = IO(new Bundle {
    val done   = Output(Bool())
    val start  = Input(Bool())

    /* Rom data form dataloader */
    val idxAddr = Output(UInt(32.W))
    val idxData = Input(NumVec(3, SInt(16.W)))

    /* Write out port to intermediate memory buffer */
    val bbWritePort = new MemWriter(new BoundingBox, 4096)
    /* Read port to intermediate memory buffer */
    val VtxReadPort = new MemReader(new ScreenVertex, 2048)
  })

  val rIdx = RegInit(0.U(32.W))
  val wIdx = RegNext(rIdx)
  val triangle2 = Reg(new Triangle2(SInt(32.W)))
  
  io.idxAddr := rIdx
  io.bbWritePort.valid := false.B; io.bbWritePort.index := wIdx; io.bbWritePort.data := DontCare
  io.VtxReadPort.req.valid := false.B; io.VtxReadPort.req.bits := 0.U

  val reqIdx = RegInit(0.U(2.W))
  io.bbWritePort.data.bb  := triangle2.aabb()
  
  val sWaitRomData :: sWriteTriangle :: sWaitResp :: sExportBB :: sDone :: Nil = Enum(5)
  val state = RegInit(sWaitRomData)

  switch (state) {
    is (sWaitRomData) {
      when(io.start) {
        state := sWriteTriangle
      }
    }

    is (sWriteTriangle) {
      io.VtxReadPort.req.valid := true.B
      io.VtxReadPort.req.bits := io.idxData(reqIdx).asUInt
      
      state := sWaitResp
    }

    is (sWaitResp) {
      when (io.VtxReadPort.resp.valid) {
        triangle2(reqIdx) := io.VtxReadPort.resp.bits.pos
        
        val nextIdx = reqIdx + 1.U
        reqIdx := nextIdx

        when (nextIdx === 3.U) {
          state := sExportBB
        } .otherwise {
          state := sWriteTriangle
        }
      }
    }

    is (sExportBB) {
      io.bbWritePort.valid := true.B
      
      rIdx := rIdx + 1.U
      reqIdx := 0.U
      
      when (rIdx + 1.U === RomData.nrMeshTriangles.U) {
        state := sDone
      } .otherwise {
        state := sWaitRomData
      }
    }

    is (sDone) {
      // Done state
    }
  }
  io.done := (state === sDone)
}
