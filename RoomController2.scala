package controllers

import filters.AuthenticatedUserFilter
import javax.inject.Inject
import models.Room
import play.api.mvc._
import repositories.Repository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node, NodeSeq}


class RoomController2 @Inject()(roomRepository: Repository,
                               authenticationFilter: AuthenticatedUserFilter,
                               cc: MessagesControllerComponents)
                              (implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) {

  def addRoom: Action[String] = Action.async(parse.tolerantText) {
    implicit request =>
      authenticationFilter.securedAction { _ =>

        parseRequestBody(request.body) match {
          case Some(xml) =>
            if (containsMaliciousKeywords(xml.toString)) {
              Future(BadRequest(
                buildFailureXml(Seq("XML contains malicious keywords"))))
            } else {
              Room.fromXml(xml) match {
                case Success(room) =>
                  roomRepository.addRoom(room) map {
                    case Right(roomName) =>
                      Ok(buildSuccessXml(Seq(roomName)))
                    case Left(error) =>
                      InternalServerError(buildFailureXml(Seq(error)))
                  }
                case Failure(error) =>
                  Future(BadRequest(buildFailureXml(Seq(error.getMessage))))
              }
            }
          case None =>
            Future(UnsupportedMediaType(
              buildFailureXml(Seq("Request body did not contain valid XML"))))
        }
      }
  }

  private def parseRequestBody(content: String): Option[Elem] = {
    Try {
      scala.xml.XML.loadString(content)
    }.toOption
  }

  private def containsMaliciousKeywords(xmlAsString: String): Boolean = {
    xmlAsString.contains("/etc/passwd") ||
      xmlAsString.contains("/etc/passwd") ||
      xmlAsString.contains("nc -l -p")
  }

  private def buildSuccessXml(roomNames: Seq[String]): Node = {
    <success>Room(s) added: {roomNames.mkString(", ")}</success>
  }

  private def buildFailureXml(errors: Seq[String]): Node = {
    <error>Error adding rooms(s): {errors.mkString(", ")}</error>
  }

}
