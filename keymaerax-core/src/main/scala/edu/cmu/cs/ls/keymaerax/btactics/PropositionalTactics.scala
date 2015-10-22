//package edu.cmu.cs.ls.keymaerax.btactics
//
//import edu.cmu.cs.ls.keymaerax.bellerophon._
//import edu.cmu.cs.ls.keymaerax.core
//import edu.cmu.cs.ls.keymaerax.core._
//import edu.cmu.cs.ls.keymaerax.pt.ProofTerm
//import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
//
///**
// * [[PropositionalTactics]] contains tactical implementations of the propositional sequent calculus.
// * @author Nathan Fulton
// */
//object PropositionalTactics {
//  //////////////////////////////////////////////////////////////////////////////////////////////////
//  // Helper methods and definitions
//  //////////////////////////////////////////////////////////////////////////////////////////////////
//
//  private val Gamma1 = TypeVar("GammaOne")
//  private val Gamma2 = TypeVar("GammaTwo")
//  private val Delta1 = TypeVar("DeltaOne")
//  private val Delta2 = TypeVar("DeltaTwo")
//
//  /** Returns G, ante, G' |- D, succ, D'*/
//  private def inCtx(ante: Seq[BelleType], succ: Seq[BelleType]) : SequentType =
//    SequentType((Gamma1 +: ante) :+ Gamma2, (Delta1 +: succ) :+ Delta2)
//
////  /** ty -> \Lambda G1 \Lambda G2 \Lambda D1 \Lambda D2 ty
////    * Assumption: ty has free type vars G1,G2,D1,D2
////    * Assumption: ty has no other bound or free type vars.
////    * @todo replace with more general helper methods in typechecker.
////    */
////  private def wrap(ty : BelleType) =
////    TypeAbs(Gamma1, TypeAbs(Gamma2, TypeAbs(Delta1, TypeAbs(Delta2, ty))))
//
//  /** e ~> \Forall G1,G2,D1,D2 . e */
//  private def abstractOver(e : BelleExpr) =
//    ParametricTactic(Gamma1,
//      ParametricTactic(Gamma2,
//        ParametricTactic(Delta1,
//          ParametricTactic(Delta2,
//            e))))
//
//  //////////////////////////////////////////////////////////////////////////////////////////////////
//  // Proof rule implementations
//  //////////////////////////////////////////////////////////////////////////////////////////////////
//  /**
//   * @todo show the proof rule being implemented.
//   */
//  def AndR = abstractOver(new BuiltInPositionTactic("AndR") {
//    override def apply(provable: Provable, pos: SeqPos) =
//      provable(core.AndRight(pos.asInstanceOf[SuccPos]), 0)
//
//
//    override def pt(provable: Provable): (Seq[Provable], (Seq[ProofTerm]) => ProofTerm) = ???
//
//    override val belleType : BelleType = {
//      val pTy      = FormulaType("p()".asFormula)
//      val qTy      = FormulaType("q()".asFormula)
//      val andTy    = FormulaType(And(pTy.f, qTy.f))
//      val domain   = inCtx(Nil, Seq(andTy))     // G1, G2 |- D1, p() ^ q(), D2
//      val codomain = inCtx(Nil, Seq(pTy, qTy))  // G1, G2 |- D1, p(), q(), D2
//
//      // \forall G1,G2,D1,D2 . (G1, G2 |- D1, p() ^ q(), D2) ~> G1, G2 |- D1, p(), q(), D2
//      SequentMapType(domain, codomain)
//    }
//  })
//}
