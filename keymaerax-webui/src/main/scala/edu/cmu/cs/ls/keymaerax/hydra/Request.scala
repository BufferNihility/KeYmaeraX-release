/**
* Copyright (c) Carnegie Mellon University.
* See LICENSE.txt for the conditions of this license.
*/
/**
 * HyDRA API Requests
 * @author Nathan Fulton
 * @author Ran Ji
 */
package edu.cmu.cs.ls.keymaerax.hydra

import java.io.{File, FileNotFoundException, FileReader}
import java.text.SimpleDateFormat
import java.util.{Locale, Calendar}

import _root_.edu.cmu.cs.ls.keymaerax.api.KeYmaeraInterface
import _root_.edu.cmu.cs.ls.keymaerax.api.KeYmaeraInterface.TaskManagement
import _root_.edu.cmu.cs.ls.keymaerax.bellerophon._
import _root_.edu.cmu.cs.ls.keymaerax.btacticinterface.BTacticParser
import _root_.edu.cmu.cs.ls.keymaerax.btactics._
import edu.cmu.cs.ls.keymaerax.btactics.{PositionLocator, DerivationInfo, AxiomInfo}
import _root_.edu.cmu.cs.ls.keymaerax.core.{Sequent, ProverException, Provable}
import _root_.edu.cmu.cs.ls.keymaerax.hydra.AgendaAwesomeResponse
import _root_.edu.cmu.cs.ls.keymaerax.hydra.SQLite.SQLiteDB
import _root_.edu.cmu.cs.ls.keymaerax.tactics.{Position, Augmentors, PosInExpr, AxiomIndex}
import _root_.edu.cmu.cs.ls.keymaerax.tacticsinterface.TacticDebugger.DebuggerListener
import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.main.JsonSchemaFactory
import edu.cmu.cs.ls.keymaerax.api.{ComponentConfig, KeYmaeraInterface}
import edu.cmu.cs.ls.keymaerax.api.KeYmaeraInterface.TaskManagement
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.launcher.KeYmaeraX._
import edu.cmu.cs.ls.keymaerax.parser.KeYmaeraXProblemParser
import edu.cmu.cs.ls.keymaerax.tactics.Tactics.Tactic
import edu.cmu.cs.ls.keymaerax.tactics.{ArithmeticTacticsImpl, TacticExceptionListener, Tactics}
import edu.cmu.cs.ls.keymaerax.tacticsinterface.CLParser
import edu.cmu.cs.ls.keymaerax.tools.Mathematica

import scala.io.Source
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.reflect.runtime._
import scala.tools.reflect.{ToolBoxError, ToolBox}


import scala.reflect.runtime._

/**
 * A Request should handle all expensive computation as well as all
 * possible side-effects of a request (e.g. updating the database), but should
 * not modify the internal state of the HyDRA server (e.g. do not update the
 * event queue).
 *
 * Requests objects should do work after getResultingUpdates is called,
 * not during object construction.
 *
 * Request.getResultingUpdates might be run from a new thread.
 */
sealed trait Request {
  def getResultingResponses() : List[Response] //see Response.scala.

  def currentDate() : String = {
    val format = new SimpleDateFormat("d-M-y")
    format.format(Calendar.getInstance().getTime())
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Users
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class CreateUserRequest(db : DBAbstraction, username : String, password:String) extends Request {
  override def getResultingResponses() = {
    val userExists = db.userExists(username)
    if(!userExists) db.createUser(username,password)
    new BooleanResponse(!userExists) :: Nil
  }
}

class LoginRequest(db : DBAbstraction, username : String, password : String) extends Request {
  override def getResultingResponses(): List[Response] = {
    new LoginResponse(db.checkPassword(username, password), username) ::  Nil
  }
}

class ProofsForUserRequest(db : DBAbstraction, userId: String) extends Request {
  def getResultingResponses() = {
    val proofs = db.getProofsForUser(userId).map(proof =>
      (proof._1, KeYmaeraInterface.getTaskLoadStatus(proof._1.proofId.toString).toString.toLowerCase))
    new ProofListResponse(proofs) :: Nil
  }
}

class UpdateProofNameRequest(db : DBAbstraction, proofId : String, newName : String) extends Request {
  def getResultingResponses() = {
    val proof = db.getProofInfo(proofId)
    db.updateProofName(proofId, newName)
    new UpdateProofNameResponse(proofId, newName) :: Nil
  }
}

/**
 * Returns an object containing all information necessary to fill out the global template (e.g., the "new events" bubble)
 * @param db
 * @param userId
 */
class DashInfoRequest(db : DBAbstraction, userId : String) extends Request{
  override def getResultingResponses() : List[Response] = {
    val openProofCount : Int = db.openProofs(userId).length
    val allModelsCount: Int = db.getModelList(userId).length
    val provedModelsCount: Int = db.getModelList(userId).count(m => db.getProofsForModel(m.modelId).exists(_.closed))

    new DashInfoResponse(openProofCount, allModelsCount, provedModelsCount) :: Nil
  }
}


class CounterExampleRequest(db : DBAbstraction, userId : String, proofId : String, nodeId: String) extends Request {
  override def getResultingResponses() : List[Response] = {
    val node = TaskManagement.getNode(proofId, nodeId) match {
      case Some(node) => node
      case None => throw new IllegalStateException("No proofNode for " + nodeId + " in proof " + proofId)
    }
    val mathematica = new Mathematica
    mathematica.init(db.getConfiguration("mathematica").config)
    val cntEx = ArithmeticTacticsImpl.showCounterExample(mathematica, node)
    mathematica.shutdown()
    new CounterExampleResponse(cntEx) :: Nil
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// System Configuration
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class KyxConfigRequest(db: DBAbstraction) extends Request {
  val newline = "\n"
  override def getResultingResponses() : List[Response] = {
    val mathConfig = db.getConfiguration("mathematica").config
    // keymaera X version
    val kyxConfig = "KeYmaera X version: " + VERSION + newline +
      "Java version: " + System.getProperty("java.runtime.version") + " with " + System.getProperty("sun.arch.data.model") + " bits" + newline +
      "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + newline +
      "LinkName: " + mathConfig.apply("linkName") + newline +
      "jlinkLibDir: " + mathConfig.apply("jlinkLibDir")
    new KyxConfigResponse(kyxConfig) :: Nil
  }
}

class KeymaeraXVersionRequest() extends Request {
  override def getResultingResponses() : List[Response] = {
    val keymaeraXVersion = VERSION
    val (upToDate, latestVersion) = UpdateChecker.getVersionStatus() match {
      case Some((upToDate, latestVersion)) => (Some(upToDate), Some(latestVersion))
      case _ => (None, None)
    }
    new KeymaeraXVersionResponse(keymaeraXVersion, upToDate, latestVersion) :: Nil
  }
}

class ConfigureMathematicaRequest(db : DBAbstraction, linkName : String, jlinkLibFileName : String) extends Request {
  private def isLinkNameCorrect(linkNameFile: java.io.File): Boolean = {
    linkNameFile.getName == "MathKernel" || linkNameFile.getName == "MathKernel.exe"
  }

  private def isJLinkLibFileCorrect(jlinkFile: java.io.File, jlinkLibDir : java.io.File): Boolean = {
    (jlinkFile.getName == "libJLinkNativeLibrary.jnilib" || jlinkFile.getName == "JLinkNativeLibrary.dll" ||
      jlinkFile.getName == "libJLinkNativeLibrary.so") && jlinkLibDir.exists() && jlinkLibDir.isDirectory
  }

  override def getResultingResponses(): List[Response] = {
    try {
      //check to make sure the indicated files exist and point to the correct files.
      val linkNameFile = new java.io.File(linkName)
      val jlinkLibFile = new java.io.File(jlinkLibFileName)
      val jlinkLibDir : java.io.File = jlinkLibFile.getParentFile
      val linkNameExists = isLinkNameCorrect(linkNameFile) && linkNameFile.exists()
      val jlinkLibFileExists = isJLinkLibFileCorrect(jlinkLibFile, jlinkLibDir) && jlinkLibFile.exists()

      if(!linkNameExists || !jlinkLibFileExists) {
        // look for the largest prefix that does exist
        var linkNamePrefix = linkNameFile
        while (!linkNamePrefix.exists && linkNamePrefix.getParent != null) {
          linkNamePrefix = new java.io.File(linkNamePrefix.getParent)
        }

        new ConfigureMathematicaResponse(
          if (linkNamePrefix.exists()) linkNamePrefix.toString else "",
          if (jlinkLibDir.exists()) jlinkLibDir.toString else "", false) :: Nil
      }
      else {
        val originalConfig = db.getConfiguration("mathematica")

        val configMap = scala.collection.immutable.Map("linkName" -> linkName, "jlinkLibDir" -> jlinkLibDir.getAbsolutePath)
        val newConfig = new ConfigurationPOJO("mathematica", configMap)

        db.updateConfiguration(newConfig)

        try {
          if(!(new File(linkName).exists() || !jlinkLibFile.exists())) throw new FileNotFoundException()
          ComponentConfig.keymaeraInitializer.initMathematicaFromDB() //um.
          new ConfigureMathematicaResponse(linkName, jlinkLibDir.getAbsolutePath, true) :: Nil
        }
        catch {
          case e : FileNotFoundException => {
            db.updateConfiguration(originalConfig)
            e.printStackTrace()
            new ConfigureMathematicaResponse(linkName, jlinkLibDir.getAbsolutePath, false) :: Nil
          }

          case e : Exception => {
            new ErrorResponse(e) :: Nil
          }
        }


      }
    }
    catch {
      case e : Exception => new ErrorResponse(e) :: Nil
    }
  }
}

class GetMathematicaConfigSuggestionRequest(db : DBAbstraction) extends Request {
  override def getResultingResponses(): List[Response] = {
    val reader = this.getClass.getResourceAsStream("/config/potentialMathematicaPaths.json")
    val contents : String = Source.fromInputStream(reader).getLines().foldLeft("")((file, line) => file + "\n" + line)
    val source : JsArray = contents.parseJson.asInstanceOf[JsArray]

    // TODO provide classes and spray JSON protocol to convert
    val os = System.getProperty("os.name")
    val osKey = osKeyOf(os.toLowerCase)
    val osPathGuesses = source.elements.find(osCfg => osCfg.asJsObject.getFields("os").head.convertTo[String] == osKey) match {
      case Some(opg) => opg.asJsObject.getFields("mathematicaPaths").head.convertTo[List[JsObject]]
      case None => throw new IllegalStateException("No default configuration for Unknown OS")
    }

    val pathTuples = osPathGuesses.map(osPath =>
      (osPath.getFields("version").head.convertTo[String],
       osPath.getFields("kernelPath").head.convertTo[String],
       osPath.getFields("kernelName").head.convertTo[String],
       osPath.getFields("jlinkPath").head.convertTo[String],
       osPath.getFields("jlinkName").head.convertTo[String]))

    val suggestion = pathTuples.find(path => new java.io.File(path._2 + path._3).exists &&
        new java.io.File(path._4 + path._5).exists) match {
      case Some(s) => s
      case None => pathTuples.head // use the first configuration as suggestion when nothing else matches
    }

    new MathematicaConfigSuggestionResponse(os, suggestion._1, suggestion._2, suggestion._3, suggestion._4, suggestion._5) :: Nil
  }

  private def osKeyOf(osName: String): String = {
    if (osName.contains("win")) "Windows"
    else if (osName.contains("mac")) "MacOS"
    else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) "Unix"
    else "Unknown"
  }
}

class GetMathematicaConfigurationRequest(db : DBAbstraction) extends Request {
  override def getResultingResponses(): List[Response] = {
    val config = db.getConfiguration("mathematica").config
    val osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH)
    val jlinkLibFile = {
      if(osName.contains("win")) "JLinkNativeLibrary.dll"
      else if(osName.contains("mac")) "libJLinkNativeLibrary.jnilib"
      else if(osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) "libJLinkNativeLibrary.so"
      else "Unknown"
    }
    if (config.contains("linkName") && config.contains("jlinkLibDir")) {
      new MathematicaConfigurationResponse(config("linkName"), config("jlinkLibDir") + File.separator + jlinkLibFile) :: Nil
    } else {
      new MathematicaConfigurationResponse("", "") :: Nil
    }
  }
}

class MathematicaStatusRequest(db : DBAbstraction) extends Request {
  override def getResultingResponses(): List[Response] = {
    val config = db.getConfiguration("mathematica").config
    new MathematicaStatusResponse(config.contains("linkName") && config.contains("jlinkLibDir")) :: Nil
  }
}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Models
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class CreateModelRequest(db : DBAbstraction, userId : String, nameOfModel : String, keyFileContents : String) extends Request {
  private var createdId : Option[String] = None

  def getModelId = createdId match {
    case Some(s) => s
    case None => throw new IllegalStateException("Requested created model ID before calling getResultingResponses, or else an error occurred during creation.")
  }

  def getResultingResponses() = {
    try {
      //Return the resulting response.
      KeYmaeraXProblemParser(keyFileContents) match {
        case f : Formula => {
          createdId = db.createModel(userId, nameOfModel, keyFileContents, currentDate()).map(x => x.toString)
          new BooleanResponse(createdId.isDefined) :: Nil
        }
        case a => new ErrorResponse(new Exception("TODO pass back the parse error.")) :: Nil //TODO-nrf pass back useful parser error messages.
      }


    }
    catch {
      case e:Exception => e.printStackTrace(); new ErrorResponse(e) :: Nil
    }
  }
}

class GetModelListRequest(db : DBAbstraction, userId : String) extends Request {
  def getResultingResponses() = {
    new ModelListResponse(db.getModelList(userId)) :: Nil
  }
}

class GetModelRequest(db : DBAbstraction, userId : String, modelId : String) extends Request {
  def getResultingResponses() = {
    val model = db.getModel(modelId)
    new GetModelResponse(model) :: Nil
  }
}

class GetModelTacticRequest(db : DBAbstraction, userId : String, modelId : String) extends Request {
  def getResultingResponses() = {
    val model = db.getModel(modelId)
    new GetModelTacticResponse(model) :: Nil
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Proofs of models
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class CreateProofRequest(db : DBAbstraction, userId : String, modelId : String, name : String, description : String)
 extends Request {
  private var proofId : Option[String] = None

  def getProofId = proofId match {
    case Some(s) => s
    case None => throw new IllegalStateException("The ID of the created proof was requested before getResultingResponses was called.")
  }
  def getResultingResponses() = {
    proofId = Some(db.createProofForModel(modelId, name, description, currentDate()))

    // Create a "task" for the model associated with this proof.
    val keyFile = db.getModel(modelId).keyFile
    KeYmaeraInterface.addTask(proofId.get, keyFile)

    new CreatedIdResponse(proofId.get) :: Nil
  }
}

class ProofsForModelRequest(db : DBAbstraction, modelId: String) extends Request {
  def getResultingResponses() = {
    val proofs = db.getProofsForModel(modelId).map(proof =>
      (proof, KeYmaeraInterface.getTaskLoadStatus(proof.proofId.toString).toString.toLowerCase))
    new ProofListResponse(proofs) :: Nil
  }
}

class OpenProofRequest(db : DBAbstraction, userId : String, proofId : String, wait : Boolean = false) extends Request {
  def getResultingResponses() = {
    /* @todo Total cop-out to help the UI run until we write something better */
    (new OpenProofResponse(db.getProofInfo(proofId), TaskManagement.TaskLoadStatus.Loaded.toString.toLowerCase())) :: Nil
  }
//  {
//    val proof = db.getProofInfo(proofId)
//
//    TaskManagement.startLoadingTask(proof.proofId)
//
//    val t = new Thread(new Runnable() {
//      override def run(): Unit = {
//        if (!KeYmaeraInterface.containsTask(proof.proofId)) {
//          val model = db.getModel(proof.modelId)
//          KeYmaeraInterface.addTask(proof.proofId, model.keyFile)
//
//          val steps: List[AbstractDispatchedPOJO] = db.getProofSteps(proof.proofId).map(step => db.getDispatchedTermOrTactic(step).getOrElse(throw new Exception("Expected to find tactic inst or term with id " + step)))
//          if (steps.nonEmpty) {
//            steps.head match {
//              case firstStep: DispatchedTacticPOJO => {
//                //@todo thead the actual exception to the UI through the database via an additional "errorThrown" column on proof.
//                val exnHandler = new TacticExceptionListener {
//                  override def acceptTacticException(tactic: Tactic, exn: Exception): Unit = {
//                    //@todo not sure if this is the correct step ID.
//                    db.updateDispatchedTacticStatus(firstStep.id, DispatchedTacticStatus.Error) //@todo not sure if this is the correct step ID.
//                  }
//                }
//                KeYmaeraInterface.runTactic(proof.proofId, firstStep.nodeId, firstStep.tacticsId, firstStep.formulaId,
//                  firstStep.id, Some(tacticCompleted(steps.toArray, 1)), exnHandler, firstStep.input, firstStep.auto)
//              }
//              case firstStep: DispatchedCLTermPOJO => {
//                //@todo thead the actual exception to the UI through the database via an additional "errorThrown" column on proof.
//                val exnHandler = new TacticExceptionListener {
//                  override def acceptTacticException(tactic: Tactic, exn: Exception): Unit = {
//                    //@todo not sure if this is the correct step ID.
//                    db.updateDispatchedTacticStatus(firstStep.id, DispatchedTacticStatus.Error)
//                  }
//                }
//                KeYmaeraInterface.runTerm(firstStep.clTerm, firstStep.proofId, firstStep.nodeId, firstStep.clTerm, Some(tacticCompleted(steps.toArray, 1)), exnHandler)
//              }
//            }
//          } else {
//            TaskManagement.finishedLoadingTask(proofId)
//          }
//        } else {
//          TaskManagement.finishedLoadingTask(proofId)
//        }
//      }
//    })
//
//    if(!wait) t.start()
//    else t.run()
//
//    val status = KeYmaeraInterface.getTaskLoadStatus(proofId)
//    new OpenProofResponse(proof, status.toString.toLowerCase) :: Nil
//  }

  //@todo To improve readability, move the once-unwinding above and this implementation into a single function.
//  private def tacticCompleted(steps : Array[AbstractDispatchedPOJO], next : Int)(tId: String)(proofId: String, nId: Option[String],
//                                                                               tacticId: String) = ???
//  {
//    if (next < steps.length) {
//
//      steps(next) match {
//        case nextStep : DispatchedTacticPOJO => {
//          //@todo thead the actual exception to the UI through the database via an additional "errorThrown" column on proof.
//          val exnHandler = new TacticExceptionListener {
//            override def acceptTacticException(tactic: Tactic, exn: Exception): Unit = {
//              db.updateDispatchedTacticStatus(tId, DispatchedTacticStatus.Error) //@todo not sure if this should be proofId, tId, or tacticId?
//            }
//          }
//          KeYmaeraInterface.runTactic(proofId, nextStep.nodeId, nextStep.tacticsId, nextStep.formulaId, nextStep.id,
//            Some(tacticCompleted(steps, next + 1)), exnHandler, nextStep.input, nextStep.auto)
//        }
//        case nextStep : DispatchedCLTermPOJO => {
//          //@todo thead the actual exception to the UI through the database via an additional "errorThrown" column on proof.
//          val exnHandler = new TacticExceptionListener {
//            override def acceptTacticException(tactic: Tactic, exn: Exception): Unit = {
//              db.updateDispatchedCLTermStatus(tId, DispatchedTacticStatus.Error) //@todo not sure if this should be proofId, tId, or tacticId?
//            }
//          }
//          KeYmaeraInterface.runTerm(nextStep.id, nextStep.proofId, nextStep.nodeId, nextStep.clTerm, Some(tacticCompleted(steps, next + 1)), exnHandler)
//        }
//      }
//    } else {
//      println("*******************\nFinished loading proof " + proofId + "\n*******************")
//      TaskManagement.finishedLoadingTask(proofId)
//    }
//  }
}

/**
 * Gets all tasks of the specified proof. A task is some work the user has to do. It is not a KeYmaera task!
 * @param db Access to the database.
 * @param userId Identifies the user.
 * @param proofId Identifies the proof.
 */
class GetProofAgendaRequest(db : DBAbstraction, userId : String, proofId : String) extends Request {
  def getResultingResponses() = {
    // TODO refactor into template method for all tasks that interact with the proof
    if (!KeYmaeraInterface.containsTask(proofId)) {
      if (!KeYmaeraInterface.isLoadingTask(proofId)) {
        new ProofNotLoadedResponse(proofId) :: Nil
      } else {
        new ProofIsLoadingResponse(proofId) :: Nil
      }
    } else {
      val proof = db.getProofInfo(proofId)
      try {

        val openGoalIds = KeYmaeraInterface.getOpenGoals(proofId)

        val result = openGoalIds.map(g => KeYmaeraInterface.getSubtree(proof.proofId.toString, Some(g), 0, true) match {
          case Some(proofNode) => (proof, g, proofNode)
          case None => throw new IllegalStateException("No subtree for goal " + g + " in proof " + proof.proofId)
        })
        new ProofAgendaResponse(result) :: Nil
      }
      catch {
        case e : IllegalStateException => {
          new ProofAgendaResponse(List()) :: Nil
        }
      }
    }
  }
}

/**
  * Gets all tasks of the specified proof. A task is some work the user has to do. It is not a KeYmaera task!
  * @param db Access to the database.
  * @param userId Identifies the user.
  * @param proofId Identifies the proof.
  */
class GetAgendaAwesomeRequest(db : DBAbstraction, userId : String, proofId : String) extends Request {
  def getResultingResponses() = {
    val response = new AgendaAwesomeResponse(ProofTree.ofTrace(db.getExecutionTrace(proofId.toInt)))
    response :: Nil
  }
}

class ProofTaskParentRequest(db: DBAbstraction, userId: String, proofId: String, nodeId: String) extends Request {
  def getResultingResponses() = {
    val tree = ProofTree.ofTrace(db.getExecutionTrace(proofId.toInt))
    tree.parent(nodeId) match {
      case None => throw new Exception("Tried to get parent of node " + nodeId + " which has no parent")
      case Some(parent) =>
        val response = new ProofTaskParentResponse(parent)
        response :: Nil
    }
  }
}

case class GetPathAllRequest(db: DBAbstraction, userId: String, proofId: String, nodeId: String) extends Request {
  def getResultingResponses() = {
    var tree: Option[TreeNode] = ProofTree.ofTrace(db.getExecutionTrace(proofId.toInt)).findNode(nodeId)
    var path: List[TreeNode] = Nil
    while (tree.nonEmpty) {
      path = tree.get :: path
      tree = tree.get.parent
    }
    /* To start with, always send the whole path. */
    val parentsRemaining = 0
    val response = new GetPathAllResponse(path.reverse, parentsRemaining)
    response :: Nil
  }
}

case class GetBranchRootRequest(db: DBAbstraction, userId: String, proofId: String, nodeId: String) extends Request {
  def getResultingResponses() = {
    val node = ProofTree.ofTrace(db.getExecutionTrace(proofId.toInt)).nodes.find({case node => node.id.toString == nodeId})
    node match {
      case None => throw new Exception("Node not found")
      case Some(node) =>
        var currNode = node
        var done = false
        while (currNode.parent.nonEmpty && !done) {
          currNode = currNode.parent.get
          /* Don't stop at the first node just because it branches (it may be the end of one branch and the start of the
          * next), but if we see branching anywhere else we've found the end of our branch. */
          if (currNode.children.size > 1) {
            done = true
          }
        }
          new GetBranchRootResponse(currNode) :: Nil
    }
  }
}

/**
 * Searches for tactics that are applicable to the specified formula. The sequent, which contains this formula, is
 * identified by the proof ID and the node ID.
 * @param db Access to the database.
 * @param userId Identifies the user.
 * @param proofId Identifies the proof.
 * @param nodeId Identifies the node. If None, request the tactics of the "root" node of the task.
 * @param formulaId Identifies the formula in the sequent on which to apply the tactic.
 */
class GetApplicableTacticsRequest(db : DBAbstraction, userId : String, proofId : String, nodeId : Option[String], formulaId : Option[String]) extends Request {
  def getResultingResponses() = ???
//  {
//    val applicableTactics = KeYmaeraInterface.getApplicableTactics(proofId, nodeId, formulaId)
//      .map(tId => db.getTactic(tId) match {
//        case Some(t) => t
//        case None => throw new IllegalStateException("Tactic " + tId + " not in database")
//    }).toList
//    new ApplicableTacticsResponse(applicableTactics) :: Nil
//  }
}

class GetApplicableAxiomsRequest(db:DBAbstraction, userId: String, proofId: String, nodeId: String, pos:Position) extends Request {
  def getResultingResponses() = {
    import edu.cmu.cs.ls.keymaerax.tactics.Augmentors._
    val sequent = ProofTree.ofTrace(db.getExecutionTrace(proofId.toInt)).findNode(nodeId).get.sequent
    val subFormula = sequent.sub(pos).get
    val axioms = AxiomIndex.axiomsFor(subFormula, Some(pos)).map{case axiom => DerivationInfo(axiom)}
    new ApplicableAxiomsResponse(axioms) :: Nil
  }
}
/**
 * Runs the specified tactic on the formula with the specified ID. The sequent, which contains this formula, is
 * identified by the proof ID and the node ID.
 * @param db Access to the database.
 * @param userId Identifies the user.
 * @param proofId Identifies the proof.
 * @param nodeId Identifies the node. If None, the tactic is run on the "root" node of the task.
 * @param formulaId Identifies the formula in the sequent on which to apply the tactic.
 * @param tacticName Identifies the tactic to run.
 * @param input The input to the tactic.
 * @param auto Indicates the degree of automation for position tactics. If formulaId != None, only SaturateCurrent is allowed.
 */
class RunTacticByNameRequest(db : DBAbstraction, userId : String, proofId : String, nodeId : Option[String],
                             formulaId : Option[String], tacticName : String, input : Map[Int,String],
                             auto: Option[String] = None) extends Request {
  def getResultingResponses() = ???
//  {
//    val tacticId = db.getTacticByName(tacticName) match {
//      case Some(t) => t.tacticId
//      case None => throw new IllegalArgumentException("Tactic name " + tacticName + " unknown")
//    }
//    new RunTacticRequest(db, userId, proofId, nodeId, formulaId, tacticId, input, auto).getResultingResponses()
//  }
}

/**
 * Runs the specified tactic on the formula with the specified ID. The sequent, which contains this formula, is
 * identified by the proof ID and the node ID.
 * @param db Access to the database.
 * @param userId Identifies the user.
 * @param proofId Identifies the proof.
 * @param nodeId Identifies the node. If None, the tactic is run on the "root" node of the task.
 * @param formulaId Identifies the formula in the sequent on which to apply the tactic.
 * @param tacticId Identifies the tactic to run.
 * @param input The input to the tactic.
 * @param auto Indicates the degree of automation for position tactics. If formulaId != None, only SaturateCurrent is allowed.
 * @see KeYmaeraInterface.PositionTacticAutomation for valid values of auto
 */
class RunTacticRequest(db : DBAbstraction, userId : String, proofId : String, nodeId : Option[String],
                       formulaId : Option[String], tacticId : String, input : Map[Int,String],
                       auto: Option[String] = None) extends Request {
  def getResultingResponses() = ???
//  {
//    val automation = auto match {
//      case Some(s) => Some(KeYmaeraInterface.PositionTacticAutomation.withName(s.toLowerCase))
//      case _ => None
//    }
//    val tId = db.createDispatchedTactics(proofId, nodeId, formulaId, tacticId, input, automation, DispatchedTacticStatus.Prepared)
//    db.updateDispatchedTactics(new DispatchedTacticPOJO(tId, proofId, nodeId, formulaId, tacticId, input, automation,
//      DispatchedTacticStatus.Running))
//
//    new Thread(new Runnable() {
//      override def run(): Unit = {
//        val exnHandler = new TacticExceptionListener {
//          override def acceptTacticException(tactic: Tactic, exn: Exception): Unit = {
//            db.synchronized({
//              db.updateDispatchedTacticStatus(tId, DispatchedTacticStatus.Error)
//            })
//            println("[HyDRA] Caught exception in Request.scala after running a tactic: " + tactic.name + " with tactic ID: " + tId)
//          }
//        }
//
//        try {
//          KeYmaeraInterface.runTactic(proofId, nodeId, tacticId, formulaId, tId,
//            Some(tacticCompleted(db)), exnHandler, input, automation)
//        }
//        catch {
//          case e : Exception => db.synchronized({
//            db.updateDispatchedTacticStatus(tId, DispatchedTacticStatus.Error)
//            throw e
//          })
//        }
//      }
//    }).start()
//
//    new DispatchedTacticResponse(new DispatchedTacticPOJO(tId, proofId, nodeId, formulaId, tacticId, input, automation,
//      DispatchedTacticStatus.Running)) :: Nil
//  }
//
//  private def tacticCompleted(db : DBAbstraction)(tId: String)(proofId: String, nId: Option[String], tacticId: String) {
//    db.synchronized {
//      // Do not change the status to finished unless the current status is different from Error.
//      // This won't prevent re-running a tactic that failed incidentally because when the tactic is
//      // re-run its current status will change to Running.
//      if(!db.getDispatchedTactics(tId).get.status.equals(DispatchedTacticStatus.Error))
//        db.updateProofOnTacticCompletion(proofId, tId)
//    }
//  }
}

case class BelleTermInput(value: String, spec:Option[ArgInfo])

/* If pos is Some then belleTerm must parse to a PositionTactic, else if pos is None belleTerm must parse
* to a Tactic */
class RunBelleTermRequest(db: DBAbstraction, userId: String, proofId: String, nodeId: String, belleTerm: String,
                         pos: Option[PositionLocator], inputs:List[BelleTermInput] = Nil) extends Request {
  val fullExpr = {
    val paramStrings = inputs.map{
      case BelleTermInput(value, Some(_:FormulaArg)) => "{`"+value+"`}"
      case BelleTermInput(value, None) => value
    }
    if(inputs.isEmpty) belleTerm
    else belleTerm + "(" + paramStrings.mkString(",") + ")"
  }

  /* Try to figure out the most intuitive inference rule to display for this tactic. If the user asks us "StepAt" then
   * we should use the StepAt logic to figure out which rule is actually being applied. Otherwise just ask AxiomInfo */
  private def getRuleName(tacticId: String, sequent:Sequent, locator:Option[PositionLocator]): String = {
    val pos = locator match {case Some(Fixed(p, _, _)) => Some(p) case _ => None}
    tacticId.toLowerCase() match {
      case ("step" | "stepat") =>
        val fml = sequent(pos.get)
        AxiomIndex.axiomFor(fml, pos) match {
          case Some(axiom) => DerivationInfo(axiom).display.name
          case None => tacticId
        }
      case _ => try {
        TacticInfo(tacticId).display.name
      } catch {
        case _ => "Tactic"
      }
    }
  }

  def getResultingResponses() = {
    BTacticParser(fullExpr) match {
      case None => throw new ProverException("Invalid Bellerophon expression:  " + belleTerm)
      case Some(expr) =>
        val appliedExpr:BelleExpr =
          (pos, expr) match {
            case (None, _:AtPosition[BelleExpr]) =>
              throw new ProverException("Can't run a positional tactic without specifying a position")
            case (None, _) => expr
            case (Some(position), expr:BelleExpr) =>
              if(expr.isInstanceOf[AtPosition[BelleExpr]]) {
                expr.asInstanceOf[AtPosition[BelleExpr]](position)
              }
              else expr
            case (pos, expr) => println ("pos " + pos.getClass.getName + ", expr " +  expr.getClass.getName); throw new ProverException("Match error")
        }
        val trace = db.getExecutionTrace(proofId.toInt)
        val tree = ProofTree.ofTrace(trace)
        val branch = tree.goalIndex(nodeId)
        val node =
          tree.findNode(nodeId) match {
            case None => throw new ProverException("Invalid node " + nodeId)
            case Some(node) => node
          }
        val ruleName = getRuleName(belleTerm, node.sequent, pos)
        val localProvable = Provable.startProof(node.sequent)
        val globalProvable =
          trace.steps match {
            case Nil => localProvable
            case steps => steps.last.output.getOrElse(steps.last.input)
          }
        val listener = new DebuggerListener(db, proofId.toInt, trace.executionId.toInt, trace.lastStepId, globalProvable, trace.alternativeOrder, branch, recursive = false, ruleName)
        val executor = BellerophonTacticExecutor.defaultExecutor
        val taskId = executor.schedule (appliedExpr, BelleProvable(localProvable), List(listener))
        val finalProvable = executor.wait(taskId) match {
          case Some(Left(BelleProvable(outputProvable))) => outputProvable
          case Some(Right(error: BelleError)) => throw new ProverException("Tactic failed with error: " + error.getMessage, error.getCause)
          case None => throw new ProverException("Could not get tactic result - execution cancelled? ")
        }
        val finalTree = ProofTree.ofTrace(db.getExecutionTrace(proofId.toInt))
        val parentNode = finalTree.findNode(nodeId).get
        val response = new RunBelleTermResponse(parentNode, parentNode.children)
        response :: Nil
    }
  }
}

class PruneBelowRequest(db : DBAbstraction, userId : String, proofId : String, nodeId : String, goalId : String) extends Request {
  def prune(trace: ExecutionTrace, pruned:Set[Int]): ExecutionTrace = {
    val tr = trace.steps.filter{case step => step.stepId >= pruned.min}
    val pruneRoot = tr.head
    val prunedGoals = pruneRoot.input.subgoals.map{case _ => false}
    val (_ ,outputSteps) =
      tr.foldLeft((prunedGoals, Nil:List[ExecutionStep])){case ((prunedGoals, acc), step) =>
        val delta = step.output.get.subgoals.length - step.input.subgoals.length
        val branch = step.branch
        val updatedGoals =
          if (delta == 0) prunedGoals
          else if (delta == -1) {
            prunedGoals.slice(0, branch) ++ prunedGoals.slice(branch + 1, prunedGoals.length)
          } else {
            prunedGoals ++ Array.tabulate(delta){case _ => pruned.contains(step.stepId)}
          }
        val outputBranch =
          prunedGoals.zipWithIndex.count{case(b,i) => i < branch && !b}
          + (if(step.branch >= pruneRoot.branch) 1 else 0)

        if(pruned.contains(step.stepId)) {
          (updatedGoals, acc)
        } else {
          // @todo update rest of args correctly
          (updatedGoals, ExecutionStep(step.stepId, step.input, step.output, outputBranch, step.alternativeOrder, step.rule) :: acc)
        }
      }
    ExecutionTrace(trace.proofId, trace.executionId, trace.conclusion, outputSteps.reverse)
  }

  def getResultingResponses() = {
    val trace = db.getExecutionTrace(proofId.toInt)
    val tree = ProofTree.ofTrace(trace)
    val prunedSteps = tree.allDescendants(goalId).flatMap{case node => node.endStep.toList}
    if(prunedSteps.isEmpty) {
      throw new Exception("No steps under node. Nothing to do.")
    }
    val prunedStepIds = prunedSteps.map{case step => step.stepId}.toSet
    val prunedTrace = prune(trace, prunedStepIds)
    db.addAlternative(prunedStepIds.min, prunedTrace)
    val goalNode = tree.findNode(goalId).get
    val item = AgendaItem(goalNode.id.toString, "Unnamed Item", proofId.toString, goalNode)
    val response = new PruneBelowResponse(item)
    response :: Nil
  }
}

class RunCLTermRequest(db : DBAbstraction, userId : String, proofId : String, nodeId : Option[String], clTerm : String) extends Request {
  def getResultingResponses() = ???
//  {
//    try {
//      //Make sure that the tactic is going to construct and parse before updating the database.
//      CLInterpreter.construct(CLParser(clTerm).getOrElse(throw new Exception("Failed to parse CL term: " + clTerm)))
//
//      val termId = db.createDispatchedCLTerm(proofId, nodeId, clTerm)
//      //Update status to running.
//      val dispatchedTerm = new DispatchedCLTermPOJO(termId, proofId, nodeId, clTerm, Some(DispatchedTacticStatus.Running))
//      db.updateDispatchedCLTerm(dispatchedTerm)
//      //Run the tactic.
//      new Thread(new Runnable() {
//        val exnHandler = new TacticExceptionListener {
//          override def acceptTacticException(tactic: Tactic, exn: Exception): Unit = db.updateDispatchedCLTermStatus(termId, DispatchedTacticStatus.Error)
//        }
//
//        override def run(): Unit = try {
//          KeYmaeraInterface.runTerm(termId, proofId, nodeId, clTerm, Some(completionContinuation(db)), exnHandler)
//        } catch {
//          case e : Exception => {
//            //@todo update the database when an error for the running cl term. This is how it's done but for built-in tactics (I think): db.updateDispatchedTacticStatus(termId, "error")
//            throw e
//          }
//        }
//      }).start()
//
//      //Construct the response to this request.
//      new DispatchedCLTermResponse(dispatchedTerm):: Nil
//    }
//    catch {
//      case e:Exception => { e.printStackTrace(); new ErrorResponse(e) :: Nil }
//    }
//  }

  private def completionContinuation(db : DBAbstraction)(termId : String)(proodId : String, nodeId : Option[String], clTerm : String): Unit = ???
//  {
//    db.synchronized {
//      db.updateProofOnCLTermCompletion(proofId, termId)
//    }
//  }
}

class GetDispatchedTacticRequest(db : DBAbstraction, userId : String, proofId : String, tId : String) extends Request {
  def getResultingResponses() = ???
//  {
//    try {
//      db.getDispatchedTactics(tId) match {
//        case Some(d) => new DispatchedTacticResponse(d) :: Nil
//        case _ => new ErrorResponse(new IllegalArgumentException("Cannot find dispatched tactic with ID: " + tId)) :: Nil
//      }
//    }
//    catch {
//      case e:Exception => new ErrorResponse(e) :: Nil //@todo indicate tactic is running?
//    }
//  }
}

class GetDispatchedTermRequest(db : DBAbstraction, userId : String, proofId : String, termId : String) extends Request {
  def getResultingResponses() = ???
//  {
//    try {
//      db.getDispatchedCLTerm(termId) match {
//        case Some(d) => new DispatchedCLTermResponse(d) :: Nil
//        case _ => new ErrorResponse(new IllegalArgumentException("Cannot find dispatched term with ID: " + termId)) :: Nil
//      }
//    }
//    catch {
//      case e:Exception => new ErrorResponse(e) :: Nil //@todo indicate term is running?
//    }
//  }
}


class GetProofTreeRequest(db : DBAbstraction, userId : String, proofId : String, nodeId : Option[String]) extends Request{
  override def getResultingResponses(): List[Response] = {
    // TODO fetch only one branch, need to refactor UI for this
    val node = KeYmaeraInterface.getSubtree(proofId, nodeId, 1000, false)
    node match {
      case Some(theNode) =>
        val schema = JsonSchemaFactory.byDefault().getJsonSchema(JsonLoader.fromReader(new FileReader("src/main/resources/js/schema/prooftree.js")))
        val report = schema.validate(JsonLoader.fromString(theNode))
        if (report.isSuccess)
          new AngularTreeViewResponse(theNode) :: Nil
        else {
          throw report.iterator().next().asException()
        }
      case None          => new ErrorResponse(new Exception("Could not find a node associated with these id's.")) :: Nil
    }
  }
}

class GetProofHistoryRequest(db : DBAbstraction, userId : String, proofId : String) extends Request {
  override def getResultingResponses(): List[Response] = ???
//  {
//    if(db.getProofInfo(proofId).stepCount!=0) {
//      val steps = db.getProofSteps(proofId).map(step => db.getDispatchedTactics(step)).filter(_.isDefined).map(_.get).
//        map(step => step -> db.getTactic(step.tacticsId).getOrElse(
//        throw new IllegalStateException(s"Proof refers to unknown tactic ${step.tacticsId}")))
//      if (steps.nonEmpty) {
//        new ProofHistoryResponse(steps) :: Nil
//      } else new ErrorResponse(new Exception("Could not find a proof history associated with these ids.")) :: Nil
//    } else Nil
//  }
}

class GetProofNodeInfoRequest(db : DBAbstraction, userId : String, proofId : String, nodeId: Option[String]) extends Request {
  def getResultingResponses() = {
    // TODO refactor into template method for all tasks that interact with the proof
    if (!KeYmaeraInterface.containsTask(proofId)) {
      if (!KeYmaeraInterface.isLoadingTask(proofId)) {
        new ProofNotLoadedResponse(proofId) :: Nil
      } else {
        new ProofIsLoadingResponse(proofId) :: Nil
      }
    } else {
      val proofNode = KeYmaeraInterface.getSubtree(proofId, nodeId, 0, printSequent = true) match {
        case Some(pn) => pn
        case None => throw new IllegalStateException("No subtree for goal " + nodeId + " in proof " + proofId)
      }
      new ProofNodeInfoResponse(proofId, nodeId, proofNode) :: Nil
    }
  }
}

class GetProofLoadStatusRequest(db : DBAbstraction, userId : String, proofId : String) extends Request {
  def getResultingResponses() = {
    if (!KeYmaeraInterface.containsTask(proofId)) {
      if (!KeYmaeraInterface.isLoadingTask(proofId)) {
        new ProofNotLoadedResponse(proofId) :: Nil
      } else {
        new ProofIsLoadingResponse(proofId) :: Nil
      }
    } else {
      if (!KeYmaeraInterface.isLoadingTask(proofId)) {
        new ProofIsLoadedResponse(proofId) :: Nil
      } else {
        new ProofIsLoadingResponse(proofId) :: Nil
      }
    }
  }
}

class GetProofProgressStatusRequest(db: DBAbstraction, userId: String, proofId: String) extends Request {
  def getResultingResponses() = {
    // @todo return Loading/NotLoaded when appropriate
    val proof = db.getProofInfo(proofId)
    new ProofProgressResponse(proofId, isClosed = proof.closed) :: Nil
  }
}

class CheckIsProvedRequest(db: DBAbstraction, userId: String, proofId: String) extends Request {
  def getResultingResponses() = {
    if (!KeYmaeraInterface.containsTask(proofId)) {
      if (!KeYmaeraInterface.isLoadingTask(proofId)) {
        new ProofNotLoadedResponse(proofId) :: Nil
      } else {
        new ProofIsLoadingResponse(proofId) :: Nil
      }
    } else {
      val isProved = KeYmaeraInterface.isProved(proofId)
      new ProofVerificationResponse(proofId, isProved) :: Nil
    }
  }
}


/**
 * like GetProofTreeRequest, but fetches 0 instead of 1000 subnodes.
 * @param db
 * @param proofId
 * @param nodeId
 */
class GetNodeRequest(db : DBAbstraction, proofId : String, nodeId : Option[String]) extends Request {
  override def getResultingResponses(): List[Response] = {
    // TODO fetch only one branch, need to refactor UI for this
    val node = KeYmaeraInterface.getSubtree(proofId, nodeId, 0, true)
    node match {
      case Some(theNode) => new NodeResponse(theNode) :: Nil
      case None => new ErrorResponse(new Exception("Could not find a node associated with these id's.")) :: Nil
    }
  }
}

class IsLicenseAcceptedRequest(db : DBAbstraction) extends Request {
  def getResultingResponses() = {
    new BooleanResponse(
      db.getConfiguration("license").config.contains("accepted") && db.getConfiguration("license").config.get("accepted").get.equals("true")
    ) :: Nil
  }
}

class AcceptLicenseRequest(db : DBAbstraction) extends Request {
  def getResultingResponses() = {
    db.createConfiguration("license")
    val newConfiguration = new ConfigurationPOJO("license", Map("accepted" -> "true"))
    db.updateConfiguration(newConfiguration)
    new BooleanResponse(true) :: Nil
  }
}

/**
 * Returns either a DispatchedCLTermResponse or else an ErrorResponse (if no initialization tactic exists for the model)
 * In the latter case, you should wait until the status of the dispatched term is Finished before taking the user to the proof.
 */
class RunModelInitializationTacticRequest(db : DBAbstraction, userId : String, modelId : String) extends Request {
  override def getResultingResponses() : List[Response] = {
    val model = db.getModel(modelId)
    model.tactic match {
      case Some(tactic) => {
        val initializedProofId = db.createProofForModel(modelId, userId, "Default Proof", new java.util.Date().toString)
        new OpenProofRequest(db, userId, initializedProofId, wait = true).getResultingResponses() //@todo we should do the rest of this inside of a ???
        new RunCLTermRequest(db, userId, initializedProofId, None, tactic).getResultingResponses();

      }
      case None => new ErrorResponse(new Exception("Could not find an initialization tactic")) :: Nil
    }
  }
}


/** Runs the contents of a file as a custom tactic.
  * @todo this implementation is a hack. Specifically, two things need to change if we're going to call this from the user interface itself:
  * @todo getResultingResponses is blocking, which is not at all sustainable if this is called from the user interface.
  * @todo This proofs will not reload -- this is for one-off proving only!
  */
class RunScalaFileRequest(db: DBAbstraction, proofId: String, proof: File) extends Request {
  override def getResultingResponses(): List[Response] = ???
//  {
//    val tacticSource = scala.io.Source.fromFile(proof).mkString
//
//    val cm = universe.runtimeMirror(getClass.getClassLoader)
//    val tb = cm.mkToolBox()
//    val tactic = tb.eval(tb.parse(tacticSource)).asInstanceOf[Tactic]
//
//    //@todo provide a bunch of junk for all of these values, because we won't ever try to re-run this tactic.
//    val nodeID = ""
//    val tacticId = ""
//    val formulaId = Some("")
//    val input = Map[Int,String]()
//    val nodeId = Some("")
//    val automation = None
//    val tId = db.createDispatchedTactics(proofId, nodeId, formulaId, tacticId, input, automation, DispatchedTacticStatus.Prepared)
//
//    val exnHandler = new TacticExceptionListener {
//      override def acceptTacticException(tactic: Tactic, exn: Exception): Unit = {
//        db.synchronized({
//          db.updateDispatchedTacticStatus(tId, DispatchedTacticStatus.Error)
//        })
//        println("[HyDRA] Caught exception in Request.scala after running a tactic: " + tactic.name + " with tactic ID: " + tId)
//      }
//    }
//    def tacticCompleted(db : DBAbstraction)(tId: String)(proofId: String, nId: Option[String], tacticId: String) {
//      db.synchronized {
//        // Do not change the status to finished unless the current status is different from Error.
//        // This won't prevent re-running a tactic that failed incidentally because when the tactic is
//        // re-run its current status will change to Running.
//        if (!db.getDispatchedTactics(tId).get.status.equals(DispatchedTacticStatus.Error))
//          db.updateProofOnTacticCompletion(proofId, tId)
//      }
//    }
//
//    //Run the tactic.
//    try {
//      println("About to run a Scala file with tId" + tId)
//      KeYmaeraInterface.runTactic(proofId, nodeId, tacticId, formulaId, tId,
//        Some(tacticCompleted(db)), exnHandler, input, automation);
//
//      new ErrorResponse(new Exception("Tactic DID complete successfully, but this response should never make it to the UI.")) :: Nil
//    }
//    catch {
//      case e: Exception => db.synchronized({
//        db.updateDispatchedTacticStatus(tId, DispatchedTacticStatus.Error)
//        new ErrorResponse(e) :: Nil
//      })
//    };
//  }
}

/////
// Requests for shutting down KeYmaera if KeYmaera is hosted locally.
/////

class IsLocalInstanceRequest() extends Request {
  override def getResultingResponses(): List[Response] = new BooleanResponse(!Boot.isHosted) :: Nil
}

class ShutdownReqeuest() extends Request {
  override def getResultingResponses() : List[Response] = {
    new Thread() {
      override def run() = {
        try {
          //Tell all scheduled tactics to stop.
          //@todo figure out which of these are actually necessary.
          System.out.flush()
          System.err.flush()
          Tactics.MathematicaScheduler.shutdown()
          Tactics.KeYmaeraScheduler.shutdown()
          System.out.flush()
          System.err.flush()
          Boot.system.shutdown()
          System.out.flush()
          System.err.flush()
          this.synchronized {
            this.wait(4000)
          }
          System.out.flush()
          System.err.flush()
          System.exit(0) //should've already stopped the application by now.
        }
        catch {
          case _ : Exception => System.exit(-1)
        }

      }
    }.start

    new BooleanResponse(true) :: Nil
  }
}

class MockRequest(resourceName: String) extends Request {
  override def getResultingResponses(): List[Response] = new MockResponse(resourceName) :: Nil
}