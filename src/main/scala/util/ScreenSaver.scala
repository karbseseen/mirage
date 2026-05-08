package org.freedesktop

import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32


trait ScreenSaver extends DBusInterface:
  def Inhibit(applicationName: String, reason: String): UInt32
  def UnInhibit(cookie: UInt32): Unit
