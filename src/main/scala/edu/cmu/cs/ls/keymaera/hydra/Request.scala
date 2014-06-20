/**
 * HyDRA API Requests
 * @author Nathan Fulton
 */
package edu.cmu.cs.ls.keymaera.hydra

/**
 * A Request should handle all expensive computation as well as all
 * possible side-effects of a request (e.g. updating the database), but shold
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
}

class CreateProblemRequest(userid : String, keyFileContents : String) extends Request {
  def getResultingResponses() = {
    try {
      // TODO: use the userid
      val res = ProverBusinessLogic.addModel(keyFileContents)
      val node = ProverBusinessLogic.getSubtree(res)
      //Return the resulting response.
      new CreateProblemResponse(node, res) :: Nil
    }
    catch {
      case e:Exception => new ErrorResponse(e) :: Nil
    }
  }
}