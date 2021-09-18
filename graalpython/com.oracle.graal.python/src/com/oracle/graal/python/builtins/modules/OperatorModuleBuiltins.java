/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = OperatorModuleBuiltins.MODULE_NAME)
public class OperatorModuleBuiltins extends PythonBuiltins {

    protected static final String MODULE_NAME = "_operator";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return OperatorModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "truth", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class TruthNode extends PythonUnaryBuiltinNode {

        @Specialization
        public boolean doBoolean(boolean value) {
            return value;
        }

        @Specialization
        public boolean doNone(@SuppressWarnings("unused") PNone value) {
            return false;
        }

        @Specialization
        public boolean doInt(long value) {
            return value != 0;
        }

        @Specialization
        @TruffleBoundary
        public boolean doPInt(PInt value) {
            return !value.getValue().equals(BigInteger.ZERO);
        }

        @Specialization
        public boolean doDouble(double value) {
            return value != 0;
        }

        @Specialization
        public boolean doString(String value) {
            return !value.isEmpty();
        }

        private @Child LookupAndCallUnaryNode boolNode;
        private @Child LookupAndCallUnaryNode lenNode;

        @Fallback
        public boolean doObject(VirtualFrame frame, Object value) {
            if (boolNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                boolNode = insert((LookupAndCallUnaryNode.create(SpecialMethodNames.__BOOL__)));
            }
            Object result = boolNode.executeObject(frame, value);
            if (result != PNone.NO_VALUE) {
                return (boolean) result;
            }
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert((LookupAndCallUnaryNode.create(SpecialMethodNames.__LEN__)));
            }

            result = lenNode.executeObject(frame, value);
            if (result == PNone.NO_VALUE) {
                return false;
            }
            return (int) result != 0;
        }
    }

    @Builtin(name = "getitem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodSlot.class)
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        public static Object doDict(VirtualFrame frame, PDict dict, Object item,
                        @Cached DictBuiltins.GetItemNode getItem) {
            return getItem.execute(frame, dict, item);
        }

        @Specialization(guards = "!isPString(value)")
        public static Object doSequence(VirtualFrame frame, PSequence value, Object index,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(frame, getStorage.execute(value), index);
        }

        @Specialization
        public Object doObject(VirtualFrame frame, Object value, Object index,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "GetItem") LookupSpecialMethodSlotNode lookupGetItem,
                        @Cached CallBinaryMethodNode callGetItem) {
            Object method = lookupGetItem.execute(frame, getClassNode.execute(value), value);
            if (method == PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, value);
            }
            return callGetItem.executeObject(frame, method, value, index);
        }
    }

    // _compare_digest
    @Builtin(name = "_compare_digest", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class CompareDigestNode extends PythonBinaryBuiltinNode {

        @Specialization
        public boolean compare(Object left, Object right,
                        @Cached CastToJavaStringNode cast,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            try {
                String leftString = cast.execute(left);
                String rightString = cast.execute(right);
                return tscmp(leftString, rightString);
            } catch (CannotCastException e) {
                if (!bufferAcquireLib.hasBuffer(left) || !bufferAcquireLib.hasBuffer(right)) {
                    throw raise(TypeError, "unsupported operand types(s) or combination of types: '%p' and '%p'", left, right);
                }
                Object leftBuffer = bufferAcquireLib.acquireReadonly(left);
                try {
                    Object rightBuffer = bufferAcquireLib.acquireReadonly(right);
                    try {
                        return tscmp(bufferLib.getCopiedByteArray(leftBuffer), bufferLib.getCopiedByteArray(rightBuffer));
                    } finally {
                        bufferLib.release(rightBuffer);
                    }
                } finally {
                    bufferLib.release(leftBuffer);
                }
            }
        }

        // Comparison that's safe against timing attacks
        @TruffleBoundary
        private static boolean tscmp(String leftIn, String right) {
            String left = leftIn;
            int result = 0;
            if (left.length() != right.length()) {
                left = right;
                result = 1;
            }
            for (int i = 0; i < left.length(); i++) {
                result |= left.charAt(i) ^ right.charAt(i);
            }
            return result == 0;
        }

        @TruffleBoundary
        private static boolean tscmp(byte[] leftIn, byte[] right) {
            byte[] left = leftIn;
            int result = 0;
            if (left.length != right.length) {
                left = right;
                result = 1;
            }
            for (int i = 0; i < left.length; i++) {
                result |= left[i] ^ right[i];
            }
            return result == 0;
        }
    }

    @Builtin(name = "index", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object asIndex(VirtualFrame frame, Object value,
                        @Cached PyNumberIndexNode index) {
            return index.execute(frame, value);
        }
    }
}
