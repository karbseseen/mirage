package util

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import constant.Constants
import core.main.MainApp
import org.freedesktop.ScreenSaver
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import util.JavaPlatform.OS


class WakeLock:
  private var locked = false

  def lock(): Unit = WakeLockUnsafe.synchronized:
    if (!locked)
      WakeLockUnsafe.lockUnsync()
      locked = true

  def unlock(): Unit = WakeLockUnsafe.synchronized:
    if (locked)
      WakeLockUnsafe.unlockUnsync()
      locked = false


object WakeLockUnsafe:

  private var lockCount = 0

  private type Unlock = Option[() => Unit]
  private var unlockImpl: Unlock = None

  def lock(): Unit = synchronized(lockUnsync())
  def unlock(): Unit = synchronized(unlockUnsync())

  private[util] def lockUnsync(): Unit =
    if (lockCount == 0)
      unlockImpl = JavaPlatform.os match
        case OS.Windows => windows
        case OS.MacOS => macOs
        case OS.Linux => linuxDbus orElse linuxSystemd
    lockCount += 1

  private[util] def unlockUnsync(): Unit = synchronized:
    lockCount -= 1
    if (lockCount == 0)
      unlockImpl.foreach(_())
      unlockImpl = None

  MainApp.shutdownHook:
    synchronized:
      lockCount = Int.MinValue
      unlockImpl.foreach(_())
      unlockImpl = None


  private def windows: Unlock =
    trait Kernel32 extends StdCallLibrary:
      def SetThreadExecutionState(esFlags: Int): Int
    val kernel32 = Native.load("kernel32", classOf[Kernel32])

    inline val ES_CONTINUOUS = 0x80000000
    inline val ES_SYSTEM_REQUIRED = 0x00000001
    inline val ES_DISPLAY_REQUIRED = 0x00000002

    kernel32.SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED)
    Some(() => kernel32.SetThreadExecutionState(ES_CONTINUOUS))

  private def macOs =
    val process = ProcessBuilder("caffeinate", "-d").start()
    Some(() => process.destroy())

  private def linuxDbus = try
    val connection = DBusConnectionBuilder.forSessionBus.build
    try
      val screenSaver = connection.getRemoteObject("org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver", classOf[ScreenSaver])
      val cookie = screenSaver.Inhibit(Constants.appName, Constants.inhibitReason)
      Some: () =>
        screenSaver.UnInhibit(cookie)
        connection.disconnect()
    catch case _: Throwable =>
      connection.disconnect()
      None
  catch case _: Throwable =>
    None

  private def linuxSystemd =
    val process = ProcessBuilder(
      "systemd-inhibit",
      "--what=idle",
      "--who=" + Constants.appName,
      "--why=" + Constants.inhibitReason,
      "sleep",
      "infinity",
    ).start()
    Some(() => process.destroy())
