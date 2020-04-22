using System;
using System.Collections.Generic;
using System.Data;
using System.Data.Entity;
using System.Linq;
using System.Net;
using System.Web;
using System.Web.Mvc;
using BlogManager.Models;
using BlogManager.DAL;
using System.Xml;
using System.Xml.Linq;
using System.Xml.XPath;

namespace BlogManager.Controllers
{
    [Authorize]
    public class BlogsController : Controller
    {
        private BlogStore service = new BlogStore();

        // GET: Blogs
        public ActionResult Index()
        {
            return View(BlogStore.GetUserBlogs(User.Identity.Name));
        }
        // GET: Blogs/Create
        public ActionResult Create()
        {
            return View();
        }

        // POST: Blogs/Create
        [HttpPost]
        [ValidateAntiForgeryToken]
        public ActionResult Create(HttpPostedFileBase postedFile)
        {
            if (postedFile != null)
            {
                XPathDocument xmlDoc = 
                    new XPathDocument(postedFile.InputStream);
                XPathNavigator navigator = xmlDoc.CreateNavigator();
                XPathNavigator node = navigator.SelectSingleNode("/Blog");

                XPathNodeIterator elements = 
                    node.SelectChildren(XPathNodeType.Element);
                Blog blog = new Blog();
                foreach (XPathNavigator item in elements)
                {
                    switch (item.Name)
                    {
                        case "Title":
                            blog.Title = item.Value;
                            break;
                        case "Author":
                            blog.Author = item.Value;
                            break;
                        case "Content":
                            blog.Content = item.Value;
                            break;
                        case "Status":
                            blog.Status = item.Value;
                            break;
                    }
                }
                BlogStore.CreateBlog(blog, User.Identity.Name);
                return RedirectToAction("Index");
            }
            return View();
        }
    }
}
