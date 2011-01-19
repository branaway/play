package play.data.parsing;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class ApacheMultipartParserTest {

	private static final String CONTENT_TYPE = "multipart/mixed; boundary=\"frontier\"";
	static final String good = 
			"--frontier\r\n" + 
			"Content-Disposition: form-data; name=\"appType\"\r\n" + 
			"\r\n" + 
			"iPhone\r\n" + 
			"--frontier\r\n" + 
			"Content-Disposition: form-data; name=\"phoneNum\"\r\n" + 
			"\r\n" + 
			"111\r\n" + 
			"--frontier--";
	static final String bad = 
			"--frontier\r\n" + //extra empty section 
			"\r\n" + 
			"--frontier\r\n" + 
			"Content-Disposition: form-data; name=\"appType\"\r\n" + 
			"\r\n" + 
			"iPhone\r\n" + 
			"--frontier\r\n" + 
			"Content-Disposition: form-data; name=\"phoneNum\"\r\n" + 
			"\r\n" + 
			"111\r\n" + 
			"--frontier--";
	InputStream goodIS;
	InputStream badIS;
	
	@Before
	public void init() throws UnsupportedEncodingException {
		goodIS = new ByteArrayInputStream(good.getBytes("UTF-8"));
		badIS = new ByteArrayInputStream(bad.getBytes("UTF-8"));
	}
	
	@Test
	public void testNormal() {
		ApacheMultipartParser p = new ApacheMultipartParser() {
			@Override
			protected String getReqContentType() {
				return CONTENT_TYPE;
			}

			@Override
			protected String getFileUploadMax() {
				return "10";
			}
		};
		Map<String, String[]> pp = p.parse(goodIS);
		assertEquals("iPhone", pp.get("appType")[0]);
		assertEquals("111", pp.get("phoneNum")[0]);
	}

	@Test
	public void testBad() {
		ApacheMultipartParser p = new ApacheMultipartParser() {
			@Override
			protected String getReqContentType() {
				return CONTENT_TYPE;
			}
			
			@Override
			protected String getFileUploadMax() {
				return "10";
			}
		};
		Map<String, String[]> pp = p.parse(badIS);
		assertEquals(1, pp.size());
		// a line is lost due to the extra empty section in the body
	}
	
}
