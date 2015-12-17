/**
 * Copyright (c) Carnegie Mellon University. CONFIDENTIAL
 * See LICENSE.txt for the conditions of this license.
 */
package edu.cmu.cs.ls.keymaerax.btactics

//import edu.cmu.cs.ls.keymaerax.tactics.TactixLibrary._

import edu.cmu.cs.ls.keymaerax.bellerophon._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.tactics.Augmentors._
import edu.cmu.cs.ls.keymaerax.tactics.{PosInExpr, Position}
import edu.cmu.cs.ls.keymaerax.tools.Tool

import scala.collection.immutable._

/**
 * Hilbert Calculus for differential dynamic logic.
 * @author Andre Platzer
 * @see Andre Platzer. [[http://www.cs.cmu.edu/~aplatzer/pub/usubst.pdf A uniform substitution calculus for differential dynamic logic]].  In Amy P. Felty and Aart Middeldorp, editors, International Conference on Automated Deduction, CADE'15, Berlin, Germany, Proceedings, LNCS. Springer, 2015.
 * @see Andre Platzer. [[http://arxiv.org/pdf/1503.01981.pdf A uniform substitution calculus for differential dynamic logic.  arXiv 1503.01981]], 2015.
 * @see Andre Platzer. [[http://dx.doi.org/10.1145/2817824 Differential game logic]]. ACM Trans. Comput. Log. 17(1), 2015. [[http://arxiv.org/pdf/1408.1980 arXiv 1408.1980]]
 * @see [[HilbertCalculus.derive()]]
 * @see [[edu.cmu.cs.ls.keymaerax.core.AxiomBase]]
 */
object HilbertCalculus extends UnifyUSCalculus {
  import TactixLibrary.QE

  /** True when insisting on internal useAt technology, false when external tactic calls are okay. */
  private val INTERNAL = true

  // modalities
  /** assignb: [:=] simplify assignment `[x:=f;]p(x)` by substitution `p(f)` or equation */
  lazy val assignb            : BelleExpr =
    if (INTERNAL) useAt("[:=] assign") | useAt("[:=] assign equational") | useAt("[:=] assign update")
    else ??? //TacticLibrary.boxAssignT
  /** randomb: [:*] simplify nondeterministic assignment `[x:=*;]p(x)` to a universal quantifier `\forall x p(x)` */
  lazy val randomb            : BelleExpr = useAt("[:*] assign nondet")
  /** testb: [?] simplifies test `[?q;]p` to an implication `q->p` */
  lazy val testb              : BelleExpr = useAt("[?] test")
  /** diffSolve: solve a differential equation `[x'=f]p(x)` to `\forall t>=0 [x:=solution(t)]p(x)` */
  def diffSolve               : BelleExpr = ??? //TacticLibrary.diffSolutionT
  /** choiceb: [++] handles both cases of a nondeterministic choice `[a++b]p(x)` separately `[a]p(x) & [b]p(x)` */
  lazy val choiceb            : BelleExpr = useAt("[++] choice")
  /** composeb: [;] handle both parts of a sequential composition `[a;b]p(x)` one at a time `[a][b]p(x)` */
  lazy val composeb           : BelleExpr = useAt("[;] compose")
  /** iterateb: [*] prove a property of a loop `[{a}*]p(x)` by unrolling it once `p(x) & [a][{a}*]p(x)` */
  lazy val iterateb           : BelleExpr = useAt("[*] iterate")
  /** dualb: [^d] handle dual game `[{a}^d]p(x)` by `![a]!p(x)` */
  lazy val dualb              : BelleExpr = useAt("[d] dual")

  /** assignd: <:=> simplify assignment `<x:=f;>p(x)` by substitution `p(f)` or equation */
  lazy val assignd            : BelleExpr = useAt("<:=> assign") | useAt("<:=> assign equational") //@todo or "[:=] assign" if no clash
  /** randomd: <:*> simplify nondeterministic assignment `<x:=*;>p(x)` to an existential quantifier `\exists x p(x)` */
  lazy val randomd            : BelleExpr = useAt("<:*> assign nondet")
  /** testd: <?> simplifies test `<?q;>p` to a conjunction `q&p` */
  lazy val testd              : BelleExpr = useAt("<?> test")
  /** diffSolve: solve a differential equation `<x'=f>p(x)` to `\exists t>=0 <x:=solution(t)>p(x)` */
  def diffSolved              : BelleExpr = ???
  /** choiced: <++> handles both cases of a nondeterministic choice `<a++b>p(x)` separately `<a>p(x) | <b>p(x)` */
  lazy val choiced            : BelleExpr = useAt("<++> choice")
  /** composed: <;> handle both parts of a sequential composition `<a;b>p(x)` one at a time `<a><b>p(x)` */
  lazy val composed           : BelleExpr = useAt("<;> compose")
  /** iterated: <*> prove a property of a loop `<{a}*>p(x)` by unrolling it once `p(x) | <a><{a}*>p(x)` */
  lazy val iterated           : BelleExpr = useAt("<*> iterate")
  /** duald: `<^d>` handle dual game `<{a}^d>p(x)` by `!<a>!p(x)` */
  lazy val duald              : BelleExpr = useAt("<d> dual")

//  /** I: prove a property of a loop by induction with the given loop invariant (hybrid systems) */
//  def I(invariant : Formula)  : PositionTactic = TacticLibrary.inductionT(Some(invariant))
//  def loop(invariant: Formula) = I(invariant)
  /** K: modal modus ponens */
  //def K                       : PositionTactic = PropositionalTacticsImpl.kModalModusPonensT
  /** V: vacuous box [a]p() will be discarded and replaced by p() provided a does not changes values of postcondition p */
  lazy val V                  : BelleExpr = useAt("V vacuous")
//
//  // differential equations
  /** DW: Differential Weakening to use evolution domain constraint `[{x'=f(x)&q(x)}]p(x)` reduces to `[{x'=f(x)&q(x)}](q(x)->p(x))` */
  lazy val DW                 : BelleExpr = useAt("DW differential weakening")
  /** DC: Differential Cut a new invariant for a differential equation `[{x'=f(x)&q(x)}]p(x)` reduces to `[{x'=f(x)&q(x)&C(x)}]p(x)` with `[{x'=f(x)&q(x)}]C(x)`. */
  def DC(invariant: Formula)  : BelleExpr = useAt("DC differential cut", PosInExpr(1::0::Nil),
    (us:Subst)=>us++RenUSubst(Seq((PredOf(Function("r",None,Real,Bool),Anything), invariant)))
  )
  /** DE: Differential Effect exposes the effect of a differential equation `[x'=f(x)]p(x,x')` on its differential symbols as `[x'=f(x)][x':=f(x)]p(x,x')` with its differential assignment `x':=f(x)`. */
  lazy val DE                 : BelleExpr = ???
//    if (INTERNAL) ifElseT(isODESystem,
//      (useAt("DE differential effect (system)") * getODEDim),
//      useAt("DE differential effect"))
//    else ODETactics.diffEffectT
  /** DI: Differential Invariants are used for proving a formula to be an invariant of a differential equation.
    * `[x'=f(x)&q(x)]p(x)` reduces to `q(x) -> p(x) & [x'=f(x)]p(x)'`.
    * @see [[diffInd()]] */
  lazy val DI                 : BelleExpr = useAt("DI differential invariant", PosInExpr(1::Nil))//TacticLibrary.diffInvariant
  /** diffInd: Differential Invariant proves a formula to be an invariant of a differential equation (by DI, DW, DE) */
  lazy val diffInd            : BelleExpr = ???
//    new PositionTactic("diffInd") {
//      override def applies(s: Sequent, p: Position): Boolean = p.isSucc && (s.sub(p) match {
//        case Some(Box(_: ODESystem, _)) => true
//        case Some(_) => false
//        case None => println("ill-positioned " + p + " in " + s + "\nin " + "diffInd(" + p + ")\n(" + s + ")"); return false
//      })
//      def apply(p: Position): Tactic = new ConstructionTactic(name) {
//        override def applicable(node : ProofNode) = applies(node.sequent, p)
//        override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
//        //@todo Dconstify usually needed for DI
//          if (p.isTopLevel && p.isSucc)
//            Some(DI(p) &
//              (stepAt(p) & stepAt(p)) && (
//              QE,
//              //@note derive before DE to keep positions easier
//              derive(p.append(PosInExpr(1::Nil))) &
//                DE(p) &
//                (Dassignb(p.append(PosInExpr(1::Nil))) * (s=>getODEDim(s,p))) &
//                //@note DW after DE to keep positions easier
//                ifT(hasODEDomain, DW)(p) &
//                TacticLibrary.abstractionT(p) & QE
//              ))
//        else
//            Some((DI &
//              //@note derive before DE to keep positions easier
//              shift(PosInExpr(1::1::Nil),
//              shift(PosInExpr(1::Nil), derive) &
//                DE &
//                (shift(PosInExpr(1::Nil), Dassignb) * getODEDim) &
//                //@note DW after DE to keep positions easier
//                ifT(hasODEDomain, DW) &
//                TacticLibrary.abstractionT
//              ))(p)
//              )
//      }
//    }

  /** DG: Differential Ghost add auxiliary differential equations with extra variables `y'=a*y+b`.
    * `[x'=f(x)&q(x)]p(x)` reduces to `\exists y [x'=f(x),y'=a*y+b&q(x)]p(x)`.
    */
  def DG(y:Variable, a:Term, b:Term) : BelleExpr = useAt("DG differential ghost", PosInExpr(1::0::Nil),
    (us:Subst)=>us++RenUSubst(Seq(
      (Variable("y",None,Real), y),
      (FuncOf(Function("t",None,Real,Real),DotTerm), a),
      (FuncOf(Function("s",None,Real,Real),DotTerm), b)))
  )

  //  /** DA: Differential Ghost add auxiliary differential equations with extra variables y'=a*y+b and replacement formula */
//  def DA(y:Variable, a:Term, b:Term, r:Formula) : PositionTactic = ODETactics.diffAuxiliariesRule(y,a,b,r)
  /** DS: Differential Solution solves a simple differential equation `[x'=c&q(x)]p(x)` by reduction to
    * `\forall t>=0 ((\forall 0<=s<=t  q(x+c()*s) -> [x:=x+c()*t;]p(x))` */
  lazy val DS                 : BelleExpr = useAt("DS& differential equation solution")
  
  /** Dassignb: [:='] Substitute a differential assignment `[x':=f]p(x')` to `p(f)` */
  lazy val Dassignb           : BelleExpr = useAt("[':=] differential assign")
  /** Dplus: +' derives a sum `(f(x)+g(x))' = (f(x))' + (g(x))'` */
  lazy val Dplus              : BelleExpr = useAt("+' derive sum")
  /** neg: -' derives unary negation `(-f(x))' = -(f(x)')` */
  lazy val Dneg               : BelleExpr = useAt("-' derive neg")
  /** Dminus: -' derives a difference `(f(x)-g(x))' = (f(x))' - (g(x))'` */
  lazy val Dminus             : BelleExpr = useAt("-' derive minus")
  /** Dtimes: *' derives a product `(f(x)*g(x))' = f(x)'*g(x) + f(x)*g(x)'` */
  lazy val Dtimes             : BelleExpr = useAt("*' derive product")
  /** Dquotient: /' derives a quotient `(f(x)/g(x))' = (f(x)'*g(x) - f(x)*g(x)') / (g(x)^2)` */
  lazy val Dquotient          : BelleExpr = useAt("/' derive quotient")
  /** Dpower: ^' derives a power */
  lazy val Dpower             : BelleExpr = useAt("^' derive power", PosInExpr(1::0::Nil))
  /** Dcompose: o' derives a function composition by chain rule */
  //lazy val Dcompose           : PositionTactic = ???
  /** Dconst: c()' derives a constant `c()' = 0` */
  lazy val Dconst             : BelleExpr = useAt("c()' derive constant fn")
  /** Dvariable: x' derives a variable `(x)' = x'` */
  lazy val Dvariable          : BelleExpr = ???
//    if (false&&INTERNAL) useAt("x' derive var", PosInExpr(0::Nil)) //useAt("x' derive variable", PosInExpr(0::0::Nil))
//    else SyntacticDerivationInContext.symbolizeDifferential

  /** Dand: &' derives a conjunction `(p(x)&q(x))'` to obtain `p(x)' & q(x)'` */
  lazy val Dand               : BelleExpr = useAt("&' derive and")
  /** Dor: |' derives a disjunction `(p(x)|q(x))'` to obtain `p(x)' & q(x)'` */
  lazy val Dor                : BelleExpr = useAt("|' derive or")
  /** Dimply: ->' derives an implication `(p(x)->q(x))'` to obtain `(!p(x) | q(x))'` */
  lazy val Dimply             : BelleExpr = useAt("->' derive imply")
  /** Dequal: =' derives an equation `(f(x)=g(x))'` to obtain `f(x)'=g(x)'` */
  lazy val Dequal             : BelleExpr = useAt("=' derive =")
  /** Dnotequal: !=' derives a disequation `(f(x)!=g(x))'` to obtain `f(x)'=g(x)'` */
  lazy val Dnotequal          : BelleExpr = useAt("!=' derive !=")
  /** Dless: <' derives less-than `(f(x)<g(x))'` to obtain `f(x)'<=g(x)'` */
  lazy val Dless              : BelleExpr = useAt("<' derive <")
  /** Dlessequal: <=' derives a less-or-equal `(f(x)<=g(x))'` to obtain `f(x)'<=g(x)'` */
  lazy val Dlessequal         : BelleExpr = useAt("<=' derive <=")
  /** Dgreater: >' derives greater-than `(f(x)>g(x))'` to obtain `f(x)'>=g(x)'` */
  lazy val Dgreater           : BelleExpr = useAt(">' derive >")
  /** Dgreaterequal: >=' derives a greater-or-equal `(f(x)>=g(x))'` to obtain `f(x)'>=g(x)'` */
  lazy val Dgreaterequal      : BelleExpr = useAt(">=' derive >=")
  /** Dforall: \forall' derives an all quantifier `(\forall x p(x))'` to obtain `\forall x (p(x)')` */
  lazy val Dforall            : BelleExpr = useAt("forall' derive forall")
  /** Dexists: \exists' derives an exists quantifier */
  lazy val Dexists            : BelleExpr = useAt("exists' derive exists")



  /** splitb: splits `[a](p&q)` into `[a]p & [a]q` */
  lazy val splitb             : BelleExpr = useAt("[] split")
  /** splitd: splits `<a>(p|q)` into `<a>p | <a>q` */
  lazy val splitd             : BelleExpr = useAt("<> split")

  // def ind


  /*******************************************************************
    * First-order logic
    *******************************************************************/

  /** vacuousAll: vacuous `\forall x p()` will be discarded and replaced by p() provided x does not occur in p(). */
  lazy val vacuousAll          : BelleExpr = useAt("vacuous all quantifier")
  /** vacuousExists: vacuous `\exists x p()` will be discarded and replaced by p() provided x does not occur in p(). */
  lazy val vacuousExists       : BelleExpr = useAt("vacuous exists quantifier")

  //@todo make the other quantifier axioms accessible by useAt too

  /*******************************************************************
    * Stepping auto-tactic
    *******************************************************************/

  /**
   * Make the canonical simplifying proof step based at the indicated position
   * except when a decision needs to be made (e.g. invariants for loops or for differential equations).
   * @author Andre Platzer
   * @note Efficient source-level indexing implementation.
   */
  lazy val stepAt: DependentPositionTactic = new DependentPositionTactic("stepAt") {
    override def apply(pos: Position): DependentTactic = new DependentTactic(name) {
      override def computeExpr(v: BelleValue): BelleExpr = v match {
        case BelleProvable(provable) =>
          require(provable.subgoals.size == 1, "Exactly one subgoal expected")
          val sub = provable.subgoals.head.sub(pos)
          if (sub.isEmpty) throw new BelleUserGeneratedError("ill-positioned " + pos + " in " + provable.subgoals.head + "\nin " + "stepAt(" + pos + ")\n(" + provable.subgoals.head + ")")
          sub.get match {
            case Box(a, _) => a match {
              case _: Assign    => assignb
              case _: AssignAny => randomb
              case _: Test      => testb
//              case ode: ODESystem if ODETactics.isDiffSolvable(sub.asInstanceOf[Formula])=> Some(diffSolve)
              case _: Compose   => composeb
              case _: Choice    => choiceb
              case _: Dual      => dualb
            }
            case Diamond(a, _) => a match {
              case _: Assign    => assignd
              case _: AssignAny => randomd
              case _: Test      => testd
//              case ode:ODESystem if ODETactics.isDiffSolvable(sub.asInstanceOf[Formula])=> ???
              case _: Compose   => composed
              case _: Choice    => choiced
              case _: Dual      => duald
            }
            case DifferentialFormula(f) => f match {
              case _: Equal     => Dequal
              case _: NotEqual  => Dnotequal
              case _: Greater   => Dgreater
              case _: GreaterEqual => Dgreaterequal
              case _: Less      => Dless
              case _: LessEqual => Dlessequal
              case _: And       => Dand
              case _: Or        => Dor
              case _: Imply     => Dimply
              case _: Forall    => Dforall
              case _: Exists    => Dexists
            }
            case Differential(t) => t match {
              case _: Variable  => Dvariable
              case _: Plus      => Dplus
              case _: Neg       => Dneg
              case _: Minus     => Dminus
              case _: Times     => Dtimes
              case _: Divide    => Dquotient
              case _: Power     => Dpower
              case _: Number    => Dconst
              case FuncOf(_,Nothing) => Dconst
            }
            case Not(f)         => f match {
              case Box(_,Not(_))=> useAt("<> dual")
              case _: Box       => useAt("![]")
              case Diamond(_,Not(_))=> useAt("[] dual")
              case _: Diamond   => useAt("!<>")
              case _: Forall    => useAt("!all")
              case _: Exists    => useAt("!exists")
              case _: Equal     => useAt("! =")
              case _: NotEqual  => useAt("! !=")
              case _: Less      => useAt("! <")
              case _: LessEqual => useAt("! <=")
              case _: Greater   => useAt("! >")
              case _: GreaterEqual => useAt("! >=")
              //@note for conceptual simplicity, use propositional and Skolem sequent rules, too
              case _ if pos.isTopLevel => if (pos.isAnte) ProofRuleTactics.notL else ProofRuleTactics.notR
              case _: Not       => useAt(DerivedAxioms.doubleNegationAxiom)
              case _: And       => useAt(DerivedAxioms.notAnd)
              case _: Or        => useAt(DerivedAxioms.notOr)
              case _: Imply     => useAt(DerivedAxioms.notImply)
              case _: Equiv     => useAt(DerivedAxioms.notEquiv)
            }
            //@note for conceptual simplicity, use propositional and Skolem sequent rules, too
            case _: Not   if pos.isTopLevel => assert(assertion=false, "already above"); if(pos.isAnte) ProofRuleTactics.notL else ProofRuleTactics.notR
            case _: And   if pos.isTopLevel => if (pos.isAnte) ProofRuleTactics.andL   else ProofRuleTactics.andR
            case _: Or    if pos.isTopLevel => if (pos.isAnte) ProofRuleTactics.orL    else ProofRuleTactics.orR
            case _: Imply if pos.isTopLevel => if (pos.isAnte) ProofRuleTactics.implyL else ProofRuleTactics.implyR
            case _: Equiv if pos.isTopLevel => if (pos.isAnte) ProofRuleTactics.equivL else ProofRuleTactics.equivR
            case _: Forall if pos.isTopLevel && !pos.isAnte => TactixLibrary.allR
            case _: Exists if pos.isTopLevel &&  pos.isAnte => TactixLibrary.existsL
          }
      }
    }
  }

  /*******************************************************************
    * Derive by proof
    *******************************************************************/

  /** Derive the differential expression at the indicated position (Hilbert computation deriving the answer by proof).
    * @example When applied at 1::Nil, turns [{x'=22}](2*x+x*y>=5)' into [{x'=22}]2*x'+x'*y+x*y'>=0
    * @see [[UnifyUSCalculus.chase]]
    */
  lazy val derive: BelleExpr = ???
//    new PositionTactic("derive") {
//      override def applies(s: Sequent, p: Position): Boolean = s.sub(p) match {
//        case Some(Differential(_)) => true
//        case Some(DifferentialFormula(_)) => true
//        case Some(_) => false
//        case None => println("ill-positioned " + p + " in " + s + "\nin " + "derive(" + p + ")\n(" + s + ")"); return false
//      }
//    override def apply(p: Position): Tactic = chase(p)
//    }


  /*******************************************************************
    * Internal helpers
    *******************************************************************/

  /** Computing dimension of ODE at indicated position of a sequent */
  private val getODEDim: (Sequent, Position)=>Int = (sequent,pos) => {
    def odeDim(ode: ODESystem): Int = StaticSemantics.boundVars(ode).toSymbolSet.filter(x=>x.isInstanceOf[DifferentialSymbol]).size
    sequent.sub(pos) match {
      case Some(Box(ode: ODESystem, _))     => odeDim(ode)
      case Some(Diamond(ode: ODESystem, _)) => odeDim(ode)
      case Some(e) => throw new IllegalArgumentException("no ODE at position " + pos + " in " + sequent + "\nFound: " + e)
      case None => throw new IllegalArgumentException("ill-positioned " + pos + " in " + sequent)
    }
  }

  /** Whether there is a proper ODE System at the indicated position of a sequent with >=2 ODEs */
  private val isODESystem: (Sequent,Position)=>Boolean = (sequent,pos) => {
    sequent.sub(pos) match {
      case Some(Box(ODESystem(_:DifferentialProduct,_), _))     => true
      case Some(Diamond(ODESystem(_:DifferentialProduct,_), _)) => true
      case Some(e) => false
      case None => throw new IllegalArgumentException("ill-positioned " + pos + " in " + sequent)
    }
  }

  /** Whether the ODE at indicated position of a sequent has a nontrivial domain */
  private val hasODEDomain: (Sequent,Position)=>Boolean = (sequent,pos) => {
    sequent.sub(pos) match {
      case Some(Box(ode: ODESystem, _))     => ode.constraint != True
      case Some(Diamond(ode: ODESystem, _)) => ode.constraint != True
      case Some(e) => throw new IllegalArgumentException("no ODE at position " + pos + " in " + sequent + "\nFound: " + e)
      case None => throw new IllegalArgumentException("ill-positioned " + pos + " in " + sequent)
    }
  }
}