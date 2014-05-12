package peregin.gpv.gui.video

import com.xuggle.mediatool.ToolFactory
import java.awt.Image
import java.awt.image.BufferedImage
import peregin.gpv.model.Telemetry
import scala.concurrent._
import com.xuggle.xuggler.{ICodec, IContainer}
import peregin.gpv.util.{DurationPrinter, Logging}
import ICodec.Type._


class VideoPlayer(url: String, telemetry: Telemetry,
                  imageHandler: Image => Unit, shiftHandler: => Long,
                  timeHandler: (Long, Int) => Unit, debug: Boolean) extends Logging {

  @volatile var running = true

  val reader = ToolFactory.makeReader(url)
  reader.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR)

  reader.open()

  val container = reader.getContainer
  val durationInMillis = container.getDuration / 1000
  val bitRate = container.getBitRate
  val (videoCoder, videoStreamIx) = (0 until container.getNumStreams).
    map(container.getStream(_).getStreamCoder).
    zipWithIndex.filter(_._1.getCodecType == CODEC_TYPE_VIDEO).
    head
  val frameRate = videoCoder.getFrameRate.getDouble
  info(s"duration: ${DurationPrinter.print(durationInMillis)}")
  info(s"bit rate: $bitRate")
  info(s"video stream: $videoStreamIx")
  info(f"frame rate: $frameRate%5.2f")

  val overlay = new VideoOverlay(telemetry, imageHandler, shiftHandler, debug)
  reader.addListener(overlay)

  val controller = new VideoController(timeHandler, durationInMillis)
  overlay.addListener(controller)

  import ExecutionContext.Implicits.global
  future {
    while(running && reader.readPacket() == null) {
      // runs in a loop until the end
    }
  }

  def seek(percentage: Double) {
    val p = percentage match {
      case a if a > 100d => 100d
      case b if b < 0d => 0d
      case c => percentage
    }
    val frames = durationInMillis / 1000 * frameRate
    val jumpToFrame = frames * p / 100
    container.seekKeyFrame(videoStreamIx, jumpToFrame.toLong, IContainer.SEEK_FLAG_FRAME)
    controller.reset()
  }

  def close() {
    running = false
    reader.close()
  }
}
