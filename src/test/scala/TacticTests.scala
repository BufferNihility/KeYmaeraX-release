import edu.cmu.cs.ls.keymaera.tactics.Tactics.Tactic
import edu.cmu.cs.ls.keymaera.tests.TermTests
import org.scalatest._
import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics._
import edu.cmu.cs.ls.keymaera.tools._
import java.math.BigDecimal
import java.io.File

import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary._

class TacticTests extends FlatSpec with Matchers {
  Config.mathlicenses = 1
  Config.maxCPUs = 1
  val math = new Mathematica
  val qet = new JLinkMathematicaLink()
  
  val randomTrials = 10
  val randomFormulaComplexity = 5
  
  val x = Variable("x", None, Real)
  val y = Variable("y", None, Real)

  val zero = Number(new BigDecimal("0"))

  val one = Number(new BigDecimal("1"))

  val xgeq0 = GreaterEquals(Real, x, zero)
  val xgt0 = GreaterThan(Real, x, zero)
  val xplus1 = Add(Real, x, one)
  val xplus1gtx = GreaterThan(Real, xplus1, x)
  
  def num(n : Integer) = Number(new BigDecimal(n.toString()))
  def snum(n : String) = Number(new BigDecimal(n))

  "Tactics" should "learn a lemma from (x > 0 & y > x) -> x >= 0" in {
    val f = TacticLibrary.universalClosure(Imply(And(xgt0, GreaterThan(Real, y, x)), xgeq0))
    qet.qe(f) should be (True)
    LookupLemma.addRealArithLemma(math, f) match {
      case Some((file, id, res)) => 
        (res match {
          case Equiv(_, True) => true
          case _ => false
        }) should be (true)
        val r = new RootNode(new Sequent(Nil, Vector(), Vector()))
        val t = LookupLemma(file,id)
        val nr = r.apply(t).head
        nr.sequent.ante(nr.sequent.ante.length-1) should be (res)
      case None => "Lemma creation" should be ("successful")
    }
  }

  "Tactics" should "learn a lemma from (x > 0 & y = x+1 & y > x) -> (x >= 0 & y > 0)" in {
    val f = TacticLibrary.universalClosure(Imply(And(And(xgt0, Equals(Real, y, xplus1)), GreaterThan(Real, y, x)), And(xgeq0, GreaterThan(Real, y, zero))))
    qet.qe(f) should be (True)
    LookupLemma.addRealArithLemma(math, f) match {
      case Some((file, id, res)) => 
        (res match {
          case Equiv(_, True) => true
          case _ => false
        }) should be (true)
        val r = new RootNode(new Sequent(Nil, Vector(), Vector()))
        val t = LookupLemma(file,id)
        val nr = r.apply(t).head
        nr.sequent.ante(nr.sequent.ante.length-1) should be (res)
      case None => "Lemma creation" should be ("successful")
    }
  }

  "Tactics" should "learn a lemma from (x > 0 & y = x+1 & y > x) -> (y > 0)" in {
    val f = TacticLibrary.universalClosure(Imply(And(And(xgt0, Equals(Real, y, xplus1)), GreaterThan(Real, y, x)), GreaterThan(Real, y, zero)))
    qet.qe(f) should be (True)
    LookupLemma.addRealArithLemma(math, f) match {
      case Some((file, id, res)) => 
        (res match {
          case Equiv(_, True) => true
          case _ => false
        }) should be (true)
        val r = new RootNode(new Sequent(Nil, Vector(), Vector()))
        val t = LookupLemma(file,id)
        val nr = r.apply(t).head
        nr.sequent.ante(nr.sequent.ante.length-1) should be (res)
      case None => "Lemma creation" should be ("successful")
    }
  }

  "Tactics" should "learn a lemma from (x > 0 & y = x+1 & x+1 > x) -> (x+1 > 0)" in {
    val f = TacticLibrary.universalClosure(Imply(And(And(xgt0, Equals(Real, y, xplus1)), GreaterThan(Real, xplus1, x)), GreaterThan(Real, xplus1, zero)))
    qet.qe(f) should be (True)
    LookupLemma.addRealArithLemma(math, f) match {
      case Some((file, id, res)) => 
        (res match {
          case Equiv(_, True) => true
          case _ => false
        }) should be (true)
        val r = new RootNode(new Sequent(Nil, Vector(), Vector()))
        val t = LookupLemma(file,id)
        val nr = r.apply(t).head
        nr.sequent.ante(nr.sequent.ante.length-1) should be (res)
      case None => "Lemma creation" should be ("successful")
    }
  }

  def tryTactic(tactic: Tactic): ProofNode = {
    val x = Variable("x", None, Real)
    val y = Variable("y", None, Real)
    val xp1 = Add(Real, x, Number(1))
    val zero = Number(0)
    val r = new RootNode(new Sequent(Nil, Vector(GreaterThan(Real, x, zero), Equals(Real, y, xp1), Imply(And(GreaterThan(Real, x, zero), Equals(Real, y, xp1)), GreaterThan(Real, xp1, zero))), Vector(GreaterThan(Real, xp1, zero))))
    Tactics.KeYmaeraScheduler.dispatch(new TacticWrapper(tactic, r))
    while(!(Tactics.KeYmaeraScheduler.blocked == Tactics.KeYmaeraScheduler.maxThreads && Tactics.KeYmaeraScheduler.prioList.isEmpty)) {
      Thread.sleep(10)
    }
    r
  }

  def checkSingleAlternative(p: ProofNode): Boolean =
    if(p.children.length > 1) {
      println("found two alternatives " + p.children)
      false
    } else if(p.children.length > 0)
      p.children.head.subgoals.isEmpty || p.children.head.subgoals.foldLeft(false)((a: Boolean, b: ProofNode) => a || checkSingleAlternative(b))
    else
      true

  "Tactics (weakSeqT)*" should "produce a proof with no alternatives" in {
    val tactic = ((AxiomCloseT ~ locateSucc(indecisive(true, false, true)) ~ locateAnte(indecisive(true, false, true, true)))*)
    val r = tryTactic(tactic)
    require(checkSingleAlternative(r) == true, "The proof should not have alternatives")
  }

  "Tactics (eitherT)*" should "produce a proof with no alternatives" in {
    val tactic = ((AxiomCloseT | locateSucc(indecisive(true, false, true)) | locateAnte(indecisive(true, false, true, true)))*)
    val r = tryTactic(tactic)
    require(checkSingleAlternative(r) == true, "The proof should not have alternatives")
  }
  
  sealed abstract class ProvabilityStatus
  object Provable extends ProvabilityStatus
  object NonProvable extends ProvabilityStatus
  object UnknownProvability extends ProvabilityStatus

  /**
   * Run KeYmaera till completion using given tactic for proving given conjecture f.
   *@TODO Implement this stub
   */
  def prove(f:Formula, tactic:Tactic = TacticLibrary.default) : ProvabilityStatus = {
    val r = new RootNode(new Sequent(Nil, Vector(), Vector(f)))
    Tactics.KeYmaeraScheduler.dispatch(new TacticWrapper(tactic, r))
    while(!(Tactics.KeYmaeraScheduler.blocked == Tactics.KeYmaeraScheduler.maxThreads
      && Tactics.KeYmaeraScheduler.prioList.isEmpty
      && Tactics.MathematicaScheduler.blocked == Tactics.MathematicaScheduler.maxThreads
      && Tactics.MathematicaScheduler.prioList.isEmpty)) {
      Thread.sleep(100)
//      println("Blocked " + Tactics.KeYmaeraScheduler.blocked + " of " + Tactics.KeYmaeraScheduler.maxThreads)
//      println("Tasks open: " + Tactics.KeYmaeraScheduler.prioList.length)
//      println("Blocked on Mathematica: " + Tactics.MathematicaScheduler.blocked + " of " + Tactics.MathematicaScheduler.maxThreads)
//      println("Tasks open Mathematica: " + Tactics.MathematicaScheduler.prioList.length)
    }
    if(checkClosed(r)) Provable else {println(TermTests.print(r)); UnknownProvability}
  }

  def checkClosed(n: ProofNode): Boolean =
    n.children.map((f: ProofStep) =>  f.subgoals.foldLeft(true)(_ && checkClosed(_))).contains(true)

  
  /**
   * Tactic that applies propositional proof rules exhaustively but only closes by axiom lazyly, i.e. if no other rule applies.
   *@TODO Implement for real. This strategy uses more than propositional steps.
   */
  def lazyPropositional = ((locate(indecisive(true, false, false, true)) | closeT)*)

  "Tactics (propositional)" should "prove A->A for any A" in {
    val tactic = lazyPropositional
    for (i <- 1 to randomTrials) {
      val A = new RandomFormula().nextFormula(randomFormulaComplexity)
      val formula = Imply(A, A)
      prove(formula, tactic) should be (Provable)
    }
  }

  it should "prove A->(B->A) for any A,B" in {
    val tactic = lazyPropositional
    for (i <- 1 to randomTrials) {
      val A = new RandomFormula().nextFormula(randomFormulaComplexity)
      val B = new RandomFormula().nextFormula(randomFormulaComplexity)
      val formula = Imply(A, Imply(B, A))
      prove(formula, tactic) should be (Provable)
    }
  }

  it should "prove (A->(B->C)) <-> ((A&B)->C) for any A,B,C" in {
    val tactic = lazyPropositional
    for (i <- 1 to randomTrials) {
      val A = new RandomFormula().nextFormula(randomFormulaComplexity)
      val B = new RandomFormula().nextFormula(randomFormulaComplexity)
      val C = new RandomFormula().nextFormula(randomFormulaComplexity)
      val formula = Equiv(Imply(A, Imply(B, C)), Imply(And(A,B),C))
      prove(formula, tactic) should be (Provable)
    }
  }

  it should "prove (~A->A) -> A for any A" in {
    val tactic = lazyPropositional
    for (i <- 1 to randomTrials) {
      val A = new RandomFormula().nextFormula(randomFormulaComplexity)
      val formula = Imply(Imply(Not(A),A),A)
      prove(formula, tactic) should be (Provable)
    }
  }
  
  it should "prove (A->B) && (C->D) |= (A&C)->(B&D) for any A,B,C,D" in {
    val tactic = lazyPropositional
    for (i <- 1 to randomTrials) {
      val A = new RandomFormula().nextFormula(randomFormulaComplexity)
      val B = new RandomFormula().nextFormula(randomFormulaComplexity)
      val C = new RandomFormula().nextFormula(randomFormulaComplexity)
      val D = new RandomFormula().nextFormula(randomFormulaComplexity)
      val formula = Imply(And(Imply(A,B),Imply(C,D)) , Imply(And(A,C),And(B,D)))
      prove(formula, tactic) should be (Provable)
    }
  }
  
  it should "prove ((A->B)->A)->A for any A,B" in {
    val tactic = lazyPropositional
    for (i <- 1 to randomTrials) {
      val A = new RandomFormula().nextFormula(randomFormulaComplexity)
      val B = new RandomFormula().nextFormula(randomFormulaComplexity)
      val formula = Imply(Imply(Imply(A,B),A),A)
      prove(formula, tactic) should be (Provable)
    }
  }
  
  "Tactics (default)" should "prove a>0 -> [x:=77]a>0" in {
    val x = Variable("x", None, Real)
    val a = Variable("a", None, Real)
    val formula = Imply(GreaterThan(Real, a,Number(0)),
      BoxModality(Assign(x, Number(77)), GreaterThan(Real, a,Number(0))))
    prove(formula) should be (Provable)
  }
  
  it should "prove a>0 -> [x:=x+1]a>0" in {
    val x = Variable("x", None, Real)
    val a = Variable("a", None, Real)
    val formula = Imply(GreaterThan(Real, a,Number(0)),
      BoxModality(Assign(x, Add(Real, x,Number(1))), GreaterThan(Real, a,Number(0))))
    prove(formula) should be (Provable)
  }

  it should "prove z>0 -> [y:=y+1]z>0" in {
    val z = Variable("z", None, Real)
    val y = Variable("y", None, Real)
    val formula = Imply(GreaterThan(Real, z,Number(0)),
      BoxModality(Assign(y, Add(Real, y,Number(1))), GreaterThan(Real, z,Number(0))))
    prove(formula) should be (Provable)
  }

  it should "prove x>0 -> [x:=x+1]x>1" in {
    val x = Variable("x", None, Real)
    val formula = Imply(GreaterThan(Real, x,Number(0)),
      BoxModality(Assign(x, Add(Real, x,Number(1))), GreaterThan(Real, x,Number(1))))
    prove(formula) should be (Provable)
  }
  
  it should "prove z>0 -> [z:=z+1]z>1" in {
    val x = Variable("z", None, Real)
    val formula = Imply(GreaterThan(Real, x,Number(0)),
      BoxModality(Assign(x, Add(Real, x,Number(1))), GreaterThan(Real, x,Number(1))))
    prove(formula) should be (Provable)
  }

  it should "prove x>0 -> [y:=x; x:=y+1; ](x>y & y>0)" in {
    val x = Variable("x", None, Real)
    val y = Variable("y", None, Real)
    val formula = Imply(GreaterThan(Real, x,Number(0)),
      BoxModality(Sequence(Assign(y, x), Assign(x, Add(Real, y,Number(1)))), And(GreaterThan(Real, x,y), GreaterThan(Real, y, Number(0)))))
    prove(formula) should be (Provable)
  }

  it should "prove x>0 -> [x:=x+1;y:=x-1 ++ y:=x; x:=y+1; ](x>y & y>0)" in {
    val x = Variable("x", None, Real)
    val y = Variable("y", None, Real)
    val formula = Imply(GreaterThan(Real, x,Number(0)),
      BoxModality(Choice(Sequence(Assign(x,Add(Real,x,Number(1))),Assign(y,Subtract(Real,x,Number(1)))),
        Sequence(Assign(y, x), Assign(x, Add(Real, y,Number(1))))), And(GreaterThan(Real, x,y), GreaterThan(Real, y, Number(0)))))
    prove(formula) should be (Provable)
  }

  it should "not prove invalid x>0 -> [x:=x+1;y:=x ++ y:=x; x:=y+1; ](x>y & y>0)" in {
    val x = Variable("x", None, Real)
    val y = Variable("y", None, Real)
    val formula = Imply(GreaterThan(Real, x,Number(0)),
      BoxModality(Choice(Sequence(Assign(x,Add(Real,x,Number(1))),Assign(y,x)),
        Sequence(Assign(y, x), Assign(x, Add(Real, y,Number(1))))), And(GreaterThan(Real, x,y), GreaterThan(Real, y, Number(0)))))
    prove(formula) should not be (Provable)
  }

  //@TODO Implement
  def unsoundUniformSubstitution(assume : Formula, conclude : Formula, s: Substitution) = Provable

  //@TODO Move the subsequent tests to UniformSubstitutionTest.scala
  "Uniform Substitution" should "not apply unsoundly to [y:=5;x:=2]p(x)<->p(2) with .>y for p(.)" in {
    val x = Variable("x", None, Real)
    val y = Variable("y", None, Real)
    val p1 = Function("p", None, Real, Bool)
    val assume = Equiv(BoxModality(Sequence(Assign(y, Number(5)), Assign(x, Number(2))), ApplyPredicate(p1, x)),
      ApplyPredicate(p1, Number(2)))
    val conclude = Equiv(BoxModality(Sequence(Assign(y, Number(5)), Assign(x, Number(2))), GreaterThan(Real,x,y)),
      GreaterThan(Real, Number(2), y))
    val l = Variable("l", None, Real)
    unsoundUniformSubstitution(assume, conclude,
      new Substitution(List(
      new SubstitutionPair(ApplyPredicate(p1,l), GreaterThan(Real,l,y))))) should not be (Provable)

    unsoundUniformSubstitution(assume, conclude,
      new Substitution(List(
      new SubstitutionPair(ApplyPredicate(p1,x), GreaterThan(Real,x,y))))) should not be (Provable)
    
    unsoundUniformSubstitution(assume, conclude,
      new Substitution(List(
      new SubstitutionPair(ApplyPredicate(p1,y), GreaterThan(Real,y,y))))) should not be (Provable)
  }

  it should "not apply unsoundly to [y:=5;x:=x^2]p(x+y)<->p(x^2+5) with y>=. for p(.)" in {
    val x = Variable("x", None, Real)
    val y = Variable("y", None, Real)
    val p1 = Function("p", None, Real, Bool)
    val assume = Equiv(BoxModality(Sequence(Assign(y, Number(5)), Assign(x, Exp(Real,x, Number(2)))), ApplyPredicate(p1, Add(Real,x,y))),
      ApplyPredicate(p1, Add(Real,Exp(Real,x,Number(2)),Number(5))))
    val conclude = Equiv(BoxModality(Sequence(Assign(y, Number(5)), Assign(x, Exp(Real,x, Number(2)))), GreaterEquals(Real,y, Add(Real,x,y))),
        GreaterEquals(Real,y, Add(Real,Exp(Real,x,Number(2)),Number(5))))
    val l = Variable("l", None, Real)
    unsoundUniformSubstitution(assume, conclude,
      new Substitution(List(
      new SubstitutionPair(ApplyPredicate(p1,l), GreaterEquals(Real,y,l))))) should not be (Provable)

    unsoundUniformSubstitution(assume, conclude,
      new Substitution(List(
      new SubstitutionPair(ApplyPredicate(p1,x), GreaterEquals(Real,y,x))))) should not be (Provable)
    
    unsoundUniformSubstitution(assume, conclude,
      new Substitution(List(
      new SubstitutionPair(ApplyPredicate(p1,y), GreaterEquals(Real,y,y))))) should not be (Provable)
  }

  it should "not apply unsoundly to [x:=x+1]p(x)<->p(x+1) with .>0&\\exists x. x<. for p(.)" in {
    val x = Variable("x", None, Real)
    val y = Variable("y", None, Real)
    val p1 = Function("p", None, Real, Bool)
    val assume = Equiv(BoxModality(Assign(x, Add(Real,x,Number(1))), ApplyPredicate(p1, x)),
      ApplyPredicate(p1, Add(Real,x, Number(1))))
    val conclude = Equiv(BoxModality(Assign(x, Add(Real,x,Number(1))), And(GreaterThan(Real,x,Number(0)),Exists(Seq(x),LessThan(Real,x,x)))),
      And(GreaterThan(Real,Add(Real,x,Number(1)),Number(0)),Exists(Seq(x),LessThan(Real,x,Add(Real,x,Number(1))))))
    val l = Variable("l", None, Real)
    unsoundUniformSubstitution(assume, conclude,
      new Substitution(List(
      new SubstitutionPair(ApplyPredicate(p1,l), And(GreaterThan(Real,l,Number(0)),Exists(Seq(x),LessThan(Real,x,l))))))) should not be (Provable)

    unsoundUniformSubstitution(assume, conclude,
      new Substitution(List(
      new SubstitutionPair(ApplyPredicate(p1,x), And(GreaterThan(Real,x,Number(0)),Exists(Seq(x),LessThan(Real,x,x))))))) should not be (Provable)
    
    unsoundUniformSubstitution(assume, conclude,
      new Substitution(List(
      new SubstitutionPair(ApplyPredicate(p1,y), And(GreaterThan(Real,y,Number(0)),Exists(Seq(x),LessThan(Real,x,y))))))) should not be (Provable)
  }
}
