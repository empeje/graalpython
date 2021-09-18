/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRWPair;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.PEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READ1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READINTO1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITE;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_UNINIT;
import static com.oracle.graal.python.nodes.ErrorMessages.THE_S_OBJECT_IS_BEING_GARBAGE_COLLECTED;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.statement.ExceptionHandlingStatementNode.chainExceptions;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PBufferedRWPair)
public class BufferedRWPairBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedRWPairBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 3, parameterNames = {"$self", "reader", "writer", "buffer_size"})
    @ArgumentClinic(name = "buffer_size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedRWPairBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        public PNone doInit(VirtualFrame frame, PRWPair self, Object reader, Object writer, int bufferSize,
                        @Cached IOBaseBuiltins.CheckReadableNode checkReadableNode,
                        @Cached IOBaseBuiltins.CheckWritableNode checkWritableNode,
                        @Cached BufferedReaderBuiltins.BufferedReaderInit initReaderNode,
                        @Cached BufferedWriterBuiltins.BufferedWriterInit initWriterNode) {
            checkReadableNode.call(frame, reader);
            checkWritableNode.call(frame, writer);
            self.setReader(factory().createBufferedReader(PBufferedReader));
            initReaderNode.execute(frame, self.getReader(), reader, bufferSize, factory());
            self.setWriter(factory().createBufferedWriter(PBufferedWriter));
            initWriterNode.execute(frame, self.getWriter(), writer, bufferSize, factory());
            return PNone.NONE;
        }
    }

    abstract static class ReaderInitCheckPythonUnaryBuiltinNode extends PythonUnaryBuiltinNode {

        protected static boolean isInit(PRWPair self) {
            return self.getReader() != null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isInit(self)")
        Object error(VirtualFrame frame, PRWPair self) {
            throw raise(ValueError, IO_UNINIT);
        }
    }

    abstract static class WriterInitCheckPythonUnaryBuiltinNode extends PythonUnaryBuiltinNode {

        protected static boolean isInit(PRWPair self) {
            return self.getWriter() != null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isInit(self)")
        Object error(VirtualFrame frame, PRWPair self) {
            throw raise(ValueError, IO_UNINIT);
        }
    }

    abstract static class ReaderInitCheckPythonBinaryBuiltinNode extends PythonBinaryBuiltinNode {

        protected static boolean isInit(PRWPair self) {
            return self.getReader() != null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isInit(self)")
        Object error(VirtualFrame frame, PRWPair self, Object arg) {
            throw raise(ValueError, IO_UNINIT);
        }
    }

    abstract static class WriterInitCheckPythonBinaryBuiltinNode extends PythonBinaryBuiltinNode {

        protected static boolean isInit(PRWPair self) {
            return self.getWriter() != null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isInit(self)")
        Object error(VirtualFrame frame, PRWPair self, Object arg) {
            throw raise(ValueError, IO_UNINIT);
        }
    }

    @Builtin(name = READ, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadNode extends ReaderInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object read(VirtualFrame frame, PRWPair self, Object args,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getReader(), READ, args);
        }
    }

    @Builtin(name = PEEK, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PeekNode extends ReaderInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object peek(VirtualFrame frame, PRWPair self, Object args,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getReader(), PEEK, args);
        }
    }

    @Builtin(name = READ1, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class Read1Node extends ReaderInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object read1(VirtualFrame frame, PRWPair self, Object args,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getReader(), READ1, args);
        }
    }

    @Builtin(name = READINTO, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadIntoNode extends ReaderInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object readInto(VirtualFrame frame, PRWPair self, Object args,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getReader(), READINTO, args);
        }
    }

    @Builtin(name = READINTO1, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadInto1Node extends ReaderInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object readInto1(VirtualFrame frame, PRWPair self, Object args,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getReader(), READINTO1, args);
        }
    }

    @Builtin(name = WRITE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class WriteNode extends WriterInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object write(VirtualFrame frame, PRWPair self, Object args,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getWriter(), WRITE, args);
        }
    }

    @Builtin(name = FLUSH, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends WriterInitCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object doit(VirtualFrame frame, PRWPair self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getWriter(), FLUSH);
        }
    }

    @Builtin(name = READABLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends ReaderInitCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object doit(VirtualFrame frame, PRWPair self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getReader(), READABLE);
        }
    }

    @Builtin(name = WRITABLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends WriterInitCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object doit(VirtualFrame frame, PRWPair self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getWriter(), WRITABLE);
        }
    }

    @Builtin(name = CLOSE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object close(VirtualFrame frame, PRWPair self,
                        @Cached PyObjectCallMethodObjArgs callMethodReader,
                        @Cached PyObjectCallMethodObjArgs callMethodWriter,
                        @Cached ConditionProfile gotException,
                        @Cached BranchProfile hasException) {
            PException writeEx = null;
            if (self.getWriter() != null) {
                try {
                    callMethodWriter.execute(frame, self.getWriter(), CLOSE);
                } catch (PException e) {
                    hasException.enter();
                    writeEx = e;
                }
            } else {
                writeEx = getRaiseNode().raise(ValueError, IO_UNINIT);
            }

            PException readEx;
            if (self.getReader() != null) {
                try {
                    Object res = callMethodReader.execute(frame, self.getReader(), CLOSE);
                    if (gotException.profile(writeEx != null)) {
                        throw writeEx;
                    }
                    return res;
                } catch (PException e) {
                    readEx = e;
                }
            } else {
                readEx = getRaiseNode().raise(ValueError, IO_UNINIT);
            }

            hasException.enter();
            return chainedError(writeEx, readEx, gotException);
        }

        static Object chainedError(PException first, PException second, ConditionProfile gotFirst) {
            if (gotFirst.profile(first != null)) {
                chainExceptions(second.getEscapedException(), first);
                throw second.getExceptionForReraise();
            } else {
                throw second;
            }
        }
    }

    @Builtin(name = ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAttyNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doit(VirtualFrame frame, PRWPair self,
                        @Cached PyObjectCallMethodObjArgs callMethodWriter,
                        @Cached PyObjectCallMethodObjArgs callMethodReader,
                        @CachedLibrary(limit = "1") PythonObjectLibrary isSame,
                        @Cached ConditionProfile isSameProfile) {
            Object res = callMethodWriter.execute(frame, self.getWriter(), ISATTY);
            if (isSameProfile.profile(!isSame.isSame(res, getCore().getFalse()))) {
                /* either True or exception */
                return res;
            }
            return callMethodReader.execute(frame, self.getReader(), ISATTY);
        }
    }

    @Builtin(name = CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.getWriter() != null")
        static Object doit(VirtualFrame frame, PRWPair self,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, self.getWriter(), CLOSED);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(VirtualFrame frame, Object self) {
            throw raise(RuntimeError, THE_S_OBJECT_IS_BEING_GARBAGE_COLLECTED, "BufferedRWPair");
        }
    }
}
