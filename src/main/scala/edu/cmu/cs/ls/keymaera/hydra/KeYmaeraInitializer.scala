package edu.cmu.cs.ls.keymaera.hydra

import edu.cmu.cs.ls.keymaera.api.{ComponentConfig, KeYmaeraInterface}
import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics.{TacticLibrary2, Tactics, TacticLibrary}
import edu.cmu.cs.ls.keymaera.tactics.Tactics.{Tactic, PositionTactic}

import scala.reflect.runtime.universe.TypeTag

/**
 * Initializes KeYmaera and its database.
 * @param env The dependency injection environment
 */
class KeYmaeraInitializer(env : {val db: DBAbstraction
                                 val tacticLibrary: TacticLibrary2}) {

  def initMathematicaFromDB() = {
    getMathematicaLinkName match {
      case Some(l) => getMathematicaLibDir match {
        case Some(libDir) => Tactics.MathematicaScheduler.init(Map("linkName" -> l, "libDir" -> libDir))
        case None => Tactics.MathematicaScheduler.init(Map("linkName" -> l))
      }

      case None => println("Warning: Mathematica not configured")
    }
  }

  def initialize() {
    Tactics.KeYmaeraScheduler.init(Map())
    // TODO move to DB initialization
    env.db.createConfiguration("mathematica")
    // TODO replace with dependency injection
    initMathematicaFromDB()

    initTactic("keymaera.default", "TacticLibrary.default", TacticKind.Tactic, env.tacticLibrary.default)
    initTactic("keymaera.defaultNoArith", "TacticLibrary.defaultNoArith", TacticKind.Tactic, env.tacticLibrary.defaultNoArith)
    initTactic("keymaera.step", "TacticLibrary.step", TacticKind.PositionTactic, TacticLibrary.step)
    initTactic("keymaera.propositional", "TacticLibrary.propositional", TacticKind.Tactic, TacticLibrary.propositional)
    initTactic("keymaera.arithmetic", "TacticLibrary.arithmeticT", TacticKind.Tactic, TacticLibrary.arithmeticT)
    initTactic("dl.axiomclose", "TacticLibrary.AxiomCloseT", TacticKind.Tactic, TacticLibrary.AxiomCloseT)

    initTactic("dl.and-left", "TacticLibrary.AndLeftT", TacticKind.PositionTactic, TacticLibrary.AndLeftT)
    initTactic("dl.and-right", "TacticLibrary.AndRightT", TacticKind.PositionTactic, TacticLibrary.AndRightT)
    initTactic("dl.or-left", "TacticLibrary.OrLeftT", TacticKind.PositionTactic, TacticLibrary.OrLeftT)
    initTactic("dl.or-right", "TacticLibrary.OrRightT", TacticKind.PositionTactic, TacticLibrary.OrRightT)
    initTactic("dl.imply-left", "TacticLibrary.ImplyLeftT", TacticKind.PositionTactic, TacticLibrary.ImplyLeftT)
    initTactic("dl.imply-right", "TacticLibrary.ImplyRightT", TacticKind.PositionTactic, TacticLibrary.ImplyRightT)
    initTactic("dl.equiv-left", "TacticLibrary.EquivLeftT", TacticKind.PositionTactic, TacticLibrary.EquivLeftT)
    initTactic("dl.equiv-right", "TacticLibrary.EquivRightT", TacticKind.PositionTactic, TacticLibrary.EquivRightT)
    initTactic("dl.not-left", "TacticLibrary.NotLeftT", TacticKind.PositionTactic, TacticLibrary.NotLeftT)
    initTactic("dl.not-right", "TacticLibrary.NotRightT", TacticKind.PositionTactic, TacticLibrary.NotRightT)
    initTactic("dl.hide", "TacticLibrary.hideT", TacticKind.PositionTactic, TacticLibrary.hideT)
    initTactic("dl.close-true", "TacticLibrary.CloseTrueT", TacticKind.PositionTactic, TacticLibrary.CloseTrueT)
    initTactic("dl.close-false", "TacticLibrary.CloseFalseT", TacticKind.PositionTactic, TacticLibrary.CloseFalseT)
    initTactic("dl.close", "TacticLibrary.CloseT", TacticKind.PositionTactic, TacticLibrary.closeT)
    initTactic("dl.skolemize", "TacticLibrary.skolemizeT", TacticKind.PositionTactic, TacticLibrary.skolemizeT)
    initTactic("dl.decomposeQuan", "TacticLibrary.decomposeQuanT", TacticKind.PositionTactic, TacticLibrary.decomposeQuanT)
    initTactic("dl.abstraction", "TacticLibrary.abstractionT", TacticKind.PositionTactic, TacticLibrary.abstractionT)

    initTactic("dl.box-assign", "TacticLibrary.boxAssignT", TacticKind.PositionTactic, TacticLibrary.boxAssignT)
    initTactic("dl.box-choice", "TacticLibrary.boxChoiceT", TacticKind.PositionTactic, TacticLibrary.boxChoiceT)
    initTactic("dl.box-induction", "TacticLibrary.boxInductionT", TacticKind.PositionTactic, TacticLibrary.boxInductionT)
    initTactic("dl.box-ndetassign", "TacticLibrary.boxNDetAssignT", TacticKind.PositionTactic, TacticLibrary.boxNDetAssign)
    initTactic("dl.box-seq", "TacticLibrary.boxSeqT", TacticKind.PositionTactic, TacticLibrary.boxSeqT)
    initTactic("dl.box-test", "TacticLibrary.boxTestT", TacticKind.PositionTactic, TacticLibrary.boxTestT)

    initTactic("dl.diffsolution", "TacticLibrary.diffSolutionT", TacticKind.PositionTactic, TacticLibrary.diffSolutionT)
    initTactic("dl.diffinvariant", "TacticLibrary.diffInvariant", TacticKind.PositionTactic, TacticLibrary.diffInvariant)
    initTactic("dl.diffweaken", "TacticLibrary.diffWeakenT", TacticKind.PositionTactic, TacticLibrary.diffWeakenT)

    initInputTactic[Option[Formula]]("dl.cut", "TacticLibrary.cutT", TacticKind.InputTactic, TacticLibrary.cutT)
    initInputTactic("dl.qe", "TacticLibrary.quantifierEliminationT", TacticKind.InputTactic, TacticLibrary.quantifierEliminationT _)
    initInputTactic[Position,Position]("dl.axiomClose", "TacticLibrary.AxiomCloseT", TacticKind.InputTactic, TacticLibrary.AxiomCloseT)
//    initInputTactic("dl.axiom", "TacticLibrary.axiomT", TacticKind.InputTactic, TacticLibrary.axiomT)
    initInputPositionTactic[Option[Formula]]("dl.induction", "TacticLibrary.inductionT", TacticKind.PositionTactic, TacticLibrary.inductionT)
    initInputPositionTactic[Formula]("dl.diffcut", "TacticLibrary.diffCutT", TacticKind.InputPositionTactic, TacticLibrary.diffCutT)
    initInputPositionTactic[Variable,Term]("dl.instantiate", "TacticLibrary.instantiateQuanT", TacticKind.PositionTactic, TacticLibrary.instantiateQuanT)
  }

  private def initTactic(name : String, className : String, kind : TacticKind.Value, t : Tactic) = {
    val tactic = getOrCreateTactic(name, className, kind)
    KeYmaeraInterface.addTactic(tactic.tacticId, t)
  }
  private def initTactic(name : String, className : String, kind : TacticKind.Value, t : PositionTactic) = {
    val tactic = getOrCreateTactic(name, className, kind)
    KeYmaeraInterface.addPositionTactic(tactic.tacticId, t)
  }
  private def initInputTactic[T](name : String, className : String, kind : TacticKind.Value,
                         tGen : T => Tactic)(implicit m : TypeTag[T]) = {
    val tactic = getOrCreateTactic(name, className, kind)
    KeYmaeraInterface.addTactic(tactic.tacticId, tGen)
  }
  private def initInputTactic[T,U](name : String, className : String, kind : TacticKind.Value,
                                     tGen : (T,U) => Tactic)(implicit m : TypeTag[T], n : TypeTag[U]) = {
    val tactic = getOrCreateTactic(name, className, kind)
    KeYmaeraInterface.addTactic(tactic.tacticId, tGen)
  }
  private def initInputTactic[T,U,V](name : String, className : String, kind : TacticKind.Value,
                                 tGen : (T,U,V) => Tactic)(implicit m : TypeTag[T], n : TypeTag[U], o : TypeTag[V]) = {
    val tactic = getOrCreateTactic(name, className, kind)
    KeYmaeraInterface.addTactic(tactic.tacticId, tGen)
  }
  private def initInputPositionTactic[T](name : String, className : String, kind : TacticKind.Value,
                         tGen : T => PositionTactic)(implicit m : TypeTag[T]) = {
    val tactic = getOrCreateTactic(name, className, kind)
    KeYmaeraInterface.addPositionTactic(tactic.tacticId, tGen)
  }
  private def initInputPositionTactic[T,U](name : String, className : String, kind : TacticKind.Value,
                                         tGen : (T,U) => PositionTactic)(implicit m : TypeTag[T], n : TypeTag[U]) = {
    val tactic = getOrCreateTactic(name, className, kind)
    KeYmaeraInterface.addPositionTactic(tactic.tacticId, tGen)
  }

  private def getOrCreateTactic(name: String, className: String, kind: TacticKind.Value): TacticPOJO = {
    env.db.getTacticByName(name) match {
      case Some(t) => t
      case None =>
        val id = env.db.createTactic(name, className, kind)
        env.db.getTactic(id) match {
          case Some(t) => t
          case None => throw new IllegalStateException("Unable to insert tactic " + name + " into the database")
        }
    }
  }

  private def getMathematicaLinkName = {
    env.db.getConfiguration("mathematica").config.get("linkName")
  }

  private def getMathematicaLibDir : Option[String] = {
    val config = env.db.getConfiguration("mathematica").config
    if (config.contains("jlinkLibDir")) Some(config.get("jlinkLibDir").get)
    else None
  }
}
