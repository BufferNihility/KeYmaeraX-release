package edu.cmu.cs.ls.keymaera.tools

import com.wolfram.jlink._
import edu.cmu.cs.ls.keymaera.core.ExpressionTraversal.{StopTraversal, ExpressionTraversalFunction}
import edu.cmu.cs.ls.keymaera.core._

/**
 * An abstract interface to Mathematica link implementations.
 * The link may be used syncrhonously or asychronously.
 * Each MathematicaLink 
 * Multiple MathematicaLinks may be created by instantiating multiple copies
 * of implementing classes.
 * 
 * @author Nathan Fulton
 * @author Stefan Mitsch
 */
trait MathematicaLink extends QETool with DiffSolutionTool {
  type KExpr = edu.cmu.cs.ls.keymaera.core.Expr
  type MExpr = com.wolfram.jlink.Expr

  def run(cmd : String) : (String, KExpr)
  def run(cmd : MExpr) : (String, KExpr)

  /**
   * @return true if the job is finished, false if it is still running.
   */
  def ready : Boolean

  /** Cancels the current request.
   * @return True if job is successfully cancelled, or False if the new
   * status is unknown.
   */
  def cancel : Boolean

  def toMathematica(expr : KExpr) =
    KeYmaeraToMathematica.fromKeYmaera(expr)

  def toKeYmaera(expr : MExpr) =
    MathematicaToKeYmaera.fromMathematica(expr)
}

/**
 * A link to Mathematica using the JLink interface.
 * 
 * @author Nathan Fulton
 * @author Stefan Mitsch
 */
class JLinkMathematicaLink extends MathematicaLink {
  var ml: KernelLink = null

  // HACK assumed to be called before first use of ml
  // TODO replace with constructor and use dependency injection to provide JLinkMathematicaLink whereever needed
  /**
   * Initializes the connection to Mathematica.
   * @param linkName The name of the link to use (platform-dependent, see Mathematica documentation)
   */
  def init(linkName : String, jlinkLibDir : Option[String]) = {
    if(jlinkLibDir.isDefined) {
      System.setProperty("com.wolfram.jlink.libdir", jlinkLibDir.get) //e.g., "/usr/local/Wolfram/Mathematica/9.0/SystemFiles/Links/JLink"
    }
    ml = MathLinkFactory.createKernelLink(Array[String](
      "-linkmode", "launch",
      "-linkname", linkName + " -mathlink"))
    ml.discardAnswer()
  }

  /**
   * Closes the connection to Mathematica.
   */
  def shutdown() = {
    ml.terminateKernel()
    ml.close()
    ml = null
  }

  /**
   * Runs the command and then halts program exception until answer is returned.
   */
  def run(cmd: String) = {
    ml.synchronized {
      dispatch(cmd)
      getAnswer
    }
  }
  
  def run(cmd: MExpr) = {
    ml.synchronized {
      dispatch(cmd)
      getAnswer
    }
  }

  private def dispatch(cmd: String) : Unit = {
    ml.evaluate(cmd)
  }

  private def dispatch(cmd: MExpr) = {
    ml.evaluate(cmd)
  }

  /**
   * blocks and returns the answer.
   */
  private def getAnswer = {
    ml.waitForAnswer()
    val res = ml.getExpr
    val keymaeraResult = MathematicaToKeYmaera.fromMathematica(res)
    // toString calls dispose (see Mathematica documentation, so only call it when done with the Expr
    (res.toString, keymaeraResult)
  }

  def ready = ???

  def cancel = ???

  def qe(f : Formula) : Formula = {
    qeInOut(f)._1
  }

  def qeInOut(f : Formula) : (Formula, String, String) = {
    val input = "Reduce[" + toMathematica(f) + ",{}, Reals" + "]"
    val (output, result) = run(input)
    result match {
      case f : Formula => (f, input, output)
      case _ => throw new Exception("Expected a formula from Reduce call but got a non-formula expression.")
    }
  }

  override def diffSol(diffSys: ContEvolveProgram, diffArg: Variable, iv: Map[Variable, Function]): Option[Formula] =
    diffSol(diffArg, iv, toDiffSys(diffSys, diffArg):_*)
  override def diffSol(diffSys: ContEvolve, diffArg: Variable, iv: Map[Variable, Function]): Option[Formula] =
    diffSol(diffArg, iv, toDiffSys(diffSys.child, diffArg):_*)

  /**
   * Converts an expression into a differential equation system (list of x'=theta).
   * Expected to be in NFContEvolve form.
   * @param diffSys The expression form of the differential equation system.
   * @param diffArg The name of the differential argument (dx/d diffArg = theta).
   * @return The differential equation system.
   */
  // TODO convert more general forms
  private def toDiffSys(diffSys: KExpr, diffArg: Variable): List[(Variable, Term)] = {
    diffSys match {
      // do not solve time for now (assumed to be there but should not be solved for)
      // TODO remove restriction on t once ghost time is introduced
      case Equals(_, Derivative(_, x: Variable), theta) if x != diffArg =>  (x, theta) :: Nil
      case Equals(_, Derivative(_, x: Variable), theta) if x == diffArg =>  Nil
      case And(lhs, rhs) => toDiffSys(lhs, diffArg) ::: toDiffSys(rhs, diffArg)
      case _ => ???
    }
  }

  /**
   * Converts a system of differential equations given as ContEvolveProgram into list of x'=theta
   * @param diffSys The system of differential equations
   * @param diffArg The name of the differential argument (dx/d diffArg = theta).
   * @return The differential equation system in list form.
   */
  private def toDiffSys(diffSys: ContEvolveProgram, diffArg: Variable): List[(Variable, Term)] = {
    var result = List[(Variable, Term)]()
    ExpressionTraversal.traverse(new ExpressionTraversalFunction {
      override def preP(p: PosInExpr, e: Program): Either[Option[StopTraversal], Program] = e match {
        case AtomicContEvolve(Derivative(_, x: Variable), theta) if x != diffArg => result = result :+ (x, theta); Left(None)
        case AtomicContEvolve(Derivative(_, x: Variable), theta) if x == diffArg => Left(None)
        case NFContEvolveProgram(_, _, _) => Left(None)
        case ContEvolveProduct(_, _) => Left(None)
        case _: EmptyContEvolveProgram => Left(None)
      }
    }, diffSys)
    result
  }

  /**
   * Computes the symbolic solution of a system of differential equations.
   * @param diffArg The differential argument, i.e., d f(diffArg) / d diffArg.
   * @param diffSys The system of differential equations of the form x' = theta.
   * @return The solution if found; None otherwise
   */
  private def diffSol(diffArg: Variable, iv: Map[Variable, Function], diffSys: (Variable, Term)*): Option[Formula] = {
    val primedVars = diffSys.map(_._1)
    val functionalizedTerms = diffSys.map{ case (x, theta) => ( x, functionalizeVars(theta, diffArg, primedVars:_*)) }
    val mathTerms = functionalizedTerms.map{case (x, theta) =>
      (new MExpr(toMathematica(Derivative(Real, x)), Array[MExpr](toMathematica(diffArg))), toMathematica(theta))}
    val convertedDiffSys = mathTerms.map{case (x, theta) =>
      new MExpr(MathematicaSymbols.EQUALS, Array[MExpr](x, theta))}

    val functions = diffSys.map(t => toMathematica(functionalizeVars(t._1, diffArg)))

    val initialValues = diffSys.map(t => toMathematica(
      Equals(Real, functionalizeVars(t._1, Number(BigDecimal(0)), primedVars:_*), Apply(iv(t._1), Nothing))))

    val input = new MExpr(new MExpr(Expr.SYMBOL, "DSolve"),
      Array[MExpr](
        new MExpr(Expr.SYM_LIST, (convertedDiffSys ++ initialValues).toArray),
        new MExpr(Expr.SYM_LIST, functions.toArray),
        toMathematica(diffArg)))
    val (_, result) = run(input)
    result match {
      case f: Formula => Some(defunctionalize(f, diffArg, primedVars.map(_.name):_*))
      case _ => None
    }
  }

  /**
   * Replaces all occurrences of variables vars in the specified term t with functions of argument arg.
   * @param t The term.
   * @param arg The function argument.
   * @param vars The variables to functionalize.
   * @return The term with variables replaced by functions.
   */
  private def functionalizeVars(t: Term, arg: Term, vars: Variable*) = ExpressionTraversal.traverse(
    new ExpressionTraversalFunction {
      override def postT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = e match {
        case v@Variable(name, idx, sort) if vars.isEmpty || vars.contains(v) =>
          Right(Apply(Function(name, idx, arg.sort, sort), arg))
        case _ => Left(None)
      }
    }, t) match {
    case Some(resultTerm) => resultTerm
    case None => throw new IllegalArgumentException("Unable to functionalize " + t)
  }

  /**
   * Replaces all functions with argument arg in formula f with a variable of the same name.
   * @param f The formula.
   * @param arg The function argument.
   * @return The term with functions replaced by variables.
   */
  private def defunctionalize(f: Formula, arg: Term, fnNames: String*) = ExpressionTraversal.traverse(
    new ExpressionTraversalFunction {
      override def postT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = e match {
        case Apply(Function(name, idx, _, range), fnArg) if arg == fnArg
          && (fnNames.isEmpty || fnNames.contains(name)) => Right(Variable(name, idx, range))
        case _ => Left(None)
      }
    }, f) match {
    case Some(resultF) => resultF
    case None => throw new IllegalArgumentException("Unable to defunctionalize " + f)
  }
}