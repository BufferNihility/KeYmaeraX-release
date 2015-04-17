/**
 * Differential Dynamic Logic expression data structures.
 * @author aplatzer
 * @see "Andre Platzer. A uniform substitution calculus for differential dynamic logic.  arXiv 1503.01981, 2015."
 * @see "Andre Platzer. The complete proof theory of hybrid systems. ACM/IEEE Symposium on Logic in Computer Science, LICS 2012, June 25–28, 2012, Dubrovnik, Croatia, pages 541-550. IEEE 2012"
 */
package edu.cmu.cs.ls.keymaera.nano

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

//import edu.cmu.cs.ls.keymaera.parser.KeYmaeraPrettyPrinter  // external

/*******************************
  * Kinds of expressions
  */
sealed abstract class Kind
object TermKind extends Kind { override def toString = "Term" }
object FormulaKind extends Kind { override def toString = "Formula" }
object ProgramKind extends Kind { override def toString = "Program" }
object FunctionKind extends Kind { override def toString = "Function" }

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
 * Sort of state transformations (for programs)
 */
object Trafo extends Sort
/**
 * User-defined object sort
 */
case class ObjectSort(name : String) extends Sort

/**
 * Expressions of differential dynamic logic.
 * @author aplatzer
 */
sealed trait Expr {
  def kind : Kind
  def sort : Sort
  override def toString = "(" + prettyString() + ")"
  def prettyString() : String = "TODOTODO???TODO" //new KeYmaeraPrettyPrinter().stringify(this)
}

sealed trait Atomic extends Expr
sealed trait Composite extends Expr

sealed trait NamedSymbol extends Expr

/********************************************
 * Terms of differential dynamic logic.
 * @author aplatzer
 */
sealed trait Term extends Expr {
  final def kind = TermKind
}

// atomic terms
sealed trait AtomicTerm extends Term with Atomic {}

/**
 * real terms
 */
sealed trait RTerm extends Term {
  final def sort = Real
}

sealed case class Variable(name: String, index: Option[Int] = None, sort: Sort) extends NamedSymbol with AtomicTerm
sealed case class RVariable(name: String, index: Option[Int] = None) extends NamedSymbol with AtomicTerm with RTerm
sealed case class DifferentialSymbol(e: RVariable) extends NamedSymbol with AtomicTerm with RTerm

case class Number(value: BigDecimal) extends AtomicTerm with RTerm

sealed case class Function(name: String, domain: Sort, sort: Sort) extends Expr with NamedSymbol {
  def kind = FunctionKind
}

object DotTerm extends NamedSymbol with AtomicTerm with RTerm {
  override def toString = ("\\cdot")
}

object Nothing extends NamedSymbol with AtomicTerm {
  def sort = Unit
  override def toString = ("\\nothing")
}
object Anything extends NamedSymbol with AtomicTerm with RTerm {
  override def toString = ("\\anything")
}

case class FuncOf(func: Function, child: Term) extends AtomicTerm {
  def sort = func.sort
  require(child.sort == func.domain)
}

// composite terms
sealed trait CompositeTerm extends Term with Composite {}

case class Plus(left: RTerm, right: RTerm) extends CompositeTerm with RTerm
case class Minus(left: RTerm, right: RTerm) extends CompositeTerm with RTerm
case class Times(left: RTerm, right: RTerm) extends CompositeTerm with RTerm
case class Divide(left: RTerm, right: RTerm) extends CompositeTerm with RTerm
case class Power(left: RTerm, right: Number) extends CompositeTerm with RTerm

case class Differential(child: RTerm) extends CompositeTerm with RTerm

/********************************************
 * Formulas of differential dynamic logic.
 * @author aplatzer
 */

sealed trait Formula extends Expr {
  final def kind = FormulaKind
  final def sort = Bool
}

// atomic formulas
sealed trait AtomicFormula extends Formula with Atomic {}

object True extends AtomicFormula
object False extends AtomicFormula

case class Equal(left: Term, right: Term) extends AtomicFormula {
  require(left.sort == right.sort)
}
case class NotEqual(left: Term, right: Term) extends AtomicFormula {
  require(left.sort == right.sort)
}

case class GreaterEqual(left: RTerm, right: RTerm) extends AtomicFormula
case class Greater(left: RTerm, right: RTerm) extends AtomicFormula
case class LessEqual(left: RTerm, right: RTerm) extends AtomicFormula
case class Less(left: RTerm, right: RTerm) extends AtomicFormula

object DotFormula extends NamedSymbol with AtomicFormula {
  override def toString = "\\_"
}

case class PredOf(pred: Function, child: Term) extends AtomicFormula {
  require(child.sort == pred.domain)
}
case class PredicationalOf(pred: Function, child: Formula) extends AtomicFormula {
  require(pred.sort == Bool)
}

// composite formulas
sealed trait CompositeFormula extends Formula with Composite {}

case class Not(child: Formula) extends CompositeFormula
case class And(left: Formula, right:Formula) extends CompositeFormula
case class Or(left: Formula, right:Formula) extends CompositeFormula
case class Imply(left: Formula, right:Formula) extends CompositeFormula
case class Equiv(left: Formula, right:Formula) extends CompositeFormula

case class Forall(vars: immutable.Seq[Variable], child: Formula) extends CompositeFormula {
  require(!vars.isEmpty, "quantifiers bind at least one variable")
  require(vars.distinct.size == vars.size, "no duplicates within one quantifier block")
  //@todo require all vars have the same sort?
}
case class Exists(vars: immutable.Seq[Variable], child: Formula) extends CompositeFormula {
  require(!vars.isEmpty, "quantifiers bind at least one variable")
  require(vars.distinct.size == vars.size, "no duplicates within one quantifier block")
  //@todo require all vars have the same sort?
}

case class Box(program: Program, child: Formula) extends CompositeFormula
case class Diamond(program: Program, child: Formula) extends CompositeFormula

case class DifferentialFormula(child: Formula) extends CompositeFormula

/********************************************
  * Hybrid programs of differential dynamic logic.
  * @author aplatzer
  */

sealed trait Program extends Expr {
  final def kind = ProgramKind
  final def sort = Trafo
}

// atomic programs
sealed trait AtomicProgram extends Program with Atomic {}

sealed case class ProgramConst(name: String) extends NamedSymbol with AtomicProgram {}

case class Assign(target: Variable, e: Term) extends AtomicProgram {
  require(e.sort == target.sort)
}
case class DiffAssign(target: DifferentialSymbol, e: RTerm) extends AtomicProgram
case class AssignAny(target: Variable) extends AtomicProgram
case class Test(cond: Formula) extends AtomicProgram
case class ODESystem(ode: DifferentialProgram, constraint: Formula) extends Program

// composite programs
sealed trait CompositeProgram extends Program with Composite {}
case class Choice(left: Program, right: Program) extends Program {}
case class Compose(left: Program, right: Program) extends Program {}
case class Loop(child: Program) extends Program {}
//case class Dual(child: Program) extends Program {}

// differential programs
sealed trait DifferentialProgram extends Program {}
sealed case class DifferentialProgramConst(name: String) extends NamedSymbol with DifferentialProgram {}
case class AtomicODE(xp: DifferentialSymbol, e: RTerm) extends DifferentialProgram {}
case class DifferentialProduct(left: DifferentialProgram, right: DifferentialProgram) extends DifferentialProgram {}

object DifferentialProduct {
  /**
   * Construct an ODEProduct in reassociated normal form, i.e. as a list such that left will never be an ODEProduct in the data structures.
   * @note This is important to not get stuck after using axiom "DE differential effect (system)".\
   * @todo defined twice. So either demote DifferentialProduct to be a non-case-class. Or convention to call normalDifferentialProduct instead.
   */
  def DifferentialProduct/*@TODO apply*/(left: DifferentialProgram, right : DifferentialProgram): DifferentialProduct = reassociate(left, right)

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