/**
  * Copyright (c) Carnegie Mellon University.
  * See LICENSE.txt for the conditions of this license.
  */
package edu.cmu.cs.ls.keymaerax.bellerophon

import edu.cmu.cs.ls.keymaerax.btactics.Augmentors
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.btactics.SerializationNames.SerializationName

/**
 * Algebraic Data Type whose elements are well-formed Bellephoron tactic expressions.
 * See Table 1 of "Bellerophon: A Typed Language for Automated Deduction in a Uniform Substitution Calculus"
 * @author Nathan Fulton
 * @see [[SequentialInterpreter]]
 * @see [[edu.cmu.cs.ls.keymaerax.bellerophon]]
 */
abstract class BelleExpr(val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) {
  private[keymaerax] val DEBUG = System.getProperty("DEBUG", "false")=="true"
  // tactic combinators

  /** this & other: sequential composition executes other on the output of this, failing if either fail. */
  def &(other: BelleExpr)             = SeqTactic(this, other)
  /** this | other: alternative composition executes other if applying this fails, failing if both fail. */
  def |(other: BelleExpr)             = EitherTactic(this, other)
  /** this*: saturating repetition executes this tactic to a fixpoint, casting result to type annotation, diverging if no fixpoint. */
  def *@(annotation: BelleType)       = SaturateTactic(this, annotation)
  /** this+: saturating repetition executes this tactic to a fixpoint, requires at least one successful application */
  def +@(annotation: BelleType) = this & this*@annotation
  /** this*: bounded repetition executes this tactic to `times` number of times, failing if any of those repetitions fail. */
  def *(times: Int/*, annotation: BelleType*/) = RepeatTactic(this, times, null)
  /** <(e1,...,en): branching to run tactic `ei` on branch `i`, failing if any of them fail or if there are not exactly `n` branches. */
  def <(children: BelleExpr*)         = SeqTactic(this, BranchTactic(children))
  /** case _ of {fi => ei} uniform substitution case pattern applies the first ei such that fi uniformly substitutes to current provable for which ei does not fail, fails if the ei of all matching fi fail. */
  def U(p: (SequentType, RenUSubst => BelleExpr)*) = SeqTactic(this, USubstPatternTactic(p))
  /** partial: marks a tactic that is allowed to not close all its goals. */
  def partial                         = PartialTactic(this)
  //@todo Maybe support ?(e) or try(e) or optional(e) defined as this|skip

  override def toString: String = prettyString
  /** pretty-printed form of this Bellerophon tactic expression */
  def prettyString: String
}

abstract case class BuiltInTactic(name: String) extends BelleExpr {
  private[bellerophon] final def execute(provable: Provable): Provable = try {
    result(provable)
  } catch {
    case be: BelleError => throw be
    case t: Throwable => throw new BelleError(t.getMessage, t)
  }
  private[bellerophon] def result(provable : Provable): Provable
  override def prettyString = name
}
case class NamedTactic(name: String, tactic: BelleExpr) extends BelleExpr { override def prettyString = name }

/** ⎵: Placeholder for tactics. Reserved tactic expression */
object BelleDot extends BelleExpr { override def prettyString = ">>_<<" }

////////////////////////////////////////////////////////////////////////////////////////////////////
// Positional tactics
////////////////////////////////////////////////////////////////////////////////////////////////////

/** Applied at a position, turns into a tactic of type T. Turns a position (locator) into a tactic */
trait AtPosition[T <: BelleExpr] {
  /**
   * At a fixed position.
   * @param position The position where to apply.
   * @return The tactic.
   * @note Convenience wrapper
   * @see [[apply(locator: PositionLocator)]]
   */
  private[keymaerax] final def apply(position: Position): T = apply(Fixed(position))
  private[keymaerax] final def apply(position: Position, expected: Formula): T = apply(Fixed(position, Some(expected)))
  private[edu] final def apply(position: SeqPos): T = apply(Fixed(PositionConverter.convertPos(position)))
  /**
   * At a fixed position given through index numbers.
   * @param seqIdx The signed index in the sequent (strictly negative index for antecedent, strictly positive for succedent).
   * @param inExpr Where to apply inside the formula at index seqIdx.
   * @return The tactic.
   * @note Convenience wrapper
   * @see [[apply(position: Position)]]
   */
  final def apply(seqIdx: Int, inExpr: List[Int] = Nil): T = apply(Fixed(PositionConverter.convertPos(seqIdx, inExpr)))
  /**
   * Returns the tactic at the position identified by `locator`.
   * @param locator The locator symbol: 'L (find left), 'R (find right), '_ (find left/right appropriately for tactic),
   *                'Llast (at last position in antecedent), or 'Rlast (at last position in succedent).
   * @note Convenience wrapper
   * @see [[apply(locator: PositionLocator)]]
   * */
  final def apply(locator: Symbol): T = locator match {
    case 'L => apply(Find(0, None, AntePosition(0)))
    case 'R => apply(Find(0, None, SuccPosition(0)))
    case '_ => this match {
      case _: BuiltInLeftTactic => apply(Find(0, None, AntePosition(0)))
      case _: BuiltInRightTactic => apply(Find(0, None, SuccPosition(0)))
      case _ => throw new BelleError(s"Cannot determine whether this tactic is left/right. Please use 'L or 'R as appropriate.")
    }
    case 'Llast => apply(LastAnte(0))
    case 'Rlast => apply(LastSucc(0))
  }
  final def apply(locator: Symbol, expected: Formula): T = locator match {
    case 'L => apply(Find(0, Some(expected), AntePosition(0)))
    case 'R => apply(Find(0, Some(expected), SuccPosition(0)))
    case '_ => this match {
      case _: BuiltInLeftTactic => apply(Find(0, Some(expected), AntePosition(0)))
      case _: BuiltInRightTactic => apply(Find(0, Some(expected), SuccPosition(0)))
      case _ => throw new BelleError(s"Cannot determine whether this tactic is left/right. Please use 'L or 'R as appropriate.")
    }
      //@todo check Some(expected)
    case 'Llast => apply(LastAnte(0))
    case 'Rlast => apply(LastSucc(0))
  }

  /**
   * Returns the tactic at the position identified by `locator`.
   * @param locator The locator: Fixed, Find, LastAnte, or LastSucc
   * @return The tactic
   * @see [[PositionLocator]]
   */
  def apply(locator: PositionLocator): T
}

/** Generalizes the built in position tactics (normal, left, right) */
trait PositionalTactic extends BelleExpr with AtPosition[AppliedPositionTactic] {
  /** @note this should be called from within interpreters, but not by end-users */
  def computeResult(provable: Provable, position: Position): Provable
  final override def apply(locator: PositionLocator): AppliedPositionTactic = AppliedPositionTactic(this, locator)
}

abstract case class BuiltInPositionTactic(name: String) extends PositionalTactic {override def prettyString = name}

abstract case class BuiltInLeftTactic(name: String) extends BelleExpr with PositionalTactic {
  final override def computeResult(provable: Provable, position:Position) = position match {
    case p: AntePosition => computeAnteResult(provable, p)
    case _ => throw new BelleError("LeftTactics can only be applied at a AntePos")
  }

  def computeAnteResult(provable: Provable, pos: AntePosition): Provable

  override def prettyString = name

}

abstract case class BuiltInRightTactic(name: String) extends PositionalTactic {
  final override def computeResult(provable: Provable, position:Position) = position match {
    case p: SuccPosition => computeSuccResult(provable, p)
    case _ => throw new BelleError("RightTactics can only be applied at a SuccPos")
  }

  def computeSuccResult(provable: Provable, pos: SuccPosition) : Provable

  override def prettyString = name
}

/**
  * Stores the position tactic and position at which the tactic was applied.
  * Useful for storing execution traces.
  */
case class AppliedPositionTactic(positionTactic: BelleExpr with PositionalTactic, locator: PositionLocator) extends BelleExpr {
  import Augmentors._
  final def computeResult(provable: Provable) : Provable = try { locator match {
      case Fixed(pos, shape, exact) => shape match {
        case Some(f:Formula) =>
          require(provable.subgoals.size == 1, "Locator 'fixed with shape' applies only to provables with exactly 1 subgoal")
          //@note (implicit .apply needed to ensure subposition to pos.inExpr
          if ((exact && provable.subgoals.head.apply(pos) == f) ||
              (!exact && UnificationMatch.unifiable(f, provable.subgoals.head.apply(pos)).isDefined)) {
            positionTactic.computeResult(provable, pos)
          } else {
            throw new BelleError("Formula " + provable.subgoals.head.apply(pos) + " at position " + pos +
              " is not of expected shape " + f)
          }
        case None => positionTactic.computeResult(provable, pos)
      }
      case Find(goal, shape, start, exact) =>
        require(start.isIndexDefined(provable.subgoals(goal)), "Start position must be valid in sequent")
        tryAllAfter(provable, goal, shape, start, exact, null)
      case LastAnte(goal) => positionTactic.computeResult(provable, AntePosition(provable.subgoals(goal).ante.size-1))
      case LastSucc(goal) => positionTactic.computeResult(provable, SuccPosition(provable.subgoals(goal).succ.size-1))
    }
  } catch {
    case be: BelleError => throw be
    case t: Throwable => throw new BelleError(t.getMessage, t)
  }

  /** Recursively tries the position tactic at positions at or after pos in the specified provable. */
  private def tryAllAfter(provable: Provable, goal: Int, shape: Option[Formula], pos: Position, exact: Boolean,
                          cause: BelleError): Provable =
    if (pos.isIndexDefined(provable.subgoals(goal))) {
      try {
        shape match {
          case Some(f) if !exact && UnificationMatch.unifiable(f, provable.subgoals(goal)(pos)).isDefined =>
            positionTactic.computeResult(provable, pos)
          case Some(f) if !exact && UnificationMatch.unifiable(f, provable.subgoals(goal)(pos)).isEmpty =>
            tryAllAfter(provable, goal, shape, pos+1, exact, new BelleError(s"Formula is not of expected shape", cause))
          case Some(f) if exact && provable.subgoals(goal)(pos) == f => positionTactic.computeResult(provable, pos)
          case Some(f) if exact && provable.subgoals(goal)(pos) != f =>
            tryAllAfter(provable, goal, shape, pos+1, exact, new BelleError(s"Formula is not of expected shape", cause))
          case None => positionTactic.computeResult(provable, pos)
        }
      } catch {
        case e: Throwable =>
          val newCause = if (cause == null) new BelleError(s"Position tactic ${positionTactic.prettyString} is not " +
            s"applicable at ${pos.prettyString}", e)
          else new CompoundException(
            new BelleError(s"Position tactic ${positionTactic.prettyString} is not applicable at ${pos.prettyString}", e),
            cause)
          tryAllAfter(provable, goal, shape, pos+1, exact, newCause)
      }
    } else throw cause

  override def prettyString = positionTactic.prettyString + "(" + locator.prettyString + ")"
}

abstract case class BuiltInTwoPositionTactic(name: String) extends BelleExpr {
  /** @note this should be called from within interpreters, but not by end users. */
  def computeResult(provable : Provable, posOne: Position, posTwo: Position) : Provable

  /** Returns an explicit representation of the application of this tactic to the provided positions. */
  final def apply(posOne: Position, posTwo: Position) = AppliedTwoPositionTactic(this, posOne, posTwo)

  override def prettyString = name
}

/** Motivation is similar to [[AppliedPositionTactic]], but for [[BuiltInTwoPositionTactic]] */
case class AppliedTwoPositionTactic(positionTactic: BuiltInTwoPositionTactic, posOne: Position, posTwo: Position) extends BelleExpr {
  final def computeResult(provable: Provable) : Provable = try {
    positionTactic.computeResult(provable, posOne, posTwo)
  } catch {
    case be: BelleError => throw be
    case t: Throwable => throw new BelleError(t.getMessage, t)
  }

  override def prettyString = positionTactic.prettyString + "(" + posOne.prettyString + "," + posTwo.prettyString + ")"
}

/**
 * Dependent tactics compute a tactic to apply based on their input.
 * These tactics are probably not necessary very often, but are useful for idiomatic shortcuts.
 * See e.g., AtSubgoal.
 * @note similar to the ConstructionTactics in the old framework, except they should not be necessary
 *       nearly as often because BuiltIns have direct access to a Provable.
 * @param name The name of the tactic.
 * @todo is there a short lambda abstraction notation as syntactic sugar?
 */
abstract case class DependentTactic(name: String) extends BelleExpr {
  def computeExpr(provable: Provable): BelleExpr = throw new BelleError("Not implemented")
  def computeExpr(e: BelleValue with BelleError): BelleExpr = throw e
  /** Generic computeExpr; prefer overriding computeExpr(Provable) and computeExpr(BelleError) */
  def computeExpr(v : BelleValue): BelleExpr = try { v match {
      case BelleProvable(provable, _) => computeExpr(provable)
      case e: BelleError => computeExpr(e)
    }
  } catch {
    case be: BelleError => throw be
    case t: Throwable => if (DEBUG) t.printStackTrace(); throw new BelleError(t.getMessage, t)
  }
  override def prettyString: String = "DependentTactic(" + name + ")"
}
abstract class SingleGoalDependentTactic(override val name: String) extends DependentTactic(name) {
  def computeExpr(sequent: Sequent): BelleExpr
  final override def computeExpr(provable: Provable): BelleExpr = {
    require(provable.subgoals.size == 1, "Exactly 1 subgoal expected, but got " + provable.subgoals.size)
    computeExpr(provable.subgoals.head)
  }
}
abstract case class DependentPositionTactic(name: String) extends BelleExpr with AtPosition[DependentTactic] {
  final override def apply(locator: PositionLocator): AppliedDependentPositionTactic = new AppliedDependentPositionTactic(this, locator)
  override def prettyString: String = "DependentPositionTactic(" + name + ")"
  /** Create the actual tactic to be applied at position pos */
  def factory(pos: Position): DependentTactic
}
abstract case class InputTactic[T](name: SerializationName, input: T) extends BelleExpr {
  def computeExpr(): BelleExpr
  override def prettyString: String = "input(" + input + ")"
}
abstract case class InputPositionTactic[T](input: T, pos: Position) extends BelleExpr {
  def computeExpr(): BelleExpr
}

class AppliedDependentPositionTactic(val pt: DependentPositionTactic, locator: PositionLocator) extends DependentTactic(pt.name) {
  import Augmentors._
  final override def computeExpr(v: BelleValue): BelleExpr = locator match {
    case Fixed(pos, shape, exact) => shape match {
      case Some(f) => v match {
        case BelleProvable(provable, _) =>
          require(provable.subgoals.size == 1, "Locator 'fixed with shape' applies only to provables with exactly 1 subgoal")
          //@note (implicit .apply needed to ensure subposition to pos.inExpr
          if ((exact && provable.subgoals.head.apply(pos) == f) ||
            (!exact && UnificationMatch.unifiable(f, provable.subgoals.head.apply(pos)).isDefined)) {
            pt.factory(pos).computeExpr(v)
          } else {
            throw new BelleError("Formula " + provable.subgoals.head.apply(pos) + " at position " + pos +
              " is not of expected shape " + f)
          }
      }
      case None => pt.factory(pos).computeExpr(v)
    }
    case Find(goal, shape, start, exact) =>
      tryAllAfter(goal, shape, start, exact, null)
    case LastAnte(goal) => pt.factory(v match { case BelleProvable(provable, _) => AntePosition(provable.subgoals(goal).ante.size-1) })
    case LastSucc(goal) => pt.factory(v match { case BelleProvable(provable, _) => SuccPosition(provable.subgoals(goal).succ.size-1) })
  }

  /** Recursively tries the position tactic at positions at or after pos in the specified provable. */
  private def tryAllAfter(goal: Int, shape: Option[Formula], pos: Position, exact: Boolean,
                          cause: BelleError): DependentTactic = new DependentTactic(name) {
    override def computeExpr(v: BelleValue): BelleExpr = v match {
      case BelleProvable(provable, _) =>
        if (pos.isIndexDefined(provable.subgoals(goal))) {
          try {
            shape match {
              case Some(f) if !exact && UnificationMatch.unifiable(f, provable.subgoals(goal)(pos)).isDefined =>
                pt.factory(pos).computeExpr(v)
              case Some(f) if !exact && UnificationMatch.unifiable(f, provable.subgoals(goal)(pos)).isEmpty =>
                tryAllAfter(goal, shape, pos+1, exact, new BelleError(s"Formula is not of expected shape", cause))
              case Some(f) if exact && f == provable.subgoals(goal)(pos) => pt.factory(pos).computeExpr(v)
              case Some(f) if exact && f != provable.subgoals(goal)(pos) =>
                tryAllAfter(goal, shape, pos+1, exact, new BelleError(s"Formula is not of expected shape", cause))
              case None => pt.factory(pos).computeExpr(v)
            }
          } catch {
            case e: Throwable =>
              val newCause = if (cause == null) new BelleError(s"Dependent position tactic ${pt.prettyString} is not " +
                s"applicable at ${pos.prettyString}", e)
              else new CompoundException(
                new BelleError(s"Dependent position tactic ${pt.prettyString} is not applicable at ${pos.prettyString}", e),
                cause)
              tryAllAfter(goal, shape, pos+1, exact, newCause)
          }
        } else throw cause
      case _ => pt.factory(pos).computeExpr(v) // cannot search recursively, because don't know when to abort
    }
  }
}

/** A partial tactic is allowed to leave its subgoals around as unproved */
case class PartialTactic(child: BelleExpr, label: Option[BelleLabel] = None) extends BelleExpr {
  override def prettyString = label match {
    case Some(theLabel) => s"partial(${child.prettyString})@(${theLabel.prettyString})"
    case None => s"partial(${child.prettyString})"
  }
}

case class SeqTactic(left: BelleExpr, right: BelleExpr, override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "(" + left.prettyString + "&" + right.prettyString + ")" }
case class EitherTactic(left: BelleExpr, right: BelleExpr, override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "(" + left.prettyString + "|" + right.prettyString + ")" }
case class SaturateTactic(child: BelleExpr, annotation: BelleType, override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "(" + child.prettyString + ")*" }
case class RepeatTactic(child: BelleExpr, times: Int, annotation: BelleType, override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "(" + child.prettyString + ")*" + times }
case class BranchTactic(children: Seq[BelleExpr], override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "<( " + children.map(_.prettyString).mkString(", ") + " )" }
case class USubstPatternTactic(options: Seq[(BelleType, RenUSubst => BelleExpr)], override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "case { " + options.mkString(", ") + " }"}

/** @todo eisegesis
  * DoAll(e)(BelleProvable(p)) == < (e, ..., e) where e occurs p.subgoals.length times.
  */
case class DoAll(e: BelleExpr, override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "doall(" + e.prettyString + ")" }

/**
 * Bellerophon expressions that are values.
 */
trait BelleValue {
  def prettyString: String = toString
}
case class BelleProvable(p : Provable, label: Option[BelleLabel] = None) extends BelleExpr with BelleValue {
  override def toString: String = p.prettyString
  override def prettyString: String = p.prettyString
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Bellerophon Labels
////////////////////////////////////////////////////////////////////////////////////////////////////
trait BelleLabel {
  protected val LABEL_DELIMITER: String = ":"

  def prettyString : String = this match {
    case topLevel: BelleTopLevelLabel    => topLevel.label
    case BelleSubLabel(parent, theLabel) => parent.prettyString + LABEL_DELIMITER + theLabel
  }
  }
case class BelleTopLevelLabel(label: String) extends BelleLabel {require(!label.contains(LABEL_DELIMITER), s"Label should not contain the sublabel delimiter $LABEL_DELIMITER")}
case class BelleSubLabel(parent: BelleLabel, label: String)  extends BelleLabel {require(!label.contains(LABEL_DELIMITER), s"Label should not contain the sublabel delimiter $LABEL_DELIMITER")}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Bellerophon Types
////////////////////////////////////////////////////////////////////////////////////////////////////

/** @todo eisegesis -- simple types */
trait BelleType
case class TheType() extends BelleType
/** @todo Added because SequentTypes are needed for unification tactics. */
case class SequentType(s : Sequent) extends BelleType

