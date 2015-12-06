
import java.awt.{Rectangle, Robot, Toolkit}
import java.awt.image._
import javax.imageio.stream.FileImageOutputStream
import java.io.File

object Main {
  def main(args: Array[String]): Unit = {
    val frameRate = 15
    val seconds = args.headOption.map(_.toInt).getOrElse(1)
    val fileName = "/tmp/output.gif"
    val sleep = ((1d / frameRate) * 1000).toLong
    val frames = seconds * frameRate

    val screenSize = Toolkit.getDefaultToolkit.getScreenSize
    val rectangle = new Rectangle(0, 0, screenSize.width, screenSize.height)

    println(s"writing gif with $frames frames")

    val robot = new Robot

    val images = (0 until (seconds * frameRate)).map { _ =>
      val image = robot.createScreenCapture(rectangle)
      Thread.sleep(sleep)
      image
    }

    println(s"Completed recording. Writing to output $fileName")

    val output = new FileImageOutputStream(new File(fileName))
    val writer = new GifSequenceWriter(output, BufferedImage.TYPE_INT_RGB, frameRate, true)

    images.foreach { image =>
      writer.writeToSequence(image)
    }

    println(s"Completed writing file.")

    writer.close()
    output.close()
  }
}

