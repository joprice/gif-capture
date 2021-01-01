import java.awt._
import java.awt.image._
import java.awt.event._
import javax.imageio.stream.FileImageOutputStream
import java.io.File
import javax.swing.{JPanel, UIManager, JFrame}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global
import javax.swing.{SwingUtilities, WindowConstants}
import org.jnativehook.GlobalScreen
import org.slf4j.bridge.SLF4JBridgeHandler
import java.util.logging.Logger
import org.jnativehook.keyboard.{NativeKeyListener, NativeKeyEvent}

object Main {

  def main(args: Array[String]): Unit = {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install()
    GlobalScreen.registerNativeHook()

    object arg {
      val lifted = args.lift
      def apply[T](i: Int)(f: String => T): Option[T] = lifted(i).map(f)
    }
    val outputFile = arg(0)(_.toString).getOrElse("/tmp/output.gif")
    val frameRate = arg(1)(_.toInt).getOrElse(60)

    println(
      "Click and drag a box around the selection. Press enter when to stop recording."
    )

    EventQueue.invokeLater(new Runnable() {
      def run() = {
        val frame = overlay()
        val selection = Selection()
        selection.result.map { rectangle =>
          frame.remove(selection)
          var finished = false
          // this has to be closed to avoid capturing clicks
          frame.dispose()

          val listener = new NativeKeyListener() {
            def nativeKeyPressed(e: NativeKeyEvent) = ()

            def nativeKeyReleased(e: NativeKeyEvent) = {
              if (e.getKeyCode() == NativeKeyEvent.VC_ENTER) {
                finished = true
              }
            }

            def nativeKeyTyped(e: NativeKeyEvent) = ()
          }
          GlobalScreen.addNativeKeyListener(listener)

          val images = capture(frameRate = frameRate, rectangle, outputFile) {
            !finished
          }

          println(s"Completed recording. Writing to output $outputFile")

          writeGif(frameRate, outputFile, images)

          println(s"Completed writing file.")
          GlobalScreen.unregisterNativeHook()
        }
        frame.add(selection)
        frame.setVisible(true)
      }
    })
  }

  object Selection {
    def apply() = new Selection()
  }

  class Selection extends JPanel {
    private val rect = new Rectangle()
    private var start: Point = new Point()
    private var end: Point = new Point()
    private var startAbsolute: Point = new Point()
    private var endAbsolute: Point = new Point()
    private val p = Promise[Rectangle]()
    val result = p.future

    private val transparent = new Color(0, 0, 0, 0)
    setBackground(transparent)

    private val selectionBackground = new Color(0f, 0f, 0f, .1f)

    override def paintComponent(g: Graphics) = {
      super.paintComponent(g)

      val g2d = g.asInstanceOf[Graphics2D]
      g2d.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON
      )
      g2d.setPaint(Color.white)
      g2d.draw(rect)
      g2d.setPaint(selectionBackground)
      g2d.fill(rect)
      rect.setFrameFromDiagonal(start, end)
    }

    private val listener = new MouseAdapter() {
      override def mousePressed(e: MouseEvent): Unit = {
        println("click")
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

    result.onComplete { case _ =>
      removeMouseListener(listener)
      removeMouseMotionListener(listener)
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
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
    frame.setExtendedState(Frame.MAXIMIZED_BOTH)
    frame.setUndecorated(true)
    frame.setBackground(new Color(0, 0, 0, 0))
    frame.setAlwaysOnTop(true)
    // Without this, the window is draggable from any non transparent point, including points inside textboxes.
    frame.getRootPane.putClientProperty(
      "apple.awt.draggableWindowBackground",
      false
    )
    frame.getContentPane.setLayout(new java.awt.BorderLayout())
    frame
  }

  def writeGif(
      frameRate: Int,
      fileName: String,
      images: Seq[BufferedImage]
  ): Unit = {
    var output: FileImageOutputStream = null
    var writer: GifSequenceWriter = null
    try {
      output = new FileImageOutputStream(new File(fileName))
      writer = new GifSequenceWriter(
        output,
        BufferedImage.TYPE_INT_RGB,
        frameRate,
        true
      )
      images.foreach { image =>
        writer.writeToSequence(image)
      }
    } finally {
      if (writer != null) writer.close()
      if (output != null) output.close()
    }
  }

  def capture(frameRate: Int, rectangle: Rectangle, fileName: String)(
      continue: => Boolean
  ) = {
    val sleep = ((1d / frameRate) * 1000).toInt
    val robot = new Robot
    val images = ArrayBuffer.empty[BufferedImage]

    while (continue) {
      images += robot.createScreenCapture(rectangle)
      Thread.sleep(sleep)
    }
    images
  }
}
