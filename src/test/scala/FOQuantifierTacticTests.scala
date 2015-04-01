import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics.{FOQuantifierTacticsImpl, Interpreter, Tactics}
import edu.cmu.cs.ls.keymaera.tests.ProvabilityTestHelper
import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}
import testHelper.ProofFactory._
import testHelper.SequentFactory._
import testHelper.StringConverter._
import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary.{locateSucc,locateAnte,alphaRenamingT}
import edu.cmu.cs.ls.keymaera.tactics.FOQuantifierTacticsImpl.{uniquify,instantiateExistentialQuanT,
  instantiateUniversalQuanT,instantiateT,existentialGenT,vacuousExistentialQuanT,vacuousUniversalQuanT,decomposeQuanT}

import scala.collection.immutable.Map

/**
 * Created by smitsch on 1/31/15.
 * @author Stefan Mitsch
 */
class FOQuantifierTacticTests extends FlatSpec with Matchers with BeforeAndAfterEach {
  // TODO mathematica is only necessary because of ProofFactory -> make ProofFactory configurable

  val helper = new ProvabilityTestHelper((x) => println(x))
  val mathematicaConfig : Map[String, String] = Map("linkName" -> "/Applications/Mathematica.app/Contents/MacOS/MathKernel")

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

  "Uniquify" should "rename universally quantified variables" in {
    val tactic = locateSucc(uniquify)
    getProofSequent(tactic, new RootNode(sucSequent("\\forall y. y>0".asFormula))) should be (
      sucSequent("\\forall y_0. y_0>0".asFormula))
  }

  it should "select a fresh variable when renaming universally quantified variables" in {
    val tactic = locateSucc(uniquify)
    getProofSequent(tactic, new RootNode(sucSequent("\\forall y. [y_0:=y;]y_0>0".asFormula))) should be (
      sucSequent("\\forall y_1. [y_0:=y_1;]y_0>0".asFormula))
  }

  ignore should "rename assignments" in {
    val tactic = locateSucc(uniquify)
    getProofSequent(tactic, new RootNode(sucSequent("[y:=1;]y>0".asFormula))) should be (
      sucSequent("[y_0:=1;]y_0>0".asFormula))
  }

  ignore should "rename nondeterministic assignments" in {
    // uniquify now only needs to work on quantifiers
    val tactic = locateSucc(uniquify)
    getProofSequent(tactic, new RootNode(sucSequent("[y:=*;]y>0".asFormula))) should be (
      sucSequent("[y_0:=*;]y_0>0".asFormula))
  }

  ignore should "select a fresh variable when renaming assignments" in {
    val tactic = locateSucc(uniquify)
    getProofSequent(tactic, new RootNode(sucSequent("[y:=1;][y_0:=y;]y_0>0".asFormula))) should be (
      sucSequent("[y_1:=1;][y_0:=y_1;]y_0>0".asFormula))
  }

  ignore should "select a fresh variable when renaming nondeterministic assignments" in {
    val tactic = locateSucc(uniquify)
    getProofSequent(tactic, new RootNode(sucSequent("[y:=*;][y_0:=y;]y_0>0".asFormula))) should be (
      sucSequent("[y_1:=*;][y_0:=y_1;]y_0>0".asFormula))
  }

  "Quantifier instantiation of universal quantifier" should "instantiate simple predicate" in {
    val tactic = locateAnte(instantiateUniversalQuanT(Variable("x", None, Real), "z".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall x. x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "z>0".asFormula :: Nil, Nil))
  }

  it should "instantiate assignment modality" in {
    val tactic = locateAnte(instantiateUniversalQuanT(Variable("x", None, Real), "z".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall x. [y:=x;][y:=2;]y>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "[y:=z;][y:=2;]y>0".asFormula :: Nil, Nil))
  }

  it should "instantiate assignment modality 2" in {
    val tactic = locateAnte(instantiateUniversalQuanT(Variable("y", None, Real), "z+1".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall y. [y:=y+1;]y>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "[y:=z+1+1;]y>0".asFormula :: Nil, Nil))
  }

  it should "instantiate ODE modality" in {
    val tactic = locateAnte(instantiateUniversalQuanT(Variable("x", None, Real), "z".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall x. [y'=x;]y>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "[y'=z;]y>0".asFormula :: Nil, Nil))
  }

  it should "instantiate more complicated ODE modality" in {
    val tactic = locateAnte(instantiateT(Variable("y", None, Real), "z".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall y. [y'=x & y>2;]y>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "[z'=x & z>2;]z>0".asFormula :: Nil, Nil))
  }

  it should "instantiate even if ODE modality follows in some subformula" in {
    val tactic = locateAnte(instantiateT(Variable("y", None, Real), "z".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall y. (y=0 -> [y'=x & y>2;]y>0)".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "z=0 -> [z'=x & z>2;]z>0".asFormula :: Nil, Nil))
  }

  it should "instantiate assignment irrespective of what follows" in {
    val tactic = locateAnte(instantiateUniversalQuanT(Variable("x", None, Real), "z".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall x. [y:=x;][y'=1;]y>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "[y:=z;][y'=1;]y>0".asFormula :: Nil, Nil))
  }

  "Quantifier instantiation of existential quantifier" should "instantiate simple predicate" in {
    val tactic = locateSucc(instantiateExistentialQuanT(Variable("x", None, Real), "z".asTerm))
    getProofSequent(tactic, new RootNode(sucSequent("\\exists x. x>0".asFormula))) should be (
      sucSequent("z>0".asFormula))
  }

  it should "instantiate assignment modality" in {
    val tactic = locateSucc(instantiateExistentialQuanT(Variable("x", None, Real), "z".asTerm))
    getProofSequent(tactic, new RootNode(sucSequent("\\exists x. [y:=x;][y:=2;]y>0".asFormula))) should be (
      sucSequent("[y:=z;][y:=2;]y>0".asFormula))
  }

  it should "instantiate ODE modality" in {
    val tactic = locateSucc(instantiateExistentialQuanT(Variable("x", None, Real), "z".asTerm))
    getProofSequent(tactic, new RootNode(sucSequent("\\exists x. [y'=x;]y>0".asFormula))) should be (
      sucSequent("[y'=z;]y>0".asFormula))
  }

  it should "instantiate assignment irrespective of what follows" in {
    val tactic = locateSucc(instantiateExistentialQuanT(Variable("x", None, Real), "z".asTerm))
    getProofSequent(tactic, new RootNode(sucSequent("\\exists x. [y:=x;][y'=1;]y>0".asFormula))) should be (
      sucSequent("[y:=z;][y'=1;]y>0".asFormula))
  }

  it should "instantiate at one-below-top positions in propositional formulas" in {
    val tactic = instantiateExistentialQuanT(Variable("x", None, Real), Variable("z", None, Real))
    getProofSequent(tactic(SuccPosition(0, PosInExpr(1::Nil))),
      new RootNode(sucSequent("y>0 & \\exists x. x=5".asFormula))) should be (sucSequent("y>0 & z=5".asFormula))

    getProofSequent(tactic(SuccPosition(0, PosInExpr(0::Nil))),
      new RootNode(sucSequent("(\\exists x. x=5) & y>0".asFormula))) should be (sucSequent("z=5 & y>0".asFormula))

    getProofSequent(tactic(SuccPosition(0, PosInExpr(1::Nil))),
      new RootNode(sucSequent("y>0 | \\exists x. x=5".asFormula))) should be (sucSequent("y>0 | z=5".asFormula))

    getProofSequent(tactic(SuccPosition(0, PosInExpr(0::Nil))),
      new RootNode(sucSequent("(\\exists x. x=5) | y>0".asFormula))) should be (sucSequent("z=5 | y>0".asFormula))

    getProofSequent(tactic(SuccPosition(0, PosInExpr(1::Nil))),
      new RootNode(sucSequent("y>0 -> \\exists x. x=5".asFormula))) should be (sucSequent("y>0 -> z=5".asFormula))
  }

  it should "instantiate anywhere in propositional formulas when existential quantifier has positive polarity" in {
    val tactic = instantiateExistentialQuanT(Variable("x", None, Real), Variable("z", None, Real))
    getProofSequent(tactic(SuccPosition(0, PosInExpr(1 :: 1 :: Nil))),
      new RootNode(sucSequent("y>0 & (y>2 & \\exists x. x=5)".asFormula))) should be(sucSequent("y>0 & (y>2 & z=5)".asFormula))

    getProofSequent(tactic(SuccPosition(0, PosInExpr(1 :: 1 :: Nil))),
      new RootNode(sucSequent("y>0 & (y>2 | \\exists x. x=5)".asFormula))) should be(sucSequent("y>0 & (y>2 | z=5)".asFormula))

    getProofSequent(tactic(SuccPosition(0, PosInExpr(1 :: 1 :: Nil))),
      new RootNode(sucSequent("y>0 & (y>2 -> \\exists x. x=5)".asFormula))) should be(sucSequent("y>0 & (y>2 -> z=5)".asFormula))

    getProofSequent(tactic(SuccPosition(0, PosInExpr(1 :: 1 :: Nil))),
      new RootNode(sucSequent("y>0 | (y>2 & \\exists x. x=5)".asFormula))) should be(sucSequent("y>0 | (y>2 & z=5)".asFormula))

    getProofSequent(tactic(SuccPosition(0, PosInExpr(1 :: 1 :: Nil))),
      new RootNode(sucSequent("y>0 -> (y>2 & \\exists x. x=5)".asFormula))) should be(sucSequent("y>0 -> (y>2 & z=5)".asFormula))

    getProofSequent(tactic(SuccPosition(0, PosInExpr(1 :: 1 :: Nil))),
      new RootNode(sucSequent("y>0 -> (y>2 -> \\exists x. x=5)".asFormula))) should be(sucSequent("y>0 -> (y>2 -> z=5)".asFormula))

    getProofSequent(tactic(SuccPosition(0, PosInExpr(0 :: 0 :: Nil))),
      new RootNode(sucSequent("(\\exists x. x=5 -> y>0) -> y>2".asFormula))) should be(sucSequent("(z=5 -> y>0) -> y>2".asFormula))
  }

  it should "be applicable when existential quantifier appears in positive polarity" in {
    val tactic = instantiateExistentialQuanT(Variable("x", None, Real), Variable("z", None, Real))

    tactic(SuccPosition(0, PosInExpr(0 :: 0 :: Nil))).
      applicable(new RootNode(sucSequent("((\\exists x. x=5) -> y > 5) -> y>2".asFormula))) shouldBe true

    tactic(SuccPosition(0, PosInExpr(0 :: 1 :: 0 :: Nil))).
      applicable(new RootNode(sucSequent("(y > 5 -> ((\\exists x. x=5) -> z = 1)) -> y>2".asFormula))) shouldBe true

    tactic(SuccPosition(0, PosInExpr(0 :: 0 :: Nil))).
      applicable(new RootNode(sucSequent("(!\\exists x. x=5) -> z = 1".asFormula))) shouldBe true
  }

  it should "not be applicable when existential quantifier appears in negative polarity" in {
    val tactic = instantiateExistentialQuanT(Variable("x", None, Real), Variable("z", None, Real))

    tactic(AntePosition(0)).
      applicable(new RootNode(sequent(Nil, "\\exists x. x=5".asFormula :: Nil, Nil))) shouldBe false

    tactic(SuccPosition(0, PosInExpr(0 :: Nil))).
      applicable(new RootNode(sucSequent("!(\\exists x. x=5)".asFormula))) shouldBe false

    tactic(SuccPosition(0, PosInExpr(0 :: Nil))).
      applicable(new RootNode(sucSequent("(\\exists x. x=5) -> y>2".asFormula))) shouldBe false

    tactic(SuccPosition(0, PosInExpr(0 :: 1 :: Nil))).
      applicable(new RootNode(sucSequent("(y > 5 -> \\exists x. x=5) -> y>2".asFormula))) shouldBe false

    tactic(SuccPosition(0, PosInExpr(0 :: Nil))).
      applicable(new RootNode(sucSequent("!\\exists x. x=5".asFormula))) shouldBe false
  }

    "Quantifier instantiation" should "pick the correct subtactic" in {
    val t1 = locateSucc(instantiateT(Variable("x", None, Real), "z".asTerm))
    getProofSequent(t1, new RootNode(sucSequent("\\exists x. [y:=x;][y'=1;]y>0".asFormula))) should be (
      sucSequent("[y:=z;][y'=1;]y>0".asFormula))

    val t2 = locateAnte(instantiateT(Variable("x", None, Real), "z".asTerm))
    getProofSequent(t2, new RootNode(sequent(Nil, "\\forall x. [y:=x;][y:=2;]y>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "[y:=z;][y:=2;]y>0".asFormula :: Nil, Nil))
  }

  it should "pick the correct subtactic and try the quantified names for instantiation" in {
    val t1 = locateSucc(instantiateT)
    getProofSequent(t1, new RootNode(sucSequent("\\exists x. [y:=x;][y'=1;]y>0".asFormula))) should be (
      sucSequent("[y:=x;][y'=1;]y>0".asFormula))

    val t2 = locateAnte(instantiateT)
    getProofSequent(t2, new RootNode(sequent(Nil, "\\forall x. [y:=x;][y:=2;]y>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "[y:=x;][y:=2;]y>0".asFormula :: Nil, Nil))
  }

  "Existential generalization p(t) -> \\exists x. p(x)" should "introduce existential quantifier in antecedent" in {
    val tactic = locateAnte(existentialGenT(Variable("y", None, Real), "x".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "\\exists y. y>0".asFormula :: Nil, Nil))
  }

  it should "introduce existential quantifier in antecedent when applied to succedent" in {
    val tactic = locateSucc(existentialGenT(Variable("y", None, Real), "x".asTerm))
    getProofSequent(tactic, new RootNode(sucSequent("x>0".asFormula))) should be (
      List(sequent(Nil, "\\exists y. y>0".asFormula :: Nil, "x>0".asFormula :: Nil), sucSequent("x>0".asFormula)))
  }

  it should "replace free occurrences of t with x" in {
    val tactic = locateAnte(existentialGenT(Variable("y", None, Real), "x".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "[x:=x+1;]x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "\\exists y. [x:=y+1;]x>0".asFormula :: Nil, Nil))
  }

  it should "work in context of boxes" in {
    val tactic = existentialGenT(Variable("z", None, Real), "x".asTerm)(AntePosition(0, PosInExpr(1::Nil)))
    getProofSequent(tactic, new RootNode(sequent(Nil, "[x:=5;]x=5".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "[x:=5;](\\exists z. z=5)".asFormula :: Nil, Nil))
  }

  // TODO AlphaConversionHelper replaces variable bound by quantifier -> might be needed by some tactics (check before fixing)
  ignore should "not replace bound occurrences of t with x" in {
    val tactic = locateAnte(existentialGenT(Variable("y", None, Real), "x".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall x. x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "\\exists y. \\forall x. x>0".asFormula :: Nil, Nil))
  }

  "Vacuous universal quantification" should "introduce universal quantifier" in {
    val tactic = vacuousUniversalQuanT(Some(Variable("y", None, Real)))
    getProofSequent(locateSucc(tactic), new RootNode(sucSequent("x>0".asFormula))) should be (
      sucSequent("\\forall y. x>0".asFormula))
    getProofSequent(locateAnte(tactic), new RootNode(sequent(Nil, "x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "\\forall y. x>0".asFormula :: Nil, Nil))
  }

  it should "introduce universal quantifier in context" in {
    val tactic = vacuousUniversalQuanT(Some(Variable("y", None, Real)))
    getProofSequent(tactic(SuccPosition(0, PosInExpr(1::Nil))), new RootNode(sucSequent("[y:=5;]x>0".asFormula))) should be (
      sucSequent("[y:=5;](\\forall y. x>0)".asFormula))
    getProofSequent(tactic(AntePosition(0, PosInExpr(1::Nil))), new RootNode(sequent(Nil, "[y:=5;]x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "[y:=5;](\\forall y. x>0)".asFormula :: Nil, Nil))
  }

  it should "not introduce universal quantifier if variable occurs in p" in {
    val tactic = locateSucc(vacuousUniversalQuanT(Some(Variable("x", None, Real))))
    tactic.applicable(new RootNode(sucSequent("x>0".asFormula))) shouldBe false
  }

  it should "remove vacuous universal quantifier" in {
    val tactic = vacuousUniversalQuanT(None)
    getProofSequent(locateSucc(tactic), new RootNode(sucSequent("\\forall y. x>0".asFormula))) should be (
      sucSequent("x>0".asFormula))
    getProofSequent(locateAnte(tactic), new RootNode(sequent(Nil, "\\forall y. x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "x>0".asFormula :: Nil, Nil))
  }

  it should "remove vacuous universal quantifier in context" in {
    val tactic = vacuousUniversalQuanT(None)
    getProofSequent(tactic(SuccPosition(0, PosInExpr(1::Nil))), new RootNode(sucSequent("[x:=5;](\\forall y. x>0)".asFormula))) should be (
      sucSequent("[x:=5;]x>0".asFormula))
    getProofSequent(tactic(AntePosition(0, PosInExpr(1::Nil))), new RootNode(sequent(Nil, "[x:=5;](\\forall y. x>0)".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "[x:=5;]x>0".asFormula :: Nil, Nil))
  }

  it should "not be applicable if more than one quantified variable occurs" in {
    val tactic = locateSucc(vacuousUniversalQuanT(None))
    tactic.applicable(new RootNode(sucSequent("\\forall x,y,z. x>0".asFormula))) shouldBe false
  }

  it should "be preceded by quantifier decomposition if more than one quantified variable occurs" in {
    val tactic = Tactics.repeatT(locateSucc(decomposeQuanT) ~ locateSucc(vacuousUniversalQuanT(None)))
    getProofSequent(tactic, new RootNode(sucSequent("\\forall x,y,z. a>0".asFormula))) should be (
      sucSequent("a>0".asFormula))
  }

  "Vacuous existential quantification" should "introduce existential quantifier" in {
    val tactic = vacuousExistentialQuanT(Some(Variable("y", None, Real)))
    getProofSequent(locateSucc(tactic), new RootNode(sucSequent("x>0".asFormula))) should be (
      sucSequent("\\exists y. x>0".asFormula))
    getProofSequent(locateAnte(tactic), new RootNode(sequent(Nil, "x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "\\exists y. x>0".asFormula :: Nil, Nil))
  }

  it should "not introduce universal quantifier if variable occurs in p" in {
    val tactic = locateSucc(vacuousExistentialQuanT(Some(Variable("x", None, Real))))
    tactic.applicable(new RootNode(sucSequent("x>0".asFormula))) shouldBe false
  }

  it should "remove vacuous universal quantifier" in {
    val tactic = vacuousExistentialQuanT(None)
    getProofSequent(locateSucc(tactic), new RootNode(sucSequent("\\exists y. x>0".asFormula))) should be (
      sucSequent("x>0".asFormula))
    getProofSequent(locateAnte(tactic), new RootNode(sequent(Nil, "\\exists y. x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "x>0".asFormula :: Nil, Nil))
  }

  it should "not be applicable if more than one quantified variable occurs" in {
    val tactic = locateSucc(vacuousExistentialQuanT(None))
    tactic.applicable(new RootNode(sucSequent("\\exists x,y,z. x>0".asFormula))) shouldBe false
  }

  it should "be preceded by quantifier decomposition if more than one quantified variable occurs" in {
    val tactic = Tactics.repeatT(locateSucc(decomposeQuanT) ~ locateSucc(vacuousExistentialQuanT(None)))
    getProofSequent(tactic, new RootNode(sucSequent("\\exists x,y,z. a>0".asFormula))) should be (
      sucSequent("a>0".asFormula))
  }

  "Quantifier skolemization" should "not introduce a new name if the quantified names are unique already" in {
    import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary.skolemizeT
    val tactic = locateSucc(skolemizeT)
    val result = helper.runTactic(tactic, new RootNode(sucSequent("\\forall x. x>0".asFormula)))
    result.openGoals() should have size 1
    result.openGoals().flatMap(_.sequent.ante) shouldBe empty
    result.openGoals().flatMap(_.sequent.succ) should contain only "x>0".asFormula
  }

  it should "introduce a new name if the quantified name is not unique" in {
    import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary.skolemizeT
    val tactic = locateSucc(skolemizeT)
    val result = helper.runTactic(tactic, new RootNode(sequent(Nil, "x>2".asFormula :: Nil, "\\forall x. x>0".asFormula :: Nil)))
    result.openGoals() should have size 1
    result.openGoals().flatMap(_.sequent.ante) should contain only "x>2".asFormula
    result.openGoals().flatMap(_.sequent.succ) should contain only "x_0>0".asFormula
  }

  it should "introduce a new name even if the quantified names are unique already, if forced to do so" in {
    import edu.cmu.cs.ls.keymaera.tactics.FOQuantifierTacticsImpl.skolemizeT
    val tactic = locateSucc(skolemizeT(forceUniquify = true))
    val result = helper.runTactic(tactic, new RootNode(sucSequent("\\forall x. x>0".asFormula)))
    result.openGoals() should have size 1
    result.openGoals().flatMap(_.sequent.ante) shouldBe empty
    result.openGoals().flatMap(_.sequent.succ) should contain only "x_0>0".asFormula
  }

  it should "skolemize to a function symbol" in {
    import edu.cmu.cs.ls.keymaera.tactics.FOQuantifierTacticsImpl.skolemizeToFnT
    val tactic = locateSucc(skolemizeToFnT)
    val result = helper.runTactic(tactic, new RootNode(sucSequent("\\forall x. x>0".asFormula)))
    result.openGoals() should have size 1
    result.openGoals().flatMap(_.sequent.ante) shouldBe empty
    result.openGoals().flatMap(_.sequent.succ) should contain only "x()>0".asFormula
  }

  "Quantifier instantiation" should "instantiate quantifier with given term" in {
    import edu.cmu.cs.ls.keymaera.tactics.FOQuantifierTacticsImpl.instantiateT
    val tactic = locateAnte(instantiateT(Variable("x", None, Real), "y+1".asTerm))
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall x. x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "y+1>0".asFormula :: Nil, Nil))
  }

  it should "guess names from quantified names" in {
    import edu.cmu.cs.ls.keymaera.tactics.FOQuantifierTacticsImpl.instantiateT
    val tactic = locateAnte(instantiateT)
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall x. x>0".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "x>0".asFormula :: Nil, Nil))
  }

  it should "guess all names from quantified names" in {
    import edu.cmu.cs.ls.keymaera.tactics.FOQuantifierTacticsImpl.instantiateT
    val tactic = locateAnte(instantiateT)
    getProofSequent(tactic, new RootNode(sequent(Nil, "\\forall x,y,z. x>y+z".asFormula :: Nil, Nil))) should be (
      sequent(Nil, "x>y+z".asFormula :: Nil, Nil))
  }

  "Forall duality" should "turn a universal quantifier into a negated existential" in {
    val tactic = locateSucc(FOQuantifierTacticsImpl.forallDualT)
    getProofSequent(tactic, new RootNode(sucSequent("\\forall x . x>y".asFormula))) should be (
      sucSequent("!(\\exists x . (!x>y))".asFormula))
  }

  it should "turn a negated existential quantifier into a universal" in {
    val tactic = locateSucc(FOQuantifierTacticsImpl.forallDualT)
    getProofSequent(tactic, new RootNode(sucSequent("!(\\exists x . (!x>y))".asFormula))) should be (
      sucSequent("\\forall x . x>y".asFormula))
  }

  "Exists duality" should "turn an existential quantifier into a negated universal" in {
    val tactic = locateSucc(FOQuantifierTacticsImpl.existsDualT)
    getProofSequent(tactic, new RootNode(sucSequent("\\exists x . x>y".asFormula))) should be (
      sucSequent("!(\\forall x . (!x>y))".asFormula))
  }

  it should "turn a negated universal quantifier into an existential" in {
    val tactic = locateSucc(FOQuantifierTacticsImpl.existsDualT)
    getProofSequent(tactic, new RootNode(sucSequent("!(\\forall x . (!x>y))".asFormula))) should be (
      sucSequent("\\exists x . x>y".asFormula))
  }
}
