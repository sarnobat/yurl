import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;



public class YurlImageSizes {

	public static void main(String[] args) throws URISyntaxException, IOException {

		String url = "";
		String base = "";
		BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = br.readLine()) != null) {

//                 System.err.println("[DEBUG] current line is: " + line);
                
                try {
					StringBuffer sb = new StringBuffer();
					int size = getByteSize(line);
					sb.append(size);
					sb.append("\t");
					sb.append(line);
                	System.out.println(sb.toString());
				} catch (Exception e) {
					// log.warn: not a url
				}
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
	}

	private static int getByteSize(String absUrl) {
		if (absUrl == null || absUrl.length() == 0) {
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
}
