package controllers

import java.io._
import java.time.LocalTime

import filters.AuthenticatedUserFilter
import javax.inject.Inject
import models.Booking
import org.apache.commons.text.StringEscapeUtils
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats.parsing
import play.api.data.format.Formatter
import play.api.mvc._
import repositories.Repository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class BookingController @Inject()(roomRepository: Repository,
                                  authenticationFilter: AuthenticatedUserFilter,
                                  cc: MessagesControllerComponents)
                                 (implicit ec: ExecutionContext,
                                  configuration: Configuration)
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

  def synchronizeBookings(): Action[AnyContent] = Action.async {
    implicit request =>
      val bodyFile = request.body.asMultipartFormData.map(_.files.head)

      bodyFile match {
        case Some(file) =>
          val inputStream = fileToInputStream(file.filename)
          val parsed = parseInputStream[List[Booking]](inputStream)
          inputStream.close()

          roomRepository.findBookings() map { repositoryBookings =>
            if (parsed == repositoryBookings)
              Ok(parsed.map(_.toString()).mkString("\n"))
            else
              Conflict("List of bookings passed does not match database")
          }
        case None =>
          Future(BadRequest("No file body sent"))
      }
    }

  private def fileToInputStream(filename: String): ObjectInputStream = {
    new ObjectInputStream(new FileInputStream(filename))
  }

  private def parseInputStream[T](inputStream: ObjectInputStream): T = {
    inputStream.readObject.asInstanceOf[T]
  }

  private def htmlEncode(text: String): String = {
    StringEscapeUtils.escapeHtml4(text)
  }

}
