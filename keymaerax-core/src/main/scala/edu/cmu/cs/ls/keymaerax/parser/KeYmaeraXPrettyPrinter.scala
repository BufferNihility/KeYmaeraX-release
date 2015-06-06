/**
 * Differential Dynamic Logic pretty printer in KeYmaera X notation.
 * @author aplatzer
 * @see "Andre Platzer. A uniform substitution calculus for differential dynamic logic.  arXiv 1503.01981, 2015."
 */
package edu.cmu.cs.ls.keymaerax.parser

import edu.cmu.cs.ls.keymaerax.core._

import scala.math.Ordered

private abstract class OpAssociativity
private object AtomicFormat extends OpAssociativity
private object UnaryFormat extends OpAssociativity
private object LeftAssociative extends OpAssociativity
private object RightAssociative extends OpAssociativity
private object NonAssociative extends OpAssociativity

/**
 * Operator notation
 * @param opcode operator code used for string representation
 * @param prec unique precedence where smaller numbers indicate stronger binding
 * @param assoc associativity of this operator
 */
private case class OpNotation(opcode: String, prec: Int, assoc: OpAssociativity) extends Ordered[OpNotation] {
  def compare(other: OpNotation): Int = {
    prec - other.prec
  } ensuring(r => r!=0 || this==other, "precedence assumed unique " + this + " compared to " + other)
}


/**
 * KeYmaera X Pretty Printer.
 * @author aplatzer
 * @todo Augment with ensuring postconditions that check correct reparse non-recursively.
 */
class KeYmaeraXPrettyPrinter {

  /** Pretty-print expression to a string */
  def stringify(expr: Expression): String = expr match {
    case t: Term => stringify(t)
    case f: Formula => stringify(f)
    case p: Program => stringify(p)
  }

  /** Pretty-print term to a string */
  def stringify(term: Term): String = pp(term)

  /** Pretty-print formula to a string */
  def stringify(formula: Formula): String = pp(formula)

  /** Pretty-print program to a string */
  def stringify(program: Program): String = pp(program)

  private def pp(term: Term): String = term match {
    case x: Variable => x.toString
    case DifferentialSymbol(x) => pp(x) + op(term).opcode
    case Number(n) => n.toString()
    case FuncOf(f, c) => f.toString + "(" + pp(c) + ")"
    case DotTerm => op(DotTerm).opcode
    case Anything => op(Anything).opcode
    case Nothing => op(Nothing).opcode
    case Differential(t) => "(" + pp(t) + ")" + op(term).opcode
    case Pair(l, r) => "(" + pp(l) + op(term).opcode + pp(r) + ")"
    case t: UnaryCompositeTerm => op(t).opcode + pp(t.child)
    case t: BinaryCompositeTerm =>
      (if (op(t.left) > op(t) || op(t.left)==op(t) && op(t).assoc!=LeftAssociative) "(" + pp(t.left) + ")" else pp(t.left)) +
        op(t).opcode +
        (if (op(t.right) > op(t) || op(t.right)==op(t) && op(t).assoc!=RightAssociative) "(" + pp(t.right) + ")" else pp(t.right))
  }

  private def pp(formula: Formula): String = formula match {
    case True => op(True).opcode
    case False => op(False).opcode
    case PredOf(p, c) => p.toString + "(" + pp(c) + ")"
    case PredicationalOf(p, c) => p.toString + "{" + pp(c) + "}"
    case DotFormula=> op(DotFormula).opcode
    case f: ComparisonFormula => pp(f.left) + op(formula).opcode + pp(f.right)
    case DifferentialFormula(g) => "(" + pp(g) + ")" + op(formula).opcode
    case f: Quantified => op(formula).opcode + f.vars.mkString(",") + /**/"."/**/ + pp(f.child)
    case f: Box => "[" + pp(f.program) + "]" + pp(f.child)
    case f: Diamond => "<" + pp(f.program) + ">" + pp(f.child)
    case g: UnaryCompositeFormula => op(g).opcode + pp(g.child)
    case t: BinaryCompositeFormula =>
      (if (op(t.left) > op(t) || op(t.left)==op(t) && op(t).assoc!=LeftAssociative) "(" + pp(t.left) + ")" else pp(t.left)) +
        op(t).opcode +
        (if (op(t.right) > op(t) || op(t.right)==op(t) && op(t).assoc!=RightAssociative) "(" + pp(t.right) + ")" else pp(t.right))
  }

  private def pp(program: Program): String = program match {
    case ProgramConst(a) => a + ";"
    case Assign(x, e) => pp(x) + op(program).opcode + pp(e) + ";"
    case AssignAny(x) => pp(x) + op(program).opcode + ";"
    case DiffAssign(xp, e) => pp(xp) + op(program).opcode + pp(e) + ";"
    case Test(f) => op(program).opcode + pp(f) + ";"
    case p: DifferentialProgram => pp(p)
    case p: UnaryCompositeProgram => op(p).opcode + pp(p.child)
    case t: BinaryCompositeProgram=>
      (if (op(t.left) > op(t) || op(t.left)==op(t) && op(t).assoc!=LeftAssociative) "{" + pp(t.left) + "}" else pp(t.left)) +
        op(t).opcode +
        (if (op(t.right) > op(t) || op(t.right)==op(t) && op(t).assoc!=RightAssociative) "{" + pp(t.right) + "}" else pp(t.right))
  }

  private def pp(program: DifferentialProgram): String = program match {
    case ODESystem(ode, f) => "{" + pp(ode) + op(program).opcode + pp(f) + "}"
    case DifferentialProgramConst(a) => a.toString
    case AtomicODE(xp, e) => pp(xp) + op(program).opcode + pp(e)
    case t: DifferentialProduct =>
      (if (op(t.left) > op(t) || op(t.left)==op(t) && op(t).assoc!=LeftAssociative) "{" + pp(t.left) + "}" else pp(t.left)) +
        op(t).opcode +
        (if (op(t.right) > op(t) || op(t.right)==op(t) && op(t).assoc!=RightAssociative) "{" + pp(t.right) + "}" else pp(t.right))
  }

  /** The operator code of the top-level operator of expr */
  private def op(expr: Expression) = expr match {
    case t: Variable     => OpNotation("???",   0, AtomicFormat)
    case t: Number       => OpNotation("???",   0, AtomicFormat)
    case t: DifferentialSymbol => OpNotation("'",    10, AtomicFormat)
    case t: Differential => OpNotation("'",    10, UnaryFormat)
    case t: FuncOf     => OpNotation("???",   0, AtomicFormat)
    case t: Neg          => OpNotation("-",    11, UnaryFormat)
    case t: Power        => OpNotation("^",    20, RightAssociative)
    case t: Times        => OpNotation("*",    30, LeftAssociative)
    case t: Divide       => OpNotation("/",    40, LeftAssociative)
    case t: Plus         => OpNotation("+",    50, LeftAssociative)
    case t: Minus        => OpNotation("-",    60, LeftAssociative)
    case t: Pair         => OpNotation(",",     2, RightAssociative)
    case DotTerm         => OpNotation("•",     0, AtomicFormat)
    case Nothing         => OpNotation("",      0, AtomicFormat)
    case Anything        => OpNotation("?",     0, AtomicFormat)

    case t: DifferentialFormula => OpNotation("'", 80, UnaryFormat)
    case t: PredOf     => OpNotation("???",   0, AtomicFormat)
    case t: PredicationalOf => OpNotation("???",   0, AtomicFormat)
    case DotFormula      => OpNotation("_",     0, AtomicFormat)
    case True            => OpNotation("true",  0, AtomicFormat)
    case False           => OpNotation("false", 0, AtomicFormat)
    case f: Equal        => OpNotation("=",    90, NonAssociative)
    case f: NotEqual     => OpNotation("!=",   90, NonAssociative)
    case f: GreaterEqual => OpNotation(">=",   90, NonAssociative)
    case f: Greater      => OpNotation(">",    90, NonAssociative)
    case f: LessEqual    => OpNotation("<=",   90, NonAssociative)
    case f: Less         => OpNotation("<",    90, NonAssociative)
    case f: Forall       => OpNotation("\\forall",96, UnaryFormat)
    case f: Exists       => OpNotation("\\exists",97, UnaryFormat)
    case f: Box          => OpNotation("[]",   98, UnaryFormat)
    case f: Diamond      => OpNotation("<>",   99, UnaryFormat)
    case f: Not          => OpNotation("!",   100, UnaryFormat)
    case f: And          => OpNotation("&",   110, LeftAssociative)
    case f: Or           => OpNotation("|",   120, LeftAssociative)
    case f: Imply        => OpNotation("->",  130, RightAssociative)
    case f: Equiv        => OpNotation("<->", 140, NonAssociative)

    case t: ProgramConst => OpNotation("???",   0, AtomicFormat)
    case t: DifferentialProgramConst => OpNotation("???",   0, AtomicFormat)
    case p: Assign       => OpNotation(":=",  200, AtomicFormat)
    case p: DiffAssign   => OpNotation(":=",  200, AtomicFormat)
    case p: AssignAny    => OpNotation(":= *",  200, AtomicFormat)
    case p: Test         => OpNotation("?",   200, AtomicFormat)
    case p: ODESystem    => OpNotation("&",   200, NonAssociative)
    case p: AtomicODE    => OpNotation("=",   200, AtomicFormat)
    case p: DifferentialProduct => OpNotation(",", 210, RightAssociative)
    case p: Loop         => OpNotation("*",   220, UnaryFormat)
    case p: Compose      => OpNotation(""+";",230, RightAssociative)
    case p: Choice       => OpNotation("++",  240, LeftAssociative)
  }
}
