package edu.cmu.cs.ls.keymaera.hydra


// POJOs, short for Plain Old Java Objects, are for us just tagged products.

class ModelPOJO(val modelId:String, val userId:String, val name:String, val date:String, val keyFile:String)
class ProofPOJO(val modelId:String, val proofId:String, val name:String, val description:String)
/**
 * Proof database
 */
trait DBAbstraction {
  /**
   * Initializes a new database.
   */
  def cleanup() : Unit

  // Users
  def userExists(username : String) : Boolean
  def createUser(username : String, password : String) : Unit
  def getUsername(uid : String) : String
  def checkPassword(username : String, password : String) : Boolean

  //Models
  def createModel(userId: String, name : String, fileContents : String) : Boolean
  def getModel(modelId : String) : ModelPOJO
  def getModelList(userId : String) : List[ModelPOJO] // name, date, fileContents
  //Proofs of models
  def createProofForModel(modelId : String, name : String, description : String) : String //returns id of create object
  def getProofsForModel(modelId : String) : List[ProofPOJO]

  //Proofs and Proof Nodes
  /**
   * This method is called when a tactic completes. It should store the result of the method in the DB.
   */
  def tacticCompletionHook : Any => Any

  def getSubtree(pnId : Int) : String
}