package edu.cmu.cs.ls.keymaera.hydra

import akka.actor.Actor
import spray.routing._
import spray.http._
import spray.httpx.encoding._
import MediaTypes._
import scala.collection.mutable.HashMap
import spray.json.JsArray

class RestApiActor extends Actor with RestApi {
  def actorRefFactory = context

  //Note: separating the actor and router allows testing of the router without
  //spinning up an actor.
  def receive = runRoute(myRoute)
}

/**
 * RestApi is the API router. See REAMDE.md for a description of the API.
 */
trait RestApi extends HttpService {
  val staticRoute = pathPrefix("static") { get { getFromResourceDirectory("static") } }
  val newUser = pathPrefix("user" / IntNumber / "create") { userid => {
    get {
      val newKey = ServerState.createSession(userid.toString)
      respondWithMediaType(`application/json`) {
        complete("{\"sessionName\": \"" + newKey + "\"}") //TODO-nrf
      }
    }
  }
  }
  /**
   * POST /proofs/< useridid > with data containing the .key file to load
   * GET /proofs/< userid >/< proofid > should load the proof id.
   */
  val createProof = pathPrefix("proofs" / IntNumber) { userid => {
    pathEnd {
      post {
//        decompressRequest()
        entity(as[String]) { keyFileContents => {
          ServerState.createSession(userid.toString)
          val request = new CreateProblemRequest(userid.toString(), keyFileContents)
          val responses = request.getResultingResponses()
          if(responses.length != 1) {
            complete(new ErrorResponse(
              new Exception("CreateProblemRequest generated too many responses"
             )).json.prettyPrint)
          }
          else {
            complete(responses.last.json.prettyPrint)
          }
        }}
      } ~ 
      get {
        val response = new UnimplementedResponse("GET proofs/<useridid>")
        complete(response.json.prettyPrint)
      }
    }
  }}

  /*val runTactic = pathPrefix("user" / IntNumber / "proofs" / Segment / "tactic" / IntNumber) { (userid, proofid, tacticid) => {
    pathEnd {
      post {
//        decompressRequest()
        entity(as[String]) { keyFileContents => {
          val request = new RunTacticRequest(userid.toString(), tacticid, proofid, "0")
          val responses = request.getResultingResponses()
          if(responses.length != 1) {
            complete(new ErrorResponse(
              new Exception("CreateProblemRequest generated too many responses"
             )).json.prettyPrint)
          }
          else {
            complete(responses.last.json.prettyPrint)
          }
        }}
      } ~
      get {
        val response = new UnimplementedResponse("GET proofs/<useridid>")
        complete(response.json.prettyPrint)
      }
    }
  }}*/

  val runTacticNode = pathPrefix("user" / IntNumber / "proofs" / Segment / "node" / Segment / "tactic" / IntNumber) { (userid, proofid, nodeid, tacticid) => {
    pathEnd {
      post {
//        decompressRequest()
        entity(as[String]) { keyFileContents => {
          val request = new RunTacticRequest(userid.toString(), tacticid, proofid, nodeid)
          val responses = request.getResultingResponses()
          if(responses.length != 1) {
            complete(new ErrorResponse(
              new Exception("CreateProblemRequest generated too many responses"
             )).json.prettyPrint)
          }
          else {
            complete(responses.last.json.prettyPrint)
          }
        }}
      } ~
      get {
        val response = new UnimplementedResponse("GET proofs/<useridid>")
        complete(response.json.prettyPrint)
      }
    }
  }}


   val getUpdates = path("user" / IntNumber / "getUpdates" / IntNumber) { (userid, count) =>
     pathEnd {
      post {
        val res = new UpdateResponse(ServerState.getUpdates(userid.toString, count)).json.prettyPrint
        complete(res)
      }
    }
  }

  val routes =
    createProof ::
    runTacticNode ::
    getUpdates ::
    staticRoute ::
    newUser ::
    Nil
  val myRoute = routes.reduce(_ ~ _)
}


/// Leaving the implementation of the old api for reference.
// Note that it's not clear when/if we need respondWithMediaType(`application/json`) ?
//
//  val startSession = path("startSession") {
//    get {
//      val newKey = ServerState.createSession()
//      respondWithMediaType(`application/json`) {
//        complete("{\"sessionName\": \""+newKey+"\"}") //TODO-nrf 
//      }
//    }
//  }
//
//  /**
//   * TODO ew. See comment on ServerState.getUpdates...
//   */
//  val getUpdates = path("getUpdates") {
//    get {
//      respondWithMediaType(`application/json`) {
//        parameter("sessionName", "count") { 
//           (sessionName, count) => complete(ServerState.getUpdates(sessionName, count))
//        }
//      }
//    }
//  }
//  
//  val startNewProblem = path("startNewProblem") {
//    post {
//      parameter("sessionName") { sessionName => {
//        decompressRequest() {
//          entity(as[String]) {
//            problem => {
//              val request = new Problem(sessionName, problem)
//              val result = KeYmaeraClient.serviceRequest(sessionName, request) 
//              complete("[]")
//            }
//          }
//        }
//      }}
//    }
//  }
//  
//  val formulaToString = path("formulaToString") {
//    get {
//      parameter("sessionName", "uid") { (sessionName,uid) => {
//        val request = new FormulaToStringRequest(sessionName, uid)
//        val result = KeYmaeraClient.serviceRequest(sessionName, request)
//        complete("[" + result.map(_.json).mkString(",") + "]")
//      }}
//    }
//  }
// 
//  val formulaToInteractiveString = path("formulaToInteractiveString") {
//    get {
//      parameter("sessionName", "uid") { (sessionName,uid) => {
//        val request = new FormulaToInteractiveStringRequest(sessionName, uid)
//        val result = KeYmaeraClient.serviceRequest(sessionName, request)
//        complete("[" + result.map(_.json).mkString(",") + "]")
//      }}
//    }
//  }
//  
//  val formulaFromUid = path("formulaFromUid") {
//    get {
//      parameter("sessionName", "uid") { (sessionName,uid) => {
//        val request = new FormulaFromUidRequest(sessionName, uid)
//        val result = KeYmaeraClient.serviceRequest(sessionName, request)
//        complete("[" + result.map(_.json).mkString(",") + "]")
//      }}
//    }
//  }
//  
//  val runTactic = path("runTactic") {
//    get {
//      parameter("sessionName", "tacticName", "uid", "parentId") {
//        (sessionName, tacticName, uid, parentId) => {
//          val request = RunTacticRequest(sessionName, tacticName, uid, None, parentId)
//          val result = KeYmaeraClient.serviceRequest(sessionName, request)
//          complete("[" + result.map(_.json).mkString(",") + "]")
//        }
//      }
//    }
//  }
//
//  
//  //TODO-nrf next tactic should be a runTactic that takes some user input. Pass this
//  //input in as a list of strings where the runTactic request passes None.
//
////  val nodeClosed = path("nodeClosed") undefCall
////  val nodePruned = path("nodePruned") undefCall
////  val limitExceeded = path("limitExceeded") undefCall
////  val startingStrategy = path("startingStrategy") undefCall
////  val applyTactic = path("applyTactic") undefCall
////  val getNode = path("getNode") undefCall
//
//  val routes =
//    javascriptRoute ::
//    cssRoute ::
//    staticRoute ::
//    //Real stuff begins here.
//    getUpdates ::
//    startSession ::
//    startNewProblem ::
//    formulaToString ::
//    formulaToInteractiveString ::
//    formulaFromUid::
//    runTactic::
//    Nil
//
//  val myRoute = routes.reduce(_ ~ _)
//}
