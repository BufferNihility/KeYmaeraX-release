package edu.cmu.cs.ls.keymaera.api

import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.core.ExpressionTraversal.{StopTraversal, ExpressionTraversalFunction}
import edu.cmu.cs.ls.keymaera.parser.{KeYmaeraPrettyPrinter, ParseSymbols}
import spray.json._
import scala.collection.immutable.Seq
import edu.cmu.cs.ls.keymaera.core.Sequent
import edu.cmu.cs.ls.keymaera.core.PosInExpr

import scala.collection.mutable

// TODO fetch sequents only on demand
object JSONConverter {
  val prettyPrinter = new KeYmaeraPrettyPrinter(ParseSymbols) //todo use appropriate symbol table.

  def convertPos(p: PosInExpr) = JsString(p.pos.mkString(","))

  def convertNamedSymbol(n: NamedSymbol) = JsString(n.name + (n.index match { case Some(j) => "_" + j case _ => "" }))

  def convertFormula(formula: Formula, formulaId: String, nodeId: String): JsObject = {
    val jsonStack = new mutable.Stack[List[JsValue]]

    val fn = new ExpressionTraversalFunction {
      override def preF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] = {
        e match {
          case True() => /* nothing to do */
          case False() => /* nothing to do */
          case x@PredicateConstant(a, b) => /* nothing to do */
          case ApplyPredicate(a, b) => /* nothing to do */
          case _ => jsonStack.push(List())
        }
        Left(None)
      }

      override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = {
        e match {
          case Number(s, i) => /* Nothing to do here */
          case x@Variable(_, _, _) => /* Nothing to do here */
          case _ => jsonStack.push(List())
        }
        Left(None)
      }

      override def preP(p: PosInExpr, e: Program): Either[Option[StopTraversal], Program] = {
        e match {
          case x@ProgramConstant(_, _) => /* Nothing to do */
          case _ => jsonStack.push(List())
        }
        Left(None)
      }

      override def postF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] = {
        val cf = ("nodeId" -> JsString(nodeId)) :: ("id" -> convertPos(p)) :: Nil
        val o = e match {
          case True() => Some(JsObject(("name" -> JsString("true")) +: cf))
          case False() => Some(JsObject(("name" -> JsString("false")) +: cf))
          case x@PredicateConstant(a, b) => Some(JsObject(("name" -> convertNamedSymbol(x.asInstanceOf[PredicateConstant])) +: cf))
          case ApplyPredicate(a, b) => Some(JsObject(("name" -> JsString("apply")) :: ("children" -> JsArray(convertNamedSymbol(a) +: jsonStack.pop())) :: Nil ++: cf))
          case Equals(d, a, b) => Some(JsObject(("name" -> JsString("equals")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case NotEquals(d, a, b) => Some(JsObject(("name" -> JsString("notEquals")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case ProgramEquals(a, b) => Some(JsObject(("name" -> JsString("programEquals")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case ProgramNotEquals(a, b) => Some(JsObject(("name" -> JsString("programNotEquals")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case LessThan(d, a, b) => Some(JsObject(("name" -> JsString("lt")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case LessEqual(d, a, b) => Some(JsObject(("name" -> JsString("leq")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case GreaterEqual(d, a, b) => Some(JsObject(("name" -> JsString("geq")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case GreaterThan(d, a, b) => Some(JsObject(("name" -> JsString("gt")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Not(a) => Some(JsObject(("name" -> JsString("not")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case And(a, b) => Some(JsObject(("name" -> JsString("and")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Or(a, b) => Some(JsObject(("name" -> JsString("or")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Imply(a, b) => Some(JsObject(("name" -> JsString("imply")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Equiv(a, b) => Some(JsObject(("name" -> JsString("equiv")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case BoxModality(a, b) => Some(JsObject(("name" -> JsString("boxmodality")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case DiamondModality(a, b) => Some(JsObject(("name" -> JsString("diamondmodality")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Forall(v, a) => Some(JsObject(("name" -> JsString("forall")) :: ("variables" -> JsArray(v.map(convertNamedSymbol).toList)) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Exists(v, a) => Some(JsObject(("name" -> JsString("exists")) :: ("variables" -> JsArray(v.map(convertNamedSymbol).toList)) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case _ => None
        }
        o match {
          case Some(oo) => jsonStack.push(jsonStack.pop() :+ oo)
          case None => /* nothing to do */
        }
        Left(None)
      }

      override def postT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = {
        val cf = ("nodeId" -> JsString(nodeId)) :: ("id" -> convertPos(p)) :: Nil
        val o = e match {
          case Number(s, i) => Some(JsObject(("name" -> JsString(i.toString)) +: cf))
          case x@Variable(_, _, _) => Some(JsObject(("name" -> convertNamedSymbol(x.asInstanceOf[Variable])) +: cf))
          case Apply(a, b) => Some(JsObject(("name" -> JsString("apply")) :: ("children" -> JsArray(convertNamedSymbol(a) :: Nil ++: jsonStack.pop())) :: Nil ++: cf))
          case Neg(_, a) => Some(JsObject(("name" -> JsString("neg")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Derivative(_, a) => Some(JsObject(("name" -> JsString("derivative")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Add(_, a, b) => Some(JsObject(("name" -> JsString("add")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Subtract(_, a, b) => Some(JsObject(("name" -> JsString("subtract")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Multiply(_, a, b) => Some(JsObject(("name" -> JsString("multiply")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Divide(_, a, b) => Some(JsObject(("name" -> JsString("divide")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Exp(_, a, b) => Some(JsObject(("name" -> JsString("exp")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case _ => None
        }
        o match {
          case Some(oo) => jsonStack.push(jsonStack.pop() :+ oo)
          case None => /* nothing to do */
        }
        Left(None)
      }

      override def postP(p: PosInExpr, e: Program): Either[Option[StopTraversal], Program] = {
        val cf = ("nodeId" -> JsString(nodeId)) :: ("id" -> convertPos(p)) :: Nil
        val o = e match {
          case x@ProgramConstant(_, _) => Some(JsObject(("name" -> convertNamedSymbol(x.asInstanceOf[ProgramConstant])) +: cf))
          case Assign(_, _) => Some(JsObject(("name" -> JsString("Assign")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case NDetAssign(_) => Some(JsObject(("name" -> JsString("NDetAssign")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Sequence(_, _) => Some(JsObject(("name" -> JsString("Sequence")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Choice(_, _) => Some(JsObject(("name" -> JsString("Choice")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Test(_) => Some(JsObject(("name" -> JsString("Test")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case Loop(_) => Some(JsObject(("name" -> JsString("Loop")) :: ("children" -> JsArray(jsonStack.pop())) :: Nil ++: cf))
          case _ => None
        }
        o match {
          case Some(oo) => jsonStack.push(jsonStack.pop() :+ oo)
          case None => /* nothing to do */
        }
        Left(None)
      }
    }
    jsonStack.push(List())
    ExpressionTraversal.traverse(fn, formula)
    jsonStack.pop().head.asJsObject()
  }

  def convertPosition(pos : Position) = JsObject(
    "kind" -> JsString(if (pos.isAnte) "ante" else "succ"),
    "index" -> JsNumber(pos.getIndex),
    "inExpr" -> convertPos(pos.inExpr)
  )

  def convertRule(rule : Rule) = {
    val cf = ("name" -> JsString(rule.name)) :: Nil
    rule match {
      case r : AssumptionRule => JsObject(("kind" -> JsString("AssumptionRule")) :: ("pos" -> convertPosition(r.pos))
        :: ("assumption" -> convertPosition(r.aPos)) :: Nil ++: cf)
      case r : PositionRule => JsObject(("kind" -> JsString("PositionRule"))
        :: ("pos" -> convertPosition(r.pos)) :: Nil ++: cf)
      case r : TwoPositionRule => JsObject(("kind" -> JsString("TwoPositionRule"))
        :: ("pos1" -> convertPosition(r.pos1)) :: ("pos2" -> convertPosition(r.pos2)) :: Nil ++: cf)
      case _ => JsObject(("kind" -> JsString("UnspecificRule")) +: cf)
    }
  }

  def convert(l: Seq[Formula], ante: String, nodeId: String): JsArray =
    JsArray(l.zipWithIndex.map(f => JsObject(
        "nodeId"  -> JsString(nodeId),
        "id"      -> JsString(ante + ":" + f._2),
        "formula" -> convertFormula(f._1, ante + ":" + f._2, nodeId)
      )).toList)
  def convert(s: Sequent, nodeId: String): JsObject =
    JsObject(
      "nodeId"  -> JsString(nodeId),
      "ante"    -> convert(s.ante, "ante", nodeId),
      "succ"    -> convert(s.succ, "succ", nodeId)
    )
  def convert(id: String, limit: Option[Int], store: ((ProofNode, String) => Unit))(p: ProofNode): JsObject = {
    store(p, id)
    JsObject(
      "id"    -> JsString(id),
      "sequent"   -> sequent(p, id),
      "infos"     -> infos(p),
      "children"  -> (limit match {
        case Some(l) if l > 0 => JsArray(p.children.zipWithIndex.map(ps => convert(id, limit.map(i => i - 1), ps._1, ps._2, store)))
        case _ => JsArray()
      })
    )
  }
  def convert(id: String, limit: Option[Int], ps: ProofStep, i: Int, store: ((ProofNode, String) => Unit)): JsObject =
    JsObject(
      "rule"      -> convertRule(ps.rule),
      "id"        -> JsNumber(i),
      "children"  -> subgoals(id, limit, store)(ps)
    )

  def convert(id: String, filter: (ProofStepInfo => Boolean), store: (ProofNode, String) => Unit)(p: ProofNode): JsObject = {
    store(p, id)
    JsObject(
      "sequent" -> sequent(p, id),
      "infos" -> infos(p),
      "children" -> JsArray(p.children.zipWithIndex.filter(ps => filter(ps._1.tacticInfo)).map(ps => convert(id, filter, ps._1, ps._2, store)))
    )
  }

  def convert(id: String, filter: (ProofStepInfo => Boolean), ps: ProofStep, i: Int, store: (ProofNode, String) => Unit): JsObject =
    JsObject(
      "rule"      -> convertRule(ps.rule),
      "id"        -> JsNumber(i),
      "children"  -> subgoals(id, filter, store)(ps)
    )

  private def infos(p: ProofNode): JsArray = JsArray(p.tacticInfo.infos.map(s =>
    JsObject(
      "key"   -> JsString(s._1),
      "value" -> JsString(s._2)
    )).toList
  )
  private def sequent(p: ProofNode, nodeId: String) = convert(p.sequent, nodeId)
  private def updateIndex(id: String, limit: Option[Int], store: (ProofNode, String) => Unit)(in: (ProofNode, Int)): JsObject = convert(id + "_" + in._2, limit, store)(in._1)
  private def updateIndex(id: String, filter: (ProofStepInfo => Boolean), store: (ProofNode, String) => Unit)(in: (ProofNode, Int)): JsObject = convert(id + "_" + in._2, filter, store)(in._1)
  private def subgoals(id: String, limit: Option[Int], store: (ProofNode, String) => Unit)(ps: ProofStep): JsArray = JsArray(ps.subgoals.zipWithIndex.map(updateIndex(id, limit, store)))
  private def subgoals(id: String, filter: (ProofStepInfo => Boolean), store: (ProofNode, String) => Unit)(ps: ProofStep): JsArray = JsArray(ps.subgoals.zipWithIndex.map(updateIndex(id, filter, store)))

  //def apply(p: ProofNode): String = print("", None, (_, _) => ())(p)
  def apply(p: ProofNode, id: String, limit: Int, store: (ProofNode, String) => Unit): JsObject = convert(id, Some(limit), store)(p)
  def apply(p: ProofNode, id: String, filter: (ProofStepInfo => Boolean), store: ((ProofNode, String) => Unit)): JsObject = convert(id, filter, store)(p)

}
