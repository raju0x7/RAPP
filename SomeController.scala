package controllers

import java.io.ByteArrayInputStream
import java.time.LocalTime

import filters.AuthenticatedUserFilter
import javax.inject.Inject
import javax.xml.parsers.SAXParserFactory
import models.Booking
import org.apache.commons.text.StringEscapeUtils
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats.parsing
import play.api.data.format.Formatter
import play.api.mvc._
import repositories.Repository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node, NodeSeq}

class BookingController @Inject()(roomRepository: Repository,
                                  authenticationFilter: AuthenticatedUserFilter,
                                  cc: MessagesControllerComponents)
                                 (implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) with FormConstraints {

  implicit object LocalTimeFormatter extends Formatter[LocalTime] {
    private def stringToLocalTime(text: String): LocalTime = {
      val split = text.split(":").map(_.toInt)
      LocalTime.of(split(0), split(1))
    }

    override val format = Some(("format.localtime", Nil))

    override def bind(key: String, data: Map[String, String]) =
      parsing(stringToLocalTime, "error.url", Nil)(key, data)

    override def unbind(key: String, value: LocalTime) = Map(key -> value
      .toString)
  }

  private val addBookingForm: Form[Booking] = Form(
    mapping(
      "location" -> text,
      "name" -> text,
      "date" -> date("MM/dd/yyyy"),
      "start" -> of[LocalTime],
      "end" -> of[LocalTime],
      "user" -> text
    )(Booking.apply)(Booking.unapply)
  )

  private val findBookingForm: Form[String] = Form(
    single(
      "name" -> text.verifying(alphaNumericConstraint)
    )
  )

  def addBooking: Action[AnyContent] = Action.async {
    implicit request: MessagesRequest[AnyContent] =>
      authenticationFilter.securedAction { (_, username) =>
        roomRepository.findRooms() map { rooms =>
          Ok(views.html.addbooking(addBookingForm, rooms, username))
        }
      }
  }

  def saveBooking: Action[AnyContent] = Action.async {
    implicit request: MessagesRequest[AnyContent] =>
      authenticationFilter.securedAction { (_, username) =>

        val formError = { formWithErrors: Form[Booking] =>
          roomRepository.findRooms() map { rooms =>
            BadRequest(views.html.addbooking(formWithErrors, rooms, username))
          }
        }

        val formSuccess = { booking: Booking =>
          roomRepository.addBooking(booking) flatMap {
            case Right(_) =>
              Future(Ok(views.html.bookings(
                Seq(booking), roomAdded = true)))
            case Left(error) =>
              roomRepository.findRooms() map { rooms =>
                BadRequest(views.html.addbooking(
                  addBookingForm.withGlobalError(error), rooms, username)
                )
              }
          } recoverWith { case error =>
            roomRepository.findRooms().map { rooms =>
              InternalServerError(views.html.addbooking(
                addBookingForm.
                  withGlobalError(error.getMessage), rooms, username)
              )
            }
          }
        }

        addBookingForm.bindFromRequest.fold(
          formError,
          formSuccess
        )
      }
  }

  def viewBookingsByUser: Action[AnyContent] = Action.async {
    implicit request =>
      authenticationFilter.securedAction { (_, username) =>
        roomRepository.findBookingsByUser(username) map { bookings =>
          Ok(views.html.bookings(bookings))
        }
      }
  }

  def findBookingsByRoom: Action[AnyContent] = Action.async {
    implicit request: MessagesRequest[AnyContent] =>
      authenticationFilter.securedAction { _ =>
        Future(Ok(views.html.findbooking(findBookingForm)))
      }
  }

  def viewBookingsByRoom: Action[AnyContent] = Action.async {
    implicit request: MessagesRequest[AnyContent] =>
      authenticationFilter.securedAction { _ =>

        val formError = { formWithErrors: Form[String] =>
          Future(BadRequest(views.html.findbooking(formWithErrors)))
        }

        val formSuccess = { roomName: String =>
          val encodedRoomName = htmlEncode(roomName)
          roomRepository.findBookingsByRoom(encodedRoomName) map { bookings =>
            Ok(views.html.bookings(bookings))
          } recoverWith { case error =>
            Future(InternalServerError(error.getMessage))
          }
        }

        findBookingForm.bindFromRequest.fold(
          formError,
          formSuccess
        )
      }
  }

  def findBookingsByLocation: Action[String] = Action.async(parse.tolerantText) {
    implicit request =>
      authenticationFilter.securedAction { _ =>

        parseStringAsXml(request.body) match {
          case Some(xml) =>
            val tryLocation = (xml \\ "location").map {
              location => location \@ "name"
            }.headOption

            tryLocation match {
              case Some(location) =>
                roomRepository.findBookingsByLocation(location).map { bookings =>
                  Ok(buildSuccessXml(bookings))
                }
              case None =>
                Future(BadRequest(buildFailureXml("No location found in XML")))
            }
          case None =>
            Future(UnsupportedMediaType(buildFailureXml(
              "Request body was not valid XML")))
        }
      }
  }

  private def htmlEncode(text: String): String = {
    StringEscapeUtils.escapeHtml4(text)
  }

  private def parseStringAsXml(unparsed: String): Option[Elem] = {
    try {
      val unparsedInputStream = new ByteArrayInputStream(unparsed.getBytes)
      val xml = scala.xml.XML.load(unparsedInputStream)
      Some(xml)
    } catch { case _: Exception =>
      None
    }
  }

  private def buildSuccessXml(bookings: Seq[Booking]): Node = {
    <success>{bookings}</success>
  }

  private def buildFailureXml(error: String): Node = {
    <error>Error retrieving(s): {error}</error>
  }

}
