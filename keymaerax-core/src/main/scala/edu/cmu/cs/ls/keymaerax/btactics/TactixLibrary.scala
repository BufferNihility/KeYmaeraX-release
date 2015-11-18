/**
 * Copyright (c) Carnegie Mellon University. CONFIDENTIAL
 * See LICENSE.txt for the conditions of this license.
 */
package edu.cmu.cs.ls.keymaerax.btactics

import edu.cmu.cs.ls.keymaerax.bellerophon._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser.KeYmaeraXParser
import edu.cmu.cs.ls.keymaerax.tactics.{AntePosition, Generator, NoneGenerate, Position, PosInExpr, SuccPosition}

import scala.collection.immutable._

/**
 * Tactix: Main tactic library with simple interface.
 *
 * This library features all main tactic elements for most common cases, except sophisticated tactics.
 * Brief documentation for the tactics is provided inline in this interface file.
 *
 * *Following tactics forward to their implementation reveals more detailed documentation*.
 *
 * For tactics implementing built-in rules such as sequent proof rules,
 * elaborate documentation is in the [[edu.cmu.cs.ls.keymaerax.core.Rule prover kernel]].
 *
 * @author Andre Platzer
 * @see Andre Platzer. [[http://www.cs.cmu.edu/~aplatzer/pub/usubst.pdf A uniform substitution calculus for differential dynamic logic]].  In Amy P. Felty and Aart Middeldorp, editors, International Conference on Automated Deduction, CADE'15, Berlin, Germany, Proceedings, LNCS. Springer, 2015.
 * @see Andre Platzer. [[http://arxiv.org/pdf/1503.01981.pdf A uniform substitution calculus for differential dynamic logic.  arXiv 1503.01981]], 2015.
 * @see [[UnifyUSCalculus]]
 * @see [[DerivedAxioms]]
 * @see [[edu.cmu.cs.ls.keymaerax.tactics]]
 * @see [[edu.cmu.cs.ls.keymaerax.core.Rule]]
 */
object TactixLibrary extends UnifyUSCalculus {
  private val parser = KeYmaeraXParser

  /** step: one canonical simplifying proof step at the indicated formula/term position (unless @invariant etc needed) */
  lazy val step               : DependentPositionTactic = HilbertCalculus.stepAt

    /** Normalize to sequent form, keeping branching factor down by precedence */
  def normalize               : BelleExpr = (alphaRule | closeId | ls(allR) | la(existsL)
    | close
    | betaRule
    | l(step))*@TheType()
  /** exhaust propositional logic */
  def prop                    : BelleExpr = (closeId | close | alphaRule | betaRule)*@TheType()
  /** master: master tactic that tries hard to prove whatever it could */
  def master                  : BuiltInTactic = ??? //master(new NoneGenerate(), "Mathematica")
  def master(qeTool: String)  : BuiltInTactic = ??? //master(new NoneGenerate(), qeTool)
  def master(gen: Generator[Formula] = new NoneGenerate(), qeTool: String = "Mathematica"): BuiltInTactic = ??? //TacticLibrary.master(gen, true, qeTool)

  /*******************************************************************
    * unification and matching based auto-tactics
    * @see [[UnifyUSCalculus]]
    *******************************************************************/

  /** US: uniform substitution ([[edu.cmu.cs.ls.keymaerax.core.UniformSubstitutionRule USubst]])
    * @see [[UnifyUSCalculus]]
    * @see [[edu.cmu.cs.ls.keymaerax.core.UniformSubstitutionRule]]
    * @see [[edu.cmu.cs.ls.keymaerax.core.USubst]]
    */
  def US(subst: List[SubstitutionPair], delta: (Map[Formula, Formula]) = Map()): BuiltInTactic = ??? //PropositionalTacticsImpl.uniformSubstT(subst, delta)

  // conditional tactics

  /**
   * onBranch((lbl1,t1), (lbl2,t2)) uses tactic t1 on branch labelled lbl1 and t2 on lbl2
   * @see [[edu.cmu.cs.ls.keymaerax.tactics.BranchLabels]]
   * @see [[label()]]
   */
  def onBranch(s1: (String, BelleExpr), spec: (String, BelleExpr)*): BelleExpr = ??? //SearchTacticsImpl.onBranch(s1, spec:_*)

  /** Call/label the current proof branch s
    * @see [[onBranch()]]
    * @see [sublabel()]]
    */
  def label(s: String): BelleExpr = ??? //new LabelBranch(s)

  /** Mark the current proof branch and all subbranches s
    * @see [[label()]]
    */
  def sublabel(s: String): BelleExpr = ??? //new SubLabelBranch(s)

  // Locating applicable positions for PositionTactics


  /** Locate applicable position in antecedent on the left in which something matching the given shape occurs */
  def llu(tactic: PositionalTactic, shape: Formula): BuiltInTactic = ???
//    SearchTacticsImpl.locateAnte(tactic, f => UnificationMatch.unifiable(shape, f)!=None)
  /** Locate applicable position in antecedent on the left in which something matching the given shape occurs */
  def llu(tactic: PositionalTactic, shape: String): BuiltInTactic = llu(tactic, parser.formulaParser(shape))
  /** Locate applicable position in succedent on the right in which something matching the given shape occurs */
  def lru(tactic: PositionalTactic, shape: Formula): BuiltInTactic = ???
//    SearchTacticsImpl.locateSucc(tactic, f => UnificationMatch.unifiable(shape, f)!=None)
  /** Locate applicable position in succedent on the right in which something matching the given shape occurs */
  def lru(tactic: PositionalTactic, shape: String): BuiltInTactic = lru(tactic, parser.formulaParser(shape))

  /** Locate applicable position in succedent on the right in which fml occurs verbatim */
  def ls(tactic: BuiltInRightTactic, fml: String = "", key: Option[Expression] = None): BuiltInTactic = ???
//    SearchTacticsImpl.locateSucc(tactic,
//      if (fml == "") (_ => true) else _ == fml.asFormula,
//      if (key.isDefined) Some(_ == key.get) else None)
  /** Locate applicable position in succedent on the right */
  def lR(tactic: BuiltInRightTactic): BuiltInTactic = ls(tactic)
  /** Locate applicable position in antecedent on the left in which fml occurs verbatim  */
  def la(tactic: BuiltInLeftTactic, fml: String = "", key: Option[Expression] = None): BuiltInTactic = ???
//    SearchTacticsImpl.locateAnte(tactic,
//      if (fml == "") _ => true else _ == fml.asFormula,
//      if (key.isDefined) Some(_ == key.get) else None)
  /** Locate applicable position in antecedent on the left */
  def lL(tactic: BuiltInLeftTactic): BuiltInTactic = la(tactic)
  /** Locate applicable top-level position in left or right in antecedent or succedent */
  def l(tactic: PositionalTactic): BuiltInTactic  = ??? //TacticLibrary.locateAnteSucc(tactic)
  /** Locate applicable top-level position in left or right in antecedent or succedent */
  def l(tactic: DependentPositionTactic): DependentTactic = ???
  /** Locate applicable position within a given position */
  def lin(tactic: PositionalTactic): PositionalTactic = ???


  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Propositional tactics

  /** Hide/weaken whether left or right */
  lazy val hide               : BelleExpr = ProofRuleTactics.hide
  /** Hide/weaken given formula at given position */
  def hide(fml: Formula)      : BelleExpr = DebuggingTactics.assert(fml, "hiding") & ProofRuleTactics.hide
  /** Hide/weaken left: weaken a formula to drop it from the antecedent ([[edu.cmu.cs.ls.keymaerax.core.HideLeft HideLeft]]) */
  lazy val hideL              : BuiltInLeftTactic = ProofRuleTactics.hideL
  /** Hide/weaken right: weaken a formula to drop it from the succcedent ([[edu.cmu.cs.ls.keymaerax.core.HideRight HideRight]]) */
  lazy val hideR              : BuiltInRightTactic = ProofRuleTactics.hideR
  /** CoHide/coweaken whether left or right: drop all other formulas from the sequent ([[edu.cmu.cs.ls.keymaerax.core.CoHideLeft CoHideLeft]]) */
  lazy val cohide             : DependentPositionTactic = ProofRuleTactics.coHide
  /** CoHide/coweaken whether left or right: drop all other formulas from the sequent ([[edu.cmu.cs.ls.keymaerax.core.CoHideLeft CoHideLeft]]) */
  def cohide(fml: Formula)    : BelleExpr = DebuggingTactics.assert(fml, "cohiding") & cohide
  /** CoHide2/coweaken2 both left and right: drop all other formulas from the sequent ([[edu.cmu.cs.ls.keymaerax.core.CoHide2 CoHide2]]) */
  def cohide2: BuiltInTwoPositionTactic = ProofRuleTactics.coHide2
  /** !L Not left: move an negation in the antecedent to the succedent ([[edu.cmu.cs.ls.keymaerax.core.NotLeft NotLeft]]) */
  lazy val notL               : BuiltInLeftTactic = ProofRuleTactics.notL
  /** !R Not right: move an negation in the succedent to the antecedent ([[edu.cmu.cs.ls.keymaerax.core.NotRight NotRight]]) */
  lazy val notR               : BuiltInRightTactic = ProofRuleTactics.notR
  /** &L And left: split a conjunction in the antecedent into separate assumptions ([[edu.cmu.cs.ls.keymaerax.core.AndLeft AndLeft]]) */
  lazy val andL               : BuiltInLeftTactic = ProofRuleTactics.andL
  /** &R And right: prove a conjunction in the succedent on two separate branches ([[edu.cmu.cs.ls.keymaerax.core.AndRight AndRight]]) */
  lazy val andR               : BuiltInRightTactic = ProofRuleTactics.andR
  /** |L Or left: use a disjunction in the antecedent by assuming each option on separate branches ([[edu.cmu.cs.ls.keymaerax.core.OrLeft OrLeft]]) */
  lazy val orL                : BuiltInLeftTactic = ProofRuleTactics.orL
  /** |R Or right: split a disjunction in the succedent into separate formulas to show alternatively ([[edu.cmu.cs.ls.keymaerax.core.OrRight OrRight]]) */
  lazy val orR                : BuiltInRightTactic = ProofRuleTactics.orR
  /** ->L Imply left: use an implication in the antecedent by proving its left-hand side on one branch and using its right-hand side on the other branch ([[edu.cmu.cs.ls.keymaerax.core.ImplyLeft ImplyLeft]]) */
  lazy val implyL             : BuiltInLeftTactic = ProofRuleTactics.implyL
  /** ->R Imply right: prove an implication in the succedent by assuming its left-hand side and proving its right-hand side ([[edu.cmu.cs.ls.keymaerax.core.ImplyRight ImplyRight]]) */
  lazy val implyR             : BuiltInRightTactic = ProofRuleTactics.implyR
  /** <->L Equiv left: use an equivalence by considering both true or both false cases ([[edu.cmu.cs.ls.keymaerax.core.EquivLeft EquivLeft]]) */
  lazy val equivL             : BuiltInLeftTactic = ProofRuleTactics.equivL
  /** <->R Equiv right: prove an equivalence by proving both implications ([[edu.cmu.cs.ls.keymaerax.core.EquivRight EquivRight]]) */
  lazy val equivR             : BuiltInRightTactic = ProofRuleTactics.equivR

  /** cut a formula in to prove it on one branch and then assume it on the other. Or to perform a case distinction on whether it holds ([[edu.cmu.cs.ls.keymaerax.core.Cut Cut]]) */
  def cut(cut : Formula)      : InputTactic[Formula]         = ProofRuleTactics.cut(cut)
  /** cut a formula in in place of pos on the right to prove it on one branch and then assume it on the other. ([[edu.cmu.cs.ls.keymaerax.core.CutRight CutRight]]) */
  def cutR(cut : Formula)     : (SuccPos => InputTactic[Formula])  = ProofRuleTactics.cutR(cut)
  /** cut a formula in in place of pos on the left to prove it on one branch and then assume it on the other. ([[edu.cmu.cs.ls.keymaerax.core.CutLeft CutLeft]]) */
  def cutL(cut : Formula)     : (AntePos => InputTactic[Formula])  = ProofRuleTactics.cutL(cut)
  /** cut a formula in in place of pos to prove it on one branch and then assume it on the other (whether pos is left or right). ([[edu.cmu.cs.ls.keymaerax.core.CutLeft CutLeft]] or [[edu.cmu.cs.ls.keymaerax.core.CutRight CutRight]]) */
  def cutLR(cut : Formula)    : (Position => InputTactic[Formula])  = ProofRuleTactics.cutLR(cut)

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // First-order tactics

  // quantifiers
  /** all right: Skolemize a universal quantifier in the succedent ([[edu.cmu.cs.ls.keymaerax.core.Skolemize Skolemize]]) */
  lazy val allR               : BuiltInRightTactic = ProofRuleTactics.skolemizeR
  /** all left: instantiate a universal quantifier in the antecedent by a concrete instance */
  def allL(x: Variable, inst: Term) : BuiltInLeftTactic = ??? //TacticLibrary.instantiateQuanT(x, inst)
  def allL(inst: Term)        : BuiltInLeftTactic = ??? //TacticLibrary.instantiateQuanT(???, inst)
  /** exists left: Skolemize an existential quantifier in the antecedent */
  lazy val existsL            : BuiltInLeftTactic = ProofRuleTactics.skolemizeL
  /** exists right: instantiate an existential quantifier in the succedwent by a concrete instance as a witness */
  def existsR(x: Variable, inst: Term) : BuiltInRightTactic = ??? //TacticLibrary.instantiateQuanT(x, inst)
  def existsR(inst: Term)     : BuiltInRightTactic = ??? //TacticLibrary.instantiateQuanT(???, inst)

  // modalities

  /** assignb: [:=] simplify assignment `[x:=f;]p(x)` by substitution `p(f)` or equation */
  lazy val assignb            : BuiltInPositionTactic = ??? //TacticLibrary.boxAssignT
  /** randomb: [:*] simplify nondeterministic assignment `[x:=*;]p(x)` to a universal quantifier `\forall x p(x)` */
  lazy val randomb            : BuiltInPositionTactic = ??? //TacticLibrary.boxNDetAssign
  /** testb: [?] simplifies test `[?q;]p` to an implication `q->p` */
  lazy val testb              : DependentPositionTactic = useAt("[?] test")
  /** diffSolve: solve a differential equation `[x'=f]p(x)` to `\forall t>=0 [x:=solution(t)]p(x)` */
  lazy val diffSolve          : BuiltInPositionTactic = ??? //TacticLibrary.diffSolutionT
  /** choiceb: [++] handles both cases of a nondeterministic choice `[a++b]p(x)` separately `[a]p(x) & [b]p(x)` */
  lazy val choiceb            : DependentPositionTactic = useAt("[++] choice")
  /** composeb: [;] handle both parts of a sequential composition `[a;b]p(x)` one at a time `[a][b]p(x)` */
  lazy val composeb           : DependentPositionTactic = useAt("[;] compose")
  /** iterateb: [*] prove a property of a loop `[{a}*]p(x)` by unrolling it once `p(x) & [a][{a}*]p(x)` */
  lazy val iterateb           : BelleExpr = HilbertCalculus.iterateb

  /** splitb: splits `[a](p&q)` into `[a]p & [a]q` */
  lazy val splitb             : DependentPositionTactic = useAt("[] split")

  /** I: prove a property of a loop by induction with the given loop invariant (hybrid systems) */
  def I(invariant : Formula)  : BuiltInPositionTactic = ??? //TacticLibrary.inductionT(Some(invariant))
  /** loop=I: prove a property of a loop by induction with the given loop invariant (hybrid systems) */
  def loop(invariant: Formula) = I(invariant)
  /** K: modal modus ponens (hybrid systems) */
  lazy val K                  : DependentPositionTactic = useAt("K modal modus ponens", PosInExpr(1::Nil))
  /** V: vacuous box [a]p() will be discarded and replaced by p() provided a does not changes values of postcondition p */
  lazy val V                  : BuiltInPositionTactic = ??? //HybridProgramTacticsImpl.boxVacuousT

  // differential equations
  /** DW: Differential Weakening to use evolution domain constraint `[{x'=f(x)&q(x)}]p(x)` reduces to `[{x'=f(x)&q(x)}](q(x)->p(x))` */
  lazy val DW                 : BuiltInPositionTactic = ??? //TacticLibrary.diffWeakenT
  /** DC: Differential Cut a new invariant for a differential equation `[{x'=f(x)&q(x)}]p(x)` reduces to `[{x'=f(x)&q(x)&C(x)}]p(x)` with `[{x'=f(x)&q(x)}]C(x)`. */
  def DC(invariant: Formula)  : BuiltInPositionTactic = ??? //TacticLibrary.diffCutT(invariant)
  /** DE: Differential Effect exposes the effect of a differential equation `[x'=f(x)]p(x,x')` on its differential symbols as `[x'=f(x)][x':=f(x)]p(x,x')` */
  lazy val DE                 : BuiltInPositionTactic = ??? //ODETactics.diffEffectT
  /** DI: Differential Invariant proves a formula to be an invariant of a differential equation */
  lazy val DI                 : BuiltInPositionTactic = ??? //TacticLibrary.diffInvariant
  /** DG: Differential Ghost add auxiliary differential equations with extra variables `y'=a*y+b`.
    * `[x'=f(x)&q(x)]p(x)` reduces to `\exists y [x'=f(x),y'=a*y+b&q(x)]p(x)`.
    */
  def DG(y:Variable, a:Term, b:Term) : BelleExpr = ??? //ODETactics.diffAuxiliaryT(y,a,b)
  /** DA: Differential Ghost add auxiliary differential equations with extra variables y'=a*y+b and postcondition replaced by r.
    * {{{
    * G |- p(x), D   |- r(x,y) -> [x'=f(x),y'=g(x,y)&q(x)]r(x,y)
    * ----------------------------------------------------------- DA
    * G |- [x'=f(x)&q(x)]p(x), D
    * }}}
    * @see[[DA(Variable, Term, Term, Provable)]]
    * @note Uses QE to prove p(x) <-> \exists y. r(x,y)
    * @note G |- p(x) will be proved already from G if p(x) in G (verbatim)
    */
  def DA(y:Variable, a:Term, b:Term, r:Formula) : BuiltInPositionTactic = ??? //ODETactics.diffAuxiliariesRule(y,a,b,r)
  /**
   * DA: Differential Ghost expert mode. Use if QE cannot prove p(x) <-> \exists y. r(x,y).
   * To obtain a Provable with conclusion p(x) <-> \exists y. r(x,y), use TactixLibrary.by, for example:
   * @example{{{
   *   val provable = by("x>0 <-> \exists y (y>0&x*y>0)".asFormula, QE)
   * }}}
   * @see[[DA(Variable, Term, Term, Formula)]]
   * @see[[by]]
   **/
  def DA(y:Variable, a:Term, b:Term, r:Provable) : BuiltInPositionTactic = ??? //ODETactics.diffAuxiliariesRule(y,a,b,r)
  /** DS: Differential Solution solves a differential equation */
  def DS                      : BuiltInPositionTactic = ???

  /** Dassignb: Substitute a differential assignment `[x':=f]p(x')` to `p(f)` */
  lazy val Dassignb           : BuiltInPositionTactic = ??? //HybridProgramTacticsImpl.boxDerivativeAssignT
  /** Dplus: +' derives a sum `(f(x)+g(x))' = (f(x))' + (g(x))'` */
  lazy val Dplus              : BuiltInPositionTactic = ??? //SyntacticDerivationInContext.AddDerivativeT
  /** neg: -' derives unary negation `(-f(x))' = -(f(x)')` */
  lazy val Dneg               : BuiltInPositionTactic = ??? //SyntacticDerivationInContext.NegativeDerivativeT
  /** Dminus: -' derives a difference `(f(x)-g(x))' = (f(x))' - (g(x))'` */
  lazy val Dminus             : BuiltInPositionTactic = ??? //SyntacticDerivationInContext.SubtractDerivativeT
  /** Dtimes: *' derives a product `(f(x)*g(x))' = f(x)'*g(x) + f(x)*g(x)'` */
  lazy val Dtimes             : BuiltInPositionTactic = ??? //SyntacticDerivationInContext.MultiplyDerivativeT
  /** Dquotient: /' derives a quotient `(f(x)/g(x))' = (f(x)'*g(x) - f(x)*g(x)') / (g(x)^2)` */
  lazy val Dquotient          : BuiltInPositionTactic = ??? //SyntacticDerivationInContext.DivideDerivativeT
  /** Dcompose: o' derives a function composition by chain rule */
  lazy val Dcompose           : BuiltInPositionTactic = ???
  /** Dconstify: substitute non-bound occurences of x with x() */
  lazy val Dconstify          : BuiltInPositionTactic = ???

  /** Prove the given list of differential invariants in that order by DC+DI */
  //@todo could change type to invariants: Formula* if considered more readable
  def diffInvariant(invariants: List[Formula]): BuiltInPositionTactic = ???

  // more

  /* Generalize postcondition to C and, separately, prove that C implies postcondition
   * {{{
   *   genUseLbl:        genShowLbl:
   *   G |- [a]C, D      C |- B
   *   ------------------------
   *          G |- [a]B, D
   * }}}
   */
  def generalize(C: Formula)  : BuiltInPositionTactic = ???

  /** Prove the given cut formula to hold for the modality at position and turn postcondition into cut->post
    * {{{
    *   cutUseLbl:           cutShowLbl:
    *   G |- [a](C->B), D    G |- [a]C, D
    *   ---------------------------------
    *          G |- [a]B, D
    * }}}
    */
  def postCut(cut: Formula)   : BuiltInPositionTactic = ???



  // closing

  /** QE: Quantifier Elimination to decide arithmetic (after simplifying logical transformations) */
  lazy val QE                : BelleExpr         = ToolTactics.fullQE(
    /*@todo seriously?*/ edu.cmu.cs.ls.keymaerax.tactics.Tactics.MathematicaScheduler.tool.asInstanceOf[QETool])

  /** close: closes the branch when the same formula is in the antecedent and succedent or true or false close */
  lazy val close             : BuiltInTwoPositionTactic         = ProofRuleTactics.close
  /** close: closes the branch when the same formula is in the antecedent and succedent ([[edu.cmu.cs.ls.keymaerax.core.Close Close]]) */
  def close(a: AntePosition, s: SuccPosition) : BelleExpr = cohide2(a, s) & ProofRuleTactics.trivialCloser
  def close(a: Int, s: Int)  : BelleExpr = close(new AntePosition(SeqPos(a).asInstanceOf[AntePos].getIndex), new SuccPosition(SeqPos(s).asInstanceOf[SuccPos].getIndex))
  /** closeId: closes the branch when the same formula is in the antecedent and succedent ([[edu.cmu.cs.ls.keymaerax.core.Close Close]]) */
  lazy val closeId           : BuiltInTactic         = ???
  /** closeT: closes the branch when true is in the succedent ([[edu.cmu.cs.ls.keymaerax.core.CloseTrue CloseTrue]]) */
  lazy val closeT            : BuiltInRightTactic = ProofRuleTactics.closeTrue
  /** closeF: closes the branch when false is in the antecedent ([[edu.cmu.cs.ls.keymaerax.core.CloseFalse CloseFalse]]) */
  lazy val closeF            : BuiltInLeftTactic = ProofRuleTactics.closeFalse

  // counter example

  /** Generate counter example */
//  lazy val counterEx         : Tactic         = TacticLibrary.counterExampleT

  // derived propositional

  /** Turn implication on the right into an equivalence, which is useful to prove by CE etc. ([[edu.cmu.cs.ls.keymaerax.core.EquivifyRight EquivifyRight]]) */
  lazy val equivifyR          : BelleExpr = ???
  /** Modus Ponens: p&(p->q) -> q */
  def modusPonens(assumption: AntePos, implication: AntePos): BelleExpr = PropositionalTactics.modusPonens(assumption, implication)
  /** Commute equivalence on the left [[edu.cmu.cs.ls.keymaerax.btactics.ProofRuleTactics.commuteEquivL]] */
  lazy val commuteEquivL      : BuiltInLeftTactic = ProofRuleTactics.commuteEquivL
  /** Commute equivalence on the right [[edu.cmu.cs.ls.keymaerax.btactics.ProofRuleTactics.commuteEquivR]] */
  lazy val commuteEquivR      : BuiltInRightTactic = ProofRuleTactics.commuteEquivR
  //@todo commuteEqual

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Bigger Tactics.
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // Utility Tactics
  /** nil: skip is a no-op tactic that has no effect */
  lazy val nil : BelleExpr = Idioms.ident
  /** nil: skip is a no-op tactic that has no effect */
  lazy val skip : BelleExpr = nil

  /** abbrv(name) Abbreviate the term at the given position by a new name and use that name at all occurrences of that term. */
  def abbrv(name: Variable): BuiltInPositionTactic = ??? //EqualityRewritingImpl.abbrv(name)


  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Contract Tactics and Debugging Tactics
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // Tactic contracts
  /** Assert that the given condition holds for the goal's sequent. */
  def assertT(cond : Sequent=>Boolean, msg: => String): BelleExpr = ??? //DebuggingTactics.assert(cond, msg)
  /** Assertion that the sequent has the specified number of antecedent and succedent formulas, respectively. */
  def assertT(antecedents: Int, succedents: Int, msg: => String = ""): BelleExpr = DebuggingTactics.assert(antecedents, succedents, msg)
  /** Assert that the given formula is present at the given position in the sequent that this tactic is applied to. */
  def assertT(expected: Formula, pos: Position, msg: => String): BelleExpr = DebuggingTactics.assert(expected, msg)(pos)

  // PositionTactic contracts
  /** Assert that the given condition holds for the sequent at the position where the tactic is applied */
  def assertT(cond : (Sequent,Position)=>Boolean, msg: => String): BelleExpr = ??? //DebuggingTactics.assertPT(cond, msg)
  /** Assert that the given expression is present at the position in the sequent where this tactic is applied to. */
  def assertE(expected: => Expression, msg: => String): BuiltInPositionTactic = ???
//    //@todo could do if (DEBUG) to save cycles
//    assertPT((s, pos) => s.sub(pos) == Some(expected), msg + "\nExpected: " + expected.prettyString /*+ "\nFound:   " + s.sub(pos)*/)

  /** errorT raises an error upon executing this tactic, stopping processing */
  def errorT(msg: => String): BuiltInTactic = DebuggingTactics.error(msg)

  /** debug(s) sprinkles debug message s into the output and the ProofNode information */
  def debug(s: => Any): BuiltInTactic = DebuggingTactics.debug(s.toString)
  /** debugAt(s) sprinkles debug message s into the output and the ProofNode information */
  def debugAt(s: => Any): BuiltInPositionTactic = ??? //TacticLibrary.debugAtT(s)

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Special functions
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  /** Expands abs using abs(x)=y <-> (x>=0&y=x | x<=0&y=-x) */
  def abs: BuiltInPositionTactic = ??? //ArithmeticTacticsImpl.AbsT
  /** Expands min using min(x,y)=z <-> (x<=y&z=x | x>=y&z=y) */
  def min: BuiltInPositionTactic = ??? //ArithmeticTacticsImpl.MinMaxT
  /** Expands max using max(x,y)=z <-> (x>=y&z=x | x<=y&z=y) */
  def max: BuiltInPositionTactic = ??? //ArithmeticTacticsImpl.MinMaxT


  /** Alpha rules are propositional rules that do not split */
  def alphaRule: BelleExpr = lL(andL) | lR(orR) | lR(implyR) | lL(notL) | lR(notR)
  /** Beta rules are propositional rules that split */
  def betaRule: BelleExpr = lR(andR) | lL(orL) | lL(implyL) | lL(equivL) | lR(equivR)
  /** Real-closed field arithmetic after consolidating sequent into a single universally-quantified formula */
  def RCF: BelleExpr = ??? //PropositionalTacticsImpl.ConsolidateSequentT & assertT(0, 1) & FOQuantifierTacticsImpl.universalClosureT(1) & debug("Handing to Mathematica") &
    //ArithmeticTacticsImpl.quantifierEliminationT("Mathematica")

  /** Lazy Quantifier Elimination after decomposing the logic in smart ways */
  //@todo ideally this should be ?RCF so only do anything of RCF if it all succeeds with true
  def lazyQE = (
    ((alphaRule | ls(allR) | la(existsL)
      | close
      //@todo eqLeft|eqRight for equality rewriting directionally toward easy
      //| (la(TacticLibrary.eqThenHideIfChanged)*)
      | betaRule)*@TheType())
      | RCF)


  // Global Utility Functions

  /**
   * Prove the new goal by the given tactic, returning the resulting Provable
   * @see [[TactixLibrary.by(Provable)]]
   * @see [[proveBy()]]
   * @example {{{
   *   import StringConverter._
   *   import TactixLibrary._
   *   val proof = TactixLibrary.proveBy(Sequent(Nil, IndexedSeq(), IndexedSeq("(p()|q()->r()) <-> (p()->r())&(q()->r())".asFormula)), prop)
   * }}}
   */
  def proveBy(goal: Sequent, tactic: BelleExpr): Provable = {
    val v = BelleProvable(Provable.startProof(goal))
    //@todo fetch from some factory
    SequentialInterpreter()(tactic, v) match {
      case BelleProvable(provable) => provable
      case r => throw new BelleUserGeneratedError("Error in proveBy, goal\n " + goal + " was not provable but instead resulted in\n " + r)
    }
  }
  /**
   * Prove the new goal by the given tactic, returning the resulting Provable
   * @see [[TactixLibrary.by(Provable)]]
   * @example {{{
   *   import StringConverter._
   *   import TactixLibrary._
   *   val proof = TactixLibrary.proveBy("(p()|q()->r()) <-> (p()->r())&(q()->r())".asFormula, prop)
   * }}}
   */
  def proveBy(goal: Formula, tactic: BelleExpr): Provable = proveBy(Sequent(Nil, IndexedSeq(), IndexedSeq(goal)), tactic)

}
