/**
* Copyright (c) Carnegie Mellon University. CONFIDENTIAL
* See LICENSE.txt for the conditions of this license.
*/
package edu.cmu.cs.ls.keymaerax.tactics

import ExpressionTraversal.{StopTraversal, ExpressionTraversalFunction, TraverseToPosition}
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.tactics.BranchLabels._
import NNFRewrite.rewriteDoubleNegationEliminationT
import edu.cmu.cs.ls.keymaerax.tactics.AxiomaticRuleTactics.boxMonotoneT
import edu.cmu.cs.ls.keymaerax.tactics.AxiomTactic.{uncoverAxiomT, axiomLookupBaseT}
import edu.cmu.cs.ls.keymaerax.tactics.ContextTactics.cutInContext
import edu.cmu.cs.ls.keymaerax.tactics.EqualityRewritingImpl.equivRewriting
import edu.cmu.cs.ls.keymaerax.tactics.FOQuantifierTacticsImpl.existsDualT
import edu.cmu.cs.ls.keymaerax.tactics.PropositionalTacticsImpl.{AndRightT,AxiomCloseT,ImplyLeftT,ImplyRightT,
  ImplyToAndT, cutT, hideT, cohideT, cohide2T, kModalModusPonensT, modusPonensT, uniformSubstT}
import edu.cmu.cs.ls.keymaerax.tactics.Tactics._
import BindingAssessment.allNames
import edu.cmu.cs.ls.keymaerax.tactics.AlphaConversionHelper._
import edu.cmu.cs.ls.keymaerax.tactics.TacticLibrary._
import edu.cmu.cs.ls.keymaerax.tactics.TacticLibrary.TacticHelper.freshIndexInFormula
import edu.cmu.cs.ls.keymaerax.tactics.SearchTacticsImpl.{lastAnte, lastSucc}
import TacticHelper.getFormula
import SearchTacticsImpl.onBranch
import edu.cmu.cs.ls.keymaerax.tools.Tool

import scala.collection.immutable.{List, Seq}
import scala.collection.mutable
import scala.language.postfixOps



/**
 * Implementation of tactics for handling hybrid programs.
 */
object HybridProgramTacticsImpl {
  private class ModalityUnapplyer[T: Manifest](m: T => Option[(Program, Formula)]) {
    def unapply(a: Any): Option[(Program, Formula)] = {
      if (manifest[T].runtimeClass.isInstance(a)) m(a.asInstanceOf[T]) else None
    }
  }

  /*********************************************
   * Axiom Tactics
   *********************************************/

  class ByDualityAxiomTactic(base: PositionTactic) extends PositionTactic(base.name) {
    override def applies(s: Sequent, p: Position): Boolean = getFormula(s, p) match {
      case f@Diamond(prg, phi) => base.applies(s.updated(p, replaceAtPos(f, Box(prg, Not(phi)), p.inExpr)), p)
      case f@Box(prg, phi) => base.applies(s.updated(p, replaceAtPos(f, Diamond(prg, Not(phi)), p.inExpr)), p)
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(name) {
      def applicable(node : ProofNode): Boolean = applies(node.sequent, p)
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = getFormula(node.sequent, p) match {
        case Diamond(prg, phi) =>
          Some(diamondDualityT(p) & base(p.first) &
            (boxDualityT(p.first) & rewriteDoubleNegationEliminationT(p) |
              existsDualT(p) |
              NilT))
        case Box(prg, phi) =>
          Some(boxDualityT(p) & base(p.first) & (diamondDualityT(p.first) & rewriteDoubleNegationEliminationT(p) | NilT))
        case _ => None
      }
    }

    def replaceAtPos(fml: Formula, repl: Formula, where: PosInExpr): Formula = {
      ExpressionTraversal.traverse(TraverseToPosition(where, new ExpressionTraversalFunction {
        override def preF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] = {
          if (p == where) Right(repl)
          else Left(Some(ExpressionTraversal.stop))
        }
      }), fml) match {
        case Some(f) => f
      }
    }
  }

  def boxDualityT: PositionTactic = {
    def g(f: Formula): Formula = f match {
      case Box(prg, phi) => Equiv(Not(Diamond(prg, Not(phi))), f)
      case Not(Diamond(prg, Not(phi))) => Equiv(f, Box(prg, phi))
      case _ => False
    }

    uncoverAxiomT("[] dual", g, _ => boxDualityBaseT)
  }
  /* Base tactic for boxDualityT */
  private def boxDualityBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(_, Box(prg, phi)) =>
        val aA = ProgramConst("a")
        val aP = PredOf(Function("p", None, Real, Bool), Anything)
        SubstitutionPair(aA, prg) :: SubstitutionPair(aP, phi) :: Nil
      case Equiv(Not(Diamond(prg, Not(phi))), _) =>
        val aA = ProgramConst("a")
        val aP = PredOf(Function("p", None, Real, Bool), Anything)
        SubstitutionPair(aA, prg) :: SubstitutionPair(aP, phi) :: Nil
    }

    axiomLookupBaseT("[] dual", subst, _ => NilPT, (f, ax) => ax)
  }

  def boxSeqGenT(q: Formula): PositionTactic = new PositionTactic("[;] generalize") {
    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case Box(Compose(_, _), _) => true
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
        case Box(Compose(a, b), phi) =>
          Some(boxSeqT(p) & cutT(Some(Box(a, q))) & onBranch(
            // boxSeqT will move its result into last succ, cut later moves one behind
            (cutShowLbl, hideT(SuccPosition(node.sequent.succ.length - 1))),
            (cutUseLbl,
              // cut shows up at last ante
              (0 until node.sequent.ante.length).foldRight(NilT)((i, t) => t & hideT(AntePosition(i))) &
              // boxSeqT will move programs into last succ
              (0 until node.sequent.succ.length - 1).foldRight(NilT)((i, t) => t & hideT(SuccPosition(i))) &
              boxMonotoneT
              )
          ))
      }
    }
  }

  def diamondDualityT: PositionTactic = {
    def g(f: Formula): Formula = f match {
      case Diamond(prg, phi) => Equiv(Not(Box(prg, Not(phi))), f)
      case Not(Box(prg, Not(phi))) => Equiv(f, Diamond(prg, phi))
      case _ => False
    }

    uncoverAxiomT("<> dual", g, _ => diamondDualityBaseT)
  }
  /* Base tactic for diamondDualityT */
  private def diamondDualityBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(_, Diamond(prg, phi)) =>
        val aA = ProgramConst("a")
        val aP = PredOf(Function("p", None, Real, Bool), Anything)
        SubstitutionPair(aA, prg) :: SubstitutionPair(aP, phi) :: Nil
      case Equiv(Not(Box(prg, Not(phi))), _) =>
        val aA = ProgramConst("a")
        val aP = PredOf(Function("p", None, Real, Bool), Anything)
        SubstitutionPair(aA, prg) :: SubstitutionPair(aP, phi) :: Nil
    }

    axiomLookupBaseT("<> dual", subst, _ => NilPT, (f, ax) => ax)
  }

  /**
   * Creates a new axiom tactic for differential box assignment [x := t;].
   *  [v':=t;]p(v') <-> p(t)
   * @author Nathan Fulton
   * @author Stefan Mitsch
   * @return The axiom tactic.
   *
   */
  def boxDerivativeAssignT: PositionTactic = {
    def g(f: Formula): Formula = f match {
      case Box(DiffAssign(d : DifferentialSymbol, t), p) =>
        Equiv(f, SubstitutionHelper.replaceFree(p)(d, t))
      case _ => False
    }

    uncoverAxiomT("[':=] differential assign", g, f => boxDerivativeAssignBaseT)
  }
  /** Base tactic for box derivative assignment */
  private def boxDerivativeAssignBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(Box(DiffAssign(d@DifferentialSymbol(v), t), p), _) =>
        val aT = FuncOf(Function("t", None, Unit, v.sort), Nothing)
        val aP = PredOf(Function("p", None, v.sort, Bool), DotTerm) //(p(t)
        SubstitutionPair(aT, t) :: SubstitutionPair(aP, SubstitutionHelper.replaceFree(p)(d, DotTerm)) :: Nil
    }

    def alpha(fml: Formula): PositionTactic = fml match {
      case Equiv(Box(DiffAssign(DifferentialSymbol(v), t), p), _) =>
        val aV = Variable("v", None, v.sort)
        if (v.name != aV.name || v.index != aV.index) {
          new PositionTactic("Alpha") {
            override def applies(s: Sequent, p: Position): Boolean = s(p) match {
              case Equiv(Box(DiffAssign(DifferentialSymbol(_), _), _), _) => true
              case _ => false
            }

            override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
              override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
                Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

              override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
            }
          }
        } else NilPT
    }

    def axiomInstance(fml: Formula, axiom: Formula): Formula = fml match {
      case Equiv(Box(DiffAssign(DifferentialSymbol(v), t), p), _) =>
        val aV = Variable("v", None, v.sort)
        if (v.name == aV.name && v.index == aV.index) axiom
        else replace(axiom)(aV, v)
    }

    axiomLookupBaseT("[':=] differential assign", subst, alpha, axiomInstance)
  }

  /**
   * Creates a new axiom tactic for box assignment [x := t;]. Uses the box assignment tactic most appropriate
   * for the specific position.
   * @return The axiom tactic.
   * @author Stefan Mitsch
   */
  def boxAssignT: PositionTactic = boxAssignT(FOQuantifierTacticsImpl.skolemizeT)
  def boxAssignT(skolemizeHow: Boolean => PositionTactic): PositionTactic =
      new PositionTactic("[:=] assign equational") {
    override def applies(s: Sequent, p: Position): Boolean = p.inExpr == HereP && (s(p) match {
      case Box(Assign(Variable(_, _, _), _), _) => true
      case _ => false
    })

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      def assignEqualMandatory(v: Variable, t: Term, rest: Formula) = allNames(t).contains(v) || (rest match {
        case Box(_: DifferentialProgram, _) => true
        case Box(_: Loop, _) => true
        case _ => /* false requires substitution of variables */ true
      })

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
        case Box(Assign(v: Variable, t: Term), phi) if assignEqualMandatory(v, t, phi) =>
          if (p.isAnte) Some(boxAssignEqualT(p))
          // TODO could use v2vAssignT without Skolemize and ImplyRight first
          else Some(boxAssignEqualT(p) & skolemizeHow(true)(p) & ImplyRightT(p) &
            // remove stuttering assignment that may have been introduced, but don't work on assignments that were
            // already present in the original model.
            // (still not perfect, i.e., may handle multiple assignments at once: e.g., x:=x;x:=x;)
            ifElseT(n => getFormula(n.sequent, p) match {
              case Box(Assign(v2: Variable, v3: Variable), _) => v.name == v2.name && v.name == v3.name
              case _ => false }, v2vAssignT(p), NilT))
        case Box(Assign(v: Variable, t: Term), phi) if !assignEqualMandatory(v, t, phi) =>
          Some(substitutionBoxAssignT(p))
        }
      }
  }

  /**
   * Returns a position tactic to apply the := assign dual axiom, which turns a diamond assignment into a box assignment
   * and vice versa.
   * @example{{{
   *           |- [x:=2;]x=2
   *           -------------assignDual(SuccPosition(0))
   *           |- <x:=2;>x=2
   * }}}
   * @example{{{
   *           |- <x:=2;>x=2
   *           -------------assignDual(SuccPosition(0))
   *           |- [x:=2;]x=2
   * }}}
   * @return The tactic to apply the := assign dual axiom.
   */
  def assignDualT: PositionTactic = {
    def g(f: Formula): Formula = f match {
      case Diamond(prg@Assign(_, _), phi) => Equiv(f, Box(prg, phi))
      case Box(prg@Assign(_, _), phi) => Equiv(Diamond(prg, phi), f)
      case _ => False
    }
    uncoverAxiomT(":= assign dual", g, _ => assignDualBaseT)
  }
  /** Base tactic for assign dual */
  private def assignDualBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(Diamond(Assign(v, t), p), _) =>
        val aT = FuncOf(Function("t", None, Unit, v.sort), Nothing)
        val aP = PredOf(Function("p", None, v.sort, Bool), DotTerm) // p(.)
        SubstitutionPair(aT, t) :: SubstitutionPair(aP, SubstitutionHelper.replaceFree(p)(v, DotTerm)) :: Nil
    }

    def alpha(fml: Formula): PositionTactic = fml match {
      case Equiv(Diamond(Assign(v, _), _), _) =>
        val aV = Variable("v", None, v.sort)
        if (v.name != aV.name || v.index != aV.index) {
          new PositionTactic("Alpha") {
            override def applies(s: Sequent, p: Position): Boolean = s(p) match {
              case Equiv(Diamond(Assign(_, _), _), _) => true
              case _ => false
            }

            override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
              override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = Some(globalAlphaRenamingT(v, aV))
              override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
            }
          }
        } else NilPT
    }

    def axiomInstance(fml: Formula, axiom: Formula): Formula = fml match {
      case Equiv(Diamond(Assign(v, _), _), _) =>
        val aV = Variable("v", None, v.sort)
        if (v.name == aV.name && v.index == aV.index) axiom
        else replace(axiom)(aV, v)
    }

    axiomLookupBaseT(":= assign dual", subst, alpha, axiomInstance)
  }

  /**
   * Creates a new axiom tactic for diamond assignment equational &lt;x := t;&gt;. The tactic may introduce
   * stuttering assignments, if necessary (e.g., when followed by a loop or ODE).
   * @example{{{
   *           |- \exists x (x=2+y & x>y)
   *           --------------------------diamondAssignEqualT(SuccPos(0))
   *           |- <x:=2+y>(x>y)
   * }}}
   * @example{{{
   *           |- \exists x (x=2 & [x_0:=x]<{x_0'=3}>(x_0>y))
   *           ----------------------------------------------diamondAssignEqualT(SuccPos(0))
   *           |- <x:=2><{x'=3}>(x>y)
   * }}}
   * @return The tactic to apply the equational assign axiom.
   * @author Stefan Mitsch
   */
  def diamondAssignEqualT: PositionTactic = assignEqualT("<:=> assign equational", Diamond.unapply, diamondAssignWithoutAlphaT)

  /**
   * Creates a new axiom tactic for box assignment equational [x := t;]. The tactic may introduce stuttering
   * assignments, if necessary (e.g., when followed by a loop or ODE).
   * @example{{{
   *           |- \forall x (x=2+y -> x>y)
   *           ---------------------------boxAssignEqualT(SuccPos(0))
   *           |- [x:=2+y](x>y)
   * }}}
   * @example{{{
   *           |- \forall x (x=2 -> [x_0:=x][{x_0'=3}](x_0>y))
   *           -----------------------------------------------boxAssignEqualT(SuccPos(0))
   *           |- [x:=2][{x'=3}](x>y)
   * }}}
   * @return The axiom tactic to apply the equational assign axiom.
   * @author Stefan Mitsch
   */
  def boxAssignEqualT: PositionTactic = assignEqualT("[:=] assign equational", Box.unapply, boxAssignWithoutAlphaT)

  /** Generic implementation for box/diamond assign equational */
  private def assignEqualT[T: Manifest](name: String, mod: T => Option[(Program, Formula)],
                                        baseAssignT: (Variable, Boolean) => Position => Tactic): PositionTactic = new PositionTactic(name) {
    val BDModality = new ModalityUnapplyer(mod)

    override def applies(s: Sequent, p: Position): Boolean = getFormula(s, p) match {
      case BDModality(Assign(Variable(_, _,_), _), _) => true
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        val f = getFormula(node.sequent, p)
        // construct a new name for the quantified variable
        val (newV1, newV2) = f match {
          case BDModality(Assign(v: Variable, _), _) =>
            val tIdx = freshIndexInFormula(v.name, f)
            (Variable(v.name, tIdx, v.sort), Variable(v.name, Some(tIdx.get + 1), v.sort))
          case _ => throw new IllegalStateException("Impossible by assignEqualT.applies")
        }

        f match {
          case BDModality(Assign(v: Variable, _), phi: Modal)
            if loopsAndODEsOf(phi).exists(p => StaticSemantics.symbols(p).contains(v) &&
              !NameCategorizer.freeVariables(p).contains(v)) => Some(
            alphaRenamingT(v, newV1)(p.second) & baseAssignT(newV2, false)(p)
          )
          case _ => Some(baseAssignT(newV1, true)(p))
        }
      }
    }

    private def loopsAndODEsOf(fml: Formula): List[Program] = {
      val result: mutable.MutableList[Program] = mutable.MutableList()
      ExpressionTraversal.traverse(new ExpressionTraversalFunction() {
        override def preP(p: PosInExpr, e: Program): Either[Option[StopTraversal], Program] = e match {
          case Loop(_) => result += e; Left(None)
          case AtomicODE(_, _) => result += e; Left(None)
          case ODESystem(_, _) => result += e; Left(None)
          case _ => Left(None)
        }
      }, fml)
      result.toList
    }
  }

  /**
   * Creates a new axiom tactic for diamond equational assignment, based on duality.
   * @param newV The new variable to be used in the universal quantifier.
   * @param checkNewV Indicates whether or not the tactic should check that newV is indeed a new name.
   * @return The newly created tactic.
   * @author Stefan Mitsch
   */
  def diamondAssignWithoutAlphaT(newV: Variable, checkNewV: Boolean = true)(pos: Position): Tactic = {
    val implyPos = pos.first.first
    (new ByDualityAxiomTactic(boxAssignWithoutAlphaT(newV, checkNewV)))(pos) &
      // duality tactic finishes with unpolished result, because box assign has "special" result \\forall x. x=0 -> p
      ImplyToAndT(implyPos) & (rewriteDoubleNegationEliminationT(implyPos.first.second) | NilT) & existsDualT(pos)
  }

  /**
   * Creates a new axiom tactic for box assignment [x := t;]. Helper for boxAssignEqualT
   * @return The axiom tactic.
   * @author Stefan Mitsch
   */
  private def boxAssignWithoutAlphaT(newV: Variable, checkNewV: Boolean = true): PositionTactic = {
    def axiomInstance(fml: Formula): Formula = fml match {
      case Box(Assign(v: Variable, t), p) if !checkNewV || !allNames(fml).contains(newV) =>
        val g = Forall(Seq(newV), Imply(Equal(newV, t), SubstitutionHelper.replaceFree(p)(v, newV)))
        Equiv(fml, g)
      case _ => False
    }
    uncoverAxiomT("[:=] assign equational", axiomInstance, f => boxAssignWithoutAlphaBaseT(newV))
  }
  /** Base tactic for box assign without alpha */
  private def boxAssignWithoutAlphaBaseT(newV: Variable): PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(Box(Assign(v: Variable, t), p), _) =>
        val aT = FuncOf(Function("t", None, Unit, Real), Nothing)
        val aP = Function("p", None, Real, Bool)
        SubstitutionPair(aT, t) :: SubstitutionPair(PredOf(aP, DotTerm), SubstitutionHelper.replaceFree(p)(v, DotTerm)) :: Nil
    }

    val aV = Variable("v", None, Real)
    def alpha(fml: Formula): PositionTactic = fml match {
      case Equiv(Box(Assign(v: Variable, t), p), _) =>
        val left = v.name != aV.name || v.index != aV.index
        new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(Box(Assign(_, _), _), Forall(_, _)) => true
            case _ => false
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              if(left)
                Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index)
                  & alphaRenamingT(newV.name, newV.index, aV.name, aV.index)(p.second))
              else
                Some(alphaRenamingT(newV.name, newV.index, aV.name, aV.index)(p.second))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }
    }

    def axiomInstance(fml: Formula, axiom: Formula): Formula = fml match {
      case Equiv(Box(Assign(v: Variable, t), p), _) =>
        val Equiv(left, right) = axiom
        if (v.name == aV.name && v.index == aV.index) Equiv(left, replaceFree(right)(aV, newV))
        else Equiv(replaceFree(left)(aV, v, None), replaceFree(right)(aV, newV))
    }

    axiomLookupBaseT("[:=] assign equational", subst, alpha, axiomInstance)
  }

  /**
   * Creates a new axiom tactic for reversing box assignment [v := t;], i.e., introduces a ghost v for term t
   * @return The axiom tactic.
   * @author Stefan Mitsch
   */
  def discreteGhostT(ghost: Option[Variable], t: Term): PositionTactic = {
    // check specified name, or construct a new name for the ghost variable if None
    def ghostV(f: Formula): Variable = ghost match {
      case Some(gv) => require(gv == t || (!allNames(f).contains(gv))); gv
      case None => t match {
        case v: Variable => TacticHelper.freshNamedSymbol(v, f)
        case _ => throw new IllegalArgumentException("Only variables allowed when ghost name should be auto-provided")
      }
    }

    def g(f: Formula) = Equiv(Box(Assign(ghostV(f), t), SubstitutionHelper.replaceFree(f)(t, ghostV(f))), f)

    uncoverAxiomT("[:=] assign", g, f => discreteGhostBaseT(ghostV(f), t))
  }
  /** Base tactic for discrete ghost. */
  private def discreteGhostBaseT(v: Variable, t: Term): PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(g, f) =>
        val aT = FuncOf(Function("t", None, Unit, Real), Nothing)
        val aP = Function("p", None, Real, Bool)
        SubstitutionPair(aT, t) :: SubstitutionPair(PredOf(aP, DotTerm), SubstitutionHelper.replaceFree(f)(t, DotTerm)) :: Nil
    }

    def alpha(fml: Formula): PositionTactic = {
      val aV = Variable("v", None, Real)
      if (v.name != aV.name || v.index != aV.index) {
        new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(Box(Assign(_, _), _), _) => true
            case _ => false
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }
      } else NilPT
    }

    def axiomInstance(fml: Formula, axiom: Formula): Formula = {
      val Equiv(left, right) = axiom
      val aV = Variable("v", None, Real)
      Equiv(replace(left)(aV, v), right)
    }

    axiomLookupBaseT("[:=] assign", subst, alpha, axiomInstance)
  }

  /**
   * Creates a new axiom tactic for reversing box assignment [v := t;], i.e., introduces a ghost v for term t
   * @return The axiom tactic.
   * @author Stefan Mitsch
   */
  def nonAbbrvDiscreteGhostT(ghost: Option[Variable], t: Term): PositionTactic = {
    def ghostV(f: Formula): Variable = ghost match {
      case Some(gv) => require(gv == t || (!StaticSemantics.symbols(f).contains(gv))); gv
      case None => t match {
        case v: Variable => TacticHelper.freshNamedSymbol(v, f)
        case _ => throw new IllegalArgumentException("Only variables allowed when ghost name should be auto-provided")
      }
    }

    def g(f: Formula) = Equiv(Box(Assign(ghostV(f), t), f), f)

    uncoverAxiomT("[:=] vacuous assign", g, f => nonAbbrvDiscreteGhostBaseT(ghostV(f), t))
  }
  /** Base tactic for nonAbbrvDiscreteGhost */
  private def nonAbbrvDiscreteGhostBaseT(v: Variable, t: Term): PositionTactic = {
    def subst(fml: Formula) = fml match {
      case Equiv(g, f) =>
        val aT = FuncOf(Function("t", None, Unit, Real), Nothing)
        val aP = PredOf(Function("p", None, Unit, Bool), Nothing)
        SubstitutionPair(aT, t) :: SubstitutionPair(aP, f) :: Nil
    }

    val aV = Variable("v", None, Real)
    def alpha(fml: Formula): PositionTactic = {
      if (v.name != aV.name || v.index != aV.index) {
        new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(Box(Assign(_, _), _), _) => true
            case _ => false
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }
      } else NilPT
    }

    def axiomInstance(fml: Formula, axiom: Formula): Formula = {
      val Equiv(left, right) = axiom
      Equiv(replace(left)(aV, v), right)
    }

    axiomLookupBaseT("[:=] vacuous assign", subst, alpha, axiomInstance)
  }

  /**
   * Creates a new position tactic for box assignment [x := t;], for the case when followed by ODE or loop.
   * Alpha renaming in ODEs and loops introduces initial value assignments. This tactic is designed to handle those.
   * @return The tactic.
   * @author Stefan Mitsch
   */
  def v2vAssignT: PositionTactic = new PositionTactic("[:=]/<:=> assign") {
    override def applies(s: Sequent, p: Position): Boolean = getFormula(s, p) match {
      case Box(Assign(_: Variable, v: Variable), pred) => true
      case Diamond(Assign(_: Variable, v: Variable), pred) => true
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        import scala.language.postfixOps
        import SearchTacticsImpl.onBranch
        import BranchLabels.cutShowLbl
        import PropositionalTacticsImpl.EquivRightT

        val succLength = node.sequent.succ.length
        val anteLength = node.sequent.ante.length

        def createTactic(m: Formula, pred: Formula, v: Variable, t: Variable) = Some(
          cutInContext(Equiv(m, replace(pred)(v, t)), p) &
            onBranch(
              (cutShowLbl,
                // TODO does not work in mixed settings such as <x:=t>[x'=2] and [x:=t]<x'=2>
                PropositionalTacticsImpl.cohideT(SuccPosition(succLength)) & assertT(0, 1) &
                alphaRenamingT(t.name, t.index, v.name, v.index)(SuccPosition(0, PosInExpr(1 :: p.inExpr.pos))) &
                  EquivRightT(SuccPosition(0)) & (AxiomCloseT | debugT("v2vAssign: Axiom close failed unexpectedly") & stopT)),
              (cutUseLbl, equivRewriting(AntePosition(anteLength), p.topLevel))
            )
        )

        getFormula(node.sequent, p) match {
          case b@Box(Assign(v: Variable, t: Variable), pred) => createTactic(b, pred, v, t)
          case d@Diamond(Assign(v: Variable, t: Variable), pred) => createTactic(d, pred, v, t)
        }
      }
    }
  }

  /**
   * Creates a new tactic for box assignment [x := t;] when x == t.
   * @return The tactic.
   * @author Stefan Mitsch
   */
  def selfAssignmentT: PositionTactic = new PositionTactic("[:=] self-assign") {
    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case Box(Assign(v: Variable, t: Variable), _) => v == t
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
          case b@Box(Assign(v: Variable, t: Variable), _) if v == t => Some(
            abstractionT(p) & skolemizeT(p))
          case _ => throw new IllegalArgumentException("Checked by applicable to not happen")
      }
    }
  }

  /**
   * Creates a new axiom tactic for box assignment [x := t;]
   * @return The axiom tactic.
   * @author Stefan Mitsch
   */
  def substitutionBoxAssignT = substitutionAssignT("[:=] assign", Box.unapply)

  /**
   * Creates a new axiom tactic for box assignment [x := t;]
   * @return The axiom tactic.
   * @author Stefan Mitsch
   */
  def substitutionDiamondAssignT = substitutionAssignT("<:=> assign", Diamond.unapply)

  /**
   * Creates a new axiom tactic for box/diamond assignment [x := t;], < x := t;>.
   * @return The axiom tactic.
   * @author Stefan Mitsch
   */
  private def substitutionAssignT[T: Manifest](name: String, mod: T => Option[(Program, Formula)]): PositionTactic = {
    val BDModality = new ModalityUnapplyer(mod)

    def axiomInstance(fml: Formula) = fml match {
      case BDModality(Assign(v: Variable, t: Term), pred) =>
        val g = SubstitutionHelper.replaceFree(pred)(v, t)
        val instance = Equiv(fml, g)
        pred match {
          // loop and ODE are probably a little too strict here, but we have v2vBoxAssignT to handle those
          case Box(_: DifferentialProgram, _) => t match {
            case tv: Variable if v == tv => instance
            case _ => False
          }
          case Box(_: Loop, _) => t match {
            case tv: Variable if v == tv => instance
            case _ => False
          }
          case Diamond(_: DifferentialProgram, _) => t match {
            case tv: Variable if v == tv => instance
            case _ => False
          }
          case Diamond(_: Loop, _) => t match {
            case tv: Variable if v == tv => instance
            case _ => False
          }
          case _ => instance
        }
      case _ => False
    }

    uncoverAxiomT(name, axiomInstance, f => substitutionAssignBaseT(name, mod))
  }
  /** Base tactic for substitution assignment */
  private def substitutionAssignBaseT[T: Manifest](name: String, mod: T => Option[(Program, Formula)]): PositionTactic = {
    val BDModality = new ModalityUnapplyer(mod)

    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(BDModality(Assign(v: Variable, t: Term), p), _) =>
        val aT = FuncOf(Function("t", None, Unit, Real), Nothing)
        val aP = Function("p", None, Real, Bool)
        SubstitutionPair(aT, t) :: SubstitutionPair(PredOf(aP, DotTerm), SubstitutionHelper.replaceFree(p)(v, DotTerm)) :: Nil
    }

    val aV = Variable("v", None, Real)
    def alpha(fml: Formula): PositionTactic = fml match {
      case Equiv(BDModality(Assign(v: Variable, _: Term), _), _) =>
        if (v.name != aV.name || v.index != aV.index) {
          new PositionTactic("Alpha") {
            override def applies(s: Sequent, p: Position): Boolean = s(p) match {
              case Equiv(BDModality(Assign(_, _), _), _) => true
              case _ => false
            }

            override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
              override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
                Some(alphaRenamingT(v.name, v.index, aV.name, aV.index)(p) ~
                  globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

              override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
            }
          }
        } else NilPT
    }

    def axiomInstance(fml: Formula, axiom: Formula): Formula = fml match {
      case Equiv(BDModality(Assign(v: Variable, _: Term), _), _) =>
        val Equiv(lhs, rhs) = axiom
        Equiv(if (v.name != aV.name || v.index != None) replace(lhs)(aV, v) else lhs, rhs)
    }

    axiomLookupBaseT(name, subst, alpha, axiomInstance)
  }

  /**
   * Creates a new axiom tactic for V vacuous.
   * @return The new tactic.
   */
  def boxVacuousT: PositionTactic = {
    def axiomInstance(fml: Formula): Formula = fml match {
      case Box(prg, phi) => Imply(phi, fml)
      case _ => False
    }
    uncoverAxiomT("V vacuous", axiomInstance, _ => boxVacuousBaseT)
  }
  /** Base tactic for box vacuous */
  private def boxVacuousBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Imply(_, Box(prg, phi)) =>
        val aA = ProgramConst("a")
        val aP = PredOf(Function("p", None, Unit, Bool), Nothing)
        SubstitutionPair(aA, prg) :: SubstitutionPair(aP, phi) :: Nil
    }
    axiomLookupBaseT("V vacuous", subst, _ => NilPT, (f, ax) => ax)
  }

  /**
   * Creates a new axiom tactic for test [?H].
   * @return The new tactic.
   */
  def boxTestT: PositionTactic = {
    def axiomInstance(fml: Formula): Formula = fml match {
      case Box(Test(h), p) => Equiv(fml, Imply(h, p))
      case _ => False
    }
    uncoverAxiomT("[?] test", axiomInstance, _ => boxTestBaseT)
  }
  /** Base tactic for boxTestT */
  private def boxTestBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(Box(Test(h), p), _) =>
        // construct substitution
        val aH = PredOf(Function("H", None, Unit, Bool), Nothing)
        val aP = PredOf(Function("p", None, Unit, Bool), Nothing)
        SubstitutionPair(aH, h) :: SubstitutionPair(aP, p) :: Nil
    }
    axiomLookupBaseT("[?] test", subst, _ => NilPT, (f, ax) => ax)
  }

  /**
   * Creates a new axiom tactic for diamond test.
   * @return The new tactic.
   * @author Stefan Mitsch
   */
  def diamondTestT: PositionTactic = {
    def axiomInstance(fml: Formula): Formula = fml match {
      case Diamond(Test(h), p) => Equiv(fml, And(h, p))
      case _ => False
    }
    uncoverAxiomT("<?> test", axiomInstance, _ => diamondTestBaseT)
  }
  /** Base tactic for diamondTestT */
  private def diamondTestBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(Diamond(Test(h), p), _) =>
        // construct substitution
        val aH = PredOf(Function("H", None, Unit, Bool), Nothing)
        val aP = PredOf(Function("p", None, Unit, Bool), Nothing)
        SubstitutionPair(aH, h) :: SubstitutionPair(aP, p) :: Nil
    }
    axiomLookupBaseT("<?> test", subst, _ => NilPT, (f, ax) => ax)
  }

  /**
   * Creates a new axiom tactic for non-deterministic assignment [x := *].
   * @return The new tactic.
   * @author Stefan Mitsch
   */
  def boxNDetAssign: PositionTactic = new PositionTactic("[:=] assign equational") {
    override def applies(s: Sequent, p: Position): Boolean = !p.isAnte && p.inExpr == HereP && (s(p) match {
      case Box(AssignAny(v: Variable), _) => true
      case _ => false
    })

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        val f = node.sequent(p)
        // construct a new name for renaming in ODE
        val newV = f match {
          case Box(AssignAny(v: Variable), _) => TacticHelper.freshNamedSymbol(v, f)
          case _ => throw new IllegalStateException("Checked by applies to never happen")
        }

        node.sequent(p) match {
          case Box(AssignAny(v: Variable), phi@Box(_, _))
            if StaticSemantics(phi).bv.contains(v) => Some(
              alphaRenamingT(v, newV)(p.second) & boxNDetAssignWithoutAlphaT(p) & v2vAssignT(p.first)
            )
          case _ => Some(boxNDetAssignWithoutAlphaT(p))
        }
      }
    }
  }

  /**
   * Creates a new axiom tactic for non-deterministic assignment [x := *]. Helper for boxNDetAssign.
   * @return The new tactic.
   * @author Stefan Mitsch
   */
  private def boxNDetAssignWithoutAlphaT: PositionTactic = {
    def axiomInstance(fml: Formula): Formula = fml match {
      // construct axiom instance: [v:=*]p(v) <-> \forall v. p(v).
      case Box(AssignAny(v: Variable), p) => Equiv(fml, Forall(Seq(v), p))
      case _ => False
    }
    uncoverAxiomT("[:*] assign nondet", axiomInstance, _ => boxNDetAssignWithoutAlphaBaseT)
  }
  /** Base tactic for box nondeterministic assignment without alpha renaming */
  private def boxNDetAssignWithoutAlphaBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(Box(AssignAny(v: Variable), p), _) =>
        val aP = Function("p", None, Real, Bool)
        SubstitutionPair(PredOf(aP, DotTerm), SubstitutionHelper.replaceFree(p)(v, DotTerm)) :: Nil
    }

    val aV = Variable("x", None, Real)
    def alpha(fml: Formula): PositionTactic = fml match {
      case Equiv(Box(AssignAny(v: Variable), p), _) =>
        if (v.name != aV.name || v.index != aV.index) {
          new PositionTactic("Alpha") {
            override def applies(s: Sequent, p: Position): Boolean = s(p) match {
              case Equiv(Box(AssignAny(_), _), Forall(_, _)) => true
              case _ => false
            }

            override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
              override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
                Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

              override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
            }
          }
        } else NilPT
    }

    def axiomInstance(fml: Formula, axiom: Formula): Formula = fml match {
      case Equiv(Box(AssignAny(v: Variable), p), _) =>
        if (v.name != aV.name || v.index != None) replaceFree(axiom)(aV, v, None)
        else axiom
    }

    axiomLookupBaseT("[:*] assign nondet", subst, alpha, axiomInstance)
  }

  /**
   * Creates a new axiom tactic for non-deterministic assignment < x := *>.
   * @return The new tactic.
   * @author Stefan Mitsch
   */
  def diamondNDetAssign: PositionTactic = new PositionTactic("<:=> assign nondet") {
    override def applies(s: Sequent, p: Position): Boolean = getFormula(s, p) match {
      case Diamond(AssignAny(v: Variable), _) => true
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        val f = getFormula(node.sequent, p)
        // construct a new name for renaming in ODE
        val newV = f match {
          case Diamond(AssignAny(v: Variable), _) => TacticHelper.freshNamedSymbol(v, f)
          case _ => throw new IllegalStateException("Checked by applies to never happen")
        }

        f match {
          case Diamond(AssignAny(v: Variable), Diamond(prg, _))
            if StaticSemantics(prg).bv.contains(v) => Some(
            alphaRenamingT(v.name, v.index, newV.name, newV.index)(p.second) &
              diamondNDetAssignWithoutAlphaT(p) & v2vAssignT(p.first)
          )
          case _ => Some(diamondNDetAssignWithoutAlphaT(p))
        }
      }
    }
  }

  /**
   * Creates a new axiom tactic for non-deterministic assignment < x := *>. Helper for diamondNDetAssign.
   * @return The new tactic.
   * @author Stefan Mitsch
   */
  private def diamondNDetAssignWithoutAlphaT: PositionTactic = {
    def axiomInstance(fml: Formula): Formula = fml match {
      // construct axiom instance: <v:=*>p(v) <-> \exists v. p(v).
      case Diamond(AssignAny(v: Variable), p) => Equiv(fml, Exists(Seq(v), p))
      case _ => False
    }
    uncoverAxiomT("<:*> assign nondet", axiomInstance, _ => diamondNDetAssignWithoutAlphaBaseT)
  }
  /** Base tactic for diamond nondeterministic assignment without alpha renaming */
  private def diamondNDetAssignWithoutAlphaBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(Diamond(AssignAny(v: Variable), p), _) =>
        val aP = Function("p", None, Real, Bool)
        SubstitutionPair(PredOf(aP, DotTerm), SubstitutionHelper.replaceFree(p)(v, DotTerm)) :: Nil
    }

    val aV = Variable("x", None, Real)
    def alpha(fml: Formula): PositionTactic = fml match {
      case Equiv(Diamond(AssignAny(v: Variable), p), _) =>
        if (v.name != aV.name || v.index != aV.index) {
          new PositionTactic("Alpha") {
            override def applies(s: Sequent, p: Position): Boolean = s(p) match {
              case Equiv(Diamond(AssignAny(_), _), Exists(_, _)) => true
              case _ => false
            }

            override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
              override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
                Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

              override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
            }
          }
        } else NilPT
    }

    def axiomInstance(fml: Formula, axiom: Formula): Formula = fml match {
      case Equiv(Diamond(AssignAny(v: Variable), p), _) =>
        if (v.name != aV.name || v.index != None) replaceFree(axiom)(aV, v, None)
        else axiom
    }

    axiomLookupBaseT("<:*> assign nondet", subst, alpha, axiomInstance)
  }

  /**
   * Creates a new axiom tactic for sequential composition [;]
   * @return The new tactic.
   * @author Stefan Mitsch
   */
  def boxSeqT = seqT("[;] compose", Box.unapply, Box.apply)

  /**
   * Creates a new axiom tactic for diamond sequential composition <;>
   * @return The new tactic.
   * @author Stefan Mitsch
   */
  def diamondSeqT = seqT("<;> compose", Diamond.unapply, Diamond.apply)

  /**
   * Creates a new axiom tactic for box/diamond sequential composition
   * @param name The name of the axiom.
   * @param mod The unapply method of the concrete modality.
   * @param factory The apply method of the concrete modality.
   * @return The new tactic.
   * @author Stefan Mitsch
   */
  private def seqT[T: Manifest](name: String, mod: T => Option[(Program, Formula)],
                                factory: (Program, Formula) => Formula): PositionTactic = {
    val BDModality = new ModalityUnapplyer(mod)

    def axiomInstance(fml: Formula): Formula = fml match {
      case BDModality(Compose(a, b), p) => Equiv(fml, factory(a, factory(b, p)))
      case _ => False
    }
    uncoverAxiomT(name, axiomInstance, _ => seqBaseT(name, mod))
  }
  /** Base tactic for seqT */
  private def seqBaseT[T: Manifest](name: String, mod: T => Option[(Program, Formula)]): PositionTactic = {
    val BDModality = new ModalityUnapplyer(mod)

    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(BDModality(Compose(a, b), p), _) =>
        val aA = ProgramConst("a")
        val aB = ProgramConst("b")
        val aP = PredOf(Function("p", None, Real, Bool), Anything)
        SubstitutionPair(aA, a) :: SubstitutionPair(aB, b) :: SubstitutionPair(aP, p) :: Nil
    }
    axiomLookupBaseT(name, subst, _ => NilPT, (f, ax) => ax)
  }

  /**
   * Creates a new axiom tactic for box induction [*] I induction
   * @return The new tactic.
   */
  def boxInductionT: PositionTactic = {
    def axiomInstance(fml: Formula): Formula = fml match {
      // construct axiom instance: (p & [a*](p -> [a] p)) -> [a*]p
      case Box(Loop(a), p) => Imply(And(p, Box(Loop(a), Imply(p, Box(a, p)))), fml)
      case _ => False
    }
    uncoverAxiomT("I induction", axiomInstance, _ => boxInductionBaseT)
  }
  /** Base tactic for box induction */
  private def boxInductionBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Imply(And(p, Box(Loop(a), Imply(_, Box(_, _)))), _) =>
        val aA = ProgramConst("a")
        val aP = PredOf(Function("p", None, Real, Bool), Anything)
        SubstitutionPair(aA, a) :: SubstitutionPair(aP, p) :: Nil
    }

    axiomLookupBaseT("I induction", subst, _ => NilPT, (f, ax) => ax)
  }

  /**
   * Creates a new axiom tactic for box choice [++].
   * @return The new tactic.
   */
  def boxChoiceT: PositionTactic = {
    def axiomInstance(fml: Formula): Formula = fml match {
      // construct axiom instance: [ a ++ b ]p <-> [a]p & [b]p.
      case Box(Choice(a, b), p) => Equiv(fml, And(Box(a, p), Box(b, p)))
      case _ => False
    }
    uncoverAxiomT("[++] choice", axiomInstance, _ => boxChoiceBaseT)
  }
  /** Base tactic for box choice */
  private def boxChoiceBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(Box(Choice(a, b), p), _) =>
        val aA = ProgramConst("a")
        val aB = ProgramConst("b")
        val aP = PredOf(Function("p", None, Real, Bool), Anything)
        SubstitutionPair(aA, a) :: SubstitutionPair(aB, b) :: SubstitutionPair(aP, p) :: Nil
    }
    axiomLookupBaseT("[++] choice", subst, _ => NilPT, (f, ax) => ax)
  }

  /**
   * Creates a new axiom tactic for diamond choice <++>.
   * @return The new tactic.
   * @author Stefan Mitsch
   */
  def diamondChoiceT: PositionTactic = {
    def axiomInstance(fml: Formula): Formula = fml match {
      // construct axiom instance: < a ++ b >p <-> <a>p | <b>p.
      case Diamond(Choice(a, b), p) => Equiv(fml, Or(Diamond(a, p), Diamond(b, p)))
      case _ => False
    }
    uncoverAxiomT("<++> choice", axiomInstance, _ => diamondChoiceBaseT)
  }
  /** Base tactic for diamond choice */
  private def diamondChoiceBaseT: PositionTactic = {
    def subst(fml: Formula): List[SubstitutionPair] = fml match {
      case Equiv(Diamond(Choice(a, b), p), _) =>
        val aA = ProgramConst("a")
        val aB = ProgramConst("b")
        val aP = PredOf(Function("p", None, Real, Bool), Anything)
        SubstitutionPair(aA, a) :: SubstitutionPair(aB, b) :: SubstitutionPair(aP, p) :: Nil
    }
    axiomLookupBaseT("<++> choice", subst, _ => NilPT, (f, ax) => ax)
  }

  /**
   * Creates a new position tactic to apply the induction rule.
   * @param inv The invariant.
   * @return The position tactic.
   */
  def inductionT(inv: Option[Formula]): PositionTactic = new PositionTactic("induction") {
    def getBody(g: Formula): Option[Program] = g match {
      case Box(Loop(a), _) => Some(a)
      case _ => None
    }
    override def applies(s: Sequent, p: Position): Boolean = !p.isAnte && p.inExpr == HereP && getBody(s(p)).isDefined

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      def ind(cutSPos: Position, cont: Tactic) = boxInductionT(cutSPos) & AndRightT(cutSPos) &
        (LabelBranch("Close Next"), abstractionT(cutSPos) & cont)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = inv match {
        case Some(f) =>
          val cutAPos = AntePosition(node.sequent.ante.length, HereP)
          val prepareKMP = new ConstructionTactic("Prepare K modus ponens") {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
              case x@Box(a, _) =>
                val cPos = AntePosition(node.sequent.ante.length)
                val b1 = ImplyLeftT(cPos) & AxiomCloseT
                val b2 = hideT(p)
                Some(cutT(Some(Imply(Box(a, f), x))) & onBranch((cutUseLbl, b1), (cutShowLbl, b2)))
              case _ => None
            }
            override def applicable(node: ProofNode): Boolean = true
          }
          val cutSPos = SuccPosition(node.sequent.succ.length - 1, HereP)
          val useCase = prepareKMP & hideT(cutAPos) & kModalModusPonensT(cutSPos) & abstractionT(cutSPos) &
            LabelBranch(indUseCaseLbl)
          val branch1Tactic = ImplyLeftT(cutAPos) & (hideT(p) & LabelBranch(indInitLbl), useCase)
          val branch2Tactic = hideT(p) &
            ImplyRightT(cutSPos) &
            ind(cutSPos, hideT(cutAPos) & LabelBranch(indStepLbl)) &
            onBranch(("Close Next", AxiomCloseT))
          getBody(node.sequent(p)) match {
            case Some(a) =>
              Some(cutT(Some(Imply(f, Box(Loop(a), f)))) &
                onBranch((cutUseLbl, branch1Tactic), (cutShowLbl, branch2Tactic)))
            case None => None
          }
        case None => Some(ind(p, NilT) & LabelBranch(indStepLbl))
      }
    }
  }

  /**
   * Creates a new position tactic to apply the induction rule. Wipes the context instead of abstraction.
   * @param inv The invariant.
   * @return The new position tactic.
   * @author Stefan Mitsch
   */
  def wipeContextInductionT(inv: Option[Formula]): PositionTactic = new PositionTactic("induction") {
    def getBody(g: Formula): Option[Program] = g match {
      case Box(Loop(a), _) => Some(a)
      case _ => None
    }
    override def applies(s: Sequent, p: Position): Boolean = !p.isAnte && p.inExpr == HereP && getBody(s(p)).isDefined

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      def wipeContext(bvFromPos: Position, except: Position*) = new ConstructionTactic("Wipe Context") {
        require(!bvFromPos.isAnte)
        override def applicable(node: ProofNode) = true
        override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(bvFromPos) match {
          case Forall(vars, _) =>
            val anteExcepts = except.filter(_.isInstanceOf[AntePosition]).map(_.index).toSet
            val anteHidePos = node.sequent.ante.zipWithIndex.collect {
              case (f,i) if allNames(f).intersect(vars.toSet).nonEmpty => i }.toSet -- anteExcepts
            val anteHides = anteHidePos.toList.sorted.reverseMap(i => hideT(AntePosition(i)))
            val succExcepts = except.filter(_.isInstanceOf[SuccPosition]).map(_.index).toSet
            val succHidePos = node.sequent.succ.zipWithIndex.collect {
              case (f,i) if allNames(f).intersect(vars.toSet).nonEmpty => i }.toSet -- succExcepts
            val succHides = succHidePos.toList.sorted.reverseMap(i => hideT(SuccPosition(i)))
            val bvFromPosCorr = succHidePos.count(_ < bvFromPos.index)
            val correctedPos = SuccPosition(bvFromPos.index - bvFromPosCorr)
            Some((anteHides ++ succHides).foldLeft(NilT)((t, i) => t & i) &
              (skolemizeT(correctedPos)*) &
              assertT(s => s(correctedPos) match { case Forall(_, _) => false case _ => true },
                "Wipe context induction tactic did not skolemize exhaustively"))
        }
      }

      def ind(cutSPos: Position, cont: Tactic) = boxInductionT(cutSPos) & AndRightT(cutSPos) &
        (LabelBranch("Close Next"), abstractionT(cutSPos) & wipeContext(cutSPos, cutSPos) & cont)
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = inv match {
        case Some(f) =>
          val cutAPos = AntePosition(node.sequent.ante.length)
          val cutSPos = SuccPosition(node.sequent.succ.length - 1)

          val prepareKMP = new ConstructionTactic("Prepare K modus ponens") {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
              case x@Box(a, _) =>
                val cPos = AntePosition(node.sequent.ante.length)
                val b1 = ImplyLeftT(cPos) & AxiomCloseT
                val b2 = hideT(p)
                Some(cutT(Some(Imply(Box(a, f), x))) & onBranch((cutUseLbl, b1), (cutShowLbl, b2)))
              case _ => None
            }
            override def applicable(node: ProofNode): Boolean = true
          }

          val useCase = prepareKMP & hideT(cutAPos) & kModalModusPonensT(cutSPos) & abstractionT(cutSPos) &
            wipeContext(cutSPos, cutSPos) & LabelBranch(indUseCaseLbl)
          val branch1Tactic = ImplyLeftT(cutAPos) & (hideT(p) & LabelBranch(indInitLbl), useCase)
          val branch2Tactic = hideT(p) &
            ImplyRightT(cutSPos) &
            ind(cutSPos, LabelBranch(indStepLbl)) &
            onBranch(("Close Next", AxiomCloseT))
          getBody(node.sequent(p)) match {
            case Some(a) =>
              Some(cutT(Some(Imply(f, Box(Loop(a), f)))) & onBranch((cutUseLbl, branch1Tactic), (cutShowLbl, branch2Tactic)))
            case None => None
          }
        case None => Some(ind(p, NilT) & LabelBranch(indStepLbl))
      }
    }
  }

  def boxSplitConjunctionT: PositionTactic = new PositionTactic("[]split conjunction") {
    override def applies(s: Sequent, pos: Position): Boolean = getFormula(s, pos) match {
      case Box(_, And(p, q)) => true
      case _ => false
    }

    override def apply(pos: Position): Tactic = new ConstructionTactic("[]split conjunction") {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = getFormula(node.sequent, pos) match {
        case b@Box(a, And(p, q)) =>
          // implements Cresswell, Hughes. A New Introduction to Modal Logic, K2 p. 27

          // (9) ([a](q->p&q) -> ([a]q -> [a](p&q)))  ->  (([a]p & [a]q) -> [a](p&q))
          val f9 = Imply(Imply(Box(a, Imply(q, And(p, q))), Imply(Box(a, q), Box(a, And(p, q)))), Imply(And(Box(a, p), Box(a, q)), Box(a, And(p, q))))

          // (8) ([a]p -> [a](q -> p&q))  ->  (9)
          val f8 = Imply(Imply(Box(a, p), Box(a, Imply(q, And(p, q)))), f9)

          // (6) [a](q -> (p&q))  ->  ([a]q -> [a](p&q))
          val f6 = Imply(Box(a, Imply(q, And(p, q))), Imply(Box(a, q), Box(a, And(p, q))))

          // (5) [a]p -> [a](q -> p&q)
          val f5 = Imply(Box(a, p), Box(a, Imply(q, And(p, q))))

          // (4) [a](p -> (q -> p&q))  ->  ([a]p -> [a](q -> p&q))
          val f4 = Imply(Box(a, Imply(p, Imply(q, And(p, q)))), Imply(Box(a, p), Box(a, Imply(q, And(p, q)))))

          // (2) [a](p -> (q -> p&q))
          val f2 = Box(a, Imply(p, Imply(q, And(p, q))))

          // uniform substitution to get (6) from K
          val subst =
            SubstitutionPair(ProgramConst("a"), a) ::
            SubstitutionPair(PredOf(Function("p", None, Real, Bool), Anything), q) ::
            SubstitutionPair(PredOf(Function("q", None, Real, Bool), Anything), And(p, q)) :: Nil

          val k = Axiom.axioms.get("K modal modus ponens").get

          Some(cutT(Some(Equiv(b, And(Box(a, p), Box(a, q))))) & onBranch(
            (cutShowLbl, lastSucc(EquivRightT) & onBranch(
              (equivLeftLbl, AxiomCloseT),
              (equivRightLbl,
                debugT("Show [a]p & [a]q -> [a](p&q)") &
                cohide2T(AntePosition(node.sequent.ante.length), SuccPosition(node.sequent.succ.length)) &
                cutT(Some(f9)) & onBranch(
                  (cutShowLbl,
                    debugT("Show f9") &
                    cutT(Some(f8)) & onBranch(
                      (cutShowLbl,
                        debugT("Show f8") &
                        lastSucc(cohideT) &
                        /* PC8 (just propositional stuff) */
                        lastSucc(ImplyRightT)*3 & lastAnte(AndLeftT) &
                        modusPonensT(AntePosition(2), AntePosition(0)) &
                        modusPonensT(AntePosition(2), AntePosition(0)) &
                        modusPonensT(AntePosition(0), AntePosition(1)) & AxiomCloseT),
                      (cutUseLbl, cutT(Some(f5)) & onBranch(
                        (cutShowLbl,
                          debugT("Show f5, get f2 by KMP") &
                          lastSucc(cohideT) & lastSucc(kModalModusPonensT) &
                          debugT("Show f2") & lastSucc(abstractionT) & (lastSucc(skolemizeT)*) &
                          // PC4 (just propositional stuff)
                          lastSucc(ImplyRightT)*2 & lastSucc(AndRightT) & AxiomCloseT
                          ),
                        (cutUseLbl,
                          debugAtT("Use f5, modus ponens with assumption")(AntePosition(2)) &
                            modusPonensT(AntePosition(2), AntePosition(1)) &
                            AxiomCloseT)
                      ))
                    )),
                  (cutUseLbl,
                    debugT("Use f9") &
                    cutT(Some(f6)) & onBranch(
                      (cutShowLbl,
                        debugT("Show f6") &
                        lastSucc(cohideT) &
                        // uniform substitution from K
                        uniformSubstT(subst, Map(f6 -> k)) &
                        AxiomTactic.axiomT("K modal modus ponens")
                        ),
                      (cutUseLbl,
                        debugAtT("Use f6, modus ponens with assumption")(AntePosition(2)) &
                        modusPonensT(AntePosition(2), AntePosition(1)) &
                        lastAnte(ImplyLeftT) & AxiomCloseT)
                  ))
                ))
            )),
            (cutUseLbl, EqualityRewritingImpl.equivRewriting(AntePosition(node.sequent.ante.length), pos) /* desired result remains open */)
          ))
        case _ => throw new IllegalStateException("Checked by applies to never happen")
      }
    }
  }

  /**
   * Induction tactic that generates an invariant using the specified generator.
   * @param gen The invariant generator.
   * @return The induction tactic.
   */
  protected[tactics] def genInductionT(gen: Generator[Formula]): PositionTactic = new PositionTactic("Generate Invariant") {
    override def applies(s: Sequent, p: Position): Boolean = gen.peek(s, p) match {
      case Some(inv) => wipeContextInductionT(Some(inv)).applies(s, p)
      case None => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = gen(node.sequent, p) match {
        case Some(inv) => Some(wipeContextInductionT(Some(inv))(p))
        case None => None
      }

      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
    }
  }

}
