/**
 * KeYmaera Exception and Error Hierarchy
 * @author aplatzer
 * @note Code Review: 2015-05-01
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

class SubstitutionClashException(msg: String, subst: String/*Substitution*/, info: String = "") extends CoreException(msg + "\nSubstitution " + subst + " (details: " + info + ")") {
  /**
   * Add the context information to this exception, returning the resulting exception to be thrown.
   */
  def inContext(context: String): SubstitutionClashException =
    new SubstitutionClashException(msg, subst, info + "\nin " + context).initCause(this).asInstanceOf[SubstitutionClashException]
}

class BoundRenamingClashException(msg: String, ren: String/*BoundRenaming*/, info: String = "") extends CoreException(msg + "\nBoundRenaming " + ren + " (details: " + info + ")") {
  /**
   * Add the context information to this exception, returning the resulting exception to be thrown.
   */
  def inContext(context: String): BoundRenamingClashException =
    new BoundRenamingClashException(msg, ren, info + "\nin " + context).initCause(this).asInstanceOf[BoundRenamingClashException]
}

class SkolemClashException(msg: String, clashedNames:Set[_ >: NamedSymbol]) extends CoreException(msg + " " + clashedNames) {}

class InapplicableRuleException(msg: String, r:Rule, s:Sequent = null) extends CoreException(msg + "\nRule " + r + (if (s != null) " applied to " + s else "")) {
  //@TODO if (r instanceof PositionRule) msg + "\n" + s(r.pos) + "\nRule " + r + " applied to " + s
}

class UnknownOperatorException(msg: String, e:Expression) extends ProverException(msg + ": " + e.prettyString + " of " + e.getClass + " " + e) {}
