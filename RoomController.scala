package controllers

import filters.AuthenticatedUserFilter
import javax.inject.Inject
import models.Room
import play.api.mvc._
import repositories.Repository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq}


class RoomController @Inject()(roomRepository: Repository,
                               authenticationFilter: AuthenticatedUserFilter,
                               cc: MessagesControllerComponents)
                              (implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) {

  def addRoom: Action[NodeSeq] = Action.async(parse.xml) { implicit request =>
    authenticationFilter.securedAction { _ =>
      Room.fromXml(request.body) match {
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
  }

  def addRooms: Action[String] = Action.async(parse.tolerantText) {
    implicit request =>
      authenticationFilter.securedAction { _ =>

        val parsedXml = Try {
          scala.xml.XML.loadString(request.body)
        }

        parsedXml match {
          case Success(xml) =>
            val tryRooms = (xml \\ "room").map(Room.fromXml)
            val errorMessages = tryRooms.collect {
              case Failure(error) => error.getMessage
            }

            errorMessages match {
              case Nil =>
                val rooms = tryRooms.map(_.get)
                val repositoryAddResults = Future.sequence {
                  rooms.map(roomRepository.addRoom)
                }
                repositoryAddResults.map { result =>
                  val (successes, errors) = result.partition(_.isRight)
                  val roomNames = successes.map(_.right.get)
                  val errorMessages = errors.map(_.left.get)

                  (roomNames, errorMessages) match {
                    case (_ :: _, Nil) =>
                      Ok(buildSuccessXml(roomNames))
                    case (_ :: _, _ :: _) =>
                      InternalServerError(
                        buildSuccessXml(roomNames) ++
                          buildFailureXml(errorMessages)
                      )
                    case (Nil, _ :: _) =>
                      InternalServerError(buildFailureXml(errorMessages))
                    case _ => Ok(buildSuccessXml(Nil))
                  }
                }
              case errors => Future(BadRequest(buildFailureXml(errors)))
            }
          case Failure(error) =>
            Future(UnsupportedMediaType(buildFailureXml(Seq(error.getMessage))))
        }
      }
  }

  private def buildSuccessXml(roomNames: Seq[String]): Node = {
    <success>Room(s) added: {roomNames.mkString(", ")}</success>
  }

  private def buildFailureXml(errors: Seq[String]): Node = {
    <error>Error adding rooms(s): {errors.mkString(", ")}</error>
  }

}
