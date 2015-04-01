package edu.cmu.cs.ls.keymaera.tactics

import edu.cmu.cs.ls.keymaera.core.ExpressionTraversal.{StopTraversal, ExpressionTraversalFunction, TraverseToPosition}
import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.core.StaticSemantics.signature
import edu.cmu.cs.ls.keymaera.tactics.AlphaConversionHelper.replaceBound
import edu.cmu.cs.ls.keymaera.tactics.AxiomaticRuleTactics._
import edu.cmu.cs.ls.keymaera.tactics.FOQuantifierTacticsImpl.{skolemizeT,instantiateT}
import edu.cmu.cs.ls.keymaera.tactics.FormulaConverter._
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.{AxiomCloseT,ImplyRightT,ImplyLeftT,NotRightT,NotLeftT,
  AndLeftT,AndRightT}
import edu.cmu.cs.ls.keymaera.tactics.SearchTacticsImpl.{lastSucc,lastAnte}
import edu.cmu.cs.ls.keymaera.tactics.Tactics.{PositionTactic, ConstructionTactic, Tactic, stopT}
import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary.{cutT,debugT,alphaRenamingT}
import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary.TacticHelper.{getFormula,freshNamedSymbol,getTerm}


/**
 * Created by smitsch on 3/20/15.
 * @author Stefan Mitsch
 */
object ContextTactics {

  /**
   * Creates a new tactic to uncover an equivalence inside context, and execute a base tactic on that uncovered formula.
   * @param baseF The base formula to uncover.
   * @param baseT The base tactic to execute on the base formula once uncovering is completed.
   * @return The newly created tactic.
   */
  def showEquivInContext(baseF: Formula, baseT: Tactic): Tactic = new ConstructionTactic("Show Equivalence in Context") {
    override def applicable(node : ProofNode): Boolean = node.sequent.ante.isEmpty && node.sequent.succ.length == 1 &&
      (node.sequent.succ(0) match {
        case eq@Equiv(_, _) => eq == baseF || congruenceT.applicable(node)
        case _ => false
      })

    override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
      if (node.sequent.succ(0) == baseF) Some(baseT)
      else Some(congruenceT & showEquivInContext(baseF, baseT))
  }

  def peelT(counterPart: Position, inCtx: PosInExpr, baseT: PositionTactic): PositionTactic = new PositionTactic("Peel") {
    override def applies(s: Sequent, p: Position): Boolean =
      s(p).extractContext(inCtx)._1 == s(counterPart).extractContext(inCtx)._1

    override def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        val c1 = node.sequent(p).extractContext(inCtx)
        val c2 = node.sequent(counterPart).extractContext(inCtx)
        assert(c1._1 == c2._1, "Ensured by applies to never happen")
        Some(peelT(c1._1.ctx, c1._2, c2._2, counterPart, baseT)(p))
      }
    }
  }

  def peelT(context: Formula, f: Expr, g: Expr, counterPart: Position, baseT: PositionTactic): PositionTactic = new PositionTactic("Peel") {
    override def applies(s: Sequent, p: Position): Boolean = true

    override def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        // TODO use once uniform substitution of CDotFormula is fixed
        //        assert(context.instantiateContext(f) == node.sequent(p), s"Context $context with formula $f, which is\n   ${context.instantiateContext(f)}\n not found at position $p\n   ${node.sequent(p)}")
        //        assert(context.instantiateContext(g) == node.sequent(counterPart), s"Context $context with formula $g, which is\n   ${context.instantiateContext(g)}\n not found at position $counterPart\n   ${node.sequent(counterPart)}")
        if (!signature(context).contains(CDotFormula) && !signature(context).contains(CDot))
          Some(AxiomCloseT | debugT(s"Unexpected peeling result: ${node.sequent} did not close by axiom") & stopT)
        else context match {
          case CDotFormula => Some(debugT(s"Finished peeling, now calling base tactic ${baseT.name}") & baseT(p))
          case Not(phi) => Some(NotLeftT(counterPart) & NotRightT(p) & lastSucc(peelT(phi, f, g, AntePosition(node.sequent.ante.length - 1), baseT)))
          case And(lhs, rhs) => Some(AndLeftT(counterPart) & AndRightT(p) &&(
            peelT(lhs, f, g, counterPart, baseT)(p),
            peelT(rhs, f, g, AntePosition(node.sequent.ante.length), baseT)(p)))
          case Imply(lhs, rhs) => Some(ImplyRightT(p) & ImplyLeftT(counterPart) && (
            // both left-hand sides move in the last position on the other side of the turn stile
            lastSucc(peelT(lhs, f, g, AntePosition(node.sequent.ante.length), baseT)),
            // ImplyLeftT moves right-hand side into last ante position
            peelT(rhs, f, g, AntePosition(node.sequent.ante.length), baseT)(p)))
          case Forall(vars, phi) =>
            // HACK guess skolem name
            val (v, newV) = vars.head match { case v: Variable => (v, freshNamedSymbol(v, node.sequent)) }
            val newF = f match { case fml: Formula => replaceBound(fml)(v, newV) case _ => f }
            val newG = g match { case fml: Formula => replaceBound(fml)(v, newV) case _ => g }
            Some(skolemizeT(p) & alphaRenamingT(v.name, v.index, newV.name, newV.index)(counterPart) &
              // alpha renaming changes the position from counterPart to last ante
              lastAnte(instantiateT(newV, newV)) &
              peelT(phi, newF, newG, AntePosition(node.sequent.ante.length - 1), baseT)(p))
          case Exists(vars, phi) =>
            // HACK guess skolem name
            val (v, newV) = vars.head match { case v: Variable => (v, freshNamedSymbol(v, node.sequent)) }
            val newF = f match { case fml: Formula => replaceBound(fml)(v, newV) case _ => f }
            val newG = g match { case fml: Formula => replaceBound(fml)(v, newV) case _ => g }
            Some(skolemizeT(counterPart) & alphaRenamingT(v.name, v.index, newV.name, newV.index)(p) &
              // alpha renaming changes the position from p to last succ
              lastSucc(instantiateT(newV, newV)) &
              peelT(phi, newF, newG, AntePosition(node.sequent.ante.length - 1), baseT)(p))
        }
      }
    }
  }

  /**
   * Creates a tactic to cut in an equality f = g in context, i.e., C[f] <-> C[g]. Expects either the left-hand side
   * of the equality to be present or the right-hand side to be present at position ctx.
   * @param f The desired equality.
   * @param ctx Points to the position in the context.
   * @return The newly created tactic.
   */
  def cutEqualsInContext(f: Formula, ctx: Position): Tactic = cutEqualsInContext(_ => f, ctx)
  def cutEqualsInContext(g: ProofNode => Formula, ctx: Position): Tactic = new ConstructionTactic("Cut in Context") {
    def applicable(pn: ProofNode): Boolean = g(pn) match {
      case Equals(_, lhs, rhs) => val t = getTerm(pn.sequent, ctx); t == lhs || t == rhs
      case _ => false
    }

    override def constructTactic(tool: Tool, p: ProofNode): Option[Tactic] = g(p) match {
      case Equals(sort, lhs, rhs) if getTerm(p.sequent, ctx) == lhs =>
        val lhsInCtx = p.sequent(ctx.topLevel)
        val rhsInCtx = ExpressionTraversal.traverse(TraverseToPosition(ctx.inExpr, new ExpressionTraversalFunction {
          override def preT(pos: PosInExpr, t: Term): Either[Option[StopTraversal], Term] =
            if (t == lhs) Right(rhs)
            else Left(Some(ExpressionTraversal.stop))
        }), lhsInCtx) match {
          case Some(f) => f
          case None => throw new IllegalArgumentException(s"Did not find $lhs at position $ctx")
        }
        val equivOfEqualsInCtx = Equiv(lhsInCtx, rhsInCtx)
        Some(cutT(Some(equivOfEqualsInCtx)))
      case Equals(sort, lhs, rhs) if getTerm(p.sequent, ctx) == rhs =>
        val rhsInCtx = p.sequent(ctx.topLevel)
        val lhsInCtx = ExpressionTraversal.traverse(TraverseToPosition(ctx.inExpr, new ExpressionTraversalFunction {
          override def preT(pos: PosInExpr, t: Term): Either[Option[StopTraversal], Term] =
            if (t == rhs) Right(lhs)
            else Left(Some(ExpressionTraversal.stop))
        }), rhsInCtx) match {
          case Some(f) => f
          case None => throw new IllegalArgumentException(s"Did not find $rhs at position $ctx")
        }
        val equivOfEqualsInCtx = Equiv(lhsInCtx, rhsInCtx)
        Some(cutT(Some(equivOfEqualsInCtx)))
    }
  }

  /**
   * Creates a tactic to cut in an equivalence p<->q in context, i.e., C[p] <-> C[q]. Expects either the left-hand side
   * of the equivalence to be present or the right-hand side to be present at position ctx.
   * @param f The desired equivalence.
   * @param ctx Points to the position in the context.
   * @return The newly created tactic.
   */
  def cutEquivInContext(f: Formula, ctx: Position): Tactic = cutEquivInContext(_ => f, ctx)
  def cutEquivInContext(g: ProofNode => Formula, ctx: Position): Tactic = new ConstructionTactic("Cut in Context") {
    def applicable(pn: ProofNode): Boolean = g(pn) match {
      case Equiv(lhs, rhs) => val f = getFormula(pn.sequent, ctx); f == lhs || f == rhs
      case _ => false
    }

    override def constructTactic(tool: Tool, p: ProofNode): Option[Tactic] = g(p) match {
      case Equiv(lhs, rhs) if getFormula(p.sequent, ctx) == lhs =>
        val lhsInCtx = p.sequent(ctx.topLevel)
        val rhsInCtx = ExpressionTraversal.traverse(TraverseToPosition(ctx.inExpr, new ExpressionTraversalFunction {
          override def preF(pos: PosInExpr, f: Formula): Either[Option[StopTraversal], Formula] =
            if (f == lhs) Right(rhs)
            else Left(Some(ExpressionTraversal.stop))
        }), lhsInCtx) match {
          case Some(f) => f
          case None => throw new IllegalArgumentException(s"Did not find $lhs at position $ctx")
        }
        val equivInCtx = Equiv(lhsInCtx, rhsInCtx)
        Some(cutT(Some(equivInCtx)))
      case Equiv(lhs, rhs) if getFormula(p.sequent, ctx) == rhs =>
        val rhsInCtx = p.sequent(ctx.topLevel)
        val lhsInCtx = ExpressionTraversal.traverse(TraverseToPosition(ctx.inExpr, new ExpressionTraversalFunction {
          override def preF(pos: PosInExpr, f: Formula): Either[Option[StopTraversal], Formula] =
            if (f == rhs) Right(lhs)
            else Left(Some(ExpressionTraversal.stop))
        }), rhsInCtx) match {
          case Some(f) => f
          case None => throw new IllegalArgumentException(s"Did not find $rhs at position $ctx")
        }
        val equivInCtx = Equiv(lhsInCtx, rhsInCtx)
        Some(cutT(Some(equivInCtx)))
    }
  }

  /**
   * Creates a tactic to cut in an implication p->q in context, i.e., C[p] -> C[q]. Expects the right-hand side
   * of the implication to be present at position ctx.
   * @param f The desired implication.
   * @param ctx Points to the position in the context.
   * @return The newly created tactic.
   */
  def cutImplyInContext(f: Formula, ctx: Position): Tactic = cutImplyInContext(_ => f, ctx)
  def cutImplyInContext(g: ProofNode => Formula, ctx: Position): Tactic = new ConstructionTactic("Cut in Context") {
    def applicable(pn: ProofNode): Boolean = g(pn) match {
      case Imply(lhs, rhs) => getFormula(pn.sequent, ctx) == lhs || getFormula(pn.sequent, ctx) == rhs
      case _ => false
    }

    override def constructTactic(tool: Tool, p: ProofNode): Option[Tactic] = g(p) match {
      case Imply(lhs, rhs) if getFormula(p.sequent, ctx) == lhs =>
        val lhsInCtx = p.sequent(ctx.topLevel)
        val rhsInCtx = ExpressionTraversal.traverse(TraverseToPosition(ctx.inExpr, new ExpressionTraversalFunction {
          override def preF(pos: PosInExpr, f: Formula): Either[Option[StopTraversal], Formula] =
            if (f == lhs) Right(rhs)
            else Left(Some(ExpressionTraversal.stop))
        }), lhsInCtx) match {
          case Some(f) => f
          case None => throw new IllegalArgumentException(s"Did not find $lhs at position $ctx")
        }
        val implyInCtx = Imply(lhsInCtx, rhsInCtx)
        Some(cutT(Some(implyInCtx)))
      case Imply(lhs, rhs) if getFormula(p.sequent, ctx) == rhs =>
        val rhsInCtx = p.sequent(ctx.topLevel)
        val lhsInCtx = ExpressionTraversal.traverse(TraverseToPosition(ctx.inExpr, new ExpressionTraversalFunction {
          override def preF(pos: PosInExpr, f: Formula): Either[Option[StopTraversal], Formula] =
            if (f == rhs) Right(lhs)
            else Left(Some(ExpressionTraversal.stop))
        }), rhsInCtx) match {
          case Some(f) => f
          case None => throw new IllegalArgumentException(s"Did not find $rhs at position $ctx")
        }
        val implyInCtx = Imply(lhsInCtx, rhsInCtx)
        Some(cutT(Some(implyInCtx)))
    }
  }
}
