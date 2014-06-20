/**
 * HyDRA API Responses
 * @author Nathan Fulton
 * @TODO require the output of each response matches the jschema.
 */
package edu.cmu.cs.ls.keymaera.hydra
import spray.json._
import edu.cmu.cs.ls.keymaera.core.Expr
import edu.cmu.cs.ls.keymaera.core.Sequent

/**
 * Responses are like views -- they shouldn't do anything except produce appropriately
 * formatted JSON from their parameters.
 */
sealed trait Response {
  val json : JsValue
}

class ErrorResponse(exn : Exception) extends Response {
  val json = JsObject(
        "textStatus" -> JsString(exn.getMessage()),
        "errorThrown" -> JsString(exn.getStackTrace().toString()),
        "type" -> JsString("error")
      )
}

class UnimplementedResponse(callUrl : String) extends Response {
  val json = JsObject(
      "textStatus" -> JsString("Call unimplemented: " + callUrl),
      "errorThrown" -> JsString("unimplemented"),
      "type" -> JsString("error")
  )
}

class CreateProblemResponse(tree:String, proofid:String) extends Response {
  val json = JsObject(
      "proofid" -> JsString(proofid),
      "proofTree" -> JsonParser(tree)
  )
}