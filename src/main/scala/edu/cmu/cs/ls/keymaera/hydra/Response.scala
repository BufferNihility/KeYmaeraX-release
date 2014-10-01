/**
 * HyDRA API Responses
 *  @author Nathan Fulton
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

class BooleanResponse(flag : Boolean) extends Response {
  val json = JsObject(
    "success" -> (if(flag) JsTrue else JsFalse),
    "type" -> JsNull
  )
}

class LoginResponse(flag:Boolean, userId:String) extends Response {
  val json = JsObject(
    "success" -> (if(flag) JsTrue else JsFalse),
    "key" -> JsString("userId"),
    "value" -> JsString(userId),
    "type" -> JsString("LoginResponse")
  )
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

class GetProblemResponse(proofid:String, tree:String) extends Response {
  val json = JsObject(
    "proofid" -> JsString(proofid),
    "proofTree" -> JsonParser(tree)
  )
}

class TacticDispatchedResponse(proofId: String, nodeId: String, tacticId: String, tacticInstId: String) extends Response {
  val json = JsObject(
    "proofId" -> JsString(proofId),
    "nodeId" -> JsString(nodeId),
    "tacticId" -> JsString(tacticId),
    "tacticInstId" -> JsString(tacticInstId)
  )
}

class UpdateResponse(update: String) extends Response {
  val json = JsObject(
    "type" -> JsString("update"),
    "events" -> JsonParser(update)
  )
}

class ProofTreeResponse(tree: String) extends Response {
  val json = JsObject(
    "type" -> JsString("proof"),
    "tree" -> JsonParser(tree)
  )
}
