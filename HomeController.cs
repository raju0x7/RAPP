using BlogUploader.Models;
using System;
using System.Collections.Generic;
using System.Data;
using System.IO;
using System.Linq;
using System.Web;
using System.Web.Mvc;
using System.Xml;
using System.Xml.Linq;

namespace BlogUploader.Controllers
{
    public class HomeController : Controller
    {
        public ActionResult Index()
        {
            return View();
        }

        [HttpPost]
        public ActionResult Index(HttpPostedFileBase postedFile)
        {
            List<Blog> blogs = new List<Blog>();
            if (postedFile != null)
            {
                using (XmlTextReader xmlReader = new XmlTextReader(postedFile.InputStream))
                {
                    XDocument xdoc = XDocument.Load(xmlReader);
                    var query = from t in xdoc.Descendants("Blog")
                                select new Blog()
                                {
                                    Author = t.Element("Author").Value,
                                    Title = t.Element("Title").Value,
                                    Description = t.Element("Description").Value,
                                    Status = t.Element("Status").Value
                                };
                    blogs = query.ToList<Blog>();
                }
                return View(blogs);
            }
            return null;
        }
    }
}