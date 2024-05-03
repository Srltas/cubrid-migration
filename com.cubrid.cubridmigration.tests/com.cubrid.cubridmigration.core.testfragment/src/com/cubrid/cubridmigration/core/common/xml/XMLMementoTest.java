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
package com.cubrid.cubridmigration.core.common.xml;

import com.cubrid.cubridmigration.core.common.CUBRIDIOUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CUBRIDUtil
 *
 * @author JessieHuang
 * @version 1.0 - 2010-01-14
 */
public class XMLMementoTest {
    XMLMemento xml;

    /**
     * init
     *
     * @throws IOException e
     */
    @BeforeEach
    public final void init() throws IOException {
        StringBuffer buf = new StringBuffer();
        buf.append("<MySQL2CUBRID>");
        buf.append("<DataTypeMapping>");
        buf.append("<SourceDataType>");
        buf.append("<type>bit</type>");
        buf.append("<precision>1</precision>");
        buf.append("<scale></scale>");
        buf.append("</SourceDataType>");
        buf.append("<TargetDataType>");
        buf.append("<type>character</type>");
        buf.append("<precision>1</precision>");
        buf.append("<scale></scale>");
        buf.append("</TargetDataType>");
        buf.append("</DataTypeMapping>");
        buf.append("<DataTypeMapping>");
        buf.append("<SourceDataType>");
        buf.append("<type>bit</type>");
        buf.append("<precision>n</precision>");
        buf.append("</SourceDataType>");
        buf.append("<TargetDataType>");
        buf.append("<type>bit</type>");
        buf.append("<precision>n</precision>");
        buf.append("<scale></scale>");
        buf.append("</TargetDataType>");
        buf.append("</DataTypeMapping>");
        buf.append("</MySQL2CUBRID>");
        ByteArrayInputStream stream = new ByteArrayInputStream(buf.toString().getBytes());
        xml = (XMLMemento) XMLMemento.loadMemento(stream);
    }

    /** testCreateChild */
    @Test
    public final void testCreateChild() {
        xml.createChild("abc");
        assertNotNull(xml.getChild("abc"));
    }

    /** testGetChild */
    @Test
    public final void testGetChild() {
        IXMLMemento child = xml.getChild("DataTypeMapping");
        assertNotNull(child);
    }

    /** testGetChildren */
    @Test
    public final void testGetChildren() {
        IXMLMemento[] children = xml.getChildren("DataTypeMapping");
        assertTrue(children.length > 0);
    }

    /** testGetFloat */
    @Test
    public final void testGetFloat() {
        xml.putFloat("num", 1.23f);
        float res = xml.getFloat("num");
        assertTrue(1.23f == res);
        assertNull(xml.getFloat("noexistsattribute"));
    }

    /** testGetBoolean */
    @Test
    public final void testGetBoolean() {
        xml.putBoolean("bool", Boolean.FALSE);
        xml.putBoolean("bool", Boolean.TRUE);
        boolean res = xml.getBoolean("bool");
        assertTrue(res);
        assertFalse(xml.getBoolean("noexistsattribute"));
    }

    /** testGetInteger */
    @Test
    public final void testGetInteger() {
        xml.putInteger("int", 1);
        int res = xml.getInteger("int");
        assertTrue(res == 1);
        assertNull(xml.getInteger("noexistsattribute"));
    }

    /** testGetString */
    @Test
    public final void testGetString() {
        xml.putString("str", "abc");
        String res = xml.getString("str");
        assertEquals("abc", res);
        assertNull(xml.getString("noexistsattribute"));
    }

    /** testGetTextData */
    @Test
    public final void testGetTextData() {
        xml.putTextData("aaaaaaaaaa");
        assertNotNull(xml.getTextData());
    }

    /** testGetAttributeNames */
    @Test
    public final void testGetAttributeNames() {
        xml.putString("str", "abc");
        assertTrue(xml.getAttributeNames().size() > 0);
    }

    /**
     * testGetContents
     *
     * @throws IOException e
     */
    @Test
    public final void testGetContents() throws IOException {
        byte[] bs = xml.getContents();
        assertNotNull(bs);
    }

    /**
     * testGetInputStream
     *
     * @throws IOException e
     */
    @Test
    public final void testGetInputStream() throws IOException {
        InputStream input = xml.getInputStream();
        assertNotNull(input);
    }

    /**
     * testSaveToString
     *
     * @throws IOException e
     */
    @Test
    public final void testSaveToString() throws IOException {
        String str = xml.saveToString();
        assertNotNull(str);
    }

    /**
     * testSaveToFile
     *
     * @throws IOException e
     */
    @Test
    public final void testSaveToFile() throws IOException {
        URL url = ClassLoader.getSystemResource("./");
        String path = CUBRIDIOUtils.IS_OS_WINDOWS ? url.getPath().substring(1) : url.getPath();
        String fileName = path + "testxml.xml";
        xml.saveToFile(fileName);

        XMLMemento newXml = (XMLMemento) XMLMemento.loadMemento(fileName);
        assertNotNull(newXml);

        File file = new File(fileName);
        boolean flag = file.delete();
        assertTrue(flag);
    }

    /**
     * testCreateWriteRoot
     *
     * @throws ParserConfigurationException e
     */
    @Test
    public final void testCreateWriteRoot() throws ParserConfigurationException {
        XMLMemento newXml = XMLMemento.createWriteRoot("test");
        assertNotNull(newXml.getChildren("test"));
    }
}
