
import java.awt._
import java.awt.image._
import javax.imageio.stream.FileImageOutputStream
import java.io.File
import javax.swing.{JPanel, UIManager, JFrame}
import java.awt.event._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global

object Main {

  def main(args: Array[String]): Unit = {
    object arg {
      val lifted = args.lift
      def apply[T](i: Int)(f: String => T): Option[T] = lifted(i).map(f)
    }

    val lifted = args.lift
    val outputFile = arg(0)(_.toString).getOrElse("/tmp/output.gif")
    val frameRate = arg(1)(_.toInt).getOrElse(15)

    println("Click and drag a box around the selection. Press enter when to stop recording.")

    val frame = overlay()
    val selection = Selection()
    selection.result.map { rectangle =>
      try {
        var finished = false
        frame.addKeyListener(new KeyAdapter {
          override def keyPressed(e: KeyEvent): Unit = {
            if (e.getKeyCode == KeyEvent.VK_ENTER) {
              finished = true
            }
          }
        })

        val images = capture(frameRate = frameRate, rectangle, outputFile) {
          !finished
        }

        println(s"Completed recording. Writing to output $outputFile")

        writeGif(frameRate, outputFile, images)

        println(s"Completed writing file.")
      } finally {
        sys.exit(0)
      }
    }
    frame.add(selection)
    frame.setVisible(true)
  }

  object Selection {
    def apply() = new Selection()
  }

  class Selection extends JPanel {
    private val rect = new Rectangle()
    private var start: Point = new Point()
    private var end: Point = new Point()
    private var startAbsolute: Point = new Point()
    private var endAbsolute: Point  = new Point()

    val p = Promise[Rectangle]()
    val result = p.future

    val transparent = new Color(0, 0, 0, 0)
    setBackground(transparent)

    val selectionBackground = new Color(0f, 0f, 0f, .1f)

    override def paintComponent(g: Graphics) = {
      super.paintComponent(g)

      val g2d = g.asInstanceOf[Graphics2D]
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2d.setPaint(Color.white)
      g2d.draw(rect)
      g2d.setPaint(selectionBackground)
      g2d.fill(rect)
      rect.setFrameFromDiagonal(start, end)
    }

    val listener = new MouseAdapter() {
      override def mousePressed(e: MouseEvent): Unit = {
        start = e.getPoint
        startAbsolute = e.getLocationOnScreen
      }

      override def mouseDragged(e: MouseEvent): Unit = {
        end = e.getPoint
        endAbsolute = e.getLocationOnScreen
        repaint()
      }

      override def mouseReleased(e: MouseEvent): Unit = {
        // remove listeners so that the selection does not change
        removeMouseListener(this)
        removeMouseMotionListener(this)

        val position = new Rectangle()
        position.setFrameFromDiagonal(startAbsolute, endAbsolute)

        // clear selection rectangle so that the background does not get captured in the screentshot
        rect.setFrameFromDiagonal(start, start)
        repaint()

        p.success(position)
      }
    }

    addMouseListener(listener)
    addMouseMotionListener(listener)
  }

  def overlay() = {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)
    } catch {
      case ignore: Throwable =>
    }
    val frame = new JFrame("overlay")
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    frame.setExtendedState(Frame.MAXIMIZED_BOTH)
    frame.setUndecorated(true)
    frame.setBackground(new Color(0, 0, 0, 0))
    frame.setAlwaysOnTop(true)
    // Without this, the window is draggable from any non transparent point, including points inside textboxes.
    frame.getRootPane.putClientProperty("apple.awt.draggableWindowBackground", false)
    frame.getContentPane.setLayout(new java.awt.BorderLayout())
    frame
  }

  def writeGif(frameRate: Int, fileName: String, images: Seq[BufferedImage]): Unit = {
    var output: FileImageOutputStream = null
    var writer: GifSequenceWriter = null
    try {
      output = new FileImageOutputStream(new File(fileName))
      writer = new GifSequenceWriter(output, BufferedImage.TYPE_INT_RGB, frameRate, true)
      images.foreach { image =>
        writer.writeToSequence(image)
      }
    } finally {
      if (writer != null) writer.close()
      if (output != null) output.close()
    }
  }

  def capture(frameRate: Int, rectangle: Rectangle, fileName: String)(continue: => Boolean) = {
    val sleep = ((1d / frameRate) * 1000).toLong
    val robot = new Robot
    val images = ArrayBuffer.empty[BufferedImage]

    while(continue) {
      images += robot.createScreenCapture(rectangle)
      Thread.sleep(sleep)
    }
    images
  }
}

