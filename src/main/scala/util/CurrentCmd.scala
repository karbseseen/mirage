package util

import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.{Library, Native, Pointer}


object CurrentCmd:

  def apply(): List[String] =
    if (SystemInfo.os == SystemInfo.OS.Windows) windowsCmd
    else
      val info = ProcessHandle.current.info
      info.command.orElseThrow :: info.arguments.orElseThrow.toList

  private def windowsCmd = 
    val kernel32 = Native.load("kernel32", classOf[Kernel32])
    val shell32 = Native.load("shell32", classOf[Shell32])

    val argc = new IntByReference
    val argv = shell32.CommandLineToArgvW(kernel32.GetCommandLineW, argc)
    try
      argv.getPointerArray(0, argc.getValue).toList.map(_.getWideString(0))
    finally
      kernel32.LocalFree(argv)

  private trait Kernel32 extends Library:
    def GetCommandLineW: Pointer
    def LocalFree(hMem: Pointer): Pointer

  private trait Shell32 extends StdCallLibrary:
    def CommandLineToArgvW(lpCmdLine: Pointer, pNumArgs: IntByReference): Pointer
