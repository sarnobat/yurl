import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

public class YurlList {

	// TODO 2020-09: gitdb folder got moved. It can be fixed with symlinks but it's best to have properties for every single file (for readabilitytoo)
	private static final String YURL_ORDINALS = System.getProperty("user.home")			+ "/db.git/yurl_flatfile_db/yurl_master_ordinals.txt";
	private static final String DOWNLOADED_VIDEOS = System
			.getProperty("user.home")			+ "/db.git/auto/yurl_queue_httpcat_videos_downloaded.json";
	private static final String DOWNLOADED_VIDEOS_2017 = System
			.getProperty("user.home")			+ "/db.git/yurl_flatfile_db/videos_download_succeeded.txt";
	private static final String QUEUE_DIR = System.getProperty("user.home") + "/db.git/yurl_flatfile_db/";
	private static final String QUEUE_FILE_TXT_DELETE = QUEUE_DIR + "/yurl_deleted.txt";

	// TODO: Regenerate the cache file using these sources of truth.
	private static final String CATEGORY_HIERARCHY_JSON = System
			.getProperty("user.home")			+ "/github/yurl/cache/categories/all.json";

	// This only gets invoked when it receives the first request
	// Multiple instances get created
	@Path("yurl")
	public static class YurlResource { // Must be public

		@GET
		@Path("uncategorized")
		@Produces("application/json")
		public Response getUrls(
				@QueryParam("rootId") Integer iRootId,
				@QueryParam("enableCache") @DefaultValue("true") Boolean iMongoDbCacheLookupEnabled)
				throws JSONException, IOException {
			checkNotNull(iRootId);
			JSONObject categoriesTreeJson;

			if (Paths.get(CATEGORY_HIERARCHY_JSON).toFile().exists()) {
				System.out
						.println("getUrls() - preloaded categories tree not ready");
				categoriesTreeJson = readFromCache(CATEGORY_HIERARCHY_JSON);
			} else {
				throw new RuntimeException("does not exist: "
						+ CATEGORY_HIERARCHY_JSON);
			}
			try {
				JSONObject oUrlsUnderCategory;
				// We're not getting up to date pages when things change. But we
				// need to
				// start using this again if we dream of scaling this app.

				// If there were multiple clients here, you'd need to block the
				// 2nd onwards
				System.out
						.println("YurlWorldResource.getUrls() - not using cache");
				JSONObject retVal1;
				retVal1 = new JSONObject();

				Collection<String> downloadedVideos = new HashSet(
						getDownloadedVideos(DOWNLOADED_VIDEOS));
				// This takes too long.
//				downloadedVideos
//						.addAll(getDownloadedVideos2017(DOWNLOADED_VIDEOS_2017));

				// TODO 2021-09: if possible, don't hide loops inside helper methods,
				// though in this case maybe it's ok. Ideally, a lambda would make 
				// it more transparent
				retVal1.put(
						"urls",
						getItemsAtLevelAndChildLevels(iRootId, downloadedVideos));
				// We do need this. Ideally server_categoriesRecursive.groovy
				// would do it but at the moment that only uses neo4j.
				retVal1.put("categoriesRecursive", categoriesTreeJson);
				oUrlsUnderCategory = retVal1;

				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(oUrlsUnderCategory.toString())
						.type("application/json").build();
			} catch (Exception e) {
				e.printStackTrace();
				return Response.serverError()
						.header("Access-Control-Allow-Origin", "*")
						.entity(e.getStackTrace()).type("application/text")
						.build();
			}
		}

		private JSONObject readFromCache(String filePath) throws IOException {
			System.out.println("YurlList.YurlResource.readFromCache() "
					+ filePath);
			File f = Paths.get(filePath).toFile();
			String s = FileUtils.readFileToString(f, "UTF-8");
			System.out.println("YurlList.YurlResource.readFromCache() success");
			return new JSONObject(s);
		}

		@Deprecated
		// TODO: this file is not in the right format
		private Collection<String> getDownloadedVideos(String downloadedVideos)
				throws IOException {
			System.out.println("YurlList.YurlResource.getDownloadedVideos()");
			return FileUtils.readLines(Paths.get(downloadedVideos).toFile(),
					"UTF-8");
		}

		private Collection<String> getDownloadedVideos2017(
				String downloadedVideos) throws IOException {
			System.out
					.println("YurlList.YurlResource.getDownloadedVideos2017() begin: " + downloadedVideos);
			List<String> readLines = FileUtils.readLines(
					Paths.get(downloadedVideos).toFile(), "UTF-8");
			System.out
					.println("YurlList.YurlResource.getDownloadedVideos2017() 2");
			Set<String> ret = new HashSet<String>();
			for (String line : readLines) {
				String[] elements = line.split("::");
				ret.add(elements[0]);
			}
			System.out
					.println("[INFO] YurlList.YurlResource.getDownloadedVideos2017() end");
			return ret;
		}

		private static JSONObject getItemsAtLevelAndChildLevels(
				Integer iRootId, Collection<String> downloadedVideos)
				throws JSONException, IOException {
			System.err
					.println("[INFO] YurlList.YurlResource.getItemsAtLevelAndChildLevels() - begin");
			JSONObject urls = new JSONObject();
			Collection<String> categoriesToGetUrlsFrom = ImmutableList
					.<String> builder().add(iRootId.toString())
					.addAll(getChildCategories(iRootId.toString())).build();

			Map<String, String> orderMap = buildOrderMap(YURL_ORDINALS, iRootId);

			for (String categoryId : categoriesToGetUrlsFrom) {
				System.out
						.println("[INFO] YurlList.YurlResource.getItemsAtLevelAndChildLevels() "
								+ categoryId);
				if (categoryId.length() > 10) {
					throw new RuntimeException("Not a category ID: "
							+ categoryId);
				}
				// TODO 2021-09 - don't hide loops inside helper methods
				JSONArray urlsInCategory = getUrlsInCategory(categoryId,
						orderMap, downloadedVideos);
				urls.put(categoryId, urlsInCategory);
			}
			return urls;
		}

		private static Map<String, String> buildOrderMap(String yurlOrdinals,
				Integer iRootId) throws IOException {

			List<String> lines = FileUtils.readLines(Paths.get(yurlOrdinals)
					.toFile(), "UTF-8");
			Map<String, String> ret = new HashMap<String, String>();
			for (String line : filter(lines, iRootId.toString())) {
				String[] elements = line.split("::");
				ret.put(elements[1], elements[2]);
			}

			return ImmutableMap.copyOf(ret);
		}

		private static JSONArray getUrlsInCategory(String categoryId,
				Map<String, String> orderMap,
				Collection<String> downloadedVideos) throws IOException {
				
			System.err.println("[DEBUG] getUrlsInCategory() - categoryId = " + categoryId);
			// TODO 2021-09 - externalize the whole dir. 
			String p = 	System.getProperty("user.home") + "/github/yurl/tmp/urls/"                                        + categoryId + ".json";

			// Create the file if it doesn't exist
			java.nio.file.Path urlsInCategoryJsonFile = Paths.get(p);
			System.err.println("[DEBUG] getUrlsInCategory() - json file: " + p);
			if (!urlsInCategoryJsonFile.toFile().exists() || Boolean.valueOf(System.getProperty("cacheUrls", "true")) == false) {
			
				System.err.println("[DEBUG] getUrlsInCategory() - no file containing urls for this category. Generating it.");	

				JSONArray urlsInCategory = new JSONArray();

				List<String> lines1 = FileUtils.readLines(
						Paths.get(QUEUE_FILE_TXT_DELETE)
								.toFile(), "UTF-8");

				List<String> remove = getRemoveLines(lines1);
				// TODO 2021-09 - use a property for the whole file
				String p2 = System.getProperty("urlsFile",System.getProperty("user.home") + "/db.git/yurl_flatfile_db/yurl_master.txt");
				
				System.err.println("[INFO] getUrlsInCategory() - path = " + p2);
				List<String> lines = FileUtils.readLines(Paths.get(p2).toFile(), "UTF-8");
				System.err.println("[INFO] getUrlsInCategory() - lines = " + lines.size());

				if (lines.size() == 0) {
					System.err.println("[ERROR] getUrlsInCategory() - no lines in " + Paths.get(p2));
					System.exit(-1);
				}

				Map<String, String> userImages = getUserImages(Paths
						.get(System.getProperty("user.home") + "/db.git/yurl_flatfile_db/yurl_master_images.txt"));

				boolean found = false;
				for (String line : filterByCategory(
						filterToBeRemovedLines(lines, remove), categoryId)) {
					System.err.println("[DEBUG] getUrlsInCategory() - line = " + line);
					String[] elements = {} ;
					if (categoryId.equals("221013") && line.contains("RabbitMQ-Depth-Gavin-M-Roy")) {
						found = true;
					}
					if (categoryId.equals("221013") && line.contains("B07GCKZKX8")) {
						System.err.println("[ERROR] getUrlsInCategory() - should have been removed:  B07GCKZKX8");
						System.exit(-1);
					}
					try {
						elements = line.split("::");
						if (elements.length < 3) {
							System.err.println("[WARN] getUrlsInCategory() - line has unexpected format: " + line);
							continue;
						}
						@SuppressWarnings("unused")
						String categoryIdElement = elements[0];
						String url = elements[1];
						String timestamp = elements[2];
						
						// backward compatibility
						if (url.length() == 13) {
							url = elements[2];
							timestamp = elements[1];
						}

						JSONObject urlObj1 = new JSONObject();
						// TODO: moving a url will need reimplementing on the client
						// and server
						urlObj1.put("id", "STOP_RELYING_ON_THIS");
						
						if (categoryId.equals("221013") && url.contains("B07GCKZKX8")) {
							System.err.println("[ERROR] getUrlsInCategory() - url should have been removed:  B07GCKZKX8");
							System.exit(-1);
						}
						urlObj1.put("url", url);
						urlObj1.put("created", Long.parseLong(timestamp));
						if (orderMap.containsKey(url)) {
							urlObj1.put("ordinal", orderMap.get(url));
						} else {
							urlObj1.put("ordinal", Long.parseLong(timestamp));
						}
						if (downloadedVideos.contains(url)) {
							urlObj1.put("downloaded_video", true);
						} else {
							urlObj1.put("downloaded_video", false);
						}
						urlObj1.put("parentId", categoryId);
						urlObj1.put("title", "<fill this in>");
						System.err.println("[DEBUG] getUrlsInCategory() -  about to add " + categoryId + "::" + p + "::" + url);
						if (userImages.keySet().contains(url)) {
							urlObj1.put("user_image", userImages.get(url));
						}

						urlsInCategory.put(urlObj1);
					} catch (NumberFormatException e) {
						System.err.println("[ERROR] url.length() = " + elements[1].length());
						e.printStackTrace();
						continue;
					}
				}
				if (!found) {
					System.err.println("[ERROR] getUrlsInCategory() yurl httpcat pipe is not reliable RabbitMQ");
//					System.exit(-1);
				}
				System.err.println("[DEBUG] getUrlsInCategory() urlsInCategoryJsonFile.length() = " + urlsInCategory.length() );
				if (urlsInCategory.length() == 0) {
					System.err.println("[WARN] getUrlsInCategory() No urls in category " + categoryId);
				}
				FileUtils.write(urlsInCategoryJsonFile.toFile(),
						urlsInCategory.toString(2), "UTF-8");
			}
			System.err.println("[DEBUG] getUrlsInCategory() - reading json file for category " + categoryId + ": " + urlsInCategoryJsonFile);
			String arr = FileUtils.readFileToString(
                                        urlsInCategoryJsonFile.toFile(), "UTF-8");
			// this prints too much
			//System.err.println("[DEBUG] arr = " + arr);
			return new JSONArray(arr);
		}

		private static List<String> getRemoveLines(List<String> lines) {
			System.out.println("YurlList.YurlResource.getRemoveLines()");
			List<String> ret = new LinkedList<String>();
			for (String line : lines) {
				if (line.startsWith("-")) {
					ret.add(line);
				}
			}
			return ImmutableList.copyOf(ret);
		}

		private static List<String> filterToBeRemovedLines(List<String> lines,
				List<String> remove1) { // TODO: this should be a HashSet
			List<String> remove = removeField(remove1, 3);
			System.err
					.println("[DEBUG] YurlList.YurlResource.filterToBeRemovedLines() remove = "
							+ remove.toString().substring(0,300));
							
			System.err
					.println("[DEBUG] YurlList.YurlResource.filterToBeRemovedLines() remove = "
							+ remove.size() + " lines to remove");
							
			for (String line : remove) {
				if (line.contains("B07L4CTWV2")) {
					System.err.println("[WARN] filterToBeRemovedLines() - 1 - This needs to get removed (but it doesn't - why? Because of plus signs i think): " + line);
				}
				if (line.equals("-221013::https://www.amazon.com/Hawaii/dp/B07L4CTWV2/ref=sr_1_1?dchild=1&keywords=aerial+america&qid=1630798824&s=instant-video&sr=1-1")) {
					System.err.println("[WARN] filterToBeRemovedLines() - 2 - This needs to get removed (but it doesn't - why?): " + line);
				}
			}
			boolean found = false;
			for (String line : remove1) {
				if (line.contains("B07GCKZKX8")) {
					found = true;
				}
				if (line.contains("B07L4CTWV2")) {
					System.err.println("[WARN] filterToBeRemovedLines() - 5 - This needs to get removed (but it doesn't - why?): " + line);
				}
				if (line.equals("-221013::https://www.amazon.com/Hawaii/dp/B07L4CTWV2/ref=sr_1_1?dchild=1&keywords=aerial+america&qid=1630798824&s=instant-video&sr=1-1::1631850545040")) {
					System.err.println("[WARN] filterToBeRemovedLines() - 6 - This needs to get removed (but it doesn't - why?): " + line);
				}
			}
			if (!found) {
				System.exit(-1);
			}
			if (!remove .contains("-221013::https://www.amazon.com/Hawaii/dp/B07L4CTWV2/ref=sr_1_1?dchild=1&keywords=aerial+america&qid=1630798824&s=instant-video&sr=1-1")) {
				System.err.println("[WARN] YurlList.YurlResource.filterToBeRemovedLines() 3 should have been recorded as deleted: Aerial America B07L4CTWV2" );
				System.exit(-1);
			}

			if (!remove1.contains("-221013::https://www.amazon.com/Hawaii/dp/B07L4CTWV2/ref=sr_1_1?dchild=1&keywords=aerial+america&qid=1630798824&s=instant-video&sr=1-1::1631850545040")) {
				System.err.println("[WARN] YurlList.YurlResource.filterToBeRemovedLines() 4 should have been recorded as deleted: Aerial America B07L4CTWV2" );
				System.exit(-1);
			}
			List<String> ret = new LinkedList<String>();
			boolean found2 = false;
			for (String line : lines) {
			
				String[] elements = line.split("::");
				if (elements.length != 3) {
					System.err.println("[DEBUG] YurlList.YurlResource.filterToBeRemovedLines() line doesn't contain 3 fields, ignoring: " + line);
					System.err.println("[DEBUG] YurlList.YurlResource.filterToBeRemovedLines() line doesn't contain 3 fields, ignoring: " + elements);
//					System.exit(-1);
					continue;
				}
				if (line.contains("RabbitMQ-Depth-Gavin-M-Roy")) {
					found2 = true;
				}
				String categoryIdElement = elements[0];
				String url = elements[1];
				String timestamp = elements[2];
				
				// backward compatibility
				if (url.length() == 13) {
					url = elements[2];
					timestamp = elements[1];
				}
				
				String lineWithoutTimestamp = categoryIdElement + "::" + url;
				
				String lineWithout3rdField = lineWithoutTimestamp;
				//String lineWithout3rdField = removeField(line, 2);
				
				if (remove.contains("-" + lineWithout3rdField)) {
					System.err.println("[DEBUG] YurlList.YurlResource.filterToBeRemovedLines() was removed: " + line);
				} else {
					// add to output
				
					if (line.contains("B07L4CTWV2")) {
//						System.err.println("[ERROR] filterToBeRemovedLines() - should have been removed if category is 221013 : B07L4CTWV2, full line is: " + line);
					}
					if (line.startsWith("221013::https://www.amazon.com/ACCO-Binder-Cli")) {
						System.err.println("YurlList.YurlResource.filterToBeRemovedLines() ERROR 1: " + line);
						System.err.println("YurlList.YurlResource.filterToBeRemovedLines() ERROR 2: "+ remove.get(0));
					}
					ret.add(line);
				}
				
				
			}
			if (!found2) {
				System.err.println("[DEBUG] filterToBeRemovedLines() sanity check failed - RabbitMQ-Depth-Gavin-M-Roy");
				System.exit(-1);
			}
			
			System.err.println("[INFO] YurlList.YurlResource.filterToBeRemovedLines() urls that were not deleted: "+ ret.size());
			return ImmutableList.copyOf(ret);
		}

		private static List<String> removeField(List<String> remove1,
				final int i) {
			return FluentIterable.from(remove1)
					.transform(new Function<String, String>() {
						@Override
						@Nullable
						public String apply(@Nullable String input) {
							return removeField(input, i);
						}
					}).toList();
		}

		private static String removeField(String line, int i) {
			String[] e = line.split("::");
			if (e.length < 3) {
				System.err
						.println("YurlList.YurlResource.removeField() bad line: "
								+ line);
				return line;
			}
			return e[0] + "::" + e[1];
		}

		private static Map<String, String> getUserImages(java.nio.file.Path path)
				throws IOException {

			List<String> lines = FileUtils.readLines(path.toFile(), "UTF-8");
			Map<String, String> ret = new HashMap<String, String>();
			for (String line : lines) {
				try {
					String[] elements = line.split("::");
					String url = elements[0];
					String imageUrl = elements[1];
					ret.put(url, imageUrl);
				} catch (Exception e) {
					//e.printStackTrace();
					System.err.println("[WARN] getUserImages() line = " + line + "\tmesssage = " + e.getMessage());
				}
			}
			return ImmutableMap.copyOf(ret);
		}

		private static List<String> filterByCategory(List<String> lines,
				final String categoryId) {

			return FluentIterable.from(lines).filter(new Predicate<String>() {
				@Override
				public boolean apply(@Nullable String input) {
					boolean accept = input.startsWith(categoryId);
					System.err.println("[DEBUG] filterByCategory() " + accept + ": "+ input );
					return accept;
				}
			}).toList();
		}

		static String home = System.getProperty("user.home");
		private static Collection<String> getChildCategories(String iRootId)
				throws IOException {
			System.out.println("getChildCategories() - begin");
			File file = Paths.get(
					home + "/github/yurl/tmp/categories/topology/"
							+ iRootId + ".txt").toFile();
			if (!file.exists()) {
				java.nio.file.Path p = Paths
						.get(home + "/github/yurl/yurl_category_topology.txt.2017-07-29.columns_reordered");
				List<String> childCategories = new LinkedList<String>();
				StringBuffer sb = new StringBuffer();
				for (String line : FileUtils.readLines(p.toFile(), "UTF-8")) {
					String[] elements = line.split("::");
					if (elements.length < 2) {
						continue;
					}
					String categoryId = elements[0];
					String childCategoryId = elements[1];
					if (categoryId.equals(iRootId)) {
						childCategories.add(childCategoryId);
						sb.append(childCategoryId);
						sb.append("\n");
					}
				}
				FileUtils.writeLines(file, "UTF-8", childCategories);
			}
			System.out
					.println("YurlList.YurlResource.getChildCategories() file = "
							+ file.getPath());
			List<String> categoryLines = FileUtils.readLines(file, "UTF-8");
			return ImmutableList.copyOf(categoryLines);
		}

		private static List<String> filter(List<String> categoryLines,
				final String iRootId) {
			return FluentIterable.from(categoryLines)
					.filter(new Predicate<String>() {
						@Override
						public boolean apply(@Nullable String input) {
							return input.startsWith(iRootId);
						}
					}).toList();
		}

		/**
		 * file will get written to
		 */
		private static Thread refreshCategoriesTreeCacheInSeparateThreadNoNeo4j() {
			return new Thread() {
				@Override
				public void run() {
					java.nio.file.Path rel = Paths.get(CATEGORY_RELATIONSHIPS);
					Map<Integer, Integer> parents = new HashMap<Integer, Integer>();
					for (String line : readFile(rel)) {
						String[] elements = line.split("::");
						if (37658 == Integer.parseInt(elements[1])) {
							System.out.println("run() "
									+ Integer.parseInt(elements[0]));
						}
						parents.put(Integer.parseInt(elements[1]),
								Integer.parseInt(elements[0]));
					}
					java.nio.file.Path nam = Paths.get(CATEGORY_NAMES);
					Map<Integer, String> names = new HashMap<Integer, String>();
					for (String line : readFile(nam)) {
						String[] elements = line.split("::");
						names.put(Integer.parseInt(elements[0]), elements[1]);
					}

					Multimap<Integer, Integer> children = createChildrenMultimap(
							parents, names.keySet());
					JSONObject categoryHierarchy = createCategoryNode(45,
							names, parents, children);
					try {
						FileUtils.write(
								Paths.get(YurlList.CATEGORY_HIERARCHY_JSON)
										.toFile(), categoryHierarchy
										.toString(2), "UTF-8");
					} catch (Exception e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}

				private Multimap<Integer, Integer> createChildrenMultimap(
						Map<Integer, Integer> parents, Set<Integer> childIds) {

					Multimap<Integer, Integer> children = HashMultimap.create();
					for (Integer child : childIds) {
						if (!parents.keySet().contains(child)) {
							System.err
									.println("[ERROR] createChildrenMultimap() ERROR : no parent for "
											+ child);
							continue;
						}
						Integer parent = parents.get(child);
						children.put(parent, child);
						if (parent == 37658) {
							System.err.println("[DEBUG] createChildrenMultimap() "
									+ parent + " :: " + child);
						}
						// System.out
						// .println(".createChildrenMultimap() " +
						// parents.get(child) + " :: " +
						// children.get(parents.get(child)));
					}
					// System.out.println("createChildrenMultimap() children = "
					// + children);
					return children;
				}

				private JSONObject createCategoryNode(int categoryId,
						Map<Integer, String> names,
						Map<Integer, Integer> parents,
						Multimap<Integer, Integer> children) {
					JSONObject node = new JSONObject();
					node.put("name", names.get(categoryId));
					node.put("id", categoryId);
					JSONArray childCategoryNodes = new JSONArray();
					for (Integer childCategoryId : children.get(categoryId)) {
						JSONObject child = createCategoryNode(childCategoryId,
								names, parents, children);
						childCategoryNodes.put(child);
					}
					// if (childCategoryNodes.length() < 1) {
					// System.out
					// .println("YurlList.YurlResource.refreshCategoriesTreeCacheInSeparateThreadNoNeo4j().new Thread() {...}.createCategoryNode() error");
					// throw new RuntimeException("childCategoryNodes = " +
					// childCategoryNodes.length());
					// }
					node.put("children", childCategoryNodes);
					return node;
				}

				private List<String> readFile(java.nio.file.Path rel) {
					try {
						return FileUtils.readLines(rel.toFile(), "UTF-8");
					} catch (IOException e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}
			};
		}


	}

		private static final String CATEGORY_RELATIONSHIPS = System
				.getProperty("user.home")				+ "/db.git/yurl_flatfile_db/yurl_category_topology.txt";
		private static final String CATEGORY_NAMES = System
				.getProperty("user.home")				+ "/db.git/yurl_flatfile_db/yurl_category_names.txt";
				
	public static void main(String[] args) throws URISyntaxException,
			JSONException, IOException {

String[] pathsToCheck = {CATEGORY_RELATIONSHIPS, CATEGORY_NAMES,  YURL_ORDINALS,
DOWNLOADED_VIDEOS,
                         DOWNLOADED_VIDEOS_2017, CATEGORY_HIERARCHY_JSON,
                        QUEUE_DIR , QUEUE_FILE_TXT_DELETE};
        for (String pathToCheck : pathsToCheck) {
        	if (!Paths.get(pathToCheck).toFile().exists()) {
        		System.err.println("Does not exist: " + pathToCheck);
				System.exit(-1);
        	}
        }

		YurlResource.refreshCategoriesTreeCacheInSeparateThreadNoNeo4j()
				.start();
		// Turn off that stupid Jersey logger.
		// This works in Java but not in Groovy.
		// java.util.Logger.getLogger("org.glassfish.jersey").setLevel(java.util.Level.SEVERE);
		try {
			JdkHttpServerFactory.createHttpServer(new URI(
					"http://localhost:4443/"), new ResourceConfig(
					YurlResource.class));
			// Do not allow this in multiple processes otherwise your hard disk
			// will fill up
			// or overload the database
			// Problem - this won't get executed until the server ends
			// YurlWorldResource.downloadUndownloadedVideosInSeparateThread() ;
		} catch (Exception e) {
			System.out.println("Not creating server instance");
		}
	}
}
