/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nexial.installer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.nexial.installer.Const.PadOption.*;
import static org.nexial.installer.OutputUtils.INSTANCE;

public class OutputUtilsTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void pad() {
        Assert.assertEquals(">        Testing 1 2 3       <", INSTANCE.pad("> ", "Testing 1 2 3", " <", 30, CENTER));
        Assert.assertEquals(">                            <", INSTANCE.pad("> ", "   ", " <", 30, CENTER));
        Assert.assertEquals(">   Testing 1 2 3  <", INSTANCE.pad("> ", "Testing 1 2 3", " <", 20, CENTER));
        Assert.assertEquals("> Testing 1 2 3. D <\n" +
                            "> o not be alarmed <",
                            INSTANCE.pad("> ", "Testing 1 2 3. Do not be alarmed", " <", 20, CENTER));
        Assert.assertEquals("> Testing 1 2 3. D <\n" +
                            "> o not be alarmed <\n" +
                            "> . I repeat, do n <\n" +
                            "> ot be alarmed    <",
                            INSTANCE.pad("> ",
                                         "Testing 1 2 3. Do not be alarmed. I repeat, do not be alarmed",
                                         " <",
                                         20,
                                         RIGHT));
        Assert.assertEquals("> For the last tim <\n" +
                            "> e, stop freaking <\n" +
                            ">             out! <",
                            INSTANCE.pad("> ",
                                         "For the last time, stop freaking out!",
                                         " <",
                                         20,
                                         LEFT));
    }

    @Test
    public void repeatLine() {
        Assert.assertEquals("                                                                                ",
                            INSTANCE.repeatLine(null, 0));
        Assert.assertEquals("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                            INSTANCE.repeatLine("x", 0));
        Assert.assertEquals("xxxxx", INSTANCE.repeatLine("x", 5));
        Assert.assertEquals("xyzxy", INSTANCE.repeatLine("xyz", 5));
    }
}