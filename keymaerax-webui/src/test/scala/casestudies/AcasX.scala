/**
* Copyright (c) Carnegie Mellon University.
* See LICENSE.txt for the conditions of this license.
*/
package casestudies

import java.io.File

import scala.collection.immutable
import scala.collection.immutable._

import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser.KeYmaeraXProblemParser
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.tactics.ODETactics._
import edu.cmu.cs.ls.keymaerax.tactics.BranchLabels._
import edu.cmu.cs.ls.keymaerax.tactics.FOQuantifierTacticsImpl.{instantiateT,skolemizeT,instantiateExistentialQuanT}
import edu.cmu.cs.ls.keymaerax.tactics.TacticLibrary.{debugT, arithmeticT, ImplyRightT, AndLeftT, hideT, AndRightT,
  ImplyLeftT, AxiomCloseT, OrRightT, OrLeftT, cutT, locate, NotRightT, NotLeftT}
import edu.cmu.cs.ls.keymaerax.tactics.TactixLibrary._
import edu.cmu.cs.ls.keymaerax.tactics.ArithmeticTacticsImpl.{AbsAxiomT,AbsT,MinMaxAxiomT,MinMaxT,EqualReflexiveT}
import edu.cmu.cs.ls.keymaerax.tactics.EqualityRewritingImpl.{abbrv,eqLeft}
import edu.cmu.cs.ls.keymaerax.tactics.Tactics.{Tactic, PositionTactic}
import edu.cmu.cs.ls.keymaerax.tactics.PropositionalTacticsImpl.{Propositional,NonBranchingPropositionalT,cohideT}
import edu.cmu.cs.ls.keymaerax.tactics.HybridProgramTacticsImpl._
import edu.cmu.cs.ls.keymaerax.tactics._
import edu.cmu.cs.ls.keymaerax.tags.SlowTest
import edu.cmu.cs.ls.keymaerax.tools.{Mathematica, KeYmaera}
import testHelper.ProvabilityTestHelper
import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}

import testHelper.ParserFactory._
import testHelper.SequentFactory._

import scala.collection.immutable.{Nil, Map}

/**
 * Created by smitsch on 3/27/15.
 * @author Stefan Mitsch
 * @author Jean-Baptiste Jeannin
 */
@SlowTest
class AcasX extends FlatSpec with Matchers with BeforeAndAfterEach {

  val helper = new ProvabilityTestHelper((x) => println(x))
  val mathematicaConfig: Map[String, String] = helper.mathematicaConfig

  override def beforeEach() = {
    Tactics.KeYmaeraScheduler = new Interpreter(KeYmaera)
    Tactics.MathematicaScheduler = new Interpreter(new Mathematica)
    Tactics.MathematicaScheduler.init(mathematicaConfig)
    Tactics.KeYmaeraScheduler.init(Map())
  }

  override def afterEach() = {
    Tactics.MathematicaScheduler.shutdown()
    Tactics.KeYmaeraScheduler.shutdown()
    Tactics.MathematicaScheduler = null
    Tactics.KeYmaeraScheduler = null
  }

  "No Delay using Max" should "be provable" in {
    // one goal left corresponding to ODESolve issue, with 7982464f7daa4afb29295d19528830f2eff56523, Stefan, Tue Sep 8 17:41:17 2015 +0200
    // 780 seconds on robin (about 13 min)
    val s = parseToSequent(getClass.getResourceAsStream("/examples/casestudies/acasx/nodelay_max.key"))

    val invariant = ("( (w=-1 | w=1) & " +
      "      (" +
      "\\forall t \\forall ro \\forall ho" +
      "((0 <= t & t < max(0, w * (dhf - dhd)) / a &" +
      "  ro = rv * t & ho = (w * a) / 2 * t^2 + dhd * t) |" +
      "  (t >= max(0, w * (dhf - dhd)) / a &" +
      "    ro = rv * t & ho = dhf * t - w * max(0, w * (dhf - dhd))^2 / (2*a))" +
      "-> (abs(r - ro) > rp | w * h < w * ho - hp))" +
      "      )) & ( hp>0&rp>0&rv>=0&a>0 )").asFormula

    val evolutionDomain = "\\forall tside (0<=tside & tside<=kxtime_5 -> (w*(dhd_2()+ao*tside)>=w*dhf|w*ao>=a))"
    val initDomain = "w*dhd>=w*dhf|w*ao>=a"

    def dT(s : String) = debugT(s)

    val crushw = la(orL, "w=-1|w=1") && (QE, QE)
    // Q: Stefan, why did you change this from w() ?

    val crushor = (la(orL)*) & QE

    val absmax = abbrv("max((0,w*(dhf-dhd)))".asTerm, Some(Variable("maxA"))) &
      la(MinMaxT, "", Some("max(0,w*(dhf-dhd))".asTerm)) &
      ls(AbsT, "", Some("abs(r)".asTerm)) &
      ls(AbsT, "", Some("abs(h)".asTerm)) &
      la(AbsT, "", Some("abs(r-0)".asTerm))

    val absmax2 = (ls(AbsT, "", Some("abs(r_3-ro_0)".asTerm)) | dT("abs(r_3-ro_0) not present")) &
      ( abbrv("max((0,w*(dhf-dhd)))".asTerm, Some(Variable("maxI"))) &
        la(MinMaxT, "", Some("max((0,w*(dhf-dhd)))".asTerm)) | dT("max(0,w*(dhf-dhd)) not present")) &
      ( abbrv("max((0,w*(dhf-dhd_3)))".asTerm, Some(Variable("maxF"))) &
        la(MinMaxT, "", Some("max((0,w*(dhf-dhd_3)))".asTerm)) | dT("max(0,w*(dhf-dhd_3)) not present"))

    def cutEZ(c:Formula, t:Tactic) = cut(c) & onBranch(
      (cutShowLbl, t | dT("Cut didn't close") & Tactics.stopT)
    )

    val crushabsmax = absmax & crushor

    val tactic = ls(implyR) & la(andL) & ls(wipeContextInductionT(Some(invariant))) & onBranch(
      (indInitLbl, dT("Base case") & ls(andR) & closeId),
      (indUseCaseLbl, dT("Use case") & ls(implyR) & (la(andL)*) & ls(andR) && (
        la(instantiateT(Variable("t"), Number(0))) &
          la(instantiateT(Variable("ro"), Number(0))) &
          la(instantiateT(Variable("ho"), Number(0))) & la(implyL) && (
          dT("Use case 1") & ls(hide, "abs(r)>rp|abs(h)>hp") &
            /*abbrv(Variable("max0"))(SuccPosition(0, PosInExpr(0::0::0::1::1::0::Nil))) // But more fragile */
            abbrv("max((0,w*(dhf-dhd)))".asTerm, Some(Variable("maxI"))) & dT("abbrv") &
            la(MinMaxT, "", Some("max(0,w*(dhf-dhd))".asTerm)) & QE, //MinMaxT(AntePosition(9, PosInExpr(1 :: Nil)))
          dT("Absolute value") &
            ls(AbsT, "", Some("abs(r)".asTerm)) &   //AbsT(SuccPosition(0, PosInExpr(0 :: 0 :: Nil))) &
            ls(AbsT, "", Some("abs(h)".asTerm)) &   //AbsT(SuccPosition(0, PosInExpr(1 :: 0 :: Nil)))
            la(AbsT, "", Some("abs(r-0)".asTerm)) & //AbsT(AntePosition(9, PosInExpr(0 :: 0 :: Nil))) &
            dT("Use case 2") & QE
          ), closeId
        )),
      (indStepLbl, dT("Step") & ls(implyR) & ls(boxSeqGenT(invariant)) & onBranch(
        (cutShowLbl, dT("Generalization Holds") &
          ls(boxSeqT) & ls(boxChoiceT) & ls(andR) && (
          dT("1.1") & ls(boxTestT) & ls(implyR) & ls(boxNDetAssign) & ls(skolemizeT) & closeId, /* closed */
          dT("1.2") & ls(boxSeqT) & ls(boxNDetAssign) & ls(skolemizeT) & ls(boxSeqT) & ls(boxChoiceT) & dT("1.2.1") &
            la(hide, "((w=-1|w=1)&\\forall t \\forall ro \\forall ho (0<=t&t < max((0,w*(dhf-dhd)))/a&ro=rv*t&ho=w*a/2*t^2+dhd*t|t>=max((0,w*(dhf-dhd)))/a&ro=rv*t&ho=dhf*t-w*max((0,w*(dhf-dhd)))^2/(2*a)->abs(r-ro)>rp|w*h < w*ho-hp))&(hp>0&rp>0&rv>=0&a>0)")
            & ls(andR) & /* almost identical branches */
            ls(substitutionBoxAssignT) & ls(boxTestT) & dT("1.2.2") & ls(implyR) & ls(boxNDetAssign) & ls(skolemizeT) &
            ls(andR) && (ls(andR) && (dT("cohide") & cohide(SuccPosition(0)) & QE, closeId), closeId)
          /* last line used to be handled by QE, but Max broke that */
          /* Would like to replace cohide by: ls(cohide, "-1=-1|-1=1") OR ls(cohide, "1=-1|1=1") (BUT
             two different branches)*/
          )),
        (cutUseLbl, dT("Generalization Strong Enough") &
          abbrv("max((0,w*(dhf-dhd)))".asTerm, Some(Variable("max0"))) & dT("abbrv2") &
          /*abbrv(Variable("max0"))(AntePosition(0, PosInExpr(0::1::0::0::0::0::0::0::0::1::1::0::Nil)))*/
          cutEZ("!(w*dhd>=w*dhf|w*ao>=a) | (w*dhd>=w*dhf|w*ao>=a)".asFormula,
            ls(cohide, "!(w*dhd>=w*dhf|w*ao>=a) | (w*dhd>=w*dhf|w*ao>=a)") & QE) &
          la(orL, "!(w*dhd>=w*dhf|w*ao>=a) | (w*dhd>=w*dhf|w*ao>=a)") && (
          la(hide, "max0=max((0,w*(dhf-dhd)))") &
            la(hide, "((w=-1|w=1)&\\forall t \\forall ro \\forall ho (0<=t&t < max0/a&ro=rv*t&ho=w*a/2*t^2+dhd*t|t>=max0/a&ro=rv*t&ho=dhf*t-w*max0^2/(2*a)->abs(r-ro)>rp|w*h < w*ho-hp))&(hp>0&rp>0&rv>=0&a>0)") &
            dT("Before DI") &
            cutEZ("[{r'=-rv,dhd'=ao,h'=-dhd&w*dhd>=w*dhf|w*ao>=a}](0=1)".asFormula, // false as postcondition doesn't work
              ls(hide, "[{r'=-rv,dhd'=ao,h'=-dhd&w*dhd>=w*dhf|w*ao>=a}](((w=-1|w=1)&\\forall t \\forall ro \\forall ho (0<=t&t < max((0,w*(dhf-dhd)))/a&ro=rv*t&ho=w*a/2*t^2+dhd*t|t>=max((0,w*(dhf-dhd)))/a&ro=rv*t&ho=dhf*t-w*max((0,w*(dhf-dhd)))^2/(2*a)->abs(r-ro)>rp|w*h < w*ho-hp))&(hp>0&rp>0&rv>=0&a>0))")
                & ls(DI)) &
            la(hide, "!(w*dhd>=w*dhf|w*ao>=a)") &
            dT("After DI") & ls(DC("0=1".asFormula)) & onBranch(
            (cutShowLbl, dT("After DC 1") & closeId),
            (cutUseLbl, dT("After DC 2") & ls(DW) & dT("after DW") &
              ls(implyR) & la(andL) & la(cohide, "0=1") & dT("before QE") & QE)
          ),
          ls(diffSolution(None, la(hide, "max0=max((0,w*(dhf-dhd)))"))) & dT("Diff. Solution") &
            /* cutting in the side condition that we expect from diff. solution. Remove once diff. sol. produces it */
            dT("bla") & ls(implyR) & (la(andL)*) & ls(andR) && (
            ls(andR) && (
              closeId,
              dT("Before skolemization") & (ls(skolemizeT)*) & dT("After skolemization") & ls(implyR) & ls(orR) &
                //here we'd want to access previously introduced skolem symbol and time introduced by diffSolution;goal 90
                la(instantiateT(Variable("t"), "kxtime_5 + t_0".asTerm)) & // t_22+t_23: kxtime_5 == t_22, t_0 == t_23
                la(instantiateT(Variable("ro"), "rv*(kxtime_5 + t_0)".asTerm)) & // rv*(t_22+t_23)
                dT("Before CUT") &
                cut("(0<=t_0+kxtime_5 & t_0+kxtime_5<max0/a) | t_0+kxtime_5 >= max0/a".asFormula) & onBranch(
                (cutShowLbl, dT("Show Cut") & la(hide, "max0=max((0,w*(dhf-dhd)))") &
                  la(hide, "\\forall ho (0<=kxtime_5+t_0&kxtime_5+t_0 < max0/a&rv*(kxtime_5+t_0)=rv*(kxtime_5+t_0)&ho=w*a/2*(kxtime_5+t_0)^2+dhd*(kxtime_5+t_0)|kxtime_5+t_0>=max0/a&rv*(kxtime_5+t_0)=rv*(kxtime_5+t_0)&ho=dhf*(kxtime_5+t_0)-w*max0^2/(2*a)->abs(r-rv*(kxtime_5+t_0))>rp|w*h < w*ho-hp)")
                  & ls(hide, "abs(r_3-ro_0)>rp") & ls(hide, "w*h_3 < w*ho_0-hp") & dT("Show Cut 2") & ls(orR) &
                  la(orL, "0<=t_0&t_0 < max((0,w*(dhf-dhd_3)))/a&ro_0=rv*t_0&ho_0=w*a/2*t_0^2+dhd_3*t_0|t_0>=max((0,w*(dhf-dhd_3)))/a&ro_0=rv*t_0&ho_0=dhf*t_0-w*max((0,w*(dhf-dhd_3)))^2/(2*a)")
                  & (la(andL)*) & (ls(andR)*) & (QE | dT("Should be closed") & Tactics.stopT)),
                (cutUseLbl, dT("Use Cut") &
                  la(orL, "0<=t_0+kxtime_5&t_0+kxtime_5 < max0/a|t_0+kxtime_5>=max0/a") && (
                  dT("Goal 110") & la(hide, initDomain) &
                    la(instantiateT(Variable("ho"), "w*a/2*(t_0+kxtime_5)^2 + dhd*(t_0+kxtime_5)".asTerm)) //, { case Forall(Variable("ho", None, Real) :: Nil, _) => true case _ => false })
                    & dT("instantiate ho") & ((closeId | l(NonBranchingPropositionalT))*) &
                    la(implyL, "0<=kxtime_5+t_0&kxtime_5+t_0 < max0/a&rv*(kxtime_5+t_0)=rv*(kxtime_5+t_0)&w*a/2*(t_0+kxtime_5)^2+dhd*(t_0+kxtime_5)=w*a/2*(kxtime_5+t_0)^2+dhd*(kxtime_5+t_0)|kxtime_5+t_0>=max0/a&rv*(kxtime_5+t_0)=rv*(kxtime_5+t_0)&w*a/2*(t_0+kxtime_5)^2+dhd*(t_0+kxtime_5)=dhf*(kxtime_5+t_0)-w*max0^2/(2*a)->abs(r-rv*(kxtime_5+t_0))>rp|w*h < w*(w*a/2*(t_0+kxtime_5)^2+dhd*(t_0+kxtime_5))-hp")
                    && (
                    (ls(orR)*) &
                      ls(hide, "kxtime_5+t_0>=max0/a&rv*(kxtime_5+t_0)=rv*(kxtime_5+t_0)&w*a/2*(t_0+kxtime_5)^2+dhd*(t_0+kxtime_5)=dhf*(kxtime_5+t_0)-w*max0^2/(2*a)")
                      & (ls(andR)*) & (closeId | absmax2 & dT("before QE") & QE | dT("Shouldn't get here")) & dT("Shouldn't get here 2"),
                    dT("cut 3") & la(orL, "0<=t_0&t_0 < max((0,w*(dhf-dhd_3)))/a&ro_0=rv*t_0&ho_0=w*a/2*t_0^2+dhd_3*t_0|t_0>=max((0,w*(dhf-dhd_3)))/a&ro_0=rv*t_0&ho_0=dhf*t_0-w*max((0,w*(dhf-dhd_3)))^2/(2*a)")
                      && (
                      dT("Goal 124") &
                        la(orL,"abs(r-rv*(kxtime_5+t_0))>rp|w*h < w*(w*a/2*(t_0+kxtime_5)^2+dhd*(t_0+kxtime_5))-hp")&& (
                        dT("lSucc2") & ls(hide, "w*h_3 < w*ho_0-hp") & absmax2 & QE,
                        dT("Goal 135") & ls(hide, "abs(r_3-ro_0)>rp") & (la(andL)*) &
                          la(orL, "w*dhd_3>=w*dhf|w*ao>=a") && (
                          dT("Goal 146") & absmax2 & crushw,
                          dT("Goal 148") & absmax2 & crushw
                          )
                        ),
                      dT("Goal 125") &
                        la(orL,"abs(r-rv*(kxtime_5+t_0))>rp|w*h < w*(w*a/2*(t_0+kxtime_5)^2+dhd*(t_0+kxtime_5))-hp")&& (
                        dT("Goal 280") & absmax2 & QE,
                        dT("Goal 281") & absmax2 & (la(andL)*) & (la(orL)*) & QE
                        )
                      ) ),
                  // goal 111
                  dT("Goal 111") &
                    la(instantiateT(Variable("ho"), "dhf*(t_0+kxtime_5) - w*max0^2/(2*a)".asTerm)) //, { case Forall(Variable("ho", None, Real) :: Nil, _) => true case _ => false })
                    & dT("Goal 120-1") &
                    la(implyL, "0<=kxtime_5+t_0&kxtime_5+t_0 < max0/a&rv*(kxtime_5+t_0)=rv*(kxtime_5+t_0)&dhf*(t_0+kxtime_5)-w*max0^2/(2*a)=w*a/2*(kxtime_5+t_0)^2+dhd*(kxtime_5+t_0)|kxtime_5+t_0>=max0/a&rv*(kxtime_5+t_0)=rv*(kxtime_5+t_0)&dhf*(t_0+kxtime_5)-w*max0^2/(2*a)=dhf*(kxtime_5+t_0)-w*max0^2/(2*a)->abs(r-rv*(kxtime_5+t_0))>rp|w*h < w*(dhf*(t_0+kxtime_5)-w*max0^2/(2*a))-hp")
                    && (
                    dT("Goal 122") & la(hide, initDomain) & absmax2 & QE,
                    dT("Goal 123") & la(orL, "0<=t_0&t_0 < max((0,w*(dhf-dhd_3)))/a&ro_0=rv*t_0&ho_0=w*a/2*t_0^2+dhd_3*t_0|t_0>=max((0,w*(dhf-dhd_3)))/a&ro_0=rv*t_0&ho_0=dhf*t_0-w*max((0,w*(dhf-dhd_3)))^2/(2*a)")
                      && (
                      la(hide, initDomain) & absmax2 & crushor, // takes a while (about 170 seconds)
                      dT("Goal 127") &
                        la(TacticLibrary.eqLeft(exhaustive=true), "kxtime_1=0") &
                        la(TacticLibrary.eqLeft(exhaustive=true), "kxtime_4()=0") &
                        (la(andL)*) & dT("Goal 193") &
                        la(orL, "abs(r-rv*(kxtime_5+t_0))>rp|w*h < w*(dhf*(t_0+kxtime_5)-w*max0^2/(2*a))-hp") && (
                        dT("Goal 194") & absmax2 & crushor, // takes a while (100 seconds or so)
                        dT("Goal 195") & ls(hide, "abs(r_3-ro_0)>rp") & absmax2 &
                          la(orL, "0>=w*(dhf-dhd_3)&max_1=0|0 < w*(dhf-dhd_3)&max_1=w*(dhf-dhd_3)") && (
                          dT("Goal 214") & cut("w*ao>=a|!w*ao>=a".asFormula) & onBranch(
                            (cutShowLbl, ls(cohide, "w*ao>=a|!w*ao>=a") & QE),
                            (cutUseLbl, dT("Goal 214-2") & la(orL, "w*ao>=a|!w*ao>=a") && (
                              dT("Goal 214-3") /*& la(hide, initDomain)*/ & QE,
                              dT("Goal 231") & la(orL, "w*dhd_3>=w*dhf|w*ao>=a") && (
                                dT("Goal 233") & la(orL, "w*dhd>=w*dhf|w*ao>=a") && (
                                  crushor,
                                  la(notL) & closeId
                                  ),
                                la(notL) & closeId
                                ) ) ) ),
                          la(hide, initDomain) & crushor
                          )
                        )

                      )
                    )
                  )
                  )
              )
              ), QE /* End AndRight */
            )
          /* ) End cutUseLbl of 2nd ODE cut */
          /* ) End onBranch 2nd ODE cut */
          /* ) End cutUseLbl of 1st ODE cut */
          /* ) End onBranch 1st ODE cut */
          ) /* end orL on cutEZ */
        ) /* End cutUseLbl "Generalization strong enough" */
      )) /* End indStepLbl */
    )

    helper.runTactic(tactic, new RootNode(s)) shouldBe 'closed
  }


  "ACAS X" should "directly prove explicit region safety from implicit region safety and direct equivalence" in {
    val acasximplicit = KeYmaeraXProblemParser(io.Source.fromInputStream(getClass.getResourceAsStream("/examples/casestudies/acasx/nodelay.key")).mkString)
    val acasxexplicit = KeYmaeraXProblemParser(io.Source.fromInputStream(getClass.getResourceAsStream("/examples/casestudies/acasx/nodelay-explicit.key")).mkString)
    val equivalence = KeYmaeraXProblemParser(io.Source.fromInputStream(getClass.getResourceAsStream("/examples/casestudies/acasx/nodelay-equivalence-direct.key")).mkString)
    val shape = Context(
      """  (hp > 0 & rp > 0 & rv >= 0 & a > 0) &
  ( (w=-1 | w=1) &
      (
        ⎵
      ) /* C(w,dhf) */
  )
  -> [
  {   {
      { ?true; ++
        {dhf :=*; {w:=-1; ++ w:=1;}
         ?(
            ⎵
          ); /* C(w,dhf) */
        }}
        ao :=*;
      }
      {r' = -rv, dhd' = ao, h' = -dhd & (w * dhd >= w * dhf | w * ao >= a)}
   }*
  ] ((h < -hp | h > hp | r < -rp | r> rp) & ⎵)
      """.asFormula)

    import TactixLibrary._

    TactixLibrary.proveBy(acasxexplicit,
      HilbertCalculus.CE(Provable.startProof(equivalence) /*(CommuteEquivRight(SuccPos(0)), 0)*/, shape)(SuccPosition(0))).
      subgoals should contain only (
      new Sequent(Nil, immutable.IndexedSeq(), immutable.IndexedSeq(acasximplicit)),
      new Sequent(Nil, immutable.IndexedSeq(), immutable.IndexedSeq(equivalence))
      )
  }

  it should "derive distributive version of conditional equivalence" in {
    val equivalence = KeYmaeraXProblemParser(io.Source.fromInputStream(getClass.getResourceAsStream("/examples/casestudies/acasx/nodelay-equivalence.key")).mkString)
    val Imply(And(a, w), Equiv(e, i)) = equivalence
    import TactixLibrary._
    val distEquivalence = TactixLibrary.proveBy(Sequent(Nil, IndexedSeq(), IndexedSeq(Equiv(Imply(And(a,w), e), Imply(And(a,w),i)))),
      useAt("-> distributes over <->", PosInExpr(1::Nil))(1))
    distEquivalence.subgoals should contain only Sequent(Nil, IndexedSeq(), IndexedSeq(equivalence))
  }

  it should "derive sequent version of conditional equivalence" in {
    val equivalence = KeYmaeraXProblemParser(io.Source.fromInputStream(getClass.getResourceAsStream("/examples/casestudies/acasx/nodelay-equivalence.key")).mkString)
    val Imply(And(a,w), Equiv(e,i)) = equivalence
    val seqEquivalence = (Provable.startProof(Sequent(Nil, IndexedSeq(a, w), IndexedSeq(Equiv(e,i))))
    (Cut(equivalence), 0)
    // right branch reduces to the proof of "equivalence"
    (CoHideRight(SuccPos(1)), 1)
      // left branch follows from "equivalence"
      (ImplyLeft(AntePos(2)), 0)
      // third branch e<->i |- e<->i
      (Close(AntePos(2), SuccPos(0)), 2)
      // second branch a,w |- e<->i, a&w
      (AndRight(SuccPos(1)), 0)
      // second-right branch a,w |- e<->i, w
      (Close(AntePos(1), SuccPos(1)), 2)
      // second-left branch a,w |- e<->i, a
      (Close(AntePos(0), SuccPos(1)), 0)
      )
    seqEquivalence.subgoals should contain only Sequent(Nil, IndexedSeq(), IndexedSeq(equivalence))
  }

  it should "prove stylized generic region Ce safety from region Ci safety and conditional equivalence" in {
    val shape = Context(
      """  (A()) &
  ( (W(w)) &
      (
        ⎵
      ) /* C(w,dhf) */
  )
  -> [
  {   {
      { ?true; ++
        {dhf :=*; {w:=-1; ++ w:=1;}
         ?(
            ⎵
          ); /* C(w,dhf) */
        }}
        ao :=*;
      }
      {r' = -rv, dhd' = ao, h' = -dhd & (w * dhd >= w * dhf | w * ao >= a)}
   }*
  ] ((h < -hp | h > hp | r < -rp | r> rp) & ⎵)
      """.asFormula)
    val u = "(h < -hp | h > hp | r < -rp | r> rp)".asFormula
    val equivalence = "A()&W(w) -> (Ce(w,dhf/*r,dhd,h,dhf,w,ao*/)<->Ci(w,dhf/*r,dhd,h,dhf,w,ao*/))".asFormula
    val equivalenceP = Provable.startProof(Sequent(Nil, IndexedSeq(), IndexedSeq(equivalence)))
    val Imply(And(a,w), Equiv(e,i)) = equivalence
    // read off more lemmas from equivalence

    //@note same proof of seqEquivalence as in "derive sequent version of conditional equivalence"
    val seqEquivalence = (Provable.startProof(Sequent(Nil, IndexedSeq(a, w), IndexedSeq(Equiv(e,i))))
    (Cut(equivalence), 0)
    // right branch reduces to the proof of "equivalence"
    (CoHideRight(SuccPos(1)), 1)
    // left branch follows from "equivalence"
    (ImplyLeft(AntePos(2)), 0)
    // third branch e<->i |- e<->i
    (Close(AntePos(2), SuccPos(0)), 2)
    // second branch a,w |- e<->i, a&w
    (AndRight(SuccPos(1)), 0)
    // second-right branch a,w |- e<->i, w
    (Close(AntePos(1), SuccPos(1)), 2)
    // second-left branch a,w |- e<->i, a
    (Close(AntePos(0), SuccPos(1)), 0)
    )
    seqEquivalence.subgoals shouldBe equivalenceP.subgoals
    val shuffle = TactixLibrary.proveBy("(A()&W()->(Ce()<->Ci())) -> ((W()->A()->u()&Ci()) <-> (W()->A()->u()&Ce()))".asFormula, prop)
    shuffle shouldBe 'proved
    // (W(w)->A->u&Ci(w,dhf)) <-> (W(w)->A->u&Ce(w,dhf))
    val postEquivalence = TactixLibrary.proveBy(
      Equiv(Imply(w,Imply(a, And(u, i))),
            Imply(w,Imply(a, And(u, e))))
      , useAt(shuffle, PosInExpr(1::Nil))(1)
        & by(seqEquivalence))
    postEquivalence.subgoals shouldBe equivalenceP.subgoals
    val acasximplicit = shape(i)
    val acasxexplicit = shape(e)

    //@note _0 variations in induction :-/
    val w0 = "W(w_0)".asFormula
    val i0 = "Ci(w_0,dhf_0)".asFormula
    val e0 = "Ce(w_0,dhf_0)".asFormula
    val u0 = "(h_0 < -hp | h_0 > hp | r_0 < -rp | r_0> rp)".asFormula

    // (A()&W(w) -> Ce(w,dhf))  <->  (A()&W(w) -> Ci(w,dhf))
    val distEquivalence = TactixLibrary.proveBy(Sequent(Nil, IndexedSeq(), IndexedSeq(Equiv(Imply(And(a,w), e), Imply(And(a,w),i)))),
      useAt("-> distributes over <->", PosInExpr(1::Nil))(1))
    distEquivalence.subgoals shouldBe equivalenceP.subgoals
    val shuffle2 = TactixLibrary.proveBy("(A()&W()->(Ce()<->Ci())) -> ((A()&W() -> Ce() -> q()) <-> (A()&W() -> Ci() -> q()))".asFormula, prop)
    shuffle2 shouldBe 'proved
    // (A()&W(w_0) -> Ce(w_0,dhf_0) -> q())  <->  (A()&W(w_0) -> Ci(w_0,dhf_0) -> q())
    //@todo turn into a lemma:
    val distEquivImpl = (TactixLibrary.proveBy(Sequent(Nil, IndexedSeq(), IndexedSeq(Equiv(Imply(And(a,w0), Imply(e0, "q()".asFormula)), Imply(And(a,w0),Imply(i0,"q()".asFormula))))),
      // //useAt("-> distributes over <->", PosInExpr(1::Nil))(1))
      useAt(shuffle2, PosInExpr(1::Nil))(1)
        & byUS(equivalenceP)))
    distEquivImpl.subgoals shouldBe equivalenceP.subgoals
    println("distEquivImpl " + distEquivImpl)

    // begin actual proof

    acasxexplicit match {
      case Imply(And(aa, And(ww, c)), Box(Loop(_), And(_, c2))) if aa == a && ww == w && c == e && c2 == e =>
      case _ => throw new IllegalArgumentException("Unexpected input shape of explicit file")
    }
    acasximplicit match {
      case Imply(And(aa, And(ww, c)), Box(Loop(_), And(_, c2))) if aa == a && ww == w && c == i && c2 == i =>
      case _ => throw new IllegalArgumentException("Unexpected input shape of implicit file")
    }

    import TactixLibrary._
    TactixLibrary.proveBy(acasxexplicit,
      implyR(1) & andL(-1) &
        postCut(a)(1) & onBranch(
        (BranchLabels.cutShowLbl, label("") & sublabel("A() vacuous") & debug("vacuous global assumptions") & V(1) & close(-1, 1)),

        (BranchLabels.cutUseLbl, label("") & debug("true induction need") &

          postCut(w)(1) & onBranch(
          (BranchLabels.cutShowLbl, label("") & debug("w=-1 | w=1") & assertT(And(w,e), "W&Ce")(-2) & andL(-2) &
            loop(w)(1) & onBranch(
            (BranchLabels.indInitLbl, sublabel("W(w) init") & closeId),

            (BranchLabels.indStepLbl, sublabel("W(w) step") & hide(w)(-4) & hide(w)(-2) & implyR(1) & debug("step w=-1 | w=1") &
              // could also just always generalize(w0)
              // this is a more efficient version
              //@note could have handled 2*composeb(1) at once
              //@todo use W(w_0) instead of W(w) or use post-postcondition
              composeb(1) & generalize(w0)(1) & onBranch(
              (BranchLabels.genShow, V(1) & implyR(1) & closeId),
              (BranchLabels.genUse, composeb(1) & useAt("V[:*] vacuous assign nondet")(SuccPosition(0, 1::Nil)) &
                choiceb(1) & andR(1) & (
                sublabel("& left") & testb(1) & implyR(1) & closeId
                ,
                sublabel("& right") &
                  composeb(1) & composeb(SuccPosition(0, 1::Nil)) & generalize(w0)(1) & onBranch(
                  (BranchLabels.genUse, useAt("V[:*] vacuous assign nondet")(1) & closeId),
                  (BranchLabels.genShow, generalize(w0)(1) & onBranch(
                    (BranchLabels.genShow, V(1) & closeId),
                    (BranchLabels.genUse, master)
                  ))
                )
                )
                )
            )
              ),
            (BranchLabels.indUseCaseLbl, sublabel("W(w) loop use") & implyR(1) & closeId)
          )
            ),

          (BranchLabels.cutUseLbl, sublabel("A()&W(w) augmented") & assertT(And(w,e), "W&Ce")(-2) & andL(-2) & debug("inductive use of A&W") &
            cutL(i)(-3) & onBranch(
            (BranchLabels.cutShowLbl, hide(1) & label("by seq-equiv") & equivifyR(1) & by(seqEquivalence)),
            (BranchLabels.cutUseLbl, sublabel("Ce~>Ci reduction") &
              CE(postEquivalence)(SuccPosition(0, 1::Nil))
              & debug("unpack and repack to replace test") &
              loop(And(w,And(u, i)))(1) & onBranch(
              (BranchLabels.indInitLbl, sublabel("W&u*Ci init") & andR(1) & (close(-2,1) , andR(1) & (label("arith") , close(-3,1)))),
              (BranchLabels.indStepLbl, sublabel("W&u&Ci step") & hide(And(w,And(u,i)))(-4) & hide(i)(-3) & hide(w)(-2) & implyR(1) &
                composeb(1) & composeb(1) & choiceb(1)  // unpack
                //& useAt("[;] compose", PosInExpr(1::Nil))(SuccPosition(0, 1::Nil))  // gather
                & composeb(SuccPosition(0, 1::Nil)) & composeb(SuccPosition(0, 1::1::Nil))
                & debug("cutting explicit dynamics away")
                & cutAt(i0)(SuccPosition(0, 1::1::1::0::0::Nil)) & debug("cuttedAt") & onBranch(
                (BranchLabels.cutShowLbl, sublabel("show patch") & debug("showing patch")
                  & useAt("-> distributes over &", PosInExpr(0::Nil))(1)
                  & andR(1) & (
                  // left branch is unchanged
                  sublabel("cutAt no change on left") & implyR(1) & andL(-3) & closeId
                  ,
                  // right branch replaced implicit with explicit conditionally
                  sublabel("CMon++")
                    & useAt("& commute")(SuccPosition(0, 0::Nil))
                    & useAt("-> weaken", PosInExpr(1::Nil))(1)
                    & label("CMon")
                    // like CMon(PosInExpr(1::1::1::0::0::Nil)) except with context
                    & implyR(1)
                    /*
                    & (choiceb(1, 1::Nil) & choiceb(-3, 1::Nil))
                    & (useAt("[:=] assign")(1, 1::0::Nil) & useAt("[:=] assign")(-3, 1::0::Nil))
                    & (useAt("[:=] assign")(1, 1::1::Nil) & useAt("[:=] assign")(-3, 1::1::Nil))
                    & (randomb(1) & randomb(-3))
                    */
                    // gather
                    & (useAt("[;] compose", PosInExpr(1::Nil))(SuccPosition(0)) & useAt("[;] compose", PosInExpr(1::Nil))(AntePosition(2)))
                    & sublabel("postCut A()&W(w0)")
                    & postCut(And(a,w0))(1) & onBranch(
                    (BranchLabels.cutShowLbl, sublabel("generalize post A()&W(w0)") & hide(-3) & hide(And(w0,And(u0,i0)))(-2) & chase(1) & label("gen") & closeId),
                    (BranchLabels.cutUseLbl, sublabel("generalized A()&W(w0)->post")
                      & HilbertCalculus.testb(1, 1::1::Nil)
                      & debug("do use dist equiv impl")
                      & useAt(distEquivImpl.conclusion.succ.head, PosInExpr(0::Nil))(1, 1::Nil)
                      & debug("used dist equiv impl")
                      & useAt("[?] test", PosInExpr(1::Nil))(1, 1::1::Nil)
                      // drop a&w implication from postcondition again
                      //& useAt("K modal modus ponens", PosInExpr(0::Nil))(1) & implyR(1) & hide(-4)
                      & sublabel("[] post weaken")
                      & debug("do [] post weaken")
                      & useAt("[] post weaken")(1, Nil/*1::1::1::Nil*/)
                      & close(-3, 1)
                      )
                  )
                  )
                  ),
                (BranchLabels.cutUseLbl, sublabel("use patch") & debug("use patch")
                  // repacking
                  & useAt("[;] compose", PosInExpr(1::Nil))(SuccPosition(0, 1::1::Nil)) & useAt("[;] compose", PosInExpr(1::Nil))(SuccPosition(0, 1::Nil))
                  //& useAt("[;] compose", PosInExpr(0::Nil))(SuccPosition(0, 1::Nil))// ungather
                  // repack
                  & useAt("[++] choice", PosInExpr(1::Nil))(1) & useAt("[;] compose", PosInExpr(1::Nil))(1) & useAt("[;] compose", PosInExpr(1::Nil))(1)
                  & label("use patch") & debug("used patch")
                  //@todo by unrolling implicit once
                  //@todo rename acasximplicit to w_0 names ....
                  & cut(acasximplicit.asInstanceOf[Imply].right) & onBranch(
                  (BranchLabels.cutShowLbl,
                    sublabel("show implicit applicable") &
                      hide(1) &
                      cut(acasximplicit) & onBranch(
                      (BranchLabels.cutShowLbl, cohide(2) & sublabel("lookup lemma")),
                      (BranchLabels.cutUseLbl,
                        debug("show implicit applicable") &
                          implyL(-3) & (
                          hide(1) &
                            // prove A()&(W(w)&Ci(w,dhf))
                            andR(1) & (
                            label("A id") & close(-1,1)
                            ,
                            // split W(w)&u&Ci finally
                            andL(-2) & andL(-3) &
                              andR(1) & (
                              label("W(w) id") & close(-2,1)
                              ,
                              andR(1) & (
                                label("arithmetic")
                                ,
                                label("Ci id") & close(-4,1)
                                )
                              )
                            )
                          ,
                          closeId)
                        )
                    )
                    ),
                  (BranchLabels.cutUseLbl, sublabel("by implicit") & useAt("[*] approx")(-3) & close(-3,1))
                )
                  )
              )
                ),
              (BranchLabels.indUseCaseLbl, sublabel("final use") & implyR(1)
                & implyR(1) & implyR(1) & andL(-3)
                & closeId)
            )
              )
          )
            )
        )
          )
      )
    ).
      subgoals should contain only (
      new Sequent(Nil, immutable.IndexedSeq(), immutable.IndexedSeq(acasximplicit)),
      new Sequent(Nil, immutable.IndexedSeq(), immutable.IndexedSeq(equivalence))
      )

  }


  it should "prove explicit region safety from implicit region safety and conditional equivalence" in {
    val acasximplicit = KeYmaeraXProblemParser(io.Source.fromInputStream(getClass.getResourceAsStream("/examples/casestudies/acasx/nodelay_max.key")).mkString)
    val acasxexplicit = KeYmaeraXProblemParser(io.Source.fromInputStream(getClass.getResourceAsStream("/examples/casestudies/acasx/nodelay-explicit.key")).mkString)
    val equivalence = KeYmaeraXProblemParser(io.Source.fromInputStream(getClass.getResourceAsStream("/examples/casestudies/acasx/nodelay-equivalence.key")).mkString)
    val Imply(And(a,w), Equiv(e,i)) = equivalence
    //@note same proof of seqEquivalence as in "derive sequent version of conditional equivalence"
    val seqEquivalence = (Provable.startProof(Sequent(Nil, IndexedSeq(a, w), IndexedSeq(Equiv(e,i)))))

    val distEquivalence = TactixLibrary.proveBy(Sequent(Nil, IndexedSeq(), IndexedSeq(Equiv(Imply(And(a,w), e), Imply(And(a,w),i)))),
      useAt("-> distributes over <->", PosInExpr(1::Nil))(1))
    distEquivalence.subgoals should contain only Sequent(Nil, IndexedSeq(), IndexedSeq(equivalence))


    acasxexplicit match {
      case Imply(And(aa, And(ww, c)), Box(Loop(_), And(_, c2))) if aa == a && ww == w && c == e && c2 == e =>
      case _ => throw new IllegalArgumentException("Unexpected input shape of explicit file")
    }
    acasximplicit match {
      case Imply(And(aa, And(ww, c)), Box(Loop(_), And(_, c2))) if aa == a && ww == w && c == i && c2 == i =>
      case _ => throw new IllegalArgumentException("Unexpected input shape of implicit file")
    }

    import TactixLibrary._
    TactixLibrary.proveBy(acasxexplicit,
      implyR(1) & andL(-1) &
        postCut(a)(1) & onBranch(
        (BranchLabels.cutShowLbl, V(1) & close(-1, 1)),
        (BranchLabels.cutUseLbl, skip)
      )
    ).
      subgoals should contain only (
      new Sequent(Nil, immutable.IndexedSeq(), immutable.IndexedSeq(acasximplicit)),
      new Sequent(Nil, immutable.IndexedSeq(), immutable.IndexedSeq(equivalence))
      )

  }


    /*  "abs_test0" should "be provable" in {
    val s = parseToSequent(getClass.getResourceAsStream("/examples/casestudies/acasx/abs_test0.key"))

    val arith = arithmeticT

    val tactic = ls(ImplyRightT) & debugT("A simple goal with abs") &
      AbsT(AntePosition(0, PosInExpr(0 :: Nil))) & arith

    helper.runTactic(tactic, new RootNode(s)) shouldBe 'closed
  }

  "reflexivity" should "be provable 0" in {
    val arith = arithmeticT
    val s0 = new RootNode(sequent(Nil, Nil, "a=a".asFormula :: Nil))
    helper.runTactic(arith, s0) shouldBe 'closed
  }
  it should "be provable 1" in { //@todo why not?
    val arith = arithmeticT
    val s1 = new RootNode(sequent(Nil, Nil, "f(x)=f(x)".asFormula :: Nil))
    helper.runTactic(arith, s1) shouldBe 'closed
  }
  it should "NOT be provable 1b" in {
    val arith = arithmeticT
    val s1 = new RootNode(sequent(Nil, Nil, "f(x)=g(x)".asFormula :: Nil))
    helper.runTactic(arith, s1).openGoals() should have size 1
  }
  it should "NOT be provable 1c" in {
    val arith = arithmeticT
    val s1 = new RootNode(sequent(Nil, Nil, "f(x)=f(y)".asFormula :: Nil))
    helper.runTactic(arith, s1).openGoals() should have size 1
  }
  it should "NOT be provable 1d" in {
    val arith = arithmeticT
    val s1 = new RootNode(sequent(Nil, Nil, "f(x)=f(x_0)".asFormula :: Nil))
    helper.runTactic(arith, s1).openGoals() should have size 1
  }
  it should "NOT be provable 1e" in {
    val arith = arithmeticT
    val s1 = new RootNode(sequent(Nil, Nil, "f(x_0)=f(x_1)".asFormula :: Nil))
    helper.runTactic(arith, s1).openGoals() should have size 1
  }
  it should "be provable 2" in {
    val arith = arithmeticT
    val s2 = new RootNode(sequent(Nil, "f(x)=y".asFormula :: "1+y=0".asFormula :: Nil, "1+f(x)=0".asFormula :: Nil))
    helper.runTactic(arith, s2) shouldBe 'closed
  }
  it should "be provable 3" in {
    val arith = arithmeticT
    val s3 = new RootNode(sequent(Nil, "f(x)=y".asFormula :: Nil, "f(x)=f(x)".asFormula :: Nil))
    helper.runTactic(arith, s3) shouldBe 'closed
  }
  it should "be provable 4" in { //@todo why not?
    val arith = arithmeticT
    val s4 = new RootNode(sequent(Nil, Nil, "f(x)=y".asFormula  :: "f(x)=f(x)".asFormula :: Nil))
    helper.runTactic(arith, s4) shouldBe 'closed
  }
  it should "NOT be provable 4b" in {
    val arith = arithmeticT
    val s4 = new RootNode(sequent(Nil, Nil, "f(x)=y".asFormula  :: "f(x)=f(y)".asFormula :: Nil))
    helper.runTactic(arith, s4).openGoals() should have size 1
  }
  it should "be provable 5" in { //@todo why not?
    val arith = arithmeticT
    val s5 = new RootNode(sequent(Nil, "!(f(x)=f(x))".asFormula :: "!(f(x)=y)".asFormula :: Nil, Nil))
    helper.runTactic(arith, s5) shouldBe 'closed
  }
  it should "NOT be provable 5b" in {
  val arith = arithmeticT
    val s5 = new RootNode(sequent(Nil, "!(f(x)=f(y))".asFormula :: "!(f(x)=y)".asFormula :: Nil, Nil))
    helper.runTactic(arith, s5).openGoals() should have size 1
  }
*/

  "min and max" should "be parseable" in {
    "min(0, x) <= max(x, 0)".asFormula shouldBe
      LessEqual(
        FuncOf(Function("min", None, Tuple(Real, Real), Real), Pair(Number(0), Variable("x"))),
        FuncOf(Function("max", None, Tuple(Real, Real), Real), Pair(Variable("x"), Number(0)))
      )
  }

  /*
  "true at beginning" should "be provable" in {
    def cutEZ(c:Formula, t:Tactic) = cut(c) & onBranch(
      (cutShowLbl, t | debugT("Cut didn't close") & Tactics.stopT)
    )
    val tactic = debugT("") & cutEZ("!(x>=0) | x>=0".asFormula, ls(cohide, "!(x>=0) | x>=0") & QE) &
      la(orL, "!(x>=0) | x>=0") && (ls(DI), ls(diffSolution(None)) & QE)
    // could be done with DI only if it wasn't for a DI bug (faster: 11 seconds vs. 18 seconds here)
    val s2 = new RootNode(sequent(Nil, "x=y".asFormula :: Nil, "[{x'=2 & (x>=0)}](y>=0)".asFormula :: Nil))
    helper.runTactic(tactic, s2) shouldBe 'closed
  }

  "Bug in DI" should "be provable" in {
    val tactic = ls(DI) & debugT("DW")
    val s2 = new RootNode(sequent(Nil, "x=y".asFormula :: Nil, "[{x'=2 & (x>=0)}](y>=0)".asFormula :: Nil))
    // closes fine if we add y'=0 explicitly
    helper.runTactic(tactic, s2) shouldBe 'closed
  }*/
}
