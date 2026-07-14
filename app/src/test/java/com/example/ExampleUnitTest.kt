package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testStaticAssetDetection() {
    // True cases
    assertTrue(WebViewCacheManager.isStaticAsset("https://hydraulic-hamzeh.ir/style.css"))
    assertTrue(WebViewCacheManager.isStaticAsset("https://hydraulic-hamzeh.ir/script.js?v=2.1"))
    assertTrue(WebViewCacheManager.isStaticAsset("https://hydraulic-hamzeh.ir/images/hero.PNG"))
    assertTrue(WebViewCacheManager.isStaticAsset("https://hydraulic-hamzeh.ir/logo.svg#icon"))
    assertTrue(WebViewCacheManager.isStaticAsset("https://hydraulic-hamzeh.ir/font.woff2"))

    // False cases
    assertFalse(WebViewCacheManager.isStaticAsset("https://hydraulic-hamzeh.ir/"))
    assertFalse(WebViewCacheManager.isStaticAsset("https://hydraulic-hamzeh.ir/contact-us"))
    assertFalse(WebViewCacheManager.isStaticAsset("https://hydraulic-hamzeh.ir/api/get-data?file=test.css.json"))
  }
}
