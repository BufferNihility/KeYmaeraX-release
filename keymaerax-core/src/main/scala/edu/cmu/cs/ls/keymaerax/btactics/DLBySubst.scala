package edu.cmu.cs.ls.keymaerax.btactics

import edu.cmu.cs.ls.keymaerax.bellerophon._
import edu.cmu.cs.ls.keymaerax.btactics.Idioms._
import edu.cmu.cs.ls.keymaerax.btactics.ProofRuleTactics.axiomatic
import edu.cmu.cs.ls.keymaerax.btactics.TactixLibrary._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.tactics.{AntePosition, Position}
import edu.cmu.cs.ls.keymaerax.tactics.Augmentors._
import edu.cmu.cs.ls.keymaerax.tactics.TacticLibrary.TacticHelper

import scala.collection.immutable.IndexedSeq
import scala.language.postfixOps

/**
  * This is an example of how to implement some of the dL tactics using substitution tactics.
  * Created by nfulton on 11/3/15.
  */
object DLBySubst {
  /**
    * Box monotonicity.
    * {{{
    *      p |- q
    *   -------------monb
    *   [a]p |- [a]q
    * }}}
    */
  def monb = {
    val pattern = SequentType(Sequent(Nil, IndexedSeq("[a_;]p_(??)".asFormula), IndexedSeq("[a_;]q_(??)".asFormula)))
    USubstPatternTactic(
      (pattern, (ru:RenUSubst) => ru.getRenamingTactic & axiomatic("[] monotone", ru.substitution.usubst))::Nil //@todo not sure about how to handle the renaming portion?
    )
  }

  /**
   * Diamond monotonicity.
   * {{{
   *      p |- q
   *   -------------mond
   *   <a>p |- <a>q
   * }}}
   */
  def mond = {
    val pattern = SequentType(Sequent(Nil, IndexedSeq("<a_;>p_(??)".asFormula), IndexedSeq("<a_;>q_(??)".asFormula)))
    USubstPatternTactic(
      (pattern, (ru:RenUSubst) => ru.getRenamingTactic & axiomatic("<> monotone", ru.substitution.usubst))::Nil //@todo not sure about how to handle the renaming portion?
    )
  }

  /** G: Gödel generalization rule reduces a proof of `|- [a;]p(x)` to proving the postcondition `|- p(x)` in isolation.
    * {{{
    *       p(??)
    *   ----------- G
    *    [a;]p(??)
    * }}}
    * @see [[monb]] with p(x)=True
    */
  lazy val G = {
    val pattern = SequentType(Sequent(Nil, IndexedSeq(), IndexedSeq("[a_;]p_(??)".asFormula)))
    USubstPatternTactic(
      (pattern, (ru:RenUSubst) => ru.getRenamingTactic & axiomatic("Goedel", ru.substitution.usubst))::Nil
    )
  }

  /**
   * Returns a tactic to abstract a box modality to a formula that quantifies over the bound variables in the program
   * of that modality.
   * @example{{{
   *           |- \forall x x>0
   *         ------------------abstractionb(1)
   *           |- [x:=2;]x>0
   * }}}
   * @example{{{
   *           |- x>0 & z=1 -> [z:=y;]\forall x x>0
   *         --------------------------------------abstractionb(1, 1::1::Nil)
   *           |- x>0 & z=1 -> [z:=y;][x:=2;]x>0
   * }}}
   * @example{{{
   *           |- x>0
   *         ---------------abstractionb(1)
   *           |- [y:=2;]x>0
   * }}}
   * @return the abstraction tactic.
   */
  def abstractionb: DependentPositionTactic = new DependentPositionTactic("Abstraction") {
    def apply(pos: Position): DependentTactic = new SingleGoalDependentTactic(name) {
      override def computeExpr(sequent: Sequent): BelleExpr = {
        require(!pos.isAnte, "Abstraction only in succedent")
        sequent.at(pos) match {
          case (ctx, b@Box(prg, phi)) =>
            val vars = StaticSemantics.boundVars(prg).intersect(StaticSemantics.freeVars(phi)).toSet.to[Seq]
            val diffies = vars.filter(v => v.isInstanceOf[DifferentialSymbol])
            if (diffies.nonEmpty) throw new IllegalArgumentException("abstractionb: found differential symbols " + diffies + " in " + b + "\nFirst handle those")
            val qPhi =
              if (vars.isEmpty) phi
              else
              //@todo what about DifferentialSymbols in boundVars? Decided to filter out since not soundness-critical.
                vars.filter(v => v.isInstanceOf[Variable]).to[scala.collection.immutable.SortedSet].
                  foldRight(phi)((v, f) => Forall(v.asInstanceOf[Variable] :: Nil, f))

            cut(Imply(ctx(qPhi), ctx(b))) <(
              /* use */ (implyL('Llast) <(hideR(pos.topLevel) partial /* result remains open */ , closeId)) partial,
              /* show */ cohide('Rlast) & implyR('Rlast) & assertT(1, 1) & implyRi &
              CMon(pos.inExpr) & implyR('_) & //PropositionalTactics.propCMon(pos.inExpr) &
              assertT(1, 1) & assertT(s => s.ante.head == qPhi && s.succ.head == b, s"Formula $qPhi and/or $b are not in the expected positions in abstractionb") &
              topAbstraction('Rlast) & closeId
              )
        }
      }
    }
  }

  /** Top-level abstraction: basis for abstraction tactic */
  private def topAbstraction: DependentPositionTactic = new DependentPositionTactic("Top-level abstraction") {
    def apply(pos: Position): DependentTactic = new SingleGoalDependentTactic(name) {
      override def computeExpr(sequent: Sequent): BelleExpr = {
        require(!pos.isAnte, "Abstraction only in succedent")
        sequent.sub(pos) match {
          case Some(b@Box(prg, phi)) =>
            val vars = StaticSemantics.boundVars(prg).intersect(StaticSemantics.freeVars(phi)).toSet.to[Seq]
            val qPhi =
              if (vars.isEmpty) phi
              else
              //@todo what about DifferentialSymbols in boundVars? Decided to filter out since not soundness-critical.
                vars.filter(v=>v.isInstanceOf[Variable]).to[scala.collection.immutable.SortedSet].
                  foldRight(phi)((v, f) => Forall(v.asInstanceOf[Variable] :: Nil, f))

            cut(Imply(qPhi, Box(prg, qPhi))) <(
              /* use */ (implyL('Llast) <(
                hideR(pos.topLevel) partial /* result */,
                cohide2(new AntePosition(sequent.ante.length), pos.topLevel) &
                  assertT(1, 1) & assertE(Box(prg, qPhi), "abstractionb: quantified box")('Llast) &
                  assertE(b, "abstractionb: original box")('Rlast) & ?(monb) &
                  assertT(1, 1) & assertE(qPhi, "abstractionb: quantified predicate")('Llast) &
                  assertE(phi, "abstractionb: original predicate")('Rlast) & (allL('Llast)*vars.length) &
                  assertT(1, 1) & assertT(s => s.ante.head match {
                  case Forall(_, _) => phi match {
                    case Forall(_, _) => true
                    case _ => false
                  }
                  case _ => true
                }, "abstractionb: foralls must match") & closeId
                )) partial,
              /* show */ hideR(pos.topLevel) & implyR('Rlast) & V('Rlast) & closeId
            )
        }
      }
    }
  }

  /**
   * Box assignment by substitution assignment [v:=t();]p(v) <-> p(t()) (preferred),
   * or by equality assignment [x:=f();]p(??) <-> \forall x (x=f() -> p(??)) as a fallback.
   * Universal quantifiers are skolemized if applied at top-level in the succedent; they remain unhandled in the
   * antecedent and in non-top-level context.
   * @example{{{
   *    |- 1>0
   *    --------------------assignb(1)
   *    |- [x:=1;]x>0
   * }}}
   * @example{{{
   *           1>0 |-
   *    --------------------assignb(-1)
   *    [x:=1;]x>0 |-
   * }}}
   * @example{{{
   *    x_0=1 |- [{x_0:=x_0+1;}*]x_0>0
   *    ----------------------------------assignb(1)
   *          |- [x:=1;][{x:=x+1;}*]x>0
   * }}}
   * @example{{{
   *    \\forall x_0 (x_0=1 -> [{x_0:=x_0+1;}*]x_0>0) |-
   *    -------------------------------------------------assignb(-1)
   *                           [x:=1;][{x:=x+1;}*]x>0 |-
   * }}}
   * @example{{{
   *    |- [y:=2;]\\forall x_0 (x_0=1 -> x_0=1 -> [{x_0:=x_0+1;}*]x_0>0)
   *    -----------------------------------------------------------------assignb(1, 1::Nil)
   *    |- [y:=2;][x:=1;][{x:=x+1;}*]x>0
   * }}}
   * @see [[assignEquational]]
   */
  lazy val assignb: DependentPositionTactic = new DependentPositionTactic("[:=] assign") {
    override def apply(pos: Position): DependentTactic = new SingleGoalDependentTactic(name) {
      override def computeExpr(sequent: Sequent): BelleExpr = sequent.sub(pos) match {
        // slightly expensive: try substitution assignment, use equational if it fails
        case Some(Box(Assign(_, _), _)) => (useAt("[:=] assign")(pos) partial) | (assignEquational(pos) partial)
      }
    }
  }

  /**
   * Equational assignment: always introduces universal quantifier, which is skolemized if applied at top-level in the
   * succedent; it remains unhandled in the antecedent and in non-top-level context.
   * @example{{{
   *    x_0=1 |- [{x_0:=x_0+1;}*]x_0>0
   *    ----------------------------------assignEquational(1)
   *          |- [x:=1;][{x:=x+1;}*]x>0
   * }}}
   * @example{{{
   *    \\forall x_0 (x_0=1 -> [{x_0:=x_0+1;}*]x_0>0) |-
   *    -------------------------------------------------assignEquational(-1)
   *                           [x:=1;][{x:=x+1;}*]x>0 |-
   * }}}
   * @example{{{
   *    |- [y:=2;]\\forall x_0 (x_0=1 -> x_0=1 -> [{x_0:=x_0+1;}*]x_0>0)
   *    -----------------------------------------------------------------assignEquational(1, 1::Nil)
   *    |- [y:=2;][x:=1;][{x:=x+1;}*]x>0
   * }}}
   */
  lazy val assignEquational: DependentPositionTactic = new DependentPositionTactic("[:=] assign equality") {
    override def apply(pos: Position): DependentTactic = new SingleGoalDependentTactic(name) {
      override def computeExpr(sequent: Sequent): BelleExpr = sequent.sub(pos) match {
        case Some(Box(Assign(x, _), _)) =>
          ProofRuleTactics.boundRenaming(x, TacticHelper.freshNamedSymbol(x, sequent)) &
            useAt("[:=] assign")(pos.top) & useAt("[:=] assign equality")(pos) & (
              if (pos.isTopLevel && pos.isSucc) allR(pos) & implyR(pos) else ident)
      }
    }
  }

  /**
   * Generalize postcondition to C and, separately, prove that C implies postcondition.
   * The operational effect of {a;b;}@generalize(f1) is generalize(f1).
   * {{{
   *   genUseLbl:        genShowLbl:
   *   G |- [a]C, D      C |- B
   *   -------------------------
   *          G |- [a]B, D
   * }}}
   * @example{{{
   *   genUseLbl:        genShowLbl:
   *   |- [x:=2;]x>1     x>1 |- [y:=x;]y>1
   *   ------------------------------------generalize("x>1".asFormula)(1)
   *   |- [x:=2;][y:=x;]y>1
   * }}}
   * @example{{{
   *   genUseLbl:                      genShowLbl:
   *   |- a=2 -> [z:=3;][x:=2;]x>1     x>1 |- [y:=x;]y>1
   *   -------------------------------------------------generalize("x>1".asFormula)(1, 1::1::Nil)
   *   |- a=2 -> [z:=3;][x:=2;][y:=x;]y>1
   * }}}
   * @todo same for diamonds by the dual of K
   */
  def generalize(c: Formula): DependentPositionTactic = new DependentPositionTactic("generalize") {
    override def apply(pos: Position): DependentTactic = new SingleGoalDependentTactic(name) {
      override def computeExpr(sequent: Sequent): BelleExpr = sequent.at(pos) match {
        case (ctx, Box(a, _)) =>
          cutR(ctx(Box(a, c)))(pos) <(
            /* use */ /*label(BranchLabels.genUse)*/ ident,
            /* show */(cohide(pos.top) & CMon(pos.inExpr+1) & implyR(pos.top)) partial //& label(BranchLabels.genShow)
          )
      }
    }
  }
}
