package ovh.karewan.knhttp.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class UtilsTest {

	@Test
	public void encodePathSegment_keepsTheUnreservedCharacters() {
		final String value = "abcXYZ019-._~";
		assertSame(value, Utils.encodePathSegment(value));
	}

	@Test
	public void encodePathSegment_encodesEverythingElse() {
		assertEquals("a%20b", Utils.encodePathSegment("a b"));
		assertEquals("%2F%3F%23%26%3D%2B%25", Utils.encodePathSegment("/?#&=+%"));
		assertEquals("%C3%A9%E2%82%AC", Utils.encodePathSegment("é€"));
		assertEquals("null", Utils.encodePathSegment(null));
	}

	@Test
	public void findBestSampleSize_returnsPowersOfTwo() {
		assertEquals(32, Utils.findBestSampleSize(4000, 3000, 100, 75));
		assertEquals(1, Utils.findBestSampleSize(100, 100, 1000, 1000));
		assertEquals(2, Utils.findBestSampleSize(400, 400, 150, 150));
	}

	@Test(timeout = 1000)
	public void findBestSampleSize_neverLoopsForever() {
		Utils.findBestSampleSize(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0);
	}
}
