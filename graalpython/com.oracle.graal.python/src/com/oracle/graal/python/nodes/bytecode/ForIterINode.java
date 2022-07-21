/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.iterator.PIntRangeIterator;
import com.oracle.graal.python.compiler.QuickeningTypes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

/**
 * Obtains the next value of an iterator. When the iterator is exhausted it returns {@code null}. It
 * never raises {@code StopIteration}.
 */
@GenerateUncached
public abstract class ForIterINode extends PNodeWithContext {
    public abstract boolean execute(Frame frame, Object iterator, int stackTop) throws QuickeningGeneralizeException;

    @Specialization
    boolean doIntRange(VirtualFrame frame, PIntRangeIterator iterator, int stackTop,
                    @Cached("createCountingProfile()") LoopConditionProfile conditionProfile) {
        if (conditionProfile.profile(iterator.hasNextInt())) {
            frame.setInt(stackTop, iterator.nextInt());
            return true;
        }
        iterator.setExhausted();
        return false;
    }

    // TODO list, tuple, enumerate, dict keys, dict values, dict items, string, bytes

    @Specialization
    boolean doGeneric(VirtualFrame frame, Object iterator, int stackTop,
                    @Cached GetClassNode getClassNode,
                    @Cached(parameters = "Next") LookupSpecialMethodSlotNode lookupNext,
                    @Cached CallUnaryMethodNode callNext,
                    @Cached IsBuiltinClassProfile stopIterationProfile,
                    @Cached PRaiseNode raiseNode) throws QuickeningGeneralizeException {
        Object nextMethod = lookupNext.execute(frame, getClassNode.execute(iterator), iterator);
        if (nextMethod == PNone.NO_VALUE) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, iterator);
        }
        try {
            Object res = callNext.executeObject(frame, nextMethod, iterator);
            if (res instanceof Integer) {
                frame.setInt(stackTop, (int) res);
                return true;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // TODO other types
                frame.setObject(stackTop, res);
                throw new QuickeningGeneralizeException(QuickeningTypes.OBJECT);
            }
        } catch (PException e) {
            e.expectStopIteration(stopIterationProfile);
            return false;
        }
    }

    public static ForIterINode create() {
        return ForIterINodeGen.create();
    }

    public static ForIterINode getUncached() {
        return ForIterINodeGen.getUncached();
    }
}
