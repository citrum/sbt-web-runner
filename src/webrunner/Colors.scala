package webrunner

object Colors {
  import scala.Console._

  def isANSISupported = true

  def red(str: String): String = if (isANSISupported) RED + str + RESET else str
  def blue(str: String): String = if (isANSISupported) BLUE + str + RESET else str
  def cyan(str: String): String = if (isANSISupported) CYAN + str + RESET else str
  def green(str: String): String = if (isANSISupported) GREEN + str + RESET else str
  def magenta(str: String): String = if (isANSISupported) MAGENTA + str + RESET else str
  def white(str: String): String = if (isANSISupported) WHITE + str + RESET else str
  def black(str: String): String = if (isANSISupported) BLACK + str + RESET else str
  def yellow(str: String): String = if (isANSISupported) YELLOW + str + RESET else str
}
