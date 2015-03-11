import edu.cmu.cs.ls.keymaera.core._
import org.scalatest._
import testHelper.StringConverter
import scala.collection.immutable.List
import StringConverter._

import scala.collection.immutable.Seq
import scala.collection.immutable.IndexedSeq

object USubstTest extends Tag("USubstTest")

/**
 * @author aplatzer
 * @author smitsch
 */

class USubstTests extends FlatSpec with Matchers {

  val randomTrials = 40
  val randomComplexity = 20
  val rand = new RandomFormula()


  val x = Variable("x", None, Real)
  val p0 = ApplyPredicate(Function("p", None, Unit, Bool), Nothing)
  val p1 = Function("p", None, Real, Bool)
  val pn = Function("p", None, Real, Bool)
  val ap = ProgramConstant("a")

  "Uniform substitution" should "clash when using [:=] for a substitution with a free occurrence of a bound variable" taggedAs USubstTest in {
    val fn = Apply(Function("f", None, Unit, Real), Nothing)
    val prem = Equiv(
      BoxModality("x:=f();".asProgram, ApplyPredicate(p1, "x".asTerm)),
      ApplyPredicate(p1, fn)) // axioms.axiom("[:=])
    val conc = "[x:=x+1;]x!=x <-> x+1!=x".asFormula
    val s = Substitution(Seq(SubstitutionPair(ApplyPredicate(p1, CDot), NotEquals(Real, CDot, "x".asTerm)),
      SubstitutionPair(fn, "x+1".asTerm)))
    a [SubstitutionClashException] should be thrownBy UniformSubstitution(s,
      Sequent(Seq(), IndexedSeq(), IndexedSeq(prem)))(
      Sequent(Seq(), IndexedSeq(), IndexedSeq(conc)))
  }
  
  it should "clash when using [:=] for a substitution with a free occurrence of a bound variable for constants" taggedAs USubstTest in {
    val fn = Apply(Function("f", None, Unit, Real), Nothing)
    val prem = Equiv(
      BoxModality("x:=f();".asProgram, ApplyPredicate(p1, "x".asTerm)),
      ApplyPredicate(p1, fn)) // axioms.axiom("[:=])
    val conc = "[x:=0;]x=x <-> 0=x".asFormula
    val s = Substitution(Seq(SubstitutionPair(ApplyPredicate(p1, CDot), Equals(Real, CDot, "x".asTerm)),
      SubstitutionPair(fn, "0".asTerm)))
    a [SubstitutionClashException] should be thrownBy UniformSubstitution(s,
      Sequent(Seq(), IndexedSeq(), IndexedSeq(prem)))(
      Sequent(Seq(), IndexedSeq(), IndexedSeq(conc)))
  }

  /* TODO programs where not all branches write the same variables are not yet supported */
  ignore should "handle nontrivial binding structures" taggedAs USubstTest in {
    val fn = Apply(Function("f", None, Unit, Real), Nothing)
    val prem = Equiv(
      BoxModality("x:=f();".asProgram, ApplyPredicate(p1, "x".asTerm)),
      ApplyPredicate(p1, fn)) // axioms.axiom("[:=])
    val conc = "[x:=x^2;][{y:=y+1++{z:=x+z;}*}; z:=x+y*z;]y>x <-> [{y:=y+1++{z:=x^2+z;}*}; z:=x^2+y*z;]y>x^2".asFormula

    val y = Variable("y", None, Real)
    val z = Variable("z", None, Real)
    val s = Substitution(Seq(
      // [{y:=y+1++{z:=.+z}*}; z:=.+y*z]y>.
      SubstitutionPair(ApplyPredicate(p1, CDot), BoxModality(
        Sequence(
          Choice(
            Assign(y, Add(Real, y, Number(1))),
            Loop(Assign(z, Add(Real, CDot, z)))
          ),
          Assign(z, Add(Real, CDot, Multiply(Real, y, z)))),
        GreaterThan(Real, y, CDot))),
      SubstitutionPair(fn, "x^2".asTerm)))
    UniformSubstitution(s, Sequent(Seq(), IndexedSeq(), IndexedSeq(prem)))(Sequent(Seq(), IndexedSeq(), IndexedSeq(conc))) should be (List(Sequent(Seq(), IndexedSeq(), IndexedSeq(prem))))
  }

  it should "clash when using vacuous all quantifier \\forall x . for a postcondition x>=0 with a free occurrence of the bound variable" taggedAs USubstTest in {
    val fml = GreaterEqual(Real, x, Number(0))
    //@TODO val prem = Axioms.axioms("vacuous all quantifier")
    val prem = Imply(p0, Forall(Seq(x), p0))
    val conc = Forall(Seq(x), fml)
    val s = Substitution(Seq(SubstitutionPair(p0, fml)))
    a [SubstitutionClashException] should be thrownBy UniformSubstitution(s,
      Sequent(Seq(), IndexedSeq(), IndexedSeq(prem)))(
    Sequent(Seq(), IndexedSeq(), IndexedSeq(conc)))
  }
  
  it should "clash when using V on x:=x-1 for a postcondition x>=0 with a free occurrence of a bound variable" taggedAs USubstTest in {
    val fml = GreaterEqual(Real, x, Number(0))
    //@TODO val prem = Axioms.axioms("V vacuous")
    val prem = Imply(p0, BoxModality(ap, p0)) //"p->[a;]p".asFormula
    val prog = Assign(x, Subtract(Real, x, Number(1)))
    val conc = BoxModality(prog, fml)
    val s = Substitution(Seq(SubstitutionPair(p0, fml),
      SubstitutionPair(ap, prog)))
    a [SubstitutionClashException] should be thrownBy UniformSubstitution(s,
      Sequent(Seq(), IndexedSeq(), IndexedSeq(prem)))(
      Sequent(Seq(), IndexedSeq(), IndexedSeq(conc)))
  }
  
  
  // uniform substitution of rules
  
  "Uniform substitution of rules" should "instantiate Goedel from (-x)^2>=0 (I)" taggedAs USubstTest in {
    val fml = GreaterEqual(Real, Exp(Real, Neg(Real, x), Number(2)), Number(0))
    val prog = Assign(x, Subtract(Real, x, Number(1)))
    val conc = BoxModality(prog, fml)
    val s = Substitution(Seq(SubstitutionPair(ApplyPredicate(p1, Anything), fml),
      SubstitutionPair(ap, prog)))
    AxiomaticRule("Goedel", s)(
      Sequent(Seq(), IndexedSeq(), IndexedSeq(conc))) should be (List(Sequent(Seq(), IndexedSeq(), IndexedSeq(fml))))
  }
  
  it should "instantiate Goedel from (-x)^2>=0 (II)" taggedAs USubstTest in {
    val fml = "(-x)^2>=0".asFormula
    val prog = "x:=x-1;".asProgram
    val s = Substitution(
      SubstitutionPair(ApplyPredicate(p1, Anything), fml) ::
      SubstitutionPair(ap, prog) :: Nil)
    AxiomaticRule("Goedel", s)(
      Sequent(Seq(), IndexedSeq(), IndexedSeq(BoxModality(prog, fml)))) should be (List(Sequent(Seq(), IndexedSeq(), IndexedSeq(fml))))
  }
  
  it should "instantiate nontrivial binding structures in [] congruence" taggedAs USubstTest in {
      val prem = "(-x)^2>=y <-> x^2>=y".asFormula
      val conc = "[{y:=y+1++{z:=x+z;}*}; z:=x+y*z;](-x)^2>=y <-> [{y:=y+1++{z:=x+z;}*}; z:=x+y*z;]x^2>=y".asFormula

      val prog = "{y:=y+1++{z:=x+z;}*}; z:=x+y*z;".asProgram
      val q = Function("q", None, Real, Bool)
      val s = Substitution(Seq(
        SubstitutionPair(ap, prog),
        SubstitutionPair(ApplyPredicate(pn, Anything), "(-x)^2>=y".asFormula),
        SubstitutionPair(ApplyPredicate(q, Anything), "x^2>=y".asFormula)
         ))
        AxiomaticRule("[] congruence", s)(
          Sequent(Seq(), IndexedSeq(), IndexedSeq(conc))) should be (List(Sequent(Seq(), IndexedSeq(), IndexedSeq(prem))))
    }

    it should "instantiate random programs in [] monotone" taggedAs USubstTest in {
      for (i <- 1 to randomTrials) {
        val prem1 = "(-z1)^2>=z4".asFormula
        val prem2 = "z4<=z1^2".asFormula
        val prog = rand.nextProgram(randomComplexity)
        val concLhs = BoxModality(prog, prem1)
        val concRhs = BoxModality(prog, prem2)

        val q = Function("q", None, Real, Bool)
        val s = Substitution(Seq(
          SubstitutionPair(ap, prog),
          SubstitutionPair(ApplyPredicate(pn, Anything), prem1),
          SubstitutionPair(ApplyPredicate(q, Anything), prem2)
           ))
          AxiomaticRule("[] monotone", s)(Sequent(Seq(), IndexedSeq(concLhs), IndexedSeq(concRhs))) should contain only
            Sequent(Seq(), IndexedSeq(prem1), IndexedSeq(prem2))
      }
    }

    it should "instantiate random programs in [] congruence" taggedAs USubstTest in {
      for (i <- 1 to randomTrials) {
        val prem1 = "(-z1)^2>=z4".asFormula
        val prem2 = "z4<=z1^2".asFormula
        val prem = Equiv(prem1, prem2)
        val prog = rand.nextProgram(randomComplexity)
        val conc = Equiv(BoxModality(prog, prem1), BoxModality(prog, prem2))

        val q = Function("q", None, Real, Bool)
        val s = Substitution(Seq(
          SubstitutionPair(ap, prog),
          SubstitutionPair(ApplyPredicate(pn, Anything), prem1),
          SubstitutionPair(ApplyPredicate(q, Anything), prem2)
           ))
          AxiomaticRule("[] congruence", s)(
            Sequent(Seq(), IndexedSeq(), IndexedSeq(conc))) should contain only Sequent(Seq(), IndexedSeq(), IndexedSeq(prem))
      }
    }

    it should "instantiate random programs in <> congruence" taggedAs USubstTest in {
      for (i <- 1 to randomTrials) {
        val prem1 = "(-z1)^2>=z4".asFormula
        val prem2 = "z4<=z1^2".asFormula
        val prem = Equiv(prem1, prem2)
        val prog = rand.nextProgram(randomComplexity)
        val conc = Equiv(DiamondModality(prog, prem1), DiamondModality(prog, prem2))

        val q = Function("q", None, Real, Bool)
        val s = Substitution(Seq(
          SubstitutionPair(ap, prog),
          SubstitutionPair(ApplyPredicate(pn, Anything), prem1),
          SubstitutionPair(ApplyPredicate(q, Anything), prem2)
           ))
          AxiomaticRule("<> congruence", s)(
            Sequent(Seq(), IndexedSeq(), IndexedSeq(conc))) should contain only Sequent(Seq(), IndexedSeq(), IndexedSeq(prem))
      }
    }

    it should "instantiate random programs in <> monotone" taggedAs USubstTest in {
      for (i <- 1 to randomTrials) {
        val prem1 = "(-z1)^2>=z4".asFormula
        val prem2 = "z4<=z1^2".asFormula
        val prog = rand.nextProgram(randomComplexity)
        val concLhs = DiamondModality(prog, prem1)
        val concRhs = DiamondModality(prog, prem2)

        val q = Function("q", None, Real, Bool)
        val s = Substitution(Seq(
          SubstitutionPair(ap, prog),
          SubstitutionPair(ApplyPredicate(pn, Anything), prem1),
          SubstitutionPair(ApplyPredicate(q, Anything), prem2)
           ))
          AxiomaticRule("<> monotone", s)(
            Sequent(Seq(), IndexedSeq(concLhs), IndexedSeq(concRhs))) should contain only Sequent(Seq(), IndexedSeq(prem1), IndexedSeq(prem2))
      }
    }

    it should "instantiate random programs in Goedel" taggedAs USubstTest in {
      for (i <- 1 to randomTrials) {
        val prem = "(-z1)^2>=0".asFormula
        val prog = rand.nextProgram(randomComplexity)
        val conc = BoxModality(prog, prem)

        val s = Substitution(Seq(
          SubstitutionPair(ap, prog),
          SubstitutionPair(ApplyPredicate(pn, Anything), prem)
           ))
          AxiomaticRule("Goedel", s)(
            Sequent(Seq(), IndexedSeq(), IndexedSeq(conc))) should contain only Sequent(Seq(), IndexedSeq(), IndexedSeq(prem))
      }
    }
}