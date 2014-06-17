package edu.cmu.cs.ls.keymaera.api

import edu.cmu.cs.ls.keymaera.core.{RootNode, Formula, Sequent, ProofNode}
import edu.cmu.cs.ls.keymaera.parser.KeYmaeraParser
import edu.cmu.cs.ls.keymaera.tactics.Tactics.Tactic
import edu.cmu.cs.ls.keymaera.tactics.{TacticWrapper, Tactics, TacticLibrary}
import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary.Generator
import java.util

/**
 * Created by jdq on 6/12/14.
 */
object KeYmaeraInterface {

  object TaskManagement {
    private var count = 0
    @volatile var tasks: Map[Int, (ProofNode, Map[Int, ProofNode])] = Map()

    def addTask(r: ProofNode): Int = this.synchronized {
      assert(r.children.isEmpty)
      val res = count
      tasks += (res -> (r, Map()))
      count = count + 1
      res
    }

    def getRoot(id: Int): Option[ProofNode] = tasks.get(id).map(_._1)

    def getNode(tId: Int, nId: Int): Option[ProofNode] = tasks.get(tId).map(_._2.get(nId)).flatten
  }

  object TacticManagement {
    private var count = 0
    @volatile var tactics: Map[Int, () => Tactic] = Map()

    addTactic(TacticLibrary.default)

    def addTactic(t: Tactic): Int = this.synchronized {
      val res = count
      tactics += (res -> t)
      count = count + 1
      res
    }

    def getTactic(id: Int): Option[Tactic] = tactics.get(id).map(_())

    def getTactics: List[(Int, String)] = tactics.foldLeft(Nil: List[(Int, String)])((a, p) => a :+ (p._1, p._2().name))
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

  def getNode(taskId: Int, nodeId: Option[Int]): Option[String] =  (nodeId match {
      case Some(id) => TaskManagement.getNode(taskId, id)
      case None => TaskManagement.getRoot(taskId)
    }) map json

  def getTactics: List[(Int, String)] = TacticManagement.getTactics

  def runTactic(taskId: Int, nodeId: Option[Int], tacticId: Int): Option[Int] = {
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
  def getSubtree(taskId: Int, nodeId: Option[Int], depth: Int): String = ???


  def json(p: ProofNode): String = JSONConverter(p)
}
