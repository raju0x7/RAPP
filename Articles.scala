package gameforum
package controller

import cats._, cats.implicits._, cats.data._, cats.effect._

import javax.inject.{ Inject => JXInject, _ }
import play.api._
import play.api.mvc._
import play.api.data.Form, play.api.data.Forms._
import play.api.i18n.{ I18nSupport, Lang, Langs }
import play.twirl.api.Html

import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.apache.commons.text.StringEscapeUtils.{ escapeXml10 => escapeXml }

import logic.session.userErr
import model._, db.implicits._


@Singleton
class Articles @JXInject()(implicit cc: ControllerComponents, langs: Langs)
    extends AbstractController(cc) with I18nSupport {
  import Articles._, view.articles.{ html => articlesHtml }

  def list = asyncErr() { implicit req =>
    userArticles.map(articlesHtml.list(_)) }

  def filter(topic: String, name: String) = asyncErr() { implicit req =>
    userErr.semiflatMap(u => db.article.search(u.id.get, topic, name))
      .map(articlesHtml.list(_))
  }

  def export = asyncErr() { implicit req =>
    userArticles.map { ax => Ok(serializeArticles(ax))
      .as("application/x-download")
      .withHeaders("Content-disposition" -> "attachment; filename=articles.xml") }
  }

  def create = asyncErr() { implicit req =>
    userErr >> articlesHtml.create(createForm).pure[Error] }
  
  def createDo = asyncErrParse(parse.form(createForm)) { implicit req =>
    userErr.map(u => req.body.copy(user = u.id))
      .semiflatMap(db.article.create) >>
      routes.Articles.list.pure[Error] }

  def importArticles = asyncErr() { implicit req =>
    userErr >> articlesHtml.importArticles(importForm).pure[Error] }

  def importDo = asyncErrParse(parse.form(importForm)) { implicit req =>
    userErr
      .map { user => deserializeArticles(req.body).map(_.copy(user = user.id)) }
      .semiflatMap(db.article.createMany) >>
      routes.Articles.list.pure[Error]
  }
}

object Articles extends ArticleSerializer with ArticleDeserializer {
  def createForm: Form[ArticleLink] = Form {
    mapping(
      "name"  -> text
    , "link"  -> text
    , "topic" -> text
    )
    { case (name, link, topic) => ArticleLink(name = name, link = link, topic = topic) }
    { ArticleLink.unapply(_).map { case (_, _, name, link, topic) =>
      (name, link, topic) } }
  }

  def importForm: Form[String] = Form {
    mapping("articles" -> text)(identity)(Some(_)) }

  def userArticles(implicit request: Request[_], cc: ControllerComponents, langs: Langs): Error[List[ArticleLink]] =
    userErr.semiflatMap(u => db.article.listByUser(u.id.get))
}

trait ArticleSerializer {
  def serializeArticles(ax: List[ArticleLink]): String =
  s"""<?xml version="1.0" standalone="no" ?>
     |<links>
     |${ax.map(serializeArticle).mkString("\n")}
     |</links>""".stripMargin

  def serializeArticle(a: ArticleLink): String =
    s"  <link><name>${escapeXml(a.name)}</name>" +
    s"<url>${escapeXml(a.link)}</url>" +
    s"<topic>${escapeXml(a.topic)}</topic></link>"
}

trait ArticleDeserializer {
  import org.apache.commons.io.IOUtils
  import org.w3c.dom._
  import javax.xml.parsers._
  import java.io._
  import javax.xml.XMLConstants.{ FEATURE_SECURE_PROCESSING, ACCESS_EXTERNAL_DTD }


  implicit class RichNodeList(nl: NodeList) {
    def asScala: List[Node] =
      (for (i <- 0 until nl.getLength) yield nl.item(i)).toList
    
    def asScalaElem: List[Element] =
      asScala.asInstanceOf[List[Element]]
  }

  implicit class RichElem(e: Element) {
    def firstTagged(tag: String): Element =
      e.getElementsByTagName(tag).asScalaElem.head
  }

  def stringToStream(str: String): InputStream =
    IOUtils.toInputStream(str, "utf8")


  def deserializeArticles(xml: String): List[ArticleLink] = {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setAttribute(ACCESS_EXTERNAL_DTD, "all")

    val builder = factory.newDocumentBuilder()
    val doc     = builder.parse(stringToStream(xml))

    doc.getElementsByTagName("link").asScalaElem.map { e =>
      ArticleLink(None, None
      , e.firstTagged("name" ).getTextContent
      , e.firstTagged("url"  ).getTextContent
      , e.firstTagged("topic").getTextContent)
    }
  }
}
