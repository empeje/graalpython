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
package com.oracle.graal.python.builtins.modules.ctypes;

import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.ByteArrayStorage;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonObjectLibrary.class)
public class CDataObject extends PythonBuiltinObject {

    /*
     * Hm. Are there CDataObject's which do not need the b_objects member? In this case we probably
     * should introduce b_flags to mark it as present... If b_objects is not present/unused b_length
     * is unneeded as well.
     */

    PtrValue b_ptr; /* pointer to memory block */
    int b_needsfree; /* need _we_ free the memory? */
    CDataObject b_base; /* pointer to base object or NULL */
    int b_size; /* size of memory block in bytes */
    int b_length; /* number of references we need */
    int b_index; /* index of this object into base's b_object list */
    Object b_objects; /* dictionary of references we need to keep, or Py_None */

    /*
     * A default buffer in CDataObject, which can be used for small C types. If this buffer is too
     * small, PyMem_Malloc will be called to create a larger one, and this one is not used.
     *
     * Making CDataObject a variable size object would be a better solution, but more difficult in
     * the presence of PyCFuncPtrObject. Maybe later.
     */
    // Object b_value;

    public CDataObject(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        this.b_ptr = new PtrValue();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return b_ptr.ptr instanceof ByteArrayStorage;
    }

    @ExportMessage
    int getBufferLength() {
        return b_size;
    }

    @ExportMessage
    byte[] getBufferBytes() {
        assert isBuffer();
        byte[] bytes = ((ByteArrayStorage) b_ptr.ptr).value;
        if (b_ptr.offset > 0) {
            return PythonUtils.arrayCopyOfRange(bytes, b_ptr.offset, b_size);
        }
        return bytes;
    }
    /*-
    static int PyCData_NewGetBuffer(Object myself, Py_buffer *view, int flags)
    {
        CDataObject self = (CDataObject *)myself;
        StgDictObject dict = PyObject_stgdict(myself);
        Py_ssize_t i;
    
        if (view == null) return 0;
    
        view.buf = self.b_ptr;
        view.obj = myself;
        view.len = self.b_size;
        view.readonly = 0;
        /* use default format character if not set * /
        view.format = dict.format ? dict.format : "B";
        view.ndim = dict.ndim;
        view.shape = dict.shape;
        view.itemsize = self.b_size;
        if (view.itemsize) {
            for (i = 0; i < view.ndim; ++i) {
                view.itemsize /= dict.shape[i];
            }
        }
        view.strides = NULL;
        view.suboffsets = NULL;
        view.internal = NULL;
        return 0;
    }
    
    /*-
    static PyBufferProcs PyCData_as_buffer = {
            PyCData_NewGetBuffer,
            NULL,
    };
    */
}
