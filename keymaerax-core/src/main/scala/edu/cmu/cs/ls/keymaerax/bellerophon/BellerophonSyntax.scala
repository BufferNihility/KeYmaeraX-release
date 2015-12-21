package edu.cmu.cs.ls.keymaerax.bellerophon

import edu.cmu.cs.ls.keymaerax.btactics._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.tactics.{AntePosition, SuccPosition, Position, PosInExpr}

/**
 * Algebraic Data Type whose elements are well-formed Bellephoron expressions.
 * See Table 1 of "Bellerophon: A Typed Language for Automated Deduction in a Uniform Substitution Calculus"
 * @author Nathan Fulton
 */
abstract class BelleExpr(val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) {
  // Syntactic sugar for combinators.
  //@todo copy documentation
  def &(other: BelleExpr)             = SeqTactic(this, other)
  def |(other: BelleExpr)             = EitherTactic(this, other)
  def *@(annotation: BelleType)       = SaturateTactic(this, annotation)
  def *(times: Int/*, annotation: BelleType*/) = RepeatTactic(this, times, null)
  def <(children: BelleExpr*)         = SeqTactic(this, BranchTactic(children))
  def U(p: (SequentType, RenUSubst => BelleExpr)*) = SeqTactic(this, USubstPatternTactic(p))
  def partial                         = PartialTactic(this)

  override def toString: String = prettyString
  /** pretty-printed form of this Bellerophon expression */
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

/** ⎵: Placeholder for tactics. Reserved tactic expression */
object BelleDot extends BelleExpr { override def prettyString = ">>_<<" }

////////////////////////////////////////////////////////////////////////////////////////////////////
// Positional tactics
////////////////////////////////////////////////////////////////////////////////////////////////////

/** Turns a position (locator) into a tactic */
trait AtPosition[T <: BelleExpr] {
  /**
   * At a fixed position.
   * @param position The position where to apply.
   * @return The tactic.
   * @note Convenience wrapper
   * @see [[apply(locator: PositionLocator)]]
   */
  final def apply(position: Position): T = apply(Fixed(position))
  /**
   * At a fixed position given through index numbers.
   * @param seqIdx The index in the sequent (strictily negative index for antecedent, strictly positive for succedent).
   * @param inExpr Where to apply inside the formula at index seqIdx.
   * @return The tactic.
   * @note Convenience wrapper
   * @see [[apply(position: Position)]]
   */
  final def apply(seqIdx: Int, inExpr: List[Int] = Nil): T = apply(PositionConverter.convertPos(seqIdx, inExpr))
  /**
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

  /**
   * At a position identified by the locator.
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
  final def computeResult(provable: Provable) : Provable = try { locator match {
      case Fixed(pos, shape, exact) => shape match {
        case Some(f) =>
          require(provable.subgoals.size == 1, "Locator 'fixed with shape' applies only to provables with exactly 1 subgoal")
          if ((exact && provable.subgoals.head(pos) == f) ||
              (!exact && UnificationMatch.unifiable(f, provable.subgoals.head(pos)).isDefined)) {
            positionTactic.computeResult(provable, pos)
          } else {
            throw new BelleError("Formula " + provable.subgoals.head(pos) + " at position " + pos +
              " is not of expected shape " + f)
          }
        case None => positionTactic.computeResult(provable, pos)
      }
      case Find(goal, shape, start, exact) =>
        require(start.isIndexDefined(provable.subgoals(goal)), "Start position must be valid in sequent")
        tryAllAfter(provable, goal, shape, start, exact, null)
      case LastAnte(goal) => positionTactic.computeResult(provable, new AntePosition(provable.subgoals(goal).ante.size-1))
      case LastSucc(goal) => positionTactic.computeResult(provable, new SuccPosition(provable.subgoals(goal).succ.size-1))
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
 */
abstract case class DependentTactic(name: String) extends BelleExpr {
  def computeExpr(provable: Provable): BelleExpr = throw new BelleError("Not implemented")
  def computeExpr(e: BelleValue with BelleError): BelleExpr = throw e
  /** Generic computeExpr; prefer overriding computeExpr(Provable) and computeExpr(BelleError) */
  def computeExpr(v : BelleValue): BelleExpr = try { v match {
      case BelleProvable(provable) => computeExpr(provable)
      case e: BelleError => computeExpr(e)
    }
  } catch {
    case be: BelleError => throw be
    case t: Throwable => throw new BelleError(t.getMessage, t)
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
abstract case class InputTactic[T](input: T) extends BelleExpr {
  def computeExpr(): BelleExpr
  override def prettyString: String = "input(" + input + ")"
}

class AppliedDependentPositionTactic(val pt: DependentPositionTactic, locator: PositionLocator) extends DependentTactic(pt.name) {
  final override def computeExpr(v: BelleValue): BelleExpr = locator match {
    case Fixed(pos, shape, exact) => shape match {
      case Some(f) => v match {
        case BelleProvable(provable) =>
          require(provable.subgoals.size == 1, "Locator 'fixed with shape' applies only to provables with exactly 1 subgoal")
          if ((exact && provable.subgoals.head(pos) == f) ||
            (!exact && UnificationMatch.unifiable(f, provable.subgoals.head(pos)).isDefined)) {
            pt.factory(pos).computeExpr(v)
          } else {
            throw new BelleError("Formula " + provable.subgoals.head(pos) + " at position " + pos +
              " is not of expected shape " + f)
          }
      }
      case None => pt.factory(pos).computeExpr(v)
    }
    case Find(goal, shape, start, exact) =>
      tryAllAfter(goal, shape, start, exact, null)
    case LastAnte(goal) => pt.factory(v match { case BelleProvable(provable) => new AntePosition(provable.subgoals(goal).ante.size-1) })
    case LastSucc(goal) => pt.factory(v match { case BelleProvable(provable) => new SuccPosition(provable.subgoals(goal).succ.size-1) })
  }

  /** Recursively tries the position tactic at positions at or after pos in the specified provable. */
  private def tryAllAfter(goal: Int, shape: Option[Formula], pos: Position, exact: Boolean,
                          cause: BelleError): DependentTactic = new DependentTactic(name) {
    override def computeExpr(v: BelleValue): BelleExpr = v match {
      case BelleProvable(provable) =>
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
case class PartialTactic(child: BelleExpr) extends BelleExpr { override def prettyString = "partial(" + child.prettyString + ")" }

case class SeqTactic(left: BelleExpr, right: BelleExpr, override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "(" + left.prettyString + "&" + right.prettyString + ")" }
case class EitherTactic(left: BelleExpr, right: BelleExpr, override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "(" + left.prettyString + "|" + right.prettyString + ")" }
//case class ExactIterTactic(child: BelleExpr, count: Int) extends BelleExpr
case class SaturateTactic(child: BelleExpr, annotation: BelleType, override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "(" + child.prettyString + ")*" }
case class RepeatTactic(child: BelleExpr, times: Int, annotation: BelleType, override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "(" + child.prettyString + ")*" + times }
case class BranchTactic(children: Seq[BelleExpr], override val location: Array[StackTraceElement] = Thread.currentThread().getStackTrace) extends BelleExpr { override def prettyString = "<( " + children.map(_.prettyString).mkString(", ") + " )" }
//case class OptionalTactic(child: BelleExpr) extends BelleExpr
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
case class BelleProvable(p : Provable) extends BelleExpr with BelleValue {
  override def toString: String = p.prettyString
  override def prettyString: String = p.prettyString
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Bellerophon Types
////////////////////////////////////////////////////////////////////////////////////////////////////

/** @todo eisegesis -- simple types */
trait BelleType
case class TheType() extends BelleType
/** @todo Added because SequentTypes are needed for unification tactics. */
case class SequentType(s : Sequent) extends BelleType

////////////////////////////////////////////////////////////////////////////////////////////////////
// Errors
////////////////////////////////////////////////////////////////////////////////////////////////////

//@todo extend some ProverException and use the inherited inContext functionality throughout the interpreter.
class BelleError(message: String, cause: Throwable = null)
    extends ProverException(s"[Bellerophon Runtime] $message", if (cause != null) cause else new Throwable(message)) {
  /* @note mutable state for gathering the logical context that led to this exception */
  private var tacticContext: BelleExpr = BelleDot  //@todo BelleUnknown?
  def context: BelleExpr = tacticContext
  def inContext(context: BelleExpr, additionalMessage: String): BelleError = {
    this.tacticContext = context
    context.location.find(e => !("Thread.java"::"BellerophonSyntax.scala"::"SequentialInterpreter.scala"::Nil).contains(e.getFileName)) match {
      case Some(location) => getCause.setStackTrace(location +: getCause.getStackTrace)
      case None => // no specific stack trace element outside the tactic framework found -> nothing to do
    }
    super.inContext(context.prettyString, additionalMessage)
    this
  }
  override def toString: String = super.toString + "\nin " + tacticContext
}

case class BelleUserGeneratedError(message: String) extends BelleError(s"[Bellerophon User-Generated Message] $message")

class CompoundException(left: BelleError, right: BelleError)
  extends BelleError(s"Left Message: ${left.getMessage}\nRight Message: ${right.getMessage})")

object PositionConverter {
  def convertPos(seqIdx: Int, inExpr: List[Int] = Nil): Position = {
    require(seqIdx != 0, "Sequent index must be strictly negative (antecedent) or strictly positive (succedent)")
    if (seqIdx < 0) new AntePosition(-seqIdx - 1, PosInExpr(inExpr))
    else new SuccPosition(seqIdx - 1, PosInExpr(inExpr))
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Errors
////////////////////////////////////////////////////////////////////////////////////////////////////


///**
//  * Abstract positions are either actual positions, or else indicate that the tactic should point back to a position
//  * that was generated by a previous tactic.
//  *
//  * Example:
//  * {{{
//  *   AndR(SuccPos(2)) <(
//  *     ImplyR(GeneratedPosition()) & TrivialCloser,
//  *     ImplyR(GeneratedPosition()) & TrivialCloser
//  *   )
//  * }}}
//  *
//  * is equivalent to:
//  *
//  * {{{
//  *   AndR(SuccPos(2)) <(
//  *     ImplyR(SuccPos(2)) & ...,
//  *     ImplyR(SuccPos(2)) & ...
//  *   )
//  * }}}
//  *
//  * @todo Not currently using these; one thing at at a time.
//  */
//sealed trait AbstractPosition
//case class AbsolutePosition(p : Position) extends AbstractPosition
//case class GeneratedPosition()              extends AbstractPosition