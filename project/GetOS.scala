
object GetOS {
  lazy val os: String = sys.props.get("os").getOrElse {
    val sysOs = System.getProperty("os.name").toLowerCase
    if (sysOs.contains("win")) "win"
    else if (sysOs.contains("mac")) "mac"
    else if (sysOs.contains("nix") || sysOs.contains("nux") || sysOs.contains("aix")) "linux"
    else throw new Exception("Unknown OS, you should provide -Dos=win|mac|linux")
  }
}
