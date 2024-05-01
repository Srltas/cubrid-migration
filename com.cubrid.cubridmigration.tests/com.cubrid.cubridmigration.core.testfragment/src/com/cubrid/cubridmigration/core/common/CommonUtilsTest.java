/*
 * Copyright (C) 2008 Search Solution Corporation.
 * Copyright (C) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */
package com.cubrid.cubridmigration.core.common;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommonToolTest Description
 *
 * @author mouliinwang
 * @author JessieHuang
 * @version 1.0 - 2009-10-13
 */
public class CommonUtilsTest {

    /** testEqualsListsIgnoreOrder */
    @Test
    public final void testEqualsListsIgnoreOrder() {
        List<String> list1 = new ArrayList<String>();
        final List<String> list2 = new ArrayList<String>();

        list1.add("abc");
        list1.add("123");
        list1.add("d1");

        list2.add("abc");
        list2.add("123");
        list2.add("d2");

        boolean flag = CommonUtils.equalsListsIgnoreOrder(list1, list2);

        assertFalse(flag);

        list1 = null;
        flag = CommonUtils.equalsListsIgnoreOrder(list1, list2);
        assertFalse(flag);
    }

    /** testEqualsObjectObject */
    @Test
    public final void testEqualsObjectObject() {
        String obj1 = "abc";
        String obj2 = "abc";
        boolean flag = CommonUtils.equals(obj1, obj2);
        assertFalse(CommonUtils.equals(null, obj2));
        assertFalse(CommonUtils.equals(obj1, null));
        assertFalse(CommonUtils.equals(obj1, "a"));
        assertFalse(CommonUtils.equals("a", obj2));
        assertTrue(CommonUtils.equals(null, null));
        assertTrue(flag);
    }

    //	/**
    //	 * testHashCodeObject
    //	 */
    //	@Test
    //	public final void testHashCodeObject() {
    //		final int code = CommonUtils.hashCode("a");
    //		assertTrue(code > 0);
    //	}

    /** testGetSpecPath */
    @Test
    public final void testGetSpecPath() {
        String path = "c:/1.txt";
        String specPath = PathUtils.getLocalHostFilePath(path);
        assertNotNull(specPath);
    }

    /** testGetAllTimeZones */
    @Test
    public final void testStr2Int() {
        String str = "2";
        int ret = CommonUtils.str2Int(str);
        assertTrue(ret == 2);

        ret = CommonUtils.str2Int("ab");
        assertTrue(ret == 0);
    }

    /** testStr2Double */
    @Test
    public final void testStr2Double() {
        String inval = "1.0000977";
        double d1 = CommonUtils.str2Double(inval);
        assertTrue(d1 == Double.valueOf(1.0000977));

        d1 = CommonUtils.str2Double("1.88E-90");
        assertEquals(new Double(1.88E-90), new Double(d1));

        d1 = CommonUtils.str2Double("1.88E+90");
        assertEquals(new Double(1.88E+90), new Double(d1));

        d1 = CommonUtils.str2Double("1.88E90");
        assertEquals(new Double(1.88E90), new Double(d1));

        d1 = CommonUtils.str2Double("a1.88E90");
        assertEquals(new Double(0), new Double(d1));
    }

    /** testStrYN2Boolean */
    @Test
    public final void testStrYN2Boolean() {
        assertTrue(CommonUtils.strYN2Boolean("y"));
        assertFalse(CommonUtils.strYN2Boolean("n"));
        assertFalse(CommonUtils.strYN2Boolean(null));
    }

    /** testIsASCII */
    @Test
    public final void testIsASCII() {
        assertTrue(CommonUtils.isASCII("A"));
    }

    /** testValidateCheckInIdentifier */
    @Test
    public final void testValidateCheckInIdentifier() {
        String identifier = null;
        String res = CommonUtils.validateCheckInIdentifier(identifier);
        assertTrue(res.equals("empty"));

        identifier = "/";
        String res1 = CommonUtils.validateCheckInIdentifier(identifier);
        assertTrue(res1.length() > 0);

        String identifier2 = " \t.~,\\\"|][";
        String res2 = CommonUtils.validateCheckInIdentifier(identifier2);
        assertNotSame("", res2);

        String identifier3 = "}{)(=-+?<>:;!'@$^&*`";
        String res3 = CommonUtils.validateCheckInIdentifier(identifier3);
        assertNotSame("", res3);
    }

    /** testSaveDataToExcel */
    @Test
    public final void testSaveDataToExcel() {
        String[] ss =
                new String[] {"aaaaaaaaaaaaaaaa", "baaaaaaa", "caaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"};
        List<String[]> data = new ArrayList<String[]>();
        String[] t1 = new String[] {"aaaa", "bbbb", "3"};
        String[] t2 = new String[] {"4", "5", "6"};
        String[] t3 = new String[] {"7", "8", "9"};
        data.add(t1);
        data.add(t2);
        data.add(t3);

        URL url = ClassLoader.getSystemResource("./");
        String path =
                CUBRIDIOUtils.IS_OS_WINDOWS ? url.getFile().toString().substring(1) : url.getFile();

        String fileName = path + "1234qbd.xls";
        boolean flag = CUBRIDIOUtils.saveDataToExcel(fileName, ss, data);
        assertTrue(flag);

        File file = new File(fileName);
        file.delete();
    }

    /** testGetStateLocation */
    @Test
    public final void testGetStateLocation() {
        String tmpGLOPath = PathUtils.getBaseTempDir() + File.separatorChar + "test";
        tmpGLOPath = PathUtils.getLocalHostFilePath(tmpGLOPath);
        assertNotNull(tmpGLOPath);
    }

    /** testGetCharsets */
    @Test
    public final void testGetCharsets() {
        String[] charsets = CharsetUtils.getCharsets();
        assertTrue(charsets.length > 0);
    }

    /** testToEncoding */
    @Test
    public final void testToEncoding() {
        String charset = "UTF8";
        String str = "test";
        String res = CommonUtils.toEncoding(str, charset);
        assertTrue(res.equals("test"));

        charset = "UTF16";
        try {
            str = "aa";
            res = CommonUtils.toEncoding(str, charset);
        } catch (Exception e) {
            assertTrue(res.equals("test"));
        }

        assertEquals("test", CommonUtils.toEncoding("test", ""));
        assertEquals("", CommonUtils.toEncoding("", "utf8"));
    }

    /** testStr2Long */
    @Test
    public final void testStr2Long() {
        String str = "23444";
        long res = CommonUtils.str2Long(str);
        assertTrue(res == 23444);

        str = "po";
        res = CommonUtils.str2Long(str);
        assertTrue(res == 0);
    }

    @Test
    public final void testTranslateIP() throws UnknownHostException {
        String hostIP = "127.0.0.1";
        String realIP = CommonUtils.translateIP(hostIP);

        assertTrue(hostIP != realIP);

        hostIP = "localhost";
        realIP = CommonUtils.translateIP(hostIP);
        assertTrue(hostIP != realIP);

        hostIP = "192.168.1.34";
        realIP = CommonUtils.translateIP(hostIP);
        assertEquals(hostIP, realIP);
    }

    @Test
    public void testurlEncodeForSpaces() {
        assertEquals("%20a%20", CommonUtils.urlEncodeForSpaces(new char[] {' ', 'a', ' '}));
    }

    @Test
    public void testTrim0() {
        assertEquals("99.99", CommonUtils.formatCUBRIDNumber(new BigDecimal("000099.9900")));
        //		assertEquals("-99.99", CommonTool.trim0OfNumeric("-000099.9900"));
        //		assertEquals("0", CommonTool.trim0OfNumeric("0000.00"));
        //		assertEquals("99", CommonTool.trim0OfNumeric("000099"));
        //		assertEquals("-99", CommonTool.trim0OfNumeric("-000099"));
        //		assertEquals("0.99", CommonTool.trim0OfNumeric("0000.9900"));
        //		assertEquals("-0.99", CommonTool.trim0OfNumeric("-0000.9900"));
        //		assertEquals("99.99", CommonTool.trim0OfNumeric("000099.99"));
        //		assertEquals("99.99", CommonTool.trim0OfNumeric("99.9900"));
        //		assertEquals("-99.99", CommonTool.trim0OfNumeric("-99.99"));
        //		assertEquals("0", CommonTool.trim0OfNumeric("-0000.00"));
        //		assertEquals(
        //				"0.0000000000000000000000000000000000001",
        //				CommonTool.trim0OfNumeric("0.00000000000000000000000000000000000010"));
        //		assertEquals("10000", CommonTool.trim0OfNumeric("10000.00"));
    }

    @Test
    public void testGetBytesFromByteArray() {
        CommonUtils.getBytesFromByteArray(null);
        CommonUtils.getBytesFromByteArray(new Byte[] {12, 11, 22, 33});
    }
}
