import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;


public class YurlImageSizes {


	public static void main(String[] args) throws URISyntaxException, JSONException, IOException {
		//getImagesAscendingSize(args[0]);
		String url = "";
		String base = "";
		StringBuffer sb = new StringBuffer();
		BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = br.readLine()) != null) {
                // log message
                System.err.println("[DEBUG] current line is: " + line);
                
                sb.append(line);
                
                // program output
                //System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
		System.err.println("[DEBUG] failed");
	    System.exit(-1);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
	
        }

		getImagesAscendingSize(sb.toString());        

	}

	/**
	 * Ascending like du
	 */
	private static List<String> getImagesAscendingSize(String source) throws MalformedURLException,
			IOException {
String base = "";
String url = "";
			List<String> ret = ImmutableList.of();
System.err.println("[DEBUG] " + source);
			List<String> out = getAllTags(base + "/", source);
			System.err.println(out);
			Multimap<Integer, String> imageSizes = getImageSizes(out);
			ret = sortByKey(imageSizes, url);
			if (ret.size() < 1) {
				throw new RuntimeException("2 We're going to get a nullpointerexception later: ");
			}

		if (ret.size() < 1) {
			throw new RuntimeException("1 We're going to get a nullpointerexception later: " );
		}
		return ret;
	}

	private static List<String> sortByKey(Multimap<Integer, String> imageSizes, String pageUrl) {
		ImmutableList.Builder<String> finalList = ImmutableList.builder();

		// Phase 1: Sort by size ascending
		ImmutableList<Integer> sortedList = FluentIterable.from(imageSizes.keySet()).toSortedList(
				Ordering.natural());

		// Phase 2: Put non-JPGs first
		for (Integer size : sortedList) {
			for (String url : imageSizes.get(size)) {
				if (!isJpgFile(url)) {
					if (url.length() > 0) {
						finalList.add(url);
						System.out.println(size + "\t" + url);
					}
				}
			}
		}
		// Phase 3: Put JPGs last
		for (Integer size : sortedList) {
			for (String url : imageSizes.get(size)) {
				if (isJpgFile(url)) {
					finalList.add(url);
					System.out.println(size + "\t" + url);
				}
			}
		}
		return finalList.build();
	}

	private static boolean isJpgFile(String url) {
		return url.matches("(?i).*\\.jpg") || url.matches("(?i).*\\.jpg\\?.*");
	}

	private static Multimap<Integer, String> getImageSizes(List<String> out) {
		ImmutableMultimap.Builder<Integer, String> builder = ImmutableMultimap.builder();
		for (String imgSrc : out) {
			int size = getByteSize(imgSrc);
			builder.put(size, imgSrc);
		}
		return builder.build();
	}

	private static int getByteSize(String absUrl) {
		if (Strings.isNullOrEmpty(absUrl)) {
			return 0;
		}
		URL url;
		try {
			url = new URL(absUrl);
			int contentLength = url.openConnection().getContentLength();
			return contentLength;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	private static String getBaseUrl(String url1) throws MalformedURLException {
		URL url = new URL(url1);
		String file = url.getFile();
		String path;
		if (file.length() == 0) {
			path = url1;
		} else {
			path = url.getFile().substring(0, file.lastIndexOf('/'));
		}
		if (path.startsWith("http")) {
			return path;
		} else {
			String string = url.getProtocol() + "://" + url.getHost() + path;
			return string;
		}
	}

	private static List<String> getAllTags(String baseUrl, String source) throws IOException {
		Document doc = Jsoup.parse(IOUtils.toInputStream(source), "UTF-8", baseUrl);
		Elements tags = doc.getElementsByTag("img");
		return FluentIterable.<Element> from(tags).transform(IMG_TO_SOURCE).toList();
	}

	private static final Function<Element, String> IMG_TO_SOURCE = new Function<Element, String>() {
		@Override
		public String apply(Element e) {
			String absUrl = e.absUrl("src");
			return absUrl;
		}
	};
}
