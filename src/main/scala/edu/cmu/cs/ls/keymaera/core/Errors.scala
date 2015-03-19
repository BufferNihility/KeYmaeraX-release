/**
 * KeYmaera Exception and Error Hierarchy
 * @author aplatzer
 */
package edu.cmu.cs.ls.keymaera.core

/**
 * KeYmaera Prover Exceptions.
 */
class ProverException(msg: String) extends RuntimeException(msg) {
  
  //@TODO Add functionality to prettyPrint all expressions passed in on demand.
}
// class ProverException private(ex: RuntimeException) extends RuntimeException(ex) {
//   def this(message:String) = this(new RuntimeException(message))
//   def this(message:String, throwable: Throwable) = this(new RuntimeException(message, throwable))
//   
//   //@TODO Add inContext():Throwable function that gives wraps the exception within some extra information that explains the context formula in which a problem occurred. So not just the local subformula but the whole context. Useful for try catch e => throw e.inContext("Context information")
//   
//   //@TODO Add functionality to prettyPrint all expressions passed in on demand.
// }

/**
 * Critical exceptions from KeYmaera's Prover Core.
 */
class CoreException(msg:String) extends ProverException(msg) {}

class SubstitutionClashException(msg: => Any, s:Any/*Substitution*/, e:Expr, info: => Any = "") extends CoreException(msg + "\nSubstitution " + s + (if (e != null) " applied to " + e.prettyString + " " else "") + info) {
  /**
   * Add the context information to this exception, returning the resulting exception to be thrown.
   */
  def inContext(context: => Any) = new SubstitutionClashException(msg, s, e, info + "\nin " + context).initCause(this).asInstanceOf[SubstitutionClashException]
}

class SkolemClashException(msg: => Any, clashedNames:Set[NamedSymbol]) extends CoreException(msg + " " + clashedNames) {}

class InapplicableRuleException(msg: => Any, r:Rule, s:Sequent = null) extends CoreException(msg + "\nRule " + r + (if (s != null) " applied to " + s else "")) {
  //@TODO if (r instanceof PositionRule) msg + "\n" + s(r.pos) + "\nRule " + r + " applied to " + s
}

class UnknownOperatorException(msg: => Any, e:Expr) extends ProverException(msg + ": " + e.prettyString + " of " + e.getClass + " " + e) {}
  
class TacticException(msg: => Any, node: ProofNode) extends ProverException(msg + "\nat " + node.sequent) {}