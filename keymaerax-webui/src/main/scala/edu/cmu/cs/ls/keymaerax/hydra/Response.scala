/**
* Copyright (c) Carnegie Mellon University.
* See LICENSE.txt for the conditions of this license.
*/
/**
 * HyDRA API Responses
 *  @author Nathan Fulton
 *  @author Stefan Mitsch
 *  @author Ran Ji
 */
package edu.cmu.cs.ls.keymaerax.hydra

import _root_.edu.cmu.cs.ls.keymaerax.api.JSONConverter
import _root_.edu.cmu.cs.ls.keymaerax.core.Sequent
import com.fasterxml.jackson.annotation.JsonValue
import spray.json._
import java.io.{PrintWriter, StringWriter, File}


/**
 * Responses are like views -- they shouldn't do anything except produce appropriately
 * formatted JSON from their parameters.
 *
 * To create a new response:
 * <ol>
 *   <li>Create a new object extending Response (perhaps with constructor arguments)</li>
 *   <li>override the json value with the json to be generated.</li>
 *   <li>override the schema value with Some(File(...)) containing the schema.</li>
 * </ol>
 *
 * See BooleanResponse for the simplest example.
 */
sealed trait Response {
//  private def jsonFile() : Option[File] = schema match {
//      case Some(schemaFile) => {
//        val resource = this.getClass().getResource("resources/js/schema/" + schemaFile)
//        if(resource != null && resource.getFile() != null) {
//          Some(new File(resource.getFile()))
//        }
//        else {
//          if(resource == null) throw new Exception("Could not find schema file " + schemaFile)
//          else throw new Exception("Found sources but could not find file for " + schemaFile)
//        }
//      }
//    case None => None
//  }


  protected val json: JsValue
  /**
   * Should be the name of a single file within resources/js/schema.
   */
  val schema: Option[String] = None

  def getJson = {
//    validate()
    json
  }

//  def validate() = {
//    jsonFile() match {
//      case None => true //OK.
//      case Some(file) =>
//        val schema = JsonSchemaFactory.byDefault().getJsonSchema(
//          JsonLoader.fromFile(file)
//        )
//        val report = schema.validate(JsonLoader.fromString(json.prettyPrint))
//        if (report.isSuccess) true
//        else throw report.iterator().next().asException()
//    }
//  }
}

class BooleanResponse(flag : Boolean) extends Response {
  override val schema = Some("BooleanResponse.js")

  val json = JsObject(
    "success" -> (if(flag) JsTrue else JsFalse),
    "type" -> JsNull
  )
}

class ModelListResponse(models : List[ModelPOJO]) extends Response {
  val objects = models.map(modelpojo => JsObject(
    "id" -> JsString(modelpojo.modelId.toString),
    "name" -> JsString(modelpojo.name),
    "date" -> JsString(modelpojo.date),
    "description" -> JsString(modelpojo.description),
    "pubLink" -> JsString(modelpojo.pubLink),
    "keyFile" -> JsString(modelpojo.keyFile),
    "title" -> JsString(modelpojo.title),
    "hasTactic" -> JsBoolean(modelpojo.tactic.isDefined)
  ))

  val json = JsArray(objects)
}

class UpdateProofNameResponse(proofId : String, newName : String) extends Response {
  val json = JsArray()
}

/**
 *
 * @param proofs The list of proofs with their status in KeYmaera (proof, loadStatus).
 * @param models -- optionally, a list of model names associated with each of the proofs in <em>proofs</em>
 */
class ProofListResponse(proofs : List[(ProofPOJO, String)], models : Option[List[String]] = None) extends Response {
  override val schema = Some("prooflist.js")

  val objects : List[JsObject] = models match {
    case None => proofs.map({case (proof, loadStatus) => JsObject(
      "id" -> JsString(proof.proofId.toString),
      "name" -> JsString(proof.name),
      "description" -> JsString(proof.description),
      "date" -> JsString(proof.date),
      "modelId" -> JsString(proof.modelId.toString),
      "stepCount" -> JsNumber(proof.stepCount),
      "status" -> JsBoolean(proof.closed),
      "loadStatus" -> JsString(loadStatus)
    )})
    case Some(modelNames) =>
      (proofs zip modelNames).map({case (p,loadStatus) =>
        val proof = p._1
        val modelName = p._2

        JsObject(
          "id" -> JsString(proof.proofId.toString),
          "name" -> JsString(proof.name),
          "description" -> JsString(proof.description),
          "date" -> JsString(proof.date),
          "modelId" -> JsString(proof.modelId.toString),
          "stepCount" -> JsNumber(proof.stepCount),
          "status" -> JsBoolean(proof.closed),
          "loadStatus" -> JsString(loadStatus),
          "modelName" -> JsString(modelName)
        )
      })
  }

  val json = JsArray(objects)
}

class GetModelResponse(model : ModelPOJO) extends Response {
  val json = JsObject(
    "id" -> JsString(model.modelId.toString),
    "name" -> JsString(model.name),
    "date" -> JsString(model.date),
    "description" -> JsString(model.description),
    "pubLink" -> JsString(model.pubLink),
    "keyFile" -> JsString(model.keyFile),
    "title" -> JsString(model.title),
    "tactic" -> JsString(model.tactic.getOrElse(""))
  )
}

class GetModelTacticResponse(model : ModelPOJO) extends Response {
  val json = JsObject(
    "modelId" -> JsString(model.modelId.toString),
    "modelName" -> JsString(model.name),
    "tacticBody" -> JsString(model.tactic.getOrElse(""))
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

class CreatedIdResponse(id : String) extends Response {
  val json = JsObject("id" -> JsString(id))
}

class ErrorResponse(exn : Exception) extends Response {
  val sw = new StringWriter
  exn.printStackTrace(new PrintWriter(sw))
  val json = JsObject(
        "textStatus" -> JsString(exn.getMessage),
        "errorThrown" -> JsString(sw.toString),
        "type" -> JsString("error")
      )
}

class GenericOKResponse() extends Response {
  val json = JsObject(
    "success" -> JsTrue
  )
}

class UnimplementedResponse(callUrl : String) extends Response {
  val json = JsObject(
      "textStatus" -> JsString("Call unimplemented: " + callUrl),
      "errorThrown" -> JsString("unimplemented"),
      "type" -> JsString("error")
  )
}

class ProofStatusResponse(proofId: String, status: String, error: Option[String] = None) extends Response {
  override val schema = Some("proofstatus.js")
  val json = JsObject(
    "proofId" -> JsString(proofId),
    "type" -> JsString("ProofLoadStatus"),
    "status" -> JsString(status),
    "textStatus" -> JsString(status + ": " + proofId),
    "errorThrown" -> JsString(error.getOrElse(""))
  )
}
class ProofIsLoadingResponse(proofId : String) extends ProofStatusResponse(proofId, "loading")
class ProofNotLoadedResponse(proofId : String) extends ProofStatusResponse(proofId, "notloaded")
class ProofIsLoadedResponse(proofId: String) extends ProofStatusResponse(proofId, "loaded")
// progress "open": open goals
// progress "closed": no open goals but not checked for isProved
class ProofProgressResponse(proofId: String, progress: String) extends ProofStatusResponse(proofId, progress)

class GetProblemResponse(proofid:String, tree:String) extends Response {
  val json = JsObject(
    "proofid" -> JsString(proofid),
    "proofTree" -> JsonParser(tree)
  )
}

//class DispatchedTacticResponse(t : DispatchedTacticPOJO) extends Response {
//  val nid = t.nodeId match {
//    case Some(nodeId) => nodeId
//    case None => t.proofId
//  }
//
//  val json = JsObject(
//    "proofId" -> JsString(t.proofId),
//    "nodeId" -> JsString(nid),
//    "tacticId" -> JsString(t.tacticsId),
//    "tacticInstId" -> JsString(t.id),
//    "tacticInstStatus" -> JsString(t.status.toString)
//  )
//}

//class DispatchedCLTermResponse(t : DispatchedCLTermPOJO) extends Response {
//  val nid = t.nodeId match {
//    case Some(nodeId) => nodeId
//    case None => t.proofId
//  }
//
//  val json = JsObject(
//    "id" -> JsString(t.id),
//    "proofId" -> JsString(t.proofId),
//    "nodeId" -> JsString(nid),
//    "termId" -> JsString(t.id),
//    "term" -> JsString(t.clTerm),
//    "status" -> JsString(t.status.get.toString)
//  )
//}

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

class OpenProofResponse(proof : ProofPOJO, loadStatus : String) extends Response {
  override val schema = Some("proof.js")
  val json = JsObject(
    "id" -> JsString(proof.proofId.toString),
    "name" -> JsString(proof.name),
    "description" -> JsString(proof.description),
    "date" -> JsString(proof.date),
    "modelId" -> JsString(proof.modelId.toString),
    "stepCount" -> JsNumber(proof.stepCount),
    "status" -> JsBoolean(proof.closed),
    "loadStatus" -> JsString(loadStatus)
  )
}

class ProofAgendaResponse(tasks : List[(ProofPOJO, String, String)]) extends Response {
  override val schema = Some("proofagenda.js")
  val objects = tasks.map({ case (proofPojo, nodeId, nodeJson) => JsObject(
    "proofId" -> JsString(proofPojo.proofId.toString),
    "nodeId" -> JsString(nodeId),
    "proofNode" -> JsonParser(nodeJson)
  )})

  val json = JsArray(objects)
}

class AgendaAwesomeResponse(tree: Tree, agenda: List[AgendaItem]) extends Response {
  override val schema = Some("agendaawesome.js")

  def childrenJson(children: List[TreeNode]):JsValue = {
   JsArray(children.map({case node => nodeIdJson(node.id)}))
  }

  def sequentJson(sequent: Sequent): JsValue = {
    JsObject (
      "ante" -> JsArray(sequent.ante.map{case fml => JSONConverter.convertFormula(fml, null, null)}.toVector),
      "succ" -> JsArray(sequent.succ.map{case fml => JSONConverter.convertFormula(fml, null, null)}.toVector)
    )
  }

  def nodeJson(node: TreeNode): JsValue = {
    JsObject (
      "id" -> nodeIdJson(node.id),
      "sequent" -> sequentJson(node.sequent),
      "children" -> childrenJson(node.children),
      "parent" -> node.parent.map({case n => nodeIdJson(n.id)}).getOrElse(JsNull))
  }

  def pathJson(path: List[String]):JsValue = JsArray()

  def itemJson(item: AgendaItem): (String, JsValue) = {
    val value = JsObject (
      "id" -> JsString(item.id),
      "name" -> JsString(item.name),
      "proofId" -> JsString(item.proofId),
      "goal" -> JsString(item.id),
      "path" -> pathJson(item.path))
    (item.id, value)
  }

  def nodeIdJson(n: Int):JsValue = JsString("Node" + n)
  def proofIdJson(n: String):JsValue = JsString("Proof" + n)

  val proofTree = JsObject (
    "id" -> proofIdJson(tree.id),
    "nodes" -> new JsArray(tree.nodes.map({case node => nodeJson(node)})),
    "root" -> nodeIdJson(tree.root.id))

  val agendaItems = JsObject(agenda.map({case item => itemJson(item)}))

  val json =
    JsObject (
      "proofTree" -> proofTree,
      "agendaItems" -> agendaItems
    )
}

class ProofNodeInfoResponse(proofId: String, nodeId: Option[String], nodeJson: String) extends Response {
  val json = JsObject(
    "proofId" -> JsString(proofId),
    "nodeId" -> JsString(nodeId match { case Some(nId) => nId case None => "" }),
    "proofNode" -> JsonParser(nodeJson)
  )
}

//class ApplicableTacticsResponse(tactics : List[TacticPOJO]) extends Response {
//  val objects = tactics.map(tactic => JsObject(
//    "id" -> JsString(tactic.tacticId),
//    "name" -> JsString(tactic.name)
//  ))
//
//  val json = JsArray(objects)
//}

class CounterExampleResponse(cntEx: String) extends Response {
  val json = JsObject(
    "cntEx" -> JsString(cntEx)
  )
}

class KyxConfigResponse(kyxConfig: String) extends Response {
  val json = JsObject(
    "kyxConfig" -> JsString(kyxConfig)
  )
}

class KeymaeraXVersionResponse(installedVersion: String, upToDate: Option[Boolean], latestVersion: Option[String]) extends Response {
  assert(upToDate.isDefined == latestVersion.isDefined, "upToDate and latestVersion should both be defined, or both be undefined.")
  val json = upToDate match {
    case Some(b) if b == true => JsObject("keymaeraXVersion" -> JsString(installedVersion), "upToDate" -> JsTrue)
    case Some(b) if b == false => JsObject("keymaeraXVersion" -> JsString(installedVersion), "upToDate" -> JsFalse, "latestVersion" -> JsString(latestVersion.get))
    case None => JsObject("keymaeraXVersion" -> JsString(installedVersion))
  }
}

class ConfigureMathematicaResponse(linkNamePrefix : String, jlinkLibDirPrefix : String, success : Boolean) extends Response {
  val json = JsObject(
    "linkNamePrefix" -> JsString(linkNamePrefix),
    "jlinkLibDirPrefix" -> JsString(jlinkLibDirPrefix),
    "success" -> {if(success) JsTrue else JsFalse}
  )
}

//@todo these are a mess.
class MathematicaConfigSuggestionResponse(os: String, version: String, kernelPath: String, kernelName: String,
                                          jlinkPath: String, jlinkName: String) extends Response {
  override protected val json: JsValue = JsObject(
    "os" -> JsString(os),
    "version" -> JsString(version),
    "kernelPath" -> JsString(kernelPath),
    "kernelName" -> JsString(kernelName),
    "jlinkPath" -> JsString(jlinkPath),
    "jlinkName" -> JsString(jlinkName)
  )
}

class MathematicaConfigurationResponse(linkName: String, jlinkLibDir: String) extends Response {
  override protected val json: JsValue = JsObject(
    "linkName" -> JsString(linkName),
    "jlinkLibDir" -> JsString(jlinkLibDir)
  )
}

class MathematicaStatusResponse(configured : Boolean) extends Response {
  override protected val json: JsValue = JsObject(
    "configured" -> {if(configured) JsTrue else JsFalse}
  )
}


/**
 * @return JSON that is directly usable by angular.treeview
 */
class AngularTreeViewResponse(tree : String) extends Response {
  override val schema = Some("angular.treeview.js")
  
  val json = JsArray( convert(JsonParser(tree).asJsObject) )

  private def convert(node : JsObject) : JsValue = {
    //TODO switch to Jolt (https://github.com/bazaarvoice/jolt) once they can handle trees
    val children = (node.fields.get("children") match {
      case Some(c) => c
      case None => throw new IllegalArgumentException("Schema violation")
    }) match {
      case JsArray(c) => c
      case _ => throw new IllegalArgumentException("Schema violation")
    }
    val proofInfo = node.fields.get("infos") match {
      case Some(info) => info
      case None => JsArray()
    }

    val id = node.fields.get("id") match { case Some(i) => i case None => throw new IllegalArgumentException("Schema violation") }
    if (children.length > 0) {
      // TODO only retrieves the first alternative of the bipartite graph
      val step = children.head.asJsObject
      val rule = step.fields.get("rule") match {
        case Some(r) => r.asJsObject.fields.get("name") match {
          case Some(n) => n
          case None => throw new IllegalArgumentException("Schema violation")
        }
        case None => throw new IllegalArgumentException("Schema violation")
      }
      val subgoals = step.fields.get("children") match {
        case Some(gl) => gl.asInstanceOf[JsArray].elements.map(g => convert(g.asJsObject()))
        case None => throw new IllegalArgumentException("Schema violation")
      }
      JsObject(
        "id" -> id,
        "label" -> rule,
        "info" -> proofInfo,
        "children" -> JsArray(subgoals)
      )
    } else {
      JsObject(
        "id" -> id,
        "label" -> JsString("Open Goal"), // TODO only if the goal is closed, which is not yet represented in JSON
        "info" -> proofInfo,
        "children" -> JsArray()
      )
    }
  }
}

//class ProofHistoryResponse(history : List[(DispatchedTacticPOJO, TacticPOJO)]) extends Response {
//  val json = JsArray(history.map { case (dispatched, tactic) => convert(dispatched, tactic)})
//
//  private def convert(dispatched: DispatchedTacticPOJO, tactic: TacticPOJO): JsValue = JsObject(
//    "dispatched" -> JsObject(
//      "id" -> JsString(dispatched.id),
//      "proofId" -> JsString(dispatched.proofId),
//      "nodeId" -> JsString(dispatched.nodeId match { case Some(nId) => nId case _ => "" }),
//      "status" -> JsString(dispatched.status.toString),
//      "input" -> convertInput(dispatched.input)
//    ),
//    "tactic" -> JsObject(
//      "id" -> JsString(tactic.tacticId),
//      "name" -> JsString(tactic.name)
//    )
//  )
//
//  private def convertInput(input: Map[Int, String]) = JsArray(/* TODO */)
//}

class DashInfoResponse(openProofs:Int, allModels: Int, provedModels: Int) extends Response {
  override val schema = Some("DashInfoResponse.js")
  val json = JsObject(
    "open_proof_count" -> JsNumber(openProofs),
    "all_models_count" -> JsNumber(allModels),
    "proved_models_count" -> JsNumber(provedModels)
  )
}


class NodeResponse(tree : String) extends Response {
  //todo add schema.
  val node = JsonParser(tree).asJsObject
  val json = node
}

class MockResponse(resourceName: String) extends Response {
  //@todo add schema
  val json = scala.io.Source.fromInputStream(getClass.getResourceAsStream(resourceName)).mkString.parseJson
}
