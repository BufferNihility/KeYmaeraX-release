import edu.cmu.cs.ls.keymaera.tactics.ExpressionTraversal
import edu.cmu.cs.ls.keymaera.tactics.ExpressionTraversal.ExpressionTraversalFunction
import edu.cmu.cs.ls.keymaera.tactics.Position
import edu.cmu.cs.ls.keymaera.tactics.PosInExpr
import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics.{ProofNode, Interpreter, Tactics}
import testHelper.ProvabilityTestHelper
import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}

/**
 * Created by nfulton on 2/5/15.
 */
trait TacticTestSuite extends FlatSpec with Matchers with BeforeAndAfterEach {
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Boilerplate
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  val helper = new ProvabilityTestHelper(x=>println(x))

  val mathematicaConfig = helper.mathematicaConfig

  override def beforeEach() = {
    Tactics.KeYmaeraScheduler = new Interpreter(KeYmaera)
    Tactics.MathematicaScheduler = new Interpreter(new Mathematica)

    Tactics.KeYmaeraScheduler.init(Map())
    Tactics.MathematicaScheduler.init(mathematicaConfig)
  }

  override def afterEach() = {
    Tactics.KeYmaeraScheduler.shutdown()
    Tactics.MathematicaScheduler.shutdown()
    Tactics.KeYmaeraScheduler = null
    Tactics.MathematicaScheduler = null
  }
  
  protected def formulaAtExpr(node : ProofNode, position : Position) : Option[Formula] = {
    var formula : Option[Formula] = None
    val fn = new ExpressionTraversalFunction {
      override def preF(posInExpr : PosInExpr, f : Formula) = {
        if(posInExpr.equals(position.inExpr)) {
          formula = Some(f)
          Left(Some(ExpressionTraversal.stop))
        }
        else { Left(None) }
      }
    }
    ExpressionTraversal.traverse(fn, node.sequent(position))
    formula
  }
}
