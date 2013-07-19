/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly;

import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import java.util.List;
import java.util.ArrayList;


import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.Assert;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.streams.AbstractStreamReader;
import org.glassfish.grizzly.streams.BufferedInput;
import org.glassfish.grizzly.utils.conditions.Condition;
import java.util.concurrent.BlockingQueue;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Basic idea:
 *   1. Set up a Grizzly client server connection.
 *   2. Create a Writer and write everything to it (using Checkers).
 *   3. Create a Reader out of the buffers generated by the writer.
 *   4. Check that the data that was written is what's received.
 *      This is done by having the client adding the checkers to a queue.
 *      The server then pops these checkers off the queue and calls their
 *      readAndCheck methods.
 *
 *    @author Ken Cavanaugh
 *    @author John Vieten
 **/
public class ByteBufferStreamsTest extends GrizzlyTestCase {

    public static final int PORT = 7778;
    private static final Logger LOGGER = Grizzly.logger(ByteBufferStreamsTest.class);
    private final FutureImpl<Boolean> poisonFuture = SafeFutureImpl.<Boolean>create();
    private Connection clientconnection = null;
    private TCPNIOTransport servertransport = null;
    private StreamWriter clientWriter = null;
    private final BlockingQueue<Checker> checkerQueue = DataStructures.getLTQInstance(Checker.class);
    private TCPNIOTransport clienttransport;

    interface Checker {

        void write(StreamWriter writer) throws IOException;

        void readAndCheck(StreamReader reader) throws IOException;

        long operations();

        long byteSize();
    }

    abstract static class CheckerBase implements Checker {

        private String name;

        public CheckerBase() {
            String className = this.getClass().getName();
            int index = className.indexOf('$');
            name = className.substring(index + 1);
        }

        public abstract String value();

        @Override
        public String toString() {
            return name + "[" + value() + "]";
        }

        protected void werrMsg(Object obj, Throwable thr) {
            LOGGER.log(Level.SEVERE, "###Checker({0}).write: Caught {1} at parameter {2}",
                    new Object[]{toString(), thr, obj});
        }

        protected void rerrMsg(Object obj, Throwable thr) {
            LOGGER.log(Level.SEVERE, "###Checker({0}).readAndCheck: Caught {1} at parameter {2}",
                    new Object[]{toString(), thr, obj});
        }

        public void wmsg() {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.SEVERE, "Write:{0}", toString());
            }
        }

        public void rmsg() {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.SEVERE, "ReadAndCheck:{0}", toString());
            }
        }

        @Override
        public long operations() {
            return 1;
        }
    }

    static class CompositeChecker extends CheckerBase {

        private List<Checker> checkers;

        @Override
        public String value() {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Checker ch : checkers) {
                if (first) {
                    first = false;
                } else {
                    sb.append(' ');
                }

                sb.append(ch.toString());
            }

            return sb.toString();
        }

        public CompositeChecker(Checker... args) {
            checkers = new ArrayList<Checker>();
            checkers.addAll(Arrays.asList(args));
        }

        public void add(Checker ch) {
            checkers.add(ch);
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            Checker ch = null;

            try {
                for (Checker checker : checkers) {
                    ch = checker;
                    ch.write(writer);
                }
            } catch (Error err) {
                werrMsg(ch, err);
                throw err;
            } catch (RuntimeException exc) {
                werrMsg(ch, exc);
                throw exc;
            }
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            Checker ch = null;

            try {
                for (Checker checker : checkers) {
                    ch = checker;
                    ch.readAndCheck(reader);
                }
            } catch (Error err) {
                rerrMsg(ch, err);
                throw err;
            } catch (RuntimeException exc) {
                rerrMsg(ch, exc);
                throw exc;
            }
        }

        @Override
        public long operations() {
            long sum = 0;
            for (Checker ch : checkers) {
                sum += ch.operations();
            }

            return sum;
        }

        @Override
        public long byteSize() {
            long sum = 0;
            for (Checker ch : checkers) {
                sum += ch.byteSize();
            }

            return sum;
        }
    }

    static class RepeatedChecker extends CheckerBase {

        private int count;
        private Checker checker;

        @Override
        public String value() {
            return count + " " + checker.toString();
        }

        public RepeatedChecker(int count, Checker checker) {
            this.count = count;
            this.checker = checker;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            int ctr = 0;

            try {
                for (ctr = 0; ctr < count; ctr++) {
                    checker.write(writer);
                }
            } catch (Error err) {
                werrMsg(ctr, err);
                throw err;
            } catch (RuntimeException exc) {
                werrMsg(ctr, exc);
                throw exc;
            }
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            int ctr = 0;

            try {
                for (ctr = 0; ctr < count; ctr++) {
                    checker.readAndCheck(reader);
                }
            } catch (Error err) {
                rerrMsg(ctr, err);
                throw err;
            } catch (RuntimeException exc) {
                rerrMsg(ctr, exc);
                throw exc;
            }
        }

        @Override
        public long operations() {
            return count * checker.operations();
        }

        @Override
        public long byteSize() {
            return count * checker.byteSize();
        }
    }

    static class ByteChecker extends CheckerBase {

        private byte data;

        @Override
        public String value() {
            return "" + data;
        }

        public ByteChecker(byte arg) {
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            writer.writeByte(data);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            byte value = reader.readByte();
            Assert.assertEquals(data, value);
        }

        @Override
        public long byteSize() {
            return 1;
        }
    }

    static class BooleanChecker extends CheckerBase {

        private boolean data;

        @Override
        public String value() {
            return "" + data;
        }

        public BooleanChecker(boolean arg) {
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            writer.writeBoolean(data);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            boolean value = reader.readBoolean();
            Assert.assertTrue(value == data);
        }

        @Override
        public long byteSize() {
            return 1;
        }
    }

    static class CharChecker extends CheckerBase {

        private char data;

        @Override
        public String value() {
            return "" + data;
        }

        public CharChecker(char arg) {
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            writer.writeChar(data);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            char value = reader.readChar();
            Assert.assertEquals(data, value);
        }

        @Override
        public long byteSize() {
            return 2;
        }
    }

    static class ShortChecker extends CheckerBase {

        private short data;

        @Override
        public String value() {
            return "" + data;
        }

        public ShortChecker(short arg) {
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            writer.writeShort(data);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            short value = reader.readShort();
            Assert.assertEquals(data, value);
        }

        @Override
        public long byteSize() {
            return 2;
        }
    }

    static class IntChecker extends CheckerBase {

        private int data;

        @Override
        public String value() {
            return "" + data;
        }

        public IntChecker(int arg) {
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            writer.writeInt(data);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            int value = reader.readInt();
            Assert.assertEquals(data, value);
        }

        @Override
        public long byteSize() {
            return 4;
        }
    }

    static class LongChecker extends CheckerBase {

        private long data;

        @Override
        public String value() {
            return "" + data;
        }

        public LongChecker(long arg) {
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            writer.writeLong(data);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            long value = reader.readLong();
            Assert.assertEquals(data, value);
        }

        @Override
        public long byteSize() {
            return 8;
        }
    }

    static class FloatChecker extends CheckerBase {

        private float data;

        @Override
        public String value() {
            return "" + data;
        }

        public FloatChecker(float arg) {
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            writer.writeFloat(data);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            float value = reader.readFloat();
            Assert.assertEquals(data, value);
        }

        @Override
        public long byteSize() {
            return 4;
        }
    }

    static class DoubleChecker extends CheckerBase {

        private double data;

        @Override
        public String value() {
            return "" + data;
        }

        public DoubleChecker(double arg) {
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            writer.writeDouble(data);

        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            double value = reader.readDouble();
            Assert.assertEquals(data, value);
        }

        @Override
        public long byteSize() {
            return 8;
        }
    }

    static class BooleanArrayChecker extends CheckerBase {

        private boolean data;
        private int size;

        @Override
        public String value() {
            return size + ":" + data;
        }

        public BooleanArrayChecker(int size, boolean arg) {
            this.size = size;
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            boolean[] value = new boolean[size];
            for (int ctr = 0; ctr < size; ctr++) {
                value[ctr] = data;
            }
            writer.writeBooleanArray(value);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            boolean[] value = new boolean[size];
            reader.readBooleanArray(value);
            for (int ctr = 0; ctr < size; ctr++) {
                Assert.assertTrue(value[ctr] == data);
            }
        }

        @Override
        public long byteSize() {
            return size;
        }
    }

    static class ByteArrayChecker extends CheckerBase {

        private byte data;
        private int size;

        @Override
        public String value() {
            return size + ":" + data;
        }

        public ByteArrayChecker(int size, byte arg) {
            this.size = size;
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            byte[] value = new byte[size];
            for (int ctr = 0; ctr < size; ctr++) {
                value[ctr] = data;
            }
            writer.writeByteArray(value);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            byte[] value = new byte[size];
            reader.readByteArray(value);
            try {
                for (int ctr = 0; ctr < size; ctr++) {
                    Assert.assertEquals(data, value[ctr]);
                }
            } catch (Error err) {
                rerrMsg(size, err);
                throw err;
            } catch (RuntimeException exc) {
                rerrMsg(size, exc);
                throw exc;
            }
        }

        @Override
        public long byteSize() {
            return size;
        }
    }

    static class CharArrayChecker extends CheckerBase {

        private char data;
        private int size;

        @Override
        public String value() {
            return size + ":" + data;
        }

        public CharArrayChecker(int size, char arg) {
            this.size = size;
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            char[] value = new char[size];
            for (int ctr = 0; ctr < size; ctr++) {
                value[ctr] = data;
            }
            writer.writeCharArray(value);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            char[] value = new char[size];
            reader.readCharArray(value);
            try {
                for (int ctr = 0; ctr < size; ctr++) {
                    Assert.assertEquals(data, value[ctr]);
                }
            } catch (Error err) {
                rerrMsg(size, err);
                throw err;
            } catch (RuntimeException exc) {
                rerrMsg(size, exc);
                throw exc;
            }
        }

        @Override
        public long byteSize() {
            return 2 * size;
        }
    }

    static class ShortArrayChecker extends CheckerBase {

        private short data;
        private int size;

        @Override
        public String value() {
            return size + ":" + data;
        }

        public ShortArrayChecker(int size, short arg) {
            this.size = size;
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            short[] value = new short[size];
            for (int ctr = 0; ctr < size; ctr++) {
                value[ctr] = data;
            }
            writer.writeShortArray(value);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            short[] value = new short[size];
            reader.readShortArray(value);
            try {
                for (int ctr = 0; ctr < size; ctr++) {
                    Assert.assertEquals(data, value[ctr]);
                }
            } catch (Error err) {
                rerrMsg(size, err);
                throw err;
            } catch (RuntimeException exc) {
                rerrMsg(size, exc);
                throw exc;
            }
        }

        @Override
        public long byteSize() {
            return 2 * size;
        }
    }

    static class IntArrayChecker extends CheckerBase {

        private int data;
        private int size;

        @Override
        public String value() {
            return size + ":" + data;
        }

        public IntArrayChecker(int size, int arg) {
            this.size = size;
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            int[] value = new int[size];
            for (int ctr = 0; ctr < size; ctr++) {
                value[ctr] = data;
            }
            writer.writeIntArray(value);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            int[] value = new int[size];
            reader.readIntArray(value);
            try {
                for (int ctr = 0; ctr < size; ctr++) {
                    Assert.assertEquals(data, value[ctr]);
                }
            } catch (Error err) {
                rerrMsg(size, err);
                throw err;
            } catch (RuntimeException exc) {
                rerrMsg(size, exc);
                throw exc;
            }
        }

        @Override
        public long byteSize() {
            return 4 * size;
        }
    }

    static class LongArrayChecker extends CheckerBase {

        private long data;
        private int size;

        @Override
        public String value() {
            return size + ":" + data;
        }

        public LongArrayChecker(int size, long arg) {
            this.size = size;
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            long[] value = new long[size];

            try {
                for (int ctr = 0; ctr < size; ctr++) {
                    value[ctr] = data;
                }
                writer.writeLongArray(value);
            } catch (Error err) {
                werrMsg(size, err);
                throw err;
            } catch (RuntimeException exc) {
                werrMsg(size, exc);
                throw exc;
            }
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            long[] value = new long[size];
            reader.readLongArray(value);

            int ctr = 0;
            try {
                for (; ctr < size; ctr++) {
                    Assert.assertEquals(data, value[ctr]);
                }
            } catch (Error err) {
                rerrMsg(ctr, err);
                throw err;
            } catch (RuntimeException exc) {
                rerrMsg(ctr, exc);
                throw exc;
            }
        }

        @Override
        public long byteSize() {
            return 8 * size;
        }
    }

    static class FloatArrayChecker extends CheckerBase {

        private float data;
        private int size;

        @Override
        public String value() {
            return size + ":" + data;
        }

        public FloatArrayChecker(int size, float arg) {
            this.size = size;
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            float[] value = new float[size];
            for (int ctr = 0; ctr < size; ctr++) {
                value[ctr] = data;
            }
            writer.writeFloatArray(value);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            float[] value = new float[size];
            reader.readFloatArray(value);
            try {
                for (int ctr = 0; ctr < size; ctr++) {
                    Assert.assertEquals(data, value[ctr]);
                }
            } catch (Error err) {
                rerrMsg(size, err);
                throw err;
            } catch (RuntimeException exc) {
                rerrMsg(size, exc);
                throw exc;
            }
        }

        @Override
        public long byteSize() {
            return 4 * size;
        }
    }

    static class DoubleArrayChecker extends CheckerBase {

        private double data;
        private int size;

        @Override
        public String value() {
            return size + ":" + data;
        }

        public DoubleArrayChecker(int size, double arg) {
            this.size = size;
            data = arg;
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            double[] value = new double[size];
            for (int ctr = 0; ctr < size; ctr++) {
                value[ctr] = data;
            }
            writer.writeDoubleArray(value);
        }

        @Override
        public void readAndCheck(StreamReader reader) throws IOException {
            rmsg();
            double[] value = new double[size];
            reader.readDoubleArray(value);
            try {
                for (int ctr = 0; ctr < size; ctr++) {
                    Assert.assertEquals(data, value[ctr]);
                }
            } catch (Error err) {
                rerrMsg(size, err);
                throw err;
            } catch (RuntimeException exc) {
                rerrMsg(size, exc);
                throw exc;
            }
        }

        @Override
        public long byteSize() {
            return 8 * size;
        }
    }

    /**
     * Used to mark end of checking.
     *
     */
    static class PoisonChecker extends CheckerBase {

        private byte data = 1;

        @Override
        public String value() {
            return "Poison";
        }

        @Override
        public void write(StreamWriter writer) throws IOException {
            wmsg();
            writer.writeByte(data);
        }

        @Override
        public void readAndCheck(StreamReader reader) {
        }

        @Override
        public long byteSize() {
            return 1;
        }
    }

    // How best to construct checkers for running tests?
    //
    // use a series of private methods, each of which returns a Checker
    // comp( list )
    // rep( count, checker )
    // boolean      z
    // byte         b
    // char         c
    // short        s   
    // int          i
    // long         l
    // float        f
    // double       d
    // var octet    v
    // a for array
    private static Checker comp(Checker... arg) {
        return new CompositeChecker(arg);
    }

    private static Checker rep(int count, Checker ch) {
        return new RepeatedChecker(count, ch);
    }

    private static Checker z(boolean arg) {
        return new BooleanChecker(arg);
    }

    private static Checker b(byte arg) {
        return new ByteChecker(arg);
    }

    private static Checker c(char arg) {
        return new CharChecker(arg);
    }

    private static Checker s(short arg) {
        return new ShortChecker(arg);
    }

    private static Checker i(int arg) {
        return new IntChecker(arg);
    }

    private static Checker l(long arg) {
        return new LongChecker(arg);
    }

    private static Checker f(float arg) {
        return new FloatChecker(arg);
    }

    private static Checker d(double arg) {
        return new DoubleChecker(arg);
    }

    private static Checker za(int size, boolean arg) {
        return new BooleanArrayChecker(size, arg);
    }

    private static Checker ba(int size, byte arg) {
        return new ByteArrayChecker(size, arg);
    }

    private static Checker ca(int size, char arg) {
        return new CharArrayChecker(size, arg);
    }

    private static Checker sa(int size, short arg) {
        return new ShortArrayChecker(size, arg);
    }

    private static Checker ia(int size, int arg) {
        return new IntArrayChecker(size, arg);
    }

    private static Checker la(int size, long arg) {
        return new LongArrayChecker(size, arg);
    }

    private static Checker fa(int size, float arg) {
        return new FloatArrayChecker(size, arg);
    }

    private static Checker da(int size, double arg) {
        return new DoubleArrayChecker(size, arg);
    }

    public void testStreaming() throws Exception {
        // comment this test out until threading bug is found.
        //

        //testPrimitives
        int REPEAT_COUNT = 4;
        Checker ch =
                rep(REPEAT_COUNT, comp(z(true), b((byte) 46), c('A'),
                s((short) 2343), i(231231), l(-789789789789L),
                f((float) 123.23), d(2321.3232213)));
        send(ch);
        //testReaderWriter
        ch = rep(REPEAT_COUNT, comp(rep(10, l(23)), rep(20, l(32123)),
                rep(30, l(7483848374L)), rep(101, l(0))));

        send(ch);

        // testByte
        ch = rep(REPEAT_COUNT, b((byte) 23));
        send(ch);
        //testShort
        ch = rep(REPEAT_COUNT, s((short) 43));
        send(ch);
        //testInt
        ch = rep(REPEAT_COUNT, i(11));
        send(ch);
        //testLong
        ch = rep(REPEAT_COUNT, l(2378878));
        send(ch);
        ch = rep(REPEAT_COUNT, z(true));
        send(ch);
        ch = rep(REPEAT_COUNT, z(false));
        send(ch);

        ch = rep(REPEAT_COUNT, comp(
                la(913, 1234567879123456789L),
                ba(5531, (byte) 39),
                ca(5792, 'A'),
                sa(3324, (short) 27456),
                ia(479, 356789),
                la(4123, 49384892348923489L),
                fa(5321, (float) 12.7),
                da(2435, 45921.45891)));


        send(ch);

        // end
        Future future = send(new PoisonChecker());
        future.get(10, TimeUnit.SECONDS);
        
        // test streaming
        MemoryManager alloc = MemoryManager.DEFAULT_MEMORY_MANAGER;;
        byte[] testdata = new byte[500];


        for (int ctr = 0; ctr < testdata.length; ctr++) {
            testdata[ctr] = (byte) ((ctr + 10) & 255);
        }

        final BufferedInput source = new BufferedInput() {
            @Override
            protected void onOpenInputSource() throws IOException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            protected void onCloseInputSource() throws IOException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };

        AbstractStreamReader reader = new AbstractStreamReader(null, source) {

            @Override
            public GrizzlyFuture<Integer> notifyCondition(Condition condition,
                    CompletionHandler<Integer> completionHandler) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };

        Buffer buffer1 = alloc.allocate(125);
        buffer1.put(testdata, 0, 125);
        Buffer buffer2 = alloc.allocate(125);
        buffer2.put(testdata, 125, 125);
        Buffer buffer3 = alloc.allocate(125);
        buffer3.put(testdata, 250, 125);
        Buffer buffer4 = alloc.allocate(125);
        buffer4.put(testdata, 375, 125);

        source.append(buffer1.flip());
        source.append(buffer2.flip());
        source.append(buffer3.flip());
        source.append(buffer4.flip());

        byte[] checkArray = new byte[500];
        try {
            reader.readByteArray(checkArray, 0, 500);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Data generate error", e);
        }

        Assert.assertTrue(Arrays.equals(checkArray, testdata));
    }

    @Override
    public void setUp() {
        setupServer();
        setupClient();
    }

    @Override
    public void tearDown() {

        try {
            poisonFuture.get(20, TimeUnit.SECONDS);
            clientWriter.close();
            clientconnection.closeSilently();
            servertransport.stop();
            clienttransport.stop();

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Close", ex);
        }
    }

    public void setupServer() {
        servertransport = TCPNIOTransportBuilder.newInstance().build();
        // no use for default memorymanager
        servertransport.configureStandalone(true);

        try {
            final TCPNIOServerConnection serverConnection = servertransport.bind(PORT);
            servertransport.start();

            // Start echo server thread
            startEchoServerThread(servertransport, serverConnection);

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Server start error", ex);
        }

    }

    private Future<Integer> send(final Checker checker) throws IOException {
        checkerQueue.add(checker);
        checker.write(clientWriter);
        final Future<Integer> result = clientWriter.flush();
        return result;
    }

    public void setupClient() {

        clienttransport = TCPNIOTransportBuilder.newInstance().build();
        try {

            clienttransport.start();
            clienttransport.configureBlocking(false);
            clienttransport.configureStandalone(true);
            
            Future<Connection> future =
                    clienttransport.connect("localhost", PORT);
            clientconnection = future.get(10, TimeUnit.SECONDS);
            assertTrue(clientconnection != null);

            clientconnection.configureStandalone(true);
            clientWriter =
                    ((StandaloneProcessor) clientconnection.getProcessor()).
                    getStreamWriter(clientconnection);
            
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Client start error", ex);
        }
    }

    private void startEchoServerThread(final TCPNIOTransport transport,
            final TCPNIOServerConnection serverConnection) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                while (!transport.isStopped()) {
                    try {
                        Future<Connection> acceptFuture = serverConnection.accept();
                        Connection connection = acceptFuture.get(10, TimeUnit.SECONDS);
                        assertTrue(acceptFuture.isDone());
                        connection.configureStandalone(true);

                        StreamReader reader =
                                ((StandaloneProcessor) connection.getProcessor()).
                                getStreamReader(connection);
                        try {
                            Checker checker;
                            while ((checker = checkerQueue.poll(30, TimeUnit.SECONDS)) != null) {
                                if (checker instanceof PoisonChecker) {
                                    poisonFuture.result(Boolean.TRUE);
                                    return;
                                }

                                if (LOGGER.isLoggable(Level.FINEST)) {
                                    LOGGER.log(Level.FINEST, "reader.availableDataSize():{0},{1}",
                                            new Object[]{reader.available(), checker.byteSize()});
                                }

                                Future f = reader.notifyAvailable((int) checker.byteSize());
                                try {
                                    f.get(10, TimeUnit.SECONDS);
                                } catch (Exception e) {
                                    poisonFuture.failure(
                                            new Exception("Exception waiting on checker: " +
                                            checker + " size: " + checker.byteSize(), e));
                                }

                                assertTrue(f.isDone());

                                checker.readAndCheck(reader);
                            }




                        } catch (Throwable e) {
                            LOGGER.log(Level.WARNING,
                                    "Error working with accepted connection", e);
                        } finally {
                            connection.closeSilently();
                        }

                    } catch (Exception e) {
                        if (!transport.isStopped()) {
                            LOGGER.log(Level.WARNING,
                                    "Error accepting connection", e);
                            assertTrue("Error accepting connection", false);
                        }
                    }
                }
            }
        }).start();
    }
}
