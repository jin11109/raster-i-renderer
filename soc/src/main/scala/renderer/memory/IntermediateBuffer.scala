import chisel3._
import chisel3.util._
import implicits._

class ScreenVertex extends Bundle {
  val pos = new NumVec(2, SInt(32.W))
  val z   = Fixed24_13()
}

class TransFormedPos extends Bundle {
  val pos = new NumVec(3, Fixed24_13())
}

class TransFormedNorm extends Bundle {
  val norm = new NumVec(3, Fixed24_13())
}

class BoundingBox extends Bundle {
  val bb = new Aabb2(SInt(32.W))
}

class WriteReq[T <: Data](gen: T, addrWidth: Int) extends Bundle {
  val index = UInt(addrWidth.W)
  val data  = gen.cloneType
}

class MemWriter[T <: Data](gen: T, depth: Int) extends Bundle {
  val addrWidth = log2Ceil(depth)
  
  val valid = Output(Bool())
  val index = Output(UInt(addrWidth.W))
  val data  = Output(gen.cloneType)
}

class MemReader[T <: Data](gen: T, depth: Int) extends Bundle {
  val addrWidth = log2Ceil(depth)
  val req = Decoupled(UInt(addrWidth.W))  
  val resp = Flipped(Valid(gen.cloneType))
}

class GenericRam[T <: Data](gen: T, depth: Int) extends Module {
  val io = IO(new Bundle {
    val writer = Flipped(new MemWriter(gen, depth))
    val reader = Flipped(new MemReader(gen, depth))
  })

  val mem = SyncReadMem(depth, gen)

  when(io.writer.valid) {
    mem.write(io.writer.index, io.writer.data)
  }

  io.reader.req.ready := !io.writer.valid
  
  val doRead = io.reader.req.fire
  io.reader.resp.bits := mem.read(io.reader.req.bits, doRead)
  io.reader.resp.valid := RegNext(doRead, init = false.B)
}
