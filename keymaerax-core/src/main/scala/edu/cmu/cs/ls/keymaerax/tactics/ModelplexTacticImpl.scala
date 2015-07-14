package edu.cmu.cs.ls.keymaerax.tactics

import java.io.PrintWriter

import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser.{KeYmaeraXPrettyPrinter, KeYmaeraXParser, KeYmaeraXProblemParser}
import edu.cmu.cs.ls.keymaerax.tactics.ExpressionTraversal.{ExpressionTraversalFunction, StopTraversal, TraverseToPosition}
import edu.cmu.cs.ls.keymaerax.tactics.ArithmeticTacticsImpl.localQuantifierElimination
import edu.cmu.cs.ls.keymaerax.tactics.Tactics.{ConstructionTactic, Tactic, PositionTactic}
import edu.cmu.cs.ls.keymaerax.tactics.PropositionalTacticsImpl._
import edu.cmu.cs.ls.keymaerax.tactics.HybridProgramTacticsImpl._
import edu.cmu.cs.ls.keymaerax.tactics.FOQuantifierTacticsImpl.instantiateT
import edu.cmu.cs.ls.keymaerax.tactics.ODETactics.{diamondDiffSolveT, diamondDiffSolve2DT, diffIntroduceConstantT}
import edu.cmu.cs.ls.keymaerax.tactics.SearchTacticsImpl.{onBranch,locateAnte,locateSucc}
import edu.cmu.cs.ls.keymaerax.tactics.TacticLibrary.{debugT,cutT,hideT}
import edu.cmu.cs.ls.keymaerax.tactics.TacticLibrary.TacticHelper.{getFormula,isFormula}
import edu.cmu.cs.ls.keymaerax.tactics.Tactics.{NilT,NilPT}
import edu.cmu.cs.ls.keymaerax.tactics.BranchLabels._
import edu.cmu.cs.ls.keymaerax.tools.Tool
import scala.collection.immutable
import scala.language.postfixOps

/**
 * Created by smitsch on 3/6/15.
 * @author Stefan Mitsch
 */
object ModelplexTacticImpl {

  def modelplexControllerMonitorTrafo(fml: Formula, vars: Variable*): Formula = fml match {
    // models of the form (ctrl;plant)*
    case Imply(assumptions, Box(Loop(Compose(controller, ODESystem(_, _))), _)) =>
      val preassignments = vars.map(v => Assign(v, FuncOf(Function(v.name + "pre", v.index, Unit, Real), Nothing))).reduce(Compose)
      val posteqs = vars.map(v => Equal(FuncOf(Function(v.name + "post", v.index, Unit, Real), Nothing), v)).reduce(And)
//      Imply(assumptions, Diamond(preassignments, Diamond(controller, posteqs)))
      Imply(assumptions, Diamond(controller, posteqs))
    // models of the form (plant)
    case Imply(assumptions, Box(ODESystem(_, _), _)) =>
      val posteqs = vars.map(v => Equal(FuncOf(Function(v.name + "post", v.index, Unit, Real), Nothing), v)).reduce(And)
      Imply(assumptions, Diamond(Test(True), posteqs))
    case _ => throw new IllegalArgumentException("Unsupported program form")
  }

  def modelplex = new PositionTactic("Modelplex") {
    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      // TODO generate from phi -> [alpha*]psi
      case Imply(_, Diamond(_, _)) => true
      case _ => false
    }

    def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        Some(ImplyRightT(p) & ((AxiomCloseT |
            (debugT("Before HP") &
            (locateSucc(diamondSeqT) | locateSucc(diamondChoiceT) | locateSucc(diamondNDetAssign) |
             locateSucc(diamondModelplexTestT) | locateSucc(diamondAssignEqualT) |
             locateSucc(substitutionDiamondAssignT) | locateSucc(v2vAssignT) |
             locateSucc(diamondDiffSolveT) | locateSucc(diamondDiffSolve2DT)) &
            debugT("After  HP") &
            ((locateSucc(mxPropositionalRightT) | locateSucc(optimizationOneAt()) | locateAnte(PropositionalLeftT) |
              locateAnte(optimizationOneAt()))*)
            )
          )*)
        )
      }
    }
  }

  private def mxPropositionalRightT = new PositionTactic("Modelplex Propositional Right") {
    override def applies(s: Sequent, p: Position): Boolean = {
      var containsPrg = false
      s(p) match {
        // only apply to formulas that contain programs
        case f: Formula => ExpressionTraversal.traverse(new ExpressionTraversalFunction {
          override def preP(p: PosInExpr, prg: Program): Either[Option[StopTraversal], Program] = {
            containsPrg = true
            Left(Some(ExpressionTraversal.stop))
          }
        }, f)
        case _ => // nothing to do
      }
      containsPrg && PropositionalRightT.applies(s, p)
    }

    def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        Some(PropositionalRightT(p))
      }
    }
  }

  def modelplexInPlace(useOptOne: Boolean, time: Option[Variable] = None) = new PositionTactic("Modelplex In-Place") {
    override def applies(s: Sequent, p: Position): Boolean = getFormula(s, p) match {
      // TODO generate from phi -> [alpha*]psi
      case Imply(_, Diamond(_, _)) => true
      case _ => false
    }

    def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        Some(ImplyRightT(p) & ((debugT("Before HP") & hp(useOptOne, time)(p) & debugT("After  HP"))*) &
          (atOutermostQuantifier(localQuantifierElimination)(p) | NilT)
        )
      }
    }
  }

  def hp(useOptOne: Boolean, time: Option[Variable]) = locateT(
    diamondSeqT ::
    diamondChoiceT ::
    (diamondNDetAssign & (if (useOptOne) optimizationOne() else NilPT)) ::
//    diamondModelplexTestT ::
    diamondTestT ::
    substitutionDiamondAssignT ::
    v2vAssignT ::
    (diamondAssignEqualT & (if (useOptOne) optimizationOne() else NilPT)) ::
    mxDiamondDiffSolveT ::
//    (diamondDiffSolve2DT /*& (if (useOptOne && time.isDefined) optimizationOne(Some(Variable("t", None, Real), time.get)) else NilPT)*/) ::
    (mxDiamondDiffSolve2DT & (if (useOptOne) optimizationOne() else NilPT)) ::
    Nil)

  private def mxDiamondDiffSolveT: PositionTactic = new PositionTactic("<'> differential solution") {
    override def applies(s: Sequent, p: Position): Boolean = getFormula(s, p) match {
      case Diamond(ODESystem(AtomicODE(DifferentialSymbol(_), _), _), _) => true
      case _ => false
    }

    override def apply(p: Position): Tactic = (diffIntroduceConstantT & diamondDiffSolveT)(p)
  }

  private def mxDiamondDiffSolve2DT: PositionTactic = new PositionTactic("<','> differential solution") {
    override def applies(s: Sequent, p: Position): Boolean = getFormula(s, p) match {
      case Diamond(ODESystem(DifferentialProduct(
      AtomicODE(DifferentialSymbol(_), _),
      AtomicODE(DifferentialSymbol(_), _)), _), _) => true
      case _ => false
    }

    override def apply(p: Position): Tactic = (diffIntroduceConstantT & diamondDiffSolve2DT)(p)
  }

  def locateT(tactics: List[PositionTactic]) = new PositionTactic("Modelplex Locate") {
    override def applies(s: Sequent, p: Position): Boolean = locate(tactics, s, p) != NilT

    def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        Some(locate(tactics, node.sequent, p))
      }
    }
  }

  private def atOutermostQuantifier(t: PositionTactic) = new PositionTactic("ModelPlex at outermost quantifier") {
    override def applies(s: Sequent, p: Position): Boolean = positionOfOutermostQuantifier(s, p).isDefined

    override def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        positionOfOutermostQuantifier(node.sequent, p) match {
          case Some(pos) => Some(t(pos))
          case None => None
        }
      }
    }
  }

  /**
   * Returns a modified test tactic < ?H>p <-> H & (H->p)
   * @return The new tactic.
   */
  def diamondModelplexTestT = new PositionTactic("<?H> modelplex test") {
    override def applies(s: Sequent, p: Position): Boolean = getFormula(s, p) match {
      case Diamond(Test(_), _) => true
      case _ => false
    }

    def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      def constructCut(f: Formula) = ExpressionTraversal.traverse(TraverseToPosition(p.inExpr, new ExpressionTraversalFunction {
        override def preF(p: PosInExpr, f: Formula): Either[Option[StopTraversal], Formula] = f match {
          case Diamond(Test(h), phi) => Right(And(h, Imply(h, phi)))
          case _ => Left(Some(ExpressionTraversal.stop))
        }
      }), f)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        getFormula(node.sequent, p) match {
          case Diamond(Test(h), phi) =>
            Some(
              diamondTestT(p) &
                cutT(constructCut(node.sequent(p))) & onBranch(
                (cutUseLbl, debugT("use cut") & ((AxiomCloseT | locateAnte(PropositionalLeftT) | locateSucc(PropositionalRightT))*)
                  & debugT("Modelplex: Expected axiom close, but did not close")),
                (cutShowLbl, debugT("show cut") & hideT(p.topLevel))
              )
            )
        }
      }
    }
  }

  /**
   * Locates the tactic that is applicable at the outermost sub-position of p.
   * @param ts The list of tactics.
   * @param s The sequent.
   * @param p The position.
   * @return The found tactic.
   */
  private def locate(ts: List[PositionTactic], s: Sequent, p: Position): Tactic = {
    var outerMostPos = p
    ExpressionTraversal.traverse(new ExpressionTraversalFunction {
      override def preF(pos: PosInExpr, f: Formula): Either[Option[StopTraversal], Formula] = {
        val subP = if (p.isAnte) new AntePosition(p.index, pos) else new SuccPosition(p.index, pos)
        if (ts.exists(_.applies(s, subP))) {
          outerMostPos = subP
          Left(Some(ExpressionTraversal.stop))
        } else Left(None)
      }
    }, getFormula(s, p))
    println(s"Looking for tactic at $outerMostPos: ${getFormula(s, outerMostPos)}")
    ts.find(_.applies(s, outerMostPos)) match {
      case Some(t) => println(s"Applying ${t.name} at $outerMostPos"); t(outerMostPos)
      case _ => NilT
    }
  }

  private def positionOfOutermostQuantifier(s: Sequent, p: Position): Option[Position] = {
    var outermostPos: Option[Position] = None
    ExpressionTraversal.traverse(new ExpressionTraversalFunction {
      override def preF(pos: PosInExpr, f: Formula): Either[Option[StopTraversal], Formula] = f match {
        case Forall(_, _) =>
          outermostPos = Some(if (p.isAnte) new AntePosition(p.index, pos) else new SuccPosition(p.index, pos))
          Left(Some(ExpressionTraversal.stop))
        case _ => Left(None)
      }
    }, getFormula(s, p))
    outermostPos
  }

  def optimizationOne(inst: Option[(Variable, Variable)] = None) = locateT(optimizationOneAt(inst)::Nil)

  def optimizationOneAt(inst: Option[(Variable, Variable)] = None) = new PositionTactic("Optimization 1") {
    override def applies(s: Sequent, p: Position): Boolean = isFormula(s, p) && (getFormula(s, p) match {
      case Exists(vars, _) => !p.isAnte && vars.size == 1 && vars.forall { case _: Variable => true case _ => false }
      case Forall(vars, _) => p.isAnte && vars.size == 1 && vars.forall { case _: Variable => true case _ => false }
      case _ => false
    })

    def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = getFormula(node.sequent, p) match {
        case Exists(vars, phi) if !p.isAnte => inst match {
          case Some(i) => Some(instantiateT(i._1, i._2)(p))
          case None => Some(instantiateT(p))
//            require(vars.size == 1)
//            require(vars.forall { case _: Variable => true case _ => false })
//            val (v, post) = vars.map(v => (v.asInstanceOf[Variable], Function(s"${v.name}_post", v.index, Unit, v.sort))).head
////          TODO optimization one does not quite work out (how to handle resulting f_post = f_post?)
//            val f = getFormula(node.sequent, p)
//            val postFn = Apply(post, Nothing)
////            val postFn = Apply(freshNamedSymbol(post, f), Nothing)
////
//            val postTempFn = Apply(freshNamedSymbol(post, f), Nothing)
//            val substToTemp = SubstitutionPair(postTempFn, postFn) :: Nil
//            val concToTemp = SubstitutionHelper.replaceFree(f)(postFn, postTempFn)
//
//            Some(debugT("b4 optimization 1") & uniformSubstT(substToTemp, Map(f -> concToTemp)) &
//              debugT("after substitution") &
//              instantiateT(v, postFn)(p) & debugT(s"after instantiate: $postTempFn -> $postFn") &
//              substFromTemp(postTempFn, postFn)(p) & debugT("result"))
        }
        case Forall(vars, phi) if p.isAnte => inst match {
          case Some(i) => Some(instantiateT(i._1, i._2)(p))
          case None => Some(instantiateT(p))
        }
      }
    }
  }

  private def substFromTemp(postTempFn: Term, postFn: Term) = new PositionTactic("Modelplex Subst from Temp") {
    override def applies(s: Sequent, p: Position): Boolean = true

    def apply(p: Position): Tactic = new ConstructionTactic(name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        val f = getFormula(node.sequent, p)
        val substFromTemp = SubstitutionPair(postFn, postTempFn) :: Nil
        val concFromTemp = SubstitutionHelper.replaceFree(f)(postTempFn, postFn)
        Some(uniformSubstT(substFromTemp, Map(f -> concFromTemp)))
      }
    }
  }
}
