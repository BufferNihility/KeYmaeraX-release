import edu.cmu.cs.ls.keymaera.core.{KeYmaera, Sequent, RootNode}
import edu.cmu.cs.ls.keymaera.tactics.{Interpreter, Config}
import edu.cmu.cs.ls.keymaera.tacticsinterface.{CLInterpreter, CLParser}
import testHelper.StringConverter._

/**
 * Created by nfulton on 2/26/15.
 */
class CombLangInterpreterTests extends TacticTestSuite {
  "CLInterpreter" should "not choke" in {
    val t = CLInterpreter.construct(CLParser("NilT & NilT").get)
    val n = new RootNode(
      Sequent(Nil, scala.collection.immutable.IndexedSeq("1=1".asFormula), scala.collection.immutable.IndexedSeq("1=1".asFormula))
    )
    helper.runTactic(t,n)
  }

  it should "hello, world!" in {
    val t = CLInterpreter.construct(CLParser("NilT & NilT & AxiomCloseT").get)
    val n = new RootNode(
      Sequent(Nil, scala.collection.immutable.IndexedSeq("1=1".asFormula), scala.collection.immutable.IndexedSeq("1=1".asFormula))
    )
    helper.runTactic(t,n)
    n.isClosed() shouldBe true
  }

  it should "cut" in {
    val t = CLInterpreter.construct(CLParser("cutT(\"1 > 0\")").get)
    val n = new RootNode(Sequent(Nil,scala.collection.immutable.IndexedSeq("x>1".asFormula),scala.collection.immutable.IndexedSeq("x>0".asFormula)))
    helper.runTactic(t, n)
    helper.report(n)
  }

}
