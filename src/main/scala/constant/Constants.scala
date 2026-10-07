package constant

import javafx.util.Duration
import util.JavaUtil


object Constants:
  val animationCloseAfter: Duration = Duration.millis(7500)
  val animationDuration: Duration = Duration.millis(250)
  val appName: String = JavaUtil.appName
  val createTokenLink = s"https://github.com/settings/personal-access-tokens/new?name=$appName&expires_in=365"
  val createTokenLinkText = "https://github.com/settings/personal-access-tokens/new"
  val defaultColumnWidth = 150
  val githubToken = "Github personal access token"
  val heading2Size = 16.0
  val headingSize = 20.0
  val inhibitReason = "Playing some video"
  val inset = 12.0
  val modalMargin = 12.0
  val modalPadding = 15.0
  val modalSpace = 50.0
  val modalWidth = 500.0
  val playerSimultaneousBytes: Int = 1024 * 1024 * 32
  val playerSimultaneousTime: Int = 10_000
  val prioritizeFirstBytes: Int = 1024 * 1024 * 32
  val prioritizeLastBytes: Int = 1024 * 1024 * 6
  val resumeRetryTime = 40_000L           //milliseconds
  val resumeSavePeriod = 300_000L         //milliseconds
