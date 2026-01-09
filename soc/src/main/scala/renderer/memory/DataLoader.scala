import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline
import implicits._

object RomData {
  val nrMeshVertices  = 1501
  val nrMeshNormals   = 2998
  val nrMeshTriangles = 2998
}

class GeometryRomIO extends Bundle {
  // Index Request
  val idxAddr  = Input(UInt(16.W))
  val idxData  = Output(NumVec(3, SInt(16.W)))

  // Vertex Request
  val vtxAddr  = Input(UInt(16.W))
  val vtxData  = Output(NumVec(3, Fixed24_13()))

  // Normal Request
  val normAddr  = Input(UInt(16.W))
  val normData  = Output(NumVec(3, Fixed24_13()))
}

class MathTableIO extends Bundle {
  val angle  = Input(UInt(9.W))
  val sine = Output(Fixed24_13())
  val cosine = Output(Fixed24_13())
}

class DataLoader extends Module {
  val io = IO(new Bundle {
    val geo  = new GeometryRomIO
    // val tex  = new TextureRomIO
    val math = new MathTableIO
  })

  val resPath = sys.env.getOrElse("HEX_PATH", "src/main/resources/")
  println(s"[DataLoader] Loading path: $resPath")

  /* Indices (48-bit wide) */
  val idxMem = SyncReadMem(4096, UInt(48.W))
  loadMemoryFromFileInline(idxMem, resPath + "indices.hex")
  
  val idxRaw = idxMem.read(io.geo.idxAddr)
  // Unpack: v2(47-32) | v1(31-16) | v0(15-0)
  io.geo.idxData(0) := idxRaw(15, 0).asSInt
  io.geo.idxData(1) := idxRaw(31, 16).asSInt
  io.geo.idxData(2) := idxRaw(47, 32).asSInt

  /* Vertices (72-bit wide) */
  val vtxMem = SyncReadMem(4096, UInt(72.W))
  loadMemoryFromFileInline(vtxMem, resPath + "vertices.hex")

  val vtxRaw = vtxMem.read(io.geo.vtxAddr)
  // Unpack: z(71-48) | y(47-24) | x(23-0)
  io.geo.vtxData(0) := Fixed24_13.fromRaw(vtxRaw(23, 0).asSInt)
  io.geo.vtxData(1) := Fixed24_13.fromRaw(vtxRaw(47, 24).asSInt)
  io.geo.vtxData(2) := Fixed24_13.fromRaw(vtxRaw(71, 48).asSInt)

  /* Normals (72-bit wide) */
  val normMem = SyncReadMem(4096, UInt(72.W))
  loadMemoryFromFileInline(normMem, resPath + "normals.hex")

  val normRaw = normMem.read(io.geo.normAddr)
  // Unpack: Same as vertices
  io.geo.normData(0) := Fixed24_13.fromRaw(normRaw(23, 0).asSInt)
  io.geo.normData(1) := Fixed24_13.fromRaw(normRaw(47, 24).asSInt)
  io.geo.normData(2) := Fixed24_13.fromRaw(normRaw(71, 48).asSInt)

  /* Disable now because of unused data */
  /* Texture (32-bit wide) */
  // val texMem = SyncReadMem(65536, UInt(32.W))
  // loadMemoryFromFileInline(texMem, resPath + "texture.hex")

  // val texRaw = texMem.read(io.tex.addr)
  // // Unpack: 00 | R | G | B
  // io.tex.data.b       := texRaw(7, 0)
  // io.tex.data.g       := texRaw(15, 8)
  // io.tex.data.r       := texRaw(23, 16)
  // io.tex.data.padding := 0.U

  /* Math Tables (Sin/Cos) */
  val sinMem = SyncReadMem(512, UInt(24.W))
  val cosMem = SyncReadMem(512, UInt(24.W))
  loadMemoryFromFileInline(sinMem, resPath + "sine.hex")
  loadMemoryFromFileInline(cosMem, resPath + "cosine.hex")

  io.math.sine := Fixed24_13.fromRaw(sinMem.read(io.math.angle).asSInt)
  io.math.cosine := Fixed24_13.fromRaw(cosMem.read(io.math.angle).asSInt)
}
