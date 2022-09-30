// I think the only remaining method in use here is:
// Request URL: http://netgear.rohidekar.com:44447/yurl/parent?nodeId=641476
// Everything else doesn't use port 4447
// Actually also image change is
// And move()

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import com.google.common.collect.ImmutableSet;

/**
 * Deprecation doesn't mean the method can be removed. Only when index.html
 * stops referring to it can it be removed.
 * 
 * Neo4j dependencies removed.
 * 
 * Only writing to the persistent store (and the associated async tasks) should
 * remain in this thread.
 */
public class YurlStash {

  // TODO: use java properties
  private static final String QUEUE_DIR = System
      .getProperty("user.home") + "/db.git/yurl_flatfile_db/";
  private static final String QUEUE_FILE_TXT = "yurl_queue.txt";
  private static final String TITLE_FILE_TXT = "yurl_titles_2017.txt";
  private static final String QUEUE_FILE_TXT_MASTER = "yurl_master.txt";
  private static final String QUEUE_FILE_TXT_2017 = "yurl_queue_2017.txt";// Started using this in Aug 2017. Older data is not in this file.
  private static final String QUEUE_FILE_TXT_DELETE = "yurl_deleted.txt";

  @Path("yurl")
  public static class YurlResource { // Must be public

    @GET
    @Path("stash")
    @Produces("application/json")
    public Response stash(@QueryParam("param1") String iUrl,
        @QueryParam("rootId") Integer iCategoryId)
        throws JSONException, IOException {

      System.err.println("stash() begin");
      String theHttpUrl = URLDecoder.decode(iUrl, "UTF-8");
      System.err.println("stash() theHttpUrl = " + theHttpUrl);
      try {
        {
          String command = "echo '" + iCategoryId.toString() + "::"
              + iUrl + "::'`date +%s` | tee -a '"
              + (YurlStash.QUEUE_DIR + "/"
                  + YurlStash.QUEUE_FILE_TXT_MASTER)
              + "'";
          System.err.println(
              "YurlStash.YurlResource.appendToTextFileSync()");
          Process p = new ProcessBuilder()
              .directory(Paths.get(YurlStash.QUEUE_DIR).toFile())
              .command("echo", "hello world")
              .command("/bin/sh", "-c", command).inheritIO().start();
          try {
            p.waitFor();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          if (p.exitValue() == 0) {
            System.err.println(
                "YurlStash.YurlResource.appendToTextFileSync() success: "
                    + command);
          } else {
            System.err.println(
                "YurlStash.YurlResource.appendToTextFileSync() failed: "
                    + command);
          }
        }
        {
          String command = "echo '" + iCategoryId.toString() + "::"
              + theHttpUrl + "::'`date +%s` | tee -a '"
              + (QUEUE_DIR + "/" + YurlStash.QUEUE_FILE_TXT_2017)
              + "'";
          System.err.println(
              "YurlStash.YurlResource.appendToTextFileSync()");
          Process p = new ProcessBuilder()
              .directory(Paths.get(QUEUE_DIR).toFile())
              .command("echo", "hello world")
              .command("/bin/sh", "-c", command).inheritIO().start();
          try {
            p.waitFor();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          if (p.exitValue() == 0) {
            System.err.println(
                "YurlStash.YurlResource.appendToTextFileSync() success: "
                    + command);
          } else {
            System.err.println(
                "YurlStash.YurlResource.appendToTextFileSync() failed: "
                    + command);
          }
        }

        // Delete the url cache file for this category. It will get
        // regenrated next time we load that category page.
        removeCategoryCache(iCategoryId);

        _getTitle: {

          System.err.println(
              "YurlStash.YurlResource.getTitle() - we are still using this. Ideally we shouldn't.");
          String title = "";
          try {
            title = Executors.newFixedThreadPool(2)
                .invokeAll(ImmutableSet.<Callable<String>>of(() -> {
                  try {
                    return Jsoup
                        .connect(new URL(theHttpUrl).toString()).get()
                        .title();
                  } catch (org.jsoup.UnsupportedMimeTypeException e) {
                    System.err.println(
                        "YurlStash.YurlResource.jsoupHtmlDump()"
                            + e.getMessage());
                    return "";
                  }
                }), 3000L, TimeUnit.SECONDS).get(0).get();
          } catch (InterruptedException e1) {
            e1.printStackTrace();
          } catch (ExecutionException e2) {
            e2.printStackTrace();
          }
          String theTitle = title;
          if (theTitle != null && theTitle.length() > 0) {
            new Thread(() -> {
              String command = "echo '" + theHttpUrl + "::" + theTitle
                  + "' | tee -a '" + (YurlStash.QUEUE_DIR + "/"
                      + YurlStash.TITLE_FILE_TXT)
                  + "'";
              System.err.println(
                  "appendToTextFile() - command = " + command);
              try {
                Process p = new ProcessBuilder()
                    .directory(
                        Paths.get(YurlStash.QUEUE_DIR).toFile())
                    .command("echo", "hello world")
                    .command("/bin/sh", "-c", command).inheritIO()
                    .start();
                try {
                  p.waitFor();
                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
                if (p.exitValue() == 0) {
                  System.err.println(
                      "appendToTextFile() - successfully appended 2 "
                          + theHttpUrl);
                } else {
                  System.err.println(
                      "launchAsynchronousTasksHttpcat() - 4 error appending "
                          + theHttpUrl);
                }
              } catch (IOException e) {
                e.printStackTrace();
              }
            }).start();
          }
        }
        // This is not (yet) the master file. The master file is written to
        // synchronously.
        new Thread(() -> {
          String command = "echo '" + iCategoryId.toString() + "::"
              + theHttpUrl + "::'`date +%s` | tee -a '"
              + (YurlStash.QUEUE_DIR + "/" + YurlStash.QUEUE_FILE_TXT)
              + "'";
          File file = Paths.get(YurlStash.QUEUE_DIR).toFile();
          System.err.println("appendToTextFile() - " + command);
          try {
            Process p = new ProcessBuilder().directory(file)
                .command("echo", "hello world")
                .command("/bin/sh", "-c", command).inheritIO()
                .start();
            try {
              p.waitFor();
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            if (p.exitValue() == 0) {
              System.err.println(
                  "appendToTextFile() - successfully appended 5 "
                      + theHttpUrl + " to " + YurlStash.QUEUE_DIR
                      + "/" + YurlStash.QUEUE_FILE_TXT);
            } else {
              System.err
                  .println("appendToTextFile() - 3 error appending "
                      + theHttpUrl);
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
        }).start();
        System.err.println(
            "YurlStash.YurlResource.stash() sending empty json response. This should work.");
        return Response.ok()
            .header("Access-Control-Allow-Origin", "*")
            .entity(new JSONObject().toString())
            .type("application/json").build();
      } catch (Exception e) {
        e.printStackTrace();
        throw new JSONException(e);
      }
    }

    @GET
    @Path("updateImage")
    @Produces("application/json")
    public Response changeImage(@QueryParam("url") String imageUrl,
        @QueryParam("linkUrl") String iUrl,
        @QueryParam("categoryId") String iCategoryId)
        throws IOException, JSONException {
      System.err.println(
          "YurlStash.java::YurlResource.changeImage() begin");

      FileUtils.write(
          Paths.get(QUEUE_DIR + "/yurl_master_images.txt").toFile(),
          iUrl + "::" + imageUrl + "\n", "UTF-8", true);
      System.err.println(
          "YurlStash.java::YurlResource.changeImage() - success: "
              + iUrl + " :: " + imageUrl);
      new Thread(() -> {
        removeCategoryCache(Integer.parseInt(iCategoryId));
      }).start();

      return Response.ok().header("Access-Control-Allow-Origin", "*")
          .entity(new JSONObject().toString())
          .type("application/json").build();
    }

    /**
     * This MOVES a node to a new subcategory. It deletes the relationship with the
     * existing parent
     * 
     * @throws InterruptedException
     */
    @GET
    @Path("relate")
    @Produces("application/json")
    public Response move(@QueryParam("parentId") Integer iNewParentId,
        @QueryParam("url") String iUrl,
        @QueryParam("currentParentId") Integer iCurrentParentId,
        @QueryParam("created") Long created)
        throws JSONException, IOException {

      System.err.println(
          "Yurl.YurlResource.move() begin - ARE WE STILL USING THIS?");
      {
        String command = "echo '" + iNewParentId.toString() + "::"
            + iUrl + "::" + created + "' | tee -a '"
            + (YurlStash.QUEUE_DIR + "/"
                + YurlStash.QUEUE_FILE_TXT_MASTER)
            + "'";
        System.err
            .println("YurlStash.YurlResource.appendToTextFileSync()");
        Process p = new ProcessBuilder()
            .directory(Paths.get(YurlStash.QUEUE_DIR).toFile())
            .command("echo", "hello world")
            .command("/bin/sh", "-c", command).inheritIO().start();
        try {
          p.waitFor();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        if (p.exitValue() == 0) {
          System.err.println(
              "YurlStash.YurlResource.appendToTextFileSync() success: "
                  + command);
        } else {
          System.err.println(
              "YurlStash.YurlResource.appendToTextFileSync() failed: "
                  + command);
        }
      }
      System.err.println("Yurl.YurlResource.move() 2");
      {
        String command1 = "echo '" + "-" + iCurrentParentId.toString()
            + "::" + iUrl + "::" + created + "' | tee -a '"
            + (QUEUE_DIR + "/" + YurlStash.QUEUE_FILE_TXT_DELETE)
            + "'";
        System.err
            .println("YurlStash.YurlResource.appendToTextFileSync()");
        Process p = new ProcessBuilder()
            .directory(Paths.get(QUEUE_DIR).toFile())
            .command("echo", "hello world")
            .command("/bin/sh", "-c", command1).inheritIO().start();
        try {
          p.waitFor();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        if (p.exitValue() == 0) {
          System.err.println(
              "YurlStash.YurlResource.appendToTextFileSync() success: "
                  + command1);
        } else {
          System.err.println(
              "YurlStash.YurlResource.appendToTextFileSync() failed: "
                  + command1);
        }
      }
      System.err.println("Yurl.YurlResource.move() 4");

      new Thread(() -> {
        removeCategoryCache(iNewParentId);
      }).start();
      new Thread(() -> {
        removeCategoryCache(iCurrentParentId);
      }).start();

      return Response.ok().header("Access-Control-Allow-Origin", "*")
          .entity(new JSONObject().toString())
          .type("application/json").build();
    }

    private static void removeCategoryCache(Integer iCategoryId) {
      {
        java.nio.file.Path path1 = Paths
            .get(System.getProperty("user.home")
                + "/github/yurl/tmp/urls/" + iCategoryId + ".json");

        path1.toFile().delete();

        System.err.println(
            "Yurl.YurlResource.launchAsynchronousTasksHttpcat() deleted cache file 1: "
                + path1);
      }
      {
        java.nio.file.Path path = Paths
            .get(System.getProperty("user.home")
                + "/github/yurl/tmp/categories/topology/"
                + iCategoryId + ".txt");

        path.toFile().delete();

        System.err.println(
            "Yurl.YurlResource.launchAsynchronousTasksHttpcat() deleted cache file 2: "
                + path);
      }
    }
  }

  public static void main(String[] args)
      throws URISyntaxException, JSONException, IOException {
    System.err.println("main() - begin");
    String port = "4447";

    if (!Paths.get(QUEUE_DIR).toFile().exists()) {
      throw new RuntimeException();
    }
    if (!Paths
        .get(YurlStash.QUEUE_DIR + "/" + YurlStash.QUEUE_FILE_TXT)
        .toFile().exists()) {
      throw new RuntimeException();
    }
    if (!Paths
        .get(YurlStash.QUEUE_DIR + "/" + YurlStash.TITLE_FILE_TXT)
        .toFile().exists()) {
      throw new RuntimeException();
    }
    if (!Paths.get(
        YurlStash.QUEUE_DIR + "/" + YurlStash.QUEUE_FILE_TXT_MASTER)
        .toFile().exists()) {
      throw new RuntimeException();
    }
    if (!Paths.get(YurlStash.QUEUE_DIR + "/" +QUEUE_FILE_TXT_2017).toFile().exists()) {
      throw new RuntimeException();
    }
    if (!Paths.get(YurlStash.QUEUE_DIR + "/" +QUEUE_FILE_TXT_DELETE).toFile().exists()) {
      throw new RuntimeException();
    }
    if (!Paths
        .get(System.getProperty("user.home") + "/github/yurl/tmp/")
        .toFile().exists()) {
      throw new RuntimeException();
    }
    if (!Paths
        .get(System.getProperty("user.home")
            + "/github/yurl/tmp/categories/topology/")
        .toFile().exists()) {
      throw new RuntimeException();
    }

    // Turn off that stupid Jersey logger.
    // This works in Java but not in Groovy.
    // java.util.Logger.getLogger("org.glassfish.jersey").setLevel(java.util.Level.SEVERE);
    try {
      JdkHttpServerFactory.createHttpServer(
          new URI("http://localhost:" + port + "/"),
          new ResourceConfig(YurlStash.YurlResource.class));
    } catch (Exception e) {
      // e.printStackTrace();
      System.err.println("Not creating server instance");
    }
  }
}
