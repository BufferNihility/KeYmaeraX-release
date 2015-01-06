/**
 * Infrastructure for external tools
 *
 * - part of core because some rules invoke external tools to do
 *   their computation
 * - invoking an external tool blocks; the rule invoking tactics
 *   should therefore be dispatched to a corresponding thread for
 *   this tool's scheduler.
 */

package edu.cmu.cs.ls.keymaera.core

import edu.cmu.cs.ls.keymaera.tools.{JLinkMathematicaLink, QETool}

/**
 * Defines the lifecycle for external tools. A tool is available once init is called.
 * It cannot be used after shutdown. For intermediate restarting, use check_and_recover.
 */
trait Tool {
  // TODO replace with constructor in tool base and dependency injection
  /**
   * Initializes the tool.
   * @param config The tool configuration.
   */
  def init(config : Map[String,String])

  /**
   * Check whether the managed tool is still alive and recover it if not.
   * Yes, this is the mathematica kernel dies on interrupt fix-up!
   */
  def check_and_recover()

  /**
   * Shutdown the tool
   */
  def shutdown()

  /**
   * The name of the tool.
   * @return The tool name.
   */
  def name: String
}

/**
 * Base class for tool instances (e.g., a specific mathematica kernel)
 */
abstract class ToolBase(val name: String) extends Tool {

  def init(config : Map[String,String]) {}

  /**
   * Check whether the managed tool is still alive and recover it if not.
   * Yes, this is the mathematica kernel dies on interrupt fix-up!
   */
  def check_and_recover() {}

  /**
   * Shutdown the tool
   */
  def shutdown() {}

}

object KeYmaera extends ToolBase("KeYmaera") {}

class Mathematica extends ToolBase("Mathematica") {
  private val jlink = new JLinkMathematicaLink
  private[core] val cricitalQE: QETool = jlink
  
  // TODO replace with constructor and dependency injection
  override def init(config: Map[String,String]) = {
    val linkName = config.get("linkName") match {
      case Some(l) => l
      case None => throw new IllegalArgumentException("Missing configuration parameter 'linkName'")
    }
    jlink.init(linkName)
  }

  override def shutdown() = jlink.shutdown()
}
