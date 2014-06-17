package edu.cmu.cs.ls.keymaera.api

import edu.cmu.cs.ls.keymaera.core.{RootNode, Formula, Sequent, ProofNode}
import edu.cmu.cs.ls.keymaera.parser.KeYmaeraParser
import edu.cmu.cs.ls.keymaera.tactics.Tactics.{PositionTactic, Tactic}
import edu.cmu.cs.ls.keymaera.tactics.{TacticWrapper, Tactics, TacticLibrary}

/**
 * Open issues:
 * - How do we identify where changes came from?
 *   Consider the following case: A user starts a tactic on a node and then does some steps himself. If we now poll the node
 *   to find out about its children we might get a bunch of alternatives. Which one is it the user wants to see and how do we identify it?
 * - How can we attach meta information for the GUI?
 * - What information do we want to attach?
 *
 * Created by jdq on 6/12/14.
 */
object KeYmaeraInterface {

  object TaskManagement {
    private var count = 0
    @volatile var tasks: Map[Int, (ProofNode, Map[String, ProofNode])] = Map()

    def addTask(r: ProofNode): Int = this.synchronized {
      assert(r.children.isEmpty)
      val res = count
      tasks += (res -> (r, Map()))
      count = count + 1
      res
    }

    def getRoot(id: Int): Option[ProofNode] = tasks.get(id).map(_._1)

    def getNode(tId: Int, nId: String): Option[ProofNode] = tasks.get(tId).map(_._2.get(nId)).flatten
  }

  object TacticManagement {
    private var count = 0
    @volatile var tactics: Map[Int, () => Tactic] = Map()
    @volatile var positionTactics: Map[Int, () => PositionTactic] = Map()
    @volatile var inputTactics: Map[Int, (Formula) => Tactic] = Map()
    @volatile var inputPositionTactics: Map[Int, (Formula) => PositionTactic] = Map()

    addTactic(TacticLibrary.default)

    def addTactic(t: Tactic): Int = this.synchronized {
      val res = count
      tactics += (res -> (() => t))
      count = count + 1
      res
    }

    def addInputTactic(t: (Formula => Tactic)): Int = this.synchronized {
      val res = count
      inputTactics += (res -> t)
      count = count + 1
      res
    }

    def addPositionTactic(t: PositionTactic): Int = this.synchronized {
      val res = count
      positionTactics += (res -> (() => t))
      count = count + 1
      res
    }

    def addInputPositionTactic(t: (Formula => PositionTactic)): Int = this.synchronized {
      val res = count
      inputPositionTactics += (res -> t)
      count = count + 1
      res
    }

    def getTactic(id: Int): Option[Tactic] = tactics.get(id).map(_())

    def getTactics: List[(Int, String)] = tactics.foldLeft(Nil: List[(Int, String)])((a, p) => a :+ (p._1, p._2().name))

    // TODO implement getter for the other tactics
  }

  object RunningTactics {
    @volatile private var count: Int = 0
    private var tactics: Map[Int, Tactic] = Map()
    def add(t: Tactic): Int = this.synchronized {
      val res = count
      tactics += (res -> t)
      count = count + 1
      res
    }

    def get(id: Int): Option[Tactic] = tactics.get(id)
  }

  /**
   * Parse the problem and add it to the tasks management system
   *
   * @param content
   * @return JSON representation of the content + the generated task id
   */
  def addTask(content: String): (Int, String) = {
    new KeYmaeraParser().runParser(content) match {
      case f: Formula =>
        val seq = Sequent(List(), collection.immutable.IndexedSeq[Formula](), collection.immutable.IndexedSeq[Formula](f) )
        val r = new RootNode(seq)
        val id = TaskManagement.addTask(r)
        (id, json(r))
      case a => throw new IllegalStateException("Parsing the input did not result in a formula but in: " + a)
    }
  }

  def getNode(taskId: Int, nodeId: Option[String]): Option[String] =  (nodeId match {
      case Some(id) => TaskManagement.getNode(taskId, id)
      case None => TaskManagement.getRoot(taskId)
    }) map json

  def getTactics: List[(Int, String)] = TacticManagement.getTactics

  def runTactic(taskId: Int, nodeId: Option[String], tacticId: Int): Option[Int] = {
    (nodeId match {
      case Some(id) => TaskManagement.getNode(taskId, id)
      case None => TaskManagement.getRoot(taskId)
    }) match {
      case Some(n) => TacticManagement.getTactic(tacticId) match {
        case Some(t) =>
          Tactics.KeYmaeraScheduler.dispatch(new TacticWrapper(t, n))
          Some(RunningTactics.add(t))
        case None => None
      }
      case None => None
    }
  }

  def isRunning(tacticId: Int): Boolean = {
    RunningTactics.get(tacticId) match {
      case Some(t) => !t.isComplete
      case None => false
    }
  }

  /**
   * This methods allows to poll for updates downwards from a given node
   *
   * @param taskId
   * @param nodeId
   * @param depth
   * @return
   */
  def getSubtree(taskId: Int, nodeId: Option[String], depth: Int): Option[String] = {
    // TODO: this method needs to add all the nodes to the task management
    (nodeId match {
      case Some(id) => (id, TaskManagement.getNode(taskId, id))
      case None => ("", TaskManagement.getRoot(taskId))
    }) match {
      case (id, Some(n)) => Some(getSubtree(n, id, depth))
      case (_, None) => None
    }
  }

  private def getSubtree(n: ProofNode, id: String, depth: Int): String = json(n, id, depth)

  // TODO: maybe allow listeners to node change events

  def json(p: ProofNode): String = JSONConverter(p)
  def json(p: ProofNode, id: String, l: Int): String = JSONConverter(p, id, l)
}
