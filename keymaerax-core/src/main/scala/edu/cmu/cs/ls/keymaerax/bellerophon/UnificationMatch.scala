/**
  * Copyright (c) Carnegie Mellon University. CONFIDENTIAL
  * See LICENSE.txt for the conditions of this license.
  */
package edu.cmu.cs.ls.keymaerax.bellerophon

import edu.cmu.cs.ls.keymaerax.btactics.{Augmentors, SubstitutionHelper}
import edu.cmu.cs.ls.keymaerax.btactics.SubstitutionHelper.replaceFree
import edu.cmu.cs.ls.keymaerax.core._

import scala.collection.immutable
import scala.collection.immutable.{List, Nil}

/**
  * Unification/matching algorithm for tactics.
  * Unify(shape, input) matches second argument `input` against the pattern `shape` of the first argument but not vice versa.
  * Matcher leaves input alone and only substitutes into shape.
  * @author Andre Platzer
  */
// 1 pass for semanticRenaming
object UnificationMatch extends UnificationMatchBase {require(RenUSubst.semanticRenaming, "This implementation is meant for tactics built assuming semantic renaming")}
// 2 pass for semanticRenaming
//object UnificationMatch extends UnificationMatchURenAboveUSubst {require(RenUSubst.semanticRenaming, "This implementation is meant for tactics built assuming semantic renaming")}
// 2.5 pass for !semanticRenaming
//object UnificationMatch extends UnificationMatchUSubstAboveURen

/**
  * Matcher(shape, input) matches second argument `input` against the pattern `shape` of the first argument but not vice versa.
  * Matcher leaves input alone and only substitutes into shape.
  * @author Andre Platzer
  */
trait Matcher extends ((Expression,Expression) => RenUSubst) {
  /** Check result of unification for being a valid unifier/matcher */
  private[bellerophon] val REVERIFY = false

  //  type Subst = USubst
  //  private def Subst(subs: List[SubstRepl]): Subst = USubst(subs)
  //  type SubstRepl = SubstitutionPair
  //  private def SubstRepl(what: Expression, repl: Expression): SubstRepl = SubstitutionPair(what,repl)

  type Subst = RenUSubst
  //@todo .distinct may slow things down. Necessary all the time?
  protected def Subst(subs: List[SubstRepl]): Subst = RenUSubst(subs.distinct)
  type SubstRepl = Tuple2[Expression,Expression]
  protected def SubstRepl(what: Expression, repl: Expression): SubstRepl = (what,repl)

  /** Identity Subst */
  protected val id: List[SubstRepl] = Nil


  /** Compute some unifier if unifiable else None */
  def unifiable(e1: Expression, e2: Expression): Option[Subst] = try {Some(apply(e1, e2))} catch {case e: UnificationException => println("Expression un-unifiable " + e); None}

  /** Compute some unifier if unifiable else None */
  def unifiable(e1: Sequent, e2: Sequent): Option[Subst] = try {Some(apply(e1, e2))} catch {case e: UnificationException => println("Sequent un-unifiable " + e); None}

  /** apply(shape, input) matches `input` against the pattern `shape` to find a uniform substitution `\result` such that `\result(shape)==input`. */
  def apply(e1: Expression, e2: Expression): Subst

  def apply(e1: Term, e2: Term): Subst
  def apply(e1: Formula, e2: Formula): Subst
  def apply(e1: Program, e2: Program): Subst
  def apply(e1: DifferentialProgram, e2: DifferentialProgram): Subst

  def apply(e1: Sequent, e2: Sequent): Subst
}

trait InsistentMatcher extends Matcher {
  def apply(e1: Term, e2: Term): Subst       = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
  def apply(e1: Formula, e2: Formula): Subst = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
  def apply(e1: Program, e2: Program): Subst = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
  def apply(e1: DifferentialProgram, e2: DifferentialProgram): Subst = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
}

abstract trait BaseMatcher extends Matcher {
  //@todo import a debug flag as in Tactics.DEBUG
  private val DEBUG = System.getProperty("DEBUG", "false")=="true"

  def apply(e1: Expression, e2: Expression): Subst = if (e1.kind==e2.kind || e1.kind==ProgramKind && e2.kind==DifferentialProgramKind)
    e1 match {
      case t1: Term => apply(t1, e2.asInstanceOf[Term])
      case f1: Formula => apply(f1, e2.asInstanceOf[Formula])
      case p1: DifferentialProgram if !p1.isInstanceOf[ODESystem] => apply(p1, e2.asInstanceOf[DifferentialProgram])
      case p1: Program => apply(p1, e2.asInstanceOf[Program])
    } else throw new UnificationException(e1.prettyString, e2.prettyString, "have incompatible kinds " + e1.kind + " and " + e2.kind)

  //@note To circumvent shortcomings of renaming-unaware unification algorithm, the following code unifies for renaming, renames, and then reunifies the renamed outcomes for substitution
  def apply(e1: Term, e2: Term): Subst = {try {
    unified(e1, e2, Subst(unify(e1, e2)))
  } catch {case ex: ProverException => throw ex.inContext("match " + e1.prettyString + "\n   with  " + e2.prettyString)}
  } ensuring (r => !REVERIFY || r(e1) == e2, "unifier match expected to unify or fail\nunify: " + e1.prettyString + "\nwith:  " + e2.prettyString + "\nshould become equal under their unifier unifier\n" + Subst(unify(e1, e2)) + "\nhence: " + Subst(unify(e1, e2))(e1).prettyString + "\nwith:  " + e2.prettyString)

  def apply(e1: Formula, e2: Formula): Subst = {try {
    unified(e1, e2, Subst(unify(e1, e2)))
  } catch {case ex: ProverException => throw ex.inContext("match " + e1.prettyString + "\n   with  " + e2.prettyString)}
  } ensuring (r => !REVERIFY || r(e1) == e2, "unifier match expected to unify or fail\nunify: " + e1.prettyString + "\nwith:  " + e2.prettyString + "\nshould become equal under their unifier unifier\n" + Subst(unify(e1, e2)) + "\nhence: " + Subst(unify(e1, e2))(e1).prettyString + "\nwith:  " + e2.prettyString)

  def apply(e1: Program, e2: Program): Subst = {try {
    unified(e1, e2, Subst(unify(e1, e2)))
  } catch {case ex: ProverException => throw ex.inContext("match " + e1.prettyString + "\n   with  " + e2.prettyString)}
  } ensuring (r => !REVERIFY || r(e1) == e2, "unifier match expected to unify or fail\nunify: " + e1.prettyString + "\nwith:  " + e2.prettyString + "\nshould become equal under their unifier unifier\n" + Subst(unify(e1, e2)) + "\nhence: " + Subst(unify(e1, e2))(e1).prettyString + "\nwith:  " + e2.prettyString)

  def apply(e1: DifferentialProgram, e2: DifferentialProgram): Subst = {try {
    unified(e1, e2, Subst(unifyODE(e1, e2)))
  } catch {case ex: ProverException => throw ex.inContext("match " + e1.prettyString + "\n   with  " + e2.prettyString)}
  } ensuring (r => !REVERIFY || r(e1) == e2, "unifier match expected to unify or fail\nunify: " + e1.prettyString + "\nwith:  " + e2.prettyString + "\nshould become equal under their unifier unifier\n" + Subst(unify(e1, e2)) + "\nhence: " + Subst(unify(e1, e2))(e1).prettyString + "\nwith:  " + e2.prettyString)

  def apply(e1: Sequent, e2: Sequent): Subst = {try {
    Subst(unify(e1, e2))
  } catch {case ex: ProverException => throw ex.inContext("match " + e1.toString     + "\n   with  " + e2.toString)}
  } ensuring (r => !REVERIFY || r(e1) == e2, "unifier match expected to unify or fail\nunify: " + e1.prettyString + "\nwith:  " + e2.prettyString + "\nshould become equal under their unifier unifier\n" + Subst(unify(e1, e2)) + "\nhence: " + Subst(unify(e1, e2))(e1).prettyString + "\nwith:  " + e2.prettyString)

  /** Optionally log result to console */
  protected def unified(e1: Expression, e2: Expression, us: Subst): Subst =
  {if (DEBUG) println("  unify: " + e1.prettyString + "\n  with:  " + e2.prettyString + "\n  via:   " + us); us}

  protected def unify(e1: Term, e2: Term): List[SubstRepl]
  protected def unify(e1: Formula, e2: Formula): List[SubstRepl]
  protected def unify(e1: Program, e2: Program): List[SubstRepl]
  protected def unifyODE(e1: DifferentialProgram, e2: DifferentialProgram): List[SubstRepl]
  protected def unify(e1: Sequent, e2: Sequent): List[SubstRepl]

  //@todo optimize: this may be slower than static type inference
  protected def unify(e1: Expression, e2: Expression): List[SubstRepl] = e1 match {
    case t1: Term => unify(t1, e2.asInstanceOf[Term])
    case f1: Formula => unify(f1, e2.asInstanceOf[Formula])
    case p1: DifferentialProgram if !p1.isInstanceOf[ODESystem] => unifyODE(p1, e2.asInstanceOf[DifferentialProgram])
    case p1: Program => unify(p1, e2.asInstanceOf[Program])
  }

  // tools

  protected def ununifiable(e1: Expression, e2: Expression): Nothing = {
    //println(new UnificationException(e1.toString, e2.toString))
    throw new UnificationException(e1.toString, e2.toString)}

  protected def ununifiable(e1: Sequent, e2: Sequent): Nothing = {
    //println(new UnificationException(e1.toString, e2.toString))
    throw new UnificationException(e1.toString, e2.toString)}

  /** Construct the unifier that forces `e1` and `e2` to be equal (requires: check that this gives a valid substitution) */
  protected def unifier(e1: Expression, e2: Expression): List[SubstRepl] = SubstRepl(e1, e2) :: Nil

}


/**
  * Generic base for unification/matching algorithm for tactics.
  * Unify(shape, input) matches second argument `input` against the pattern `shape` of the first argument but not vice versa.
  * Matcher leaves input alone and only substitutes into shape.
  * Reasonably fast single-pass matcher.
  * @author Andre Platzer
  */
class UnificationMatchBase extends BaseMatcher {

  //@todo import a debug flag as in Tactics.DEBUG
  private val DEBUGALOT = System.getProperty("DEBUG", "false")=="true"

  /** Reunify after renaming */
  private val REUNIFY = false


  /** Composition of renaming substitution representations: Compose renaming substitution `after` after renaming substitution `before` */
  protected def compose(after: List[SubstRepl], before: List[SubstRepl]): List[SubstRepl] =
    if (after.isEmpty) before else if (before.isEmpty) after else {
      val us = Subst(after)
      try {
        //@todo uniform renaming part is flat and comes first so would really need a simple transitive closure treatment. And avoid there-and-back-again renamings. Such as (x~>y) compose (x~>y) should not be (x~>x)=()
        //@todo this is a rough approximation that may not generalize: leave vars alone
        val r = before.map(sp => try { (sp._1, if (sp._1.isInstanceOf[Variable]) sp._2 else us(sp._2)) } catch {case e: ProverException => throw e.inContext("unify.compose failed on " + sp._1 + " and " + sp._2 + " for " + us)}) ++
          after.filter(sp => !before.exists(op => op._1 == sp._1))
        if (DEBUGALOT) println("      unify.compose: " + after.mkString(", ") + " with " + before.mkString(", ") + " is " + r.mkString(", "))
        r
      } catch {case e:Throwable => println("UnificationMatch.compose({" + after.mkString(", ") + "} , {" + before.mkString(", ") + "})"); throw e}
    }

  //@note optimized: repeated implementation per type to enable the static type inference that Scala generics won't give.
  private def unifies(s1:Expression,s2:Expression, t1:Expression,t2:Expression): List[SubstRepl] = {
    val u1 = unify(s1, t1)
    try {
      compose(unify(Subst(u1)(s2), t2), u1)
    } catch {
      case e: ProverException =>
        if (DEBUGALOT) {println("      try converse since " + e.getMessage)}
        val u2 = unify(s2, t2)
        compose(unify(s1, Subst(u2)(s1)), u2)
        //@todo incomplete: match [a;]p() -> [a;]p() with [x:=x+1;]y>0 -> [x:=x+1;]y>0  will fail since both pieces need to be unified and then combined subsequently. But that's okay for now.
    }
  }
  private def unifies(s1:Term,s2:Term, t1:Term,t2:Term): List[SubstRepl] = {
    val u1 = unify(s1, t1)
    try {
      compose(unify(Subst(u1)(s2), t2), u1)
    } catch {
      case e: ProverException =>
        if (DEBUGALOT) {println("      try converse since " + e.getMessage)}
        val u2 = unify(s2, t2)
        compose(unify(s1, Subst(u2)(s1)), u2)
    }
  }
  private def unifies(s1:Formula,s2:Formula, t1:Formula,t2:Formula): List[SubstRepl] = {
    val u1 = unify(s1, t1)
    try {
      compose(unify(Subst(u1)(s2), t2), u1)
    } catch {
      case e: ProverException =>
        if (DEBUGALOT) {println("      try converse since " + e.getMessage)}
        val u2 = unify(s2, t2)
        compose(unify(s1, Subst(u2)(s1)), u2)
    }
  }
  private def unifies(s1:Program,s2:Program, t1:Program,t2:Program): List[SubstRepl] = {
    val u1 = unify(s1, t1)
    try {
      compose(unify(Subst(u1)(s2), t2), u1)
    } catch {
      case e: ProverException =>
        if (DEBUGALOT) {println("      try converse since " + e.getMessage)}
        val u2 = unify(s2, t2)
        compose(unify(s1, Subst(u2)(s1)), u2)
    }
  }
  private def unifiesODE(s1:DifferentialProgram,s2:DifferentialProgram, t1:DifferentialProgram,t2:DifferentialProgram): List[SubstRepl] = {
    val u1 = unifyODE(s1, t1)
    try {
      compose(unifyODE(Subst(u1)(s2).asInstanceOf[DifferentialProgram], t2), u1)
    } catch {
      case e: ProverException =>
        if (DEBUGALOT) {println("      try converse since " + e.getMessage)}
        val u2 = unifyODE(s2, t2)
        compose(unifyODE(s1, Subst(u2)(s1).asInstanceOf[DifferentialProgram]), u2)
    }
  }

  //  private def unifyVar(x1: Variable, e2: Expression): List[SubstRepl] = if (x1==e2) id else ununifiable(x1,e2)
  //  private def unifyVar(xp1: DifferentialSymbol, e2: Expression): List[SubstRepl] = if (xp1==e2) id else ununifiable(xp1,e2)
  protected def unifyVar(x1: Variable, e2: Expression): List[SubstRepl] = if (x1==e2) id else e2 match { case _: Variable => unifier(x1,e2.asInstanceOf[Variable]) case _ => ununifiable(x1,e2)}
  protected def unifyVar(xp1: DifferentialSymbol, e2: Expression): List[SubstRepl] = if (xp1==e2) id else e2 match { case _: DifferentialSymbol => unifier(xp1.x,e2.asInstanceOf[DifferentialSymbol].x) case _ => ununifiable(xp1,e2)}


  /** A simple recursive unification algorithm that actually just recursive single-sided matching without occurs check */
  protected def unify(e1: Term, e2: Term): List[SubstRepl] = e1 match {
    case x: Variable                      => unifyVar(x,e2)
    case xp: DifferentialSymbol           => unifyVar(xp,e2)
    case n: Number                        => if (e1==e2) id else ununifiable(e1,e2)
    case FuncOf(f:Function, Anything)     => if (e1==e2) id else unifier(e1, e2)
    case FuncOf(f:Function, Nothing)      => if (e1==e2) id else unifier(e1, e2)
    case FuncOf(f:Function, t)            => e2 match {
      case FuncOf(g, t2) if f==g => unify(t,t2) /*case DotTerm => List(SubstRepl(DotTerm, t1))*/
      // otherwise DotTerm abstraction of all occurrences of the argument
      case _ => unifier(FuncOf(f,DotTerm), replaceFree(e2)(t,DotTerm))
    }
    case Anything                         => if (e1==e2) id else unifier(Anything, e2)  //@todo where does this happen?
    case Nothing                          => if (e1==e2) id else ununifiable(e1,e2)
    case DotTerm                          => if (e1==e2) id else unifier(e1, e2)
    //@note case o1:UnaryCompositeTerm  => e2 match {case o2:UnaryCompositeTerm  if o1.reapply==o2.reapply => unify(o1.child,o2.child) case _ => ununifiable(e1,e2)}
    //@note case o1:BinaryCompositeTerm => e2 match {case o2:BinaryCompositeTerm if o1.reapply==o2.reapply => unify(o1.left,o2.left) ++ unify(o1.right,o2.right) case _ => ununifiable(e1,e2)}
    // homomorphic cases
    case Neg(t)       => e2 match {case Neg(t2) => unify(t,t2) case _ => ununifiable(e1,e2)}
      // case o: BinaryCompositeTerm => e2 match {case o2: BinaryCompositeTerm if o2.reapply==o.reapply => unify(o.left,o.right, o2.left,o2.right) case _ => ununifiable(e1,e2)}
    case Plus(l, r)   => e2 match {case Plus  (l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case Minus(l, r)  => e2 match {case Minus (l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case Times(l, r)  => e2 match {case Times (l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case Divide(l, r) => e2 match {case Divide(l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case Power(l, r)  => e2 match {case Power (l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case Differential(t) => e2 match {case Differential(t2) => unify(t,t2) case _ => ununifiable(e1,e2)}
    // unofficial
    case Pair(l, r)   => e2 match {case Pair(l2,r2)   => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
  }

  protected def unify(e1: Formula, e2: Formula): List[SubstRepl] = e1 match {
    case PredOf(f:Function, Anything)     => if (e1==e2) id else unifier(e1, e2)
    case PredOf(f:Function, Nothing)      => if (e1==e2) id else unifier(e1, e2)
    case PredOf(f:Function, t)            => e2 match {
      case PredOf(g, t2) if f == g => unify(t, t2)
      // otherwise DotTerm abstraction of all occurrences of the argument
        //@todo stutter  if not free
      case _ => if (DEBUGALOT) println("unify " + e1 + "\nwith  " + e2 + "\ngives " + unifier(PredOf(f,DotTerm), replaceFree(e2)(t,DotTerm)))
        unifier(PredOf(f,DotTerm), replaceFree(e2)(t,DotTerm))
        //@todo heuristic: for p(f()) simply pass since f() must occur somewhere else in isolation to match on it. In general may have to remember p(subst(f())) = e2 constraint regardless and post-unify.
    }
    case PredicationalOf(f:Function, DotFormula) => if (e1==e2) id else unifier(e1, e2)
    case PredicationalOf(c, fml) => e2 match {
      case PredicationalOf(g, fml2) if c == g => unify(fml, fml2)
      // otherwise DotFormula abstraction of all occurrences of the argument
      case _ => ??? //@todo List(SubstRepl(PredicationalOf(c,DotFormula), SubstitutionHelper.replaceFree(e2)(fml,DotFormula)))
    }
    case DotFormula         => if (e1==e2) id else unifier(e1, e2)
    case True | False       => if (e1==e2) id else ununifiable(e1,e2)

    // homomorphic base cases
    case Equal(l, r)        => e2 match {case Equal       (l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case NotEqual(l, r)     => e2 match {case NotEqual    (l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case GreaterEqual(l, r) => e2 match {case GreaterEqual(l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case Greater(l, r)      => e2 match {case Greater     (l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case LessEqual(l, r)    => e2 match {case LessEqual   (l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case Less(l, r)         => e2 match {case Less        (l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}

    // homomorphic cases
    case Not(g)      => e2 match {case Not(g2)      => unify(g,g2) case _ => ununifiable(e1,e2)}
    case And(l, r)   => e2 match {case And(l2,r2)   => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case Or(l, r)    => e2 match {case Or(l2,r2)    => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case Imply(l, r) => e2 match {case Imply(l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}
    case Equiv(l, r) => e2 match {case Equiv(l2,r2) => unifies(l,r, l2,r2) case _ => ununifiable(e1,e2)}

    // NOTE DifferentialFormula in analogy to Differential
    case DifferentialFormula(g) => e2 match {case DifferentialFormula(g2) => unify(g,g2) case _ => ununifiable(e1,e2)}

    // pseudo-homomorphic cases
      //@todo join should be enough for the two unifiers in this case after they have been applied to the other side
    case Forall(vars, g) if vars.length==1 => e2 match {case Forall(v2,g2) if v2.length==1 => unifies(vars.head,g, v2.head,g2) case _ => ununifiable(e1,e2)}
    case Exists(vars, g) if vars.length==1 => e2 match {case Exists(v2,g2) if v2.length==1 => unifies(vars.head,g, v2.head,g2) case _ => ununifiable(e1,e2)}

    // homomorphic cases
    case Box(a, p)       => e2 match {case Box(a2,p2)     => unifies(a,p, a2,p2) case _ => ununifiable(e1,e2)}
    case Diamond(a, p)   => e2 match {case Diamond(a2,p2) => unifies(a,p, a2,p2) case _ => ununifiable(e1,e2)}
  }

  protected def unify(e1: Program, e2: Program): List[SubstRepl] = e1 match {
    case a: ProgramConst             => if (e1==e2) id else unifier(e1, e2)
    case Assign(x, t)                => e2 match {case Assign(x2,t2) => unifies(x,t, x2,t2) case _ => ununifiable(e1,e2)}
    case DiffAssign(xp, t)           => e2 match {case DiffAssign(xp2,t2) => unifies(xp,t, xp2,t2) case _ => ununifiable(e1,e2)}
    case AssignAny(x)                => e2 match {case AssignAny(x2)    => unify(x,x2) case _ => ununifiable(e1,e2)}
    case Test(f)                     => e2 match {case Test(f2)         => unify(f,f2) case _ => ununifiable(e1,e2)}
    case ODESystem(a, h)             => e2 match {case ODESystem(a2,h2) => unifies(a,h, a2,h2) case _ => ununifiable(e1,e2)}
    //@note This case happens for standalone uniform substitutions on differential programs such as x'=f() or c as they come up in unification for example.
    case dp1: DifferentialProgram    => e2 match {case dp2: DifferentialProgram => unifyODE(dp1, dp2) case _ => ununifiable(e1, e2)}
    case Choice(a, b)                => e2 match {case Choice(a2,b2)    => unifies(a,b, a2,b2) case _ => ununifiable(e1,e2)}
    case Compose(a, b)               => e2 match {case Compose(a2,b2)   => unifies(a,b, a2,b2) case _ => ununifiable(e1,e2)}
    case Loop(a)                     => e2 match {case Loop(a2)         => unify(a,a2) case _ => ununifiable(e1,e2)}
    case Dual(a)                     => e2 match {case Dual(a2)         => unify(a,a2) case _ => ununifiable(e1,e2)}
  }

  protected def unifyODE(e1: DifferentialProgram, e2: DifferentialProgram): List[SubstRepl] = { val r = e1 match {
    case c: DifferentialProgramConst => if (e1==e2) id else unifier(e1, e2)
    case AtomicODE(xp, t) => e2 match {case AtomicODE(xp2,t2) => unifies(xp,t, xp2,t2) case _ => ununifiable(e1,e2)}
    case DifferentialProduct(a, b)   => e2 match {case DifferentialProduct(a2,b2) => unifiesODE(a,b, a2,b2) case _ => ununifiable(e1,e2)}
  }
    if (DEBUGALOT) println("    unify: " + e1.prettyString + " with " + e2.prettyString + " gives unifier " + Subst(r))
    r
  }

  protected def unify(s1: Sequent, s2: Sequent): List[SubstRepl] =
    if (!(s1.pref == s2.pref && s1.ante.length == s2.ante.length && s1.succ.length == s2.succ.length)) ununifiable(s1,s2)
    else {
      val composeFolder = (u1: List[SubstRepl], f1: Formula, f2: Formula) =>
        compose(unify(Subst(u1)(f1), f2), u1)
      val antesubst = s1.ante.indices.foldLeft(List[SubstRepl]()) ((subst,i) => composeFolder(subst, s1.ante(i), s2.ante(i)))
      val succsubst = s1.succ.indices.foldLeft(antesubst) ((subst,i) => composeFolder(subst, s1.succ(i), s2.succ(i)))
      succsubst.distinct
      //@note if flat ++ this would be easy:
//        //@todo this is really a zip fold
//      (
//        s1.ante.indices.foldLeft(List[SubstRepl]())((subst,i) => subst ++ unify(s1.ante(i), s2.ante(i))) ++
//          s1.succ.indices.foldLeft(List[SubstRepl]())((subst,i) => subst ++ unify(s1.succ(i), s2.succ(i)))
//        ).distinct
    }
}



/**
  * Unification/matching algorithm for tactics, respecting only renamings.
  * Unify(shape, input) matches second argument `input` against the pattern `shape` of the first argument but not vice versa.
  * Matcher leaves input alone and only substitutes into shape.
  *
  * This matcher only excerpts variable renaming, ignoring all other reasons to unify.
  * @author Andre Platzer
  */
private final object RenUnificationMatch extends UnificationMatchBase {
  // incomplete unification cannot succeed during REVERIFY
  override private[keymaerax] val REVERIFY = false
  // Always skip unifiers except variables, which are handled by unifyVar
  override protected def unifier(e1: Expression, e2: Expression): List[SubstRepl] = id ensuring (r => !e1.isInstanceOf[Variable])
  // Create unifiers for variables even if all others are skipped above
  override protected def unifyVar(x1: Variable, e2: Expression): List[SubstRepl] = if (x1==e2) id else e2 match { case _: Variable => List(SubstRepl(x1,e2.asInstanceOf[Variable])) case _ => List(SubstRepl(x1,e2))}
  override protected def unifyVar(xp1: DifferentialSymbol, e2: Expression): List[SubstRepl] = if (xp1==e2) id else e2 match { case _: DifferentialSymbol => List(SubstRepl(xp1.x,e2.asInstanceOf[DifferentialSymbol].x)) case _ => List(SubstRepl(xp1,e2))}

  // composition is easier for flat renaming unifiers without substitutions
  override protected def compose(after: List[SubstRepl], before: List[SubstRepl]): List[SubstRepl] = before++after
}


/**
  * Unification/matching algorithm for tactics for URenAboveUSubst.
  * Unify(shape, input) matches second argument `input` against the pattern `shape` of the first argument but not vice versa.
  * Matcher leaves input alone and only substitutes into shape.
  * @author Andre Platzer
  */
class UnificationMatchURenAboveUSubst extends /*Insistent*/Matcher { outer =>
  require(RenUSubst.semanticRenaming, "This implementation is meant for tactics built assuming semantic renaming")
  override private[bellerophon] val REVERIFY = false
  // pass 1
  private val renUMatcher = RenUnificationMatch
  // pass 2
  private val usubstUMatcher = new UnificationMatchBase { override private[keymaerax] val REVERIFY = false }

  private def unify(e1: Expression, e2: Expression): Subst = {
    val ren = renUMatcher(e1, e2)
    usubstUMatcher(ren(e1), e2) ++ ren
  }

  private def unify(e1: Sequent, e2: Sequent): Subst = {
    val ren = renUMatcher(e1, e2)
    usubstUMatcher(ren(e1), e2) ++ ren
  }

  override def apply(e1: Expression, e2: Expression): Subst = { try {
    unify(e1, e2)
  } catch {case ex: ProverException => throw ex.inContext("match " + e1.prettyString + "\n   with  " + e2.prettyString)}
  } ensuring (r => !REVERIFY || r(e1) == e2, "unifier match expected to unify or fail\nunify: " + e1.prettyString + "\nwith:  " + e2.prettyString + "\nshould become equal under their unifier unifier\n" + (unify(e1, e2)) + "\nhence: " + (unify(e1, e2))(e1).prettyString + "\nwith:  " + e2.prettyString)

  override def apply(e1: Sequent, e2: Sequent): Subst = { try {
    unify(e1, e2)
  } catch {case ex: ProverException => throw ex.inContext("match " + e1.prettyString + "\n   with  " + e2.prettyString)}
  } ensuring (r => !REVERIFY || r(e1) == e2, "unifier match expected to unify or fail\nunify: " + e1.prettyString + "\nwith:  " + e2.prettyString + "\nshould become equal under their unifier unifier\n" + (unify(e1, e2)) + "\nhence: " + (unify(e1, e2))(e1).prettyString + "\nwith:  " + e2.prettyString)

  //@todo this should come from extends InsistentMatcher
  def apply(e1: Term, e2: Term): Subst       = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
  def apply(e1: Formula, e2: Formula): Subst = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
  def apply(e1: Program, e2: Program): Subst = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
  def apply(e1: DifferentialProgram, e2: DifferentialProgram): Subst = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
}

class UnificationMatchUSubstAboveURen extends /*Insistent*/Matcher {
  require(!RenUSubst.semanticRenaming, "This implementation is meant for tactics built assuming NO semantic renaming")
  override private[bellerophon] val REVERIFY = false
  // pass 1
  private val usubstUMatcher = new UnificationMatchBase {
    // partial so can't REVERIFY
    override private[keymaerax] val REVERIFY = false
    // Skip unifiers for variables in this pass
    override protected def unifyVar(x1: Variable, e2: Expression): List[SubstRepl] = e2 match { case _: Variable => id case _ => ununifiable(x1,e2)}
    override protected def unifyVar(xp1: DifferentialSymbol, e2: Expression): List[SubstRepl] = e2 match { case _: DifferentialSymbol => id case _ => ununifiable(xp1,e2)}
  }
  // pass 2
  private val renUMatcher = RenUnificationMatch

  private def staple(e: Expression, ren: Subst, subst: Subst): Subst = {
    import Augmentors.FormulaAugmentor
    //@note optimizable
    val argOfPred: Function => Term = p => e.asInstanceOf[Formula].findSubformula(g => g match {
        //@note want to know t
      case PredOf(q,t) if q==p => true
      case _ => false
    }).get._2.asInstanceOf[PredOf].child
    val posthocDottify: (Expression, Expression) =>Expression = (what: Expression, repl: Expression) => {
      //@todo could post-hoc: replaceFree(sp._2)(ren(argumentWhereOccured),DotTerm)
      //@note this is a ridiculous approximation but fast for matching against p(x_) or f(x_) or p(x_') or f(x_') in axioms
      val r = repl match {
        case rhs: Term    => ren(
          replaceFree(
            replaceFree(rhs)(ren(Variable("x_")),DotTerm)
          ) (ren(DifferentialSymbol(Variable("x_"))), DotTerm)
        )
        case rhs: Formula => ren(
          //@todo if this match doesn't work, could keep looking for argument in next occurrence of what
          replaceFree(rhs)(ren(argOfPred(what.asInstanceOf[PredOf].func)), DotTerm)
        )
      }
      println("\t\t\tINFO: post-hoc optimizable: " + repl + " dottify " + r)
      r
    }
    //@note URename with TRANSPOSITION=true are their own inverses
    val inverseRename = (subst:RenUSubst) => RenUSubst(subst.subsDefsInput.map(sp =>
      (sp._1, sp._1 match {
        case FuncOf(_, DotTerm) => posthocDottify(sp._1, sp._2)
        case PredOf(_, DotTerm) => posthocDottify(sp._1, sp._2)
        case _ => ren(sp._2)
      } )))
    val renamedSubst = inverseRename(subst)
//    if (DEBUG) println("\n  unify: " + e1.prettyString + "\n  with:  " + e2.prettyString + "\n  subst: " + subst + "\n  gives: " + e1s + "\n  ren:   " + ren + "\n  invren: " + renamedSubst + "\n  sum:   " + (renamedSubst ++ ren) + "\n  result: " + (renamedSubst ++ ren)(e1))
    renamedSubst ++ ren
  }

  private val DEBUG = BelleExpr.DEBUG

  private def unify(e1: Expression, e2: Expression): Subst = {
    val subst = usubstUMatcher(e1, e2)
    if (DEBUG) println("\n  unify: " + e1.prettyString + "\n  with:  " + e2.prettyString + "\n  subst: " + subst + "\n  gives: " + subst(e1))
    val ren = renUMatcher(subst(e1), e2)
    //@note instead of post-hoc stapling could also add a third pass that unifies with the resulting renaming `ren` in mind.
    staple(e1, ren, subst)
  }

  private def unify(e1: Sequent, e2: Sequent): Subst = {
    val subst = usubstUMatcher(e1, e2)
    val ren = renUMatcher(subst(e1), e2)
    import Augmentors.SequentAugmentor
    staple(e1.toFormula, ren, subst)
  }

  override def apply(e1: Expression, e2: Expression): Subst = { try {
    unify(e1, e2)
  } catch {case ex: ProverException => throw ex.inContext("match " + e1.prettyString + "\n   with  " + e2.prettyString)}
  } ensuring (r => !REVERIFY || r(e1) == e2, "unifier match expected to unify or fail\nunify: " + e1.prettyString + "\nwith:  " + e2.prettyString + "\nshould become equal under their unifier unifier\n" + (unify(e1, e2)) + "\nhence: " + (unify(e1, e2))(e1).prettyString + "\nwith:  " + e2.prettyString)

  override def apply(e1: Sequent, e2: Sequent): Subst = { try {
    unify(e1, e2)
  } catch {case ex: ProverException => throw ex.inContext("match " + e1.prettyString + "\n   with  " + e2.prettyString)}
  } ensuring (r => !REVERIFY || r(e1) == e2, "unifier match expected to unify or fail\nunify: " + e1.prettyString + "\nwith:  " + e2.prettyString + "\nshould become equal under their unifier unifier\n" + (unify(e1, e2)) + "\nhence: " + (unify(e1, e2))(e1).prettyString + "\nwith:  " + e2.prettyString)

  //@todo this should come from extends InsistentMatcher
  def apply(e1: Term, e2: Term): Subst       = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
  def apply(e1: Formula, e2: Formula): Subst = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
  def apply(e1: Program, e2: Program): Subst = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])
  def apply(e1: DifferentialProgram, e2: DifferentialProgram): Subst = apply(e1.asInstanceOf[Expression], e2.asInstanceOf[Expression])

}

