import javax.swing.{JFrame, JLabel, SwingConstants, WindowConstants}


object SimpleWindow:
  def go(): Unit =
    val frame = new JFrame("My Scala Window")
    frame.setSize(400, 300)
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)

    val label = new JLabel("Hello from Scala!", SwingConstants.CENTER)
    frame.add(label)

    frame.setVisible(true)


object ScalaMain:
  def apply(args: Array[String]): Unit =
    println { List("Hello", "from", "scala", ":)").mkString(" ") }
    SimpleWindow.go()
