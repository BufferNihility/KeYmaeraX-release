package edu.cmu.cs.ls.keymaerax.btactics

import edu.cmu.cs.ls.keymaerax
import edu.cmu.cs.ls.keymaerax.bellerophon._
import edu.cmu.cs.ls.keymaerax.core.{SeqPos, Formula, Provable}
import edu.cmu.cs.ls.keymaerax.tactics.Augmentors._
import edu.cmu.cs.ls.keymaerax.tactics.{TacticWrapper, Interpreter, Tactics}
import edu.cmu.cs.ls.keymaerax.tools.{KeYmaera, Mathematica}

/**
 * @author Nathan Fulton
 */
object DebuggingTactics {
  //@todo import a debug flag as in Tactics.DEBUG
  private val DEBUG = System.getProperty("DEBUG", "false")=="true"

  def ErrorT(e : Throwable) = new BuiltInTactic("Error") {
    override def result(provable: Provable): Provable = throw e
  }

  def ErrorT(s : String) = new BuiltInTactic("Error") {
    override def result(provable: Provable): Provable = {
      throw BelleUserGeneratedError(s)
    }
  }

  /** debug is a no-op tactic that prints a message and the current provable, if the system property DEBUG is true. */
  def debug(message: => String): BuiltInTactic = new BuiltInTactic("debug") {
    override def result(provable: Provable): Provable = {
      if (DEBUG) println("===== " + message + " ==== " + provable + " =====")
      provable
    }
  }

  /** assert is a no-op tactic that raises an error if the provable is not of the expected size. */
  def assert(anteSize: Int, succSize: Int): BuiltInTactic = new BuiltInTactic("assert") {
    override def result(provable: Provable): Provable = {
      if (provable.subgoals.size != 1 || provable.subgoals.head.ante.size != anteSize ||
        provable.subgoals.head.succ.size != succSize) {
        throw new BelleUserGeneratedError("Expected 1 subgoal with: " + anteSize + " antecedent and " + succSize + " succedent formulas,\n\t but got " +
          provable.subgoals.size + " subgoals (head subgoal with: " + provable.subgoals.head.ante.size + "antecedent and " +
          provable.subgoals.head.succ.size + " succedent formulas)")
      }
      provable
    }
  }

  /** assert is a no-op tactic that raises an error if the provable has not the expected formula at the specified position. */
  def assert(fml: Formula, message: => String): BuiltInPositionTactic = new BuiltInPositionTactic("assert") {
    override def applyAt(provable: Provable, pos: SeqPos): Provable = {
      if (provable.subgoals.size != 1 || provable.subgoals.head.at(pos) != fml) {
        throw new BelleUserGeneratedError(message + "\nExpected 1 subgoal with " + fml + " at position " + pos + ",\n\t but got " +
          provable.subgoals.size + " subgoals (head subgoal with " + provable.subgoals.head.at(pos) + " at position " + pos + ")")
      }
      provable
    }
  }
}

/**
 * @author Nathan Fulton
 */
object Idioms {
  def NilT() = new BuiltInTactic("NilT") {
    override def result(provable: Provable): Provable = provable
  }
  def IdentT = NilT

  def AtSubgoal(subgoalIdx: Int, t: BelleExpr) = new DependentTactic(s"AtSubgoal($subgoalIdx, ${t.toString})") {
    override def computeExpr(v: BelleValue): BelleExpr = v match {
      case BelleProvable(provable) => {
        BranchTactic(Seq.tabulate(provable.subgoals.length)(i => if(i == subgoalIdx) t else IdentT))
      }
      case _ => throw BelleError("Cannot perform AtSubgoal on a non-Provable value.")
    }
  }

  /** Gives a name to a tactic to a definable tactic. */
  def NamedTactic(name: String, tactic: BelleExpr) = new DependentTactic(name) {
    override def computeExpr(v: BelleValue): BelleExpr = tactic
  }

  /** Establishes the fact by appealing to an existing tactic. */
  def by(fact: Provable) = new BuiltInTactic("Established by existing provable") {
    override def result(provable: Provable): Provable = {
      assert(provable.subgoals.length == 1, "Expected one subgoal but found " + provable.subgoals.length)
      provable(fact, 0)
    }
  }
}

/**
 * @author Nathan Fulton
 */
object Legacy {
  def defaultInitialization(mathematicaConfig:  Map[String,String]) = {
    Tactics.KeYmaeraScheduler = new Interpreter(KeYmaera)
    Tactics.MathematicaScheduler = new Interpreter(new Mathematica)

    Tactics.KeYmaeraScheduler.init(Map())
    Tactics.Z3Scheduler.init
    Tactics.MathematicaScheduler.init(mathematicaConfig)
  }

//  def defaultDeinitialization = {
//    if (Tactics.KeYmaeraScheduler != null) {
//      Tactics.KeYmaeraScheduler.shutdown()
//      Tactics.KeYmaeraScheduler = null
//    }
//    if (Tactics.MathematicaScheduler != null) {
//      Tactics.MathematicaScheduler.shutdown()
//      Tactics.MathematicaScheduler = null
//    }
//    if(Tactics.Z3Scheduler != null) {
//      Tactics.Z3Scheduler = null
//    }
//  }

  def InitializedScheduledTactic(mathematicaConfig : Map[String,String], tactic: keymaerax.tactics.Tactics.Tactic) = {
    defaultInitialization(mathematicaConfig)
    ScheduledTactic(tactic)
  }

  def ScheduledTactic(tactic : keymaerax.tactics.Tactics.Tactic) = new BuiltInTactic(s"Scheduled(${tactic.name})") {
    //@see [[Legacy.defaultInitialization]]
    if(!Tactics.KeYmaeraScheduler.isInitialized)
      throw BelleError("Need to initialize KeYmaera scheduler and possibly also the Mathematica scheduler before running a Legacy.ScheduledTactic.")

    override def result(provable: Provable): Provable = {
      //@todo don't know if we can create a proof node from a provable.
      if(provable.subgoals.length != 1) throw new Exception("Cannot run scheduled tactic on something with more than one subgoal.")

      val node = new keymaerax.tactics.RootNode(provable.subgoals.head)

      Tactics.KeYmaeraScheduler.dispatch(new TacticWrapper(tactic, node))

      node.provableWitness
    }
  }
}