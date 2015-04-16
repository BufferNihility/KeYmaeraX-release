/**
 * Differential Dynamic Logic expression data structures.
 * @author aplatzer
 * @see "Andre Platzer. A uniform substitution calculus for differential dynamic logic.  arXiv 1503.01981, 2015."
 * @see "Andre Platzer. The complete proof theory of hybrid systems. ACM/IEEE Symposium on Logic in Computer Science, LICS 2012, June 25–28, 2012, Dubrovnik, Croatia, pages 541-550. IEEE 2012"
 */
package edu.cmu.cs.ls.keymaera.kernel

// require favoring immutable Seqs for soundness

import scala.collection.immutable
import scala.collection.immutable.Seq
import scala.collection.immutable.IndexedSeq

import scala.collection.immutable.List
import scala.collection.immutable.Map
import scala.collection.immutable.Set

import scala.annotation.{tailrec, elidable}
import scala.annotation.elidable._

import scala.math._

import edu.cmu.cs.ls.keymaera.parser.KeYmaeraPrettyPrinter  // external

/*******************************
 * Sorts
 */
sealed abstract class Sort

/**
 * Unit type of Nothing
 */
object Unit extends Sort

/**
 * Sort of booleans: true, false
 */
object Bool extends Sort

/**
 * Sort of real numbers: 0, 1, 2.5
 */
object Real extends Sort

/**
 * User-defined object sort
 * @todo replace by statically checkable types?
 */
case class ObjectSort(name : String) extends Sort


/**
 * Expressions of differential dynamic logic.
 * @author aplatzer
 */
sealed trait Expr[K<:Expr] extends K {
  override def toString = "(" + prettyString() + ")"
  def prettyString() = ??? //new KeYmaeraPrettyPrinter().stringify(this)
}

sealed trait Atomic[K<:Expr] extends Expr[K]
sealed trait Composite[K<:Expr] extends Expr[K]

sealed trait NamedSymbol[K<:Expr] extends Expr[K] {}

/********************************************
 * Terms of differential dynamic logic.
 * @author aplatzer
 * @todo For Term but not the others could move to Term[T<Sort] to statically distinguish Term[Real] from Term[ObjectSort] even if not statically distinguishing Term[ObjectSort("A")] from Term[ObjectSort("B")] :-)
 * @todo Alternatively could duplicate Equals/NotEquals/FuncOf/Function/Variable for non-real sorts :-/
 */
sealed trait Term[S<:Sort] extends Expr[Term] {}

// atomic terms
sealed trait AtomicTerm[S<:Sort] extends Term[S] with Atomic[Term] {}

sealed case class Variable[S<:Sort](name: String, index: Option[Int] = None/*, sort: S*/) extends NamedSymbol[Term] with AtomicTerm[S] {}
sealed case class DifferentialSymbol(e: Variable[Real.type]/*@todo NamedSymbol[Real.type]?*/) extends NamedSymbol[Term] with AtomicTerm[Real.type] {}

case class Number(value: BigDecimal) extends AtomicTerm[Real.type]

sealed case class Function[D<:Sort,R<:Sort](name: String/*, domain: D, sort: R*/) extends Expr[Function] with NamedSymbol[Function] {}

object DotTerm extends AtomicTerm[Real.type] {
  override def toString = ("\\cdot")
}

//@todo to check within ObjectSorts add require(func.domain == child.sort) which in principle could be dead-code eliminated for Real?
case class FuncOf[D<:Sort,S<:Sort](func: Function[D,S], child: Term[D]) extends AtomicTerm[S]

// composite terms
sealed trait CompositeTerm[S<:Sort] extends Term[S] with Composite[Term] {}

case class Plus(left: Term[Real.type], right: Term[Real.type]) extends CompositeTerm[Real.type]
case class Minus(left: Term[Real.type], right: Term[Real.type]) extends CompositeTerm[Real.type]
case class Times(left: Term[Real.type], right: Term[Real.type]) extends CompositeTerm[Real.type]
case class Divide(left: Term[Real.type], right: Term[Real.type]) extends CompositeTerm[Real.type]
case class Power(left: Term[Real.type], right: Number) extends CompositeTerm[Real.type]

case class Differential(child: Term[Real.type]) extends CompositeTerm[Real.type]

/********************************************
 * Formulas of differential dynamic logic.
 * @author aplatzer
 */

sealed trait Formula extends Expr[Formula]

// atomic formulas
sealed trait AtomicFormula extends Formula with Atomic[Formula] {}

object True extends AtomicFormula
object False extends AtomicFormula

case class Equal[S<:Sort](left: Term[S], right: Term[S]) extends AtomicFormula
//@todo require(left.sort == right.sort) which in principle could be dead-code eliminated for Real?
case class NotEqual[S<:Sort](left: Term[S], right: Term[S]) extends AtomicFormula
//@todo require(left.sort == Real && right.sort == Real) in the following

case class GreaterEqual(left: Term[Real.type], right: Term[Real.type]) extends AtomicFormula
case class Greater(left: Term[Real.type], right: Term[Real.type]) extends AtomicFormula
case class LessEqual(left: Term[Real.type], right: Term[Real.type]) extends AtomicFormula
case class Less(left: Term[Real.type], right: Term[Real.type]) extends AtomicFormula

object DotFormula extends AtomicFormula {
  override def toString = "\\_"
}

case class PredOf[D<:Sort](pred: Function[D,Bool.type], child: Term[D]) extends AtomicFormula
case class PredicationalOf(pred: Function[Bool.type,Bool.type], child: Formula) extends AtomicFormula

// composite formulas
sealed trait CompositeFormula extends Formula with Composite[Formula] {}

case class Not(child: Formula) extends CompositeFormula
case class And(left: Formula, right:Formula) extends CompositeFormula
case class Or(left: Formula, right:Formula) extends CompositeFormula
case class Imply(left: Formula, right:Formula) extends CompositeFormula
case class Equiv(left: Formula, right:Formula) extends CompositeFormula

case class Forall(vars: immutable.Seq[Variable], child: Formula) extends CompositeFormula {
  require(!vars.isEmpty, "quantifiers bind at least one variable")
  require(vars.distinct.size == vars.size, "no duplicates within one quantifier block")
}
case class Exists(vars: immutable.Seq[Variable], child: Formula) extends CompositeFormula {
  require(!vars.isEmpty, "quantifiers bind at least one variable")
  require(vars.distinct.size == vars.size, "no duplicates within one quantifier block")
}

case class Box(program: Program, child: Formula) extends CompositeFormula
case class Diamond(program: Program, child: Formula) extends CompositeFormula

case class DifferentialFormula(child: Formula) extends CompositeFormula

/********************************************
  * Hybrid programs of differential dynamic logic.
  * @author aplatzer
  */

sealed trait Program extends Expr[Program]

// atomic programs
sealed trait AtomicProgram extends Program with Atomic[Program] {}

sealed case class ProgramConst(name: String) extends NamedSymbol[Program] with AtomicProgram {}

//@todo require(target.sort == e.sort) which in principle could be dead-code eliminated for Real?
case class Assign[S<:Sort](target: Variable[S], e: Term[S]) extends AtomicProgram
case class DiffAssign(target: DifferentialSymbol, e: Term[Real.type]) extends AtomicProgram
case class AssignAny[S<:Sort](target: Variable[S]) extends AtomicProgram
case class Test(cond: Formula) extends AtomicProgram
case class ODESystem(ode: DifferentialProgram, constraint: Formula) extends Program

// composite programs
sealed trait CompositeProgram extends Program with Composite[Program] {}
case class Choice(left: Program, right: Program) extends Program {}
case class Compose(left: Program, right: Program) extends Program {}
case class Loop(child: Program) extends Program {}
//case class Dual(child: Program) extends Program {}

// differential programs
sealed trait DifferentialProgram extends Program {}
sealed case class DifferentialProgramConst(name: String) extends NamedSymbol[Program] with DifferentialProgram {}
case class AtomicODE(xp: DifferentialSymbol, e: Term[Real.type]) extends DifferentialProgram {}
case class DifferentialProduct(left: DifferentialProgram, right: DifferentialProgram) extends DifferentialProgram {}

object DifferentialProduct {
  /**
   * Construct an ODEProduct in reassociated normal form, i.e. as a list such that left will never be an ODEProduct in the data structures.
   * @note This is important to not get stuck after using axiom "DE differential effect (system)".
   */
  def apply(left: DifferentialProgram, right : DifferentialProgram): DifferentialProduct = reassociate(left, right)

  //@tailrec
  private def reassociate(left: DifferentialProgram, right : DifferentialProgram): DifferentialProduct = left match {
    // properly associated cases
    case l:AtomicODE => new DifferentialProduct(l, right)
    case l:DifferentialProgramConst => new DifferentialProduct(l, right)
    // reassociate
    case DifferentialProduct(ll, lr) => reassociate(ll, reassociate(lr, right))
  }
  //@todo ensuring(same list of AtomicODE)
}
//@todo either enforce auto-normalization during construction via an apply method? :-)
//@todo Or flexibilize equals to be as sets that is modulo associative/commutative but possibly breaking symmetry and all kinds of things :-(